package com.assessment.platform;

import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AiWorker {
  final Store db;
  final AiProvider provider;
  final TransactionTemplate tx;

  public AiWorker(Store db, AiProvider provider, TransactionTemplate tx) {
    this.db = db;
    this.provider = provider;
    this.tx = tx;
  }

  boolean permitted(Map<String, Object> job) {
    var members =
        db.jdbc.queryForList(
            "select m.role from organization_members m join users u on u.id=m.user_id where"
                + " m.organization_id=? and m.user_id=? and u.verified=true and m.role in"
                + " ('ADMIN','RECRUITER')",
            job.get("organization_id"),
            job.get("requester_id"));
    if (members.isEmpty()) return false;
    if (Set.of("REPORT", "FEEDBACK").contains(job.get("purpose")))
      return new FeedbackEvidence(db).current((UUID) job.get("id"));
    var input = db.decode(job.get("input"), Map.class);
    String dependency = (String) input.get("dependency");
    return dependency.isEmpty()
        || !db.jdbc
            .queryForList(
                "select id from ai_artifacts where id=? and organization_id=? and state='APPROVED'",
                UUID.fromString(dependency),
                job.get("organization_id"))
            .isEmpty();
  }

  @Scheduled(fixedDelay = 2000)
  public void tick() {
    tx.executeWithoutResult(
        status -> {
          for (var j :
              db.jdbc.queryForList(
                  "select * from ai_jobs where (state='CANCEL_REQUESTED' and (lease_until is null"
                      + " or lease_until<now())) or (state in ('QUEUED','RETRY_WAIT','RUNNING') and"
                      + " (attempts>=3 and (lease_until is null or lease_until<now()) or"
                      + " created_at<now()-interval '15 minutes')) for update skip locked"))
            terminal(
                j,
                j.get("state").equals("CANCEL_REQUESTED") ? "CANCELLED" : "EXPIRED",
                "LEASE_OR_DEADLINE");
        });
    var jobs =
        db.jdbc.queryForList(
            "update ai_jobs set state='RUNNING',attempts=attempts+1,lease_until=now()+interval '60"
                + " seconds' where id in (select id from ai_jobs where ((state in"
                + " ('QUEUED','RETRY_WAIT') and available_at<=now()) or (state='RUNNING' and"
                + " lease_until<now())) and attempts<3 order by created_at for update skip locked"
                + " limit 1) returning *");
    if (jobs.isEmpty()) return;
    var job = jobs.getFirst();
    AiProvider.Output result = null;
    String failure = null;
    boolean retry = false;
    try {
      if (!permitted(job)) throw new Problem(403, "Authorization or dependency revoked");
      String source = (String) db.decode(job.get("input"), Map.class).get("source");
      result = provider.generate((String) job.get("purpose"), source);
      if (Set.of("REPORT", "FEEDBACK").contains(job.get("purpose")))
        FeedbackEvidence.validate(db.decode(source, FeedbackEvidence.Source.class), result.draft());
      else {
        AiValidation.validate((String) job.get("purpose"), source, result.draft());
        if (job.get("purpose").equals("QUESTION")) {
          var spec = db.decode(source, AiProvider.QuestionSource.class);
          var g = spec.generation();
          String type = Set.of("SINGLE", "MULTIPLE").contains(g.kind()) ? g.kind() : "CODING";
          if (result.draft().questions().size() != g.count()
              || result.draft().questions().stream()
                  .anyMatch(
                      q ->
                          !q.type().equals(type)
                              || spec.competencyMap().skills().stream()
                                  .noneMatch(s -> s.id().equals(q.skill()))
                              || q.coding() != null && !q.coding().language().equals(g.language())))
            throw new Problem(422, "Generated questions violate approved constraints");
        }
      }
    } catch (java.io.IOException e) {
      failure = "PROVIDER_TRANSIENT";
      retry = true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      failure = "INTERRUPTED";
      retry = true;
    } catch (Exception e) {
      failure = "VALIDATION_OR_POLICY_FAILED";
    }
    final var output = result;
    final var error = failure;
    final boolean transientFailure = retry;
    tx.executeWithoutResult(
        status -> {
          var current = db.one("select * from ai_jobs where id=? for update", job.get("id"));
          if (!current.get("attempts").equals(job.get("attempts"))
              || !Set.of("RUNNING", "CANCEL_REQUESTED").contains(current.get("state"))) return;
          if (current.get("state").equals("CANCEL_REQUESTED") || !permitted(current)) {
            terminal(current, "CANCELLED", "CANCELLED_OR_REVOKED");
            return;
          }
          if (error != null) {
            if (transientFailure && Attempts.number(current, "attempts") < 3) {
              db.jdbc.update(
                  "update ai_jobs set state='RETRY_WAIT',failure_code=?,available_at=now()+interval"
                      + " '20 seconds',lease_until=null where id=?",
                  error,
                  job.get("id"));
            } else terminal(current, "FAILED", error);
            return;
          }
          if (output == null) {
            terminal(current, "FAILED", "MISSING_PROVIDER_RESULT");
            return;
          }
          UUID artifact = UUID.randomUUID();
          var provenance =
              Map.of(
                  "provider",
                  output.provider(),
                  "model",
                  output.model(),
                  "providerRequestId",
                  output.requestId(),
                  "tokens",
                  output.tokens(),
                  "fake",
                  output.fake(),
                  "inputHash",
                  current.get("payload_hash"),
                  "templateVersion",
                  Set.of("REPORT", "FEEDBACK").contains(current.get("purpose"))
                      ? "feedback-v1"
                      : "authoring-v2",
                  "validatorVersion",
                  "schema-business-v2",
                  "pricing",
                  "UNKNOWN — quota units are not a monetary charge");
          db.jdbc.update(
              "insert into"
                  + " ai_artifacts(id,organization_id,job_id,author_id,purpose,content,provenance)"
                  + " values (?,?,?,?,?,?::jsonb,?::jsonb) on conflict (job_id) do nothing",
              artifact,
              current.get("organization_id"),
              current.get("id"),
              current.get("requester_id"),
              current.get("purpose"),
              db.encode(output.draft()),
              db.encode(provenance));
          terminal(current, "SUCCEEDED", null);
        });
  }

  void terminal(Map<String, Object> job, String state, String failure) {
    db.jdbc.update(
        "update ai_jobs set state=?,failure_code=?,lease_until=null,input='{}'::jsonb where id=?",
        state,
        failure,
        job.get("id"));
    db.jdbc.update(
        "update ai_usage_ledger set reserved_units=reserved_units-?,spent_units=spent_units+? where"
            + " organization_id=?",
        job.get("reservation"),
        Attempts.number(job, "attempts") > 0 ? job.get("reservation") : 0,
        job.get("organization_id"));
  }
}
