package com.assessment.platform;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AiJobs {
  final Store db;
  final Identity identity;
  final long budget;

  public AiJobs(Store db, Identity identity, @Value("${app.ai.budget-units}") long budget) {
    this.db = db;
    this.identity = identity;
    this.budget = budget;
  }

  public record Request(
      String purpose,
      String source,
      UUID approvedMapId,
      UUID attemptId,
      AiProvider.Generation generation) {
    public Request(String purpose, String source, UUID approvedMapId, UUID attemptId) {
      this(purpose, source, approvedMapId, attemptId, null);
    }
  }

  @PostMapping("/ai-jobs")
  @Transactional
  public ResponseEntity<?> request(
      @RequestHeader("Idempotency-Key") UUID key, @RequestBody Request request) {
    var a = identity.author();
    if (!Set.of("COMPETENCY", "QUESTION", "REPORT", "FEEDBACK").contains(request.purpose))
      throw new Problem(422, "Purpose not supported by this authoring pipeline");
    String source = request.source;
    Map<String, Object> evaluation = null;
    if (Set.of("REPORT", "FEEDBACK").contains(request.purpose)) {
      evaluation =
          db.one(
              "select * from evaluation_results where attempt_id=? and organization_id=? for"
                  + " update",
              request.attemptId(),
              a.org());
      source = db.encode(new FeedbackEvidence(db).source(request.attemptId(), a.org()));
    }
    if (request.purpose.equals("QUESTION")) {
      var map =
          db.one(
              "select content from ai_artifacts where id=? and organization_id=? and"
                  + " purpose='COMPETENCY' and state='APPROVED'",
              request.approvedMapId,
              a.org());
      var generation =
          request.generation() == null
              ? new AiProvider.Generation("SINGLE", "INTERMEDIATE", 1, "java")
              : request.generation();
      generation.validate();
      source =
          db.encode(
              new AiProvider.QuestionSource(
                  db.decode(map.get("content"), AiProvider.Draft.class), generation));
    }
    if (source == null || source.isBlank() || source.length() > 12000)
      throw new Problem(422, "Source must contain 1–12000 characters");
    String hash = Identity.hash(db.encode(request));
    var prior =
        db.jdbc.queryForList(
            "select id,payload_hash from ai_jobs where organization_id=? and requester_id=? and"
                + " idempotency_key=?",
            a.org(),
            a.id(),
            key);
    if (!prior.isEmpty()) {
      if (!hash.equals(prior.getFirst().get("payload_hash")))
        throw new Problem(409, "Idempotency key conflict");
      return ResponseEntity.accepted().body(Map.of("id", prior.getFirst().get("id")));
    }
    db.jdbc.update(
        "insert into ai_usage_ledger(organization_id) values (?) on conflict do nothing", a.org());
    var usage = db.one("select * from ai_usage_ledger where organization_id=? for update", a.org());
    int reservation = 3000;
    if (Attempts.number(usage, "reserved_units")
            + Attempts.number(usage, "spent_units")
            + reservation
        > budget) throw new Problem(429, "AI budget exhausted");
    db.jdbc.update(
        "update ai_usage_ledger set reserved_units=reserved_units+? where organization_id=?",
        reservation,
        a.org());
    UUID id = UUID.randomUUID();
    db.jdbc.update(
        "insert into"
            + " ai_jobs(id,organization_id,requester_id,purpose,idempotency_key,payload_hash,input,reservation)"
            + " values (?,?,?,?,?,?,?::jsonb,?)",
        id,
        a.org(),
        a.id(),
        request.purpose,
        key,
        hash,
        db.encode(
            Map.of(
                "source",
                source,
                "dependency",
                request.approvedMapId == null ? "" : request.approvedMapId.toString())),
        reservation);
    db.audit(a.org(), a.id(), "AI_JOB_REQUESTED", id);
    if (evaluation != null)
      db.jdbc.update(
          "insert into"
              + " ai_job_evaluations(job_id,organization_id,attempt_id,final_revision,evaluation_revision,consent_revision)"
              + " select ?,?,?,?,?,k.consent_revision from candidate_acknowledgments k join"
              + " assessment_attempts a on a.invitation_id=k.invitation_id where a.id=? and"
              + " k.optional_ai=true",
          id,
          a.org(),
          request.attemptId(),
          evaluation.get("final_revision"),
          evaluation.get("scoring_revision"),
          request.attemptId());
    return ResponseEntity.accepted().body(Map.of("id", id, "statusUrl", "/api/v1/ai-jobs/" + id));
  }

  @GetMapping("/ai-jobs")
  public List<Map<String, Object>> list() {
    var a = identity.staff();
    return db.jdbc.queryForList(
        "select id,purpose,state,attempts,created_at,failure_code from ai_jobs where"
            + " organization_id=? order by created_at desc limit 100",
        a.org());
  }

  @GetMapping("/ai-jobs/{id}")
  public Map<String, Object> job(@PathVariable UUID id) {
    var a = identity.staff();
    return db.one(
        "select id,purpose,state,attempts,created_at,failure_code from ai_jobs where id=? and"
            + " organization_id=?",
        id,
        a.org());
  }

  @PostMapping("/ai-jobs/{id}/cancel")
  @Transactional
  public void cancel(@PathVariable UUID id) {
    var a = identity.author();
    var job =
        db.one("select * from ai_jobs where id=? and organization_id=? for update", id, a.org());
    if (Set.of("QUEUED", "RETRY_WAIT", "RUNNING").contains(job.get("state")))
      db.jdbc.update("update ai_jobs set state='CANCEL_REQUESTED' where id=?", id);
  }

  @GetMapping("/ai-artifacts")
  public List<Map<String, Object>> artifacts() {
    var a = identity.staff();
    return db
        .jdbc
        .queryForList(
            "select id,purpose,state,revision,content,provenance,author_id from ai_artifacts where"
                + " organization_id=? order by created_at desc limit 100",
            a.org())
        .stream()
        .map(
            r -> {
              r.put("content", db.decode(r.get("content"), Map.class));
              r.put("provenance", db.decode(r.get("provenance"), Map.class));
              return r;
            })
        .toList();
  }

  public record Decision(String decision, String reason) {}

  @PostMapping("/ai-artifact-versions/{id}/review-decisions")
  @Transactional
  public void review(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long revision,
      @RequestBody Decision decision) {
    var a = identity.staff();
    var row =
        db.one(
            "select * from ai_artifacts where id=? and organization_id=? for update", id, a.org());
    if (row.get("author_id").equals(a.id()))
      throw new Problem(403, "A different authorized reviewer is required");
    if ("APPROVED".equals(decision.decision()) && row.get("purpose").equals("QUESTION"))
      GeneratedAssessments.approvable(db, row);
    if (Set.of("REPORT", "FEEDBACK").contains(row.get("purpose"))
        && !new FeedbackEvidence(db).current((UUID) row.get("job_id")))
      throw new Problem(409, "Feedback source or consent is stale");
    if (!row.get("state").equals("REVIEW_REQUIRED") || Attempts.number(row, "revision") != revision)
      throw new Problem(409, "Stale or already reviewed artifact");
    if (decision.decision == null
        || !Set.of("APPROVED", "REJECTED").contains(decision.decision)
        || decision.reason == null
        || decision.reason.isBlank()
        || decision.reason.length() > 4000) throw new Problem(422, "Decision and reason required");
    db.jdbc.update(
        "insert into ai_review_decisions values (?,?,?,?,?,?,?,now())",
        UUID.randomUUID(),
        a.org(),
        id,
        a.id(),
        decision.decision,
        decision.reason,
        revision);
    db.jdbc.update(
        "update ai_artifacts set state=?,revision=revision+1 where id=?", decision.decision, id);
    db.audit(a.org(), a.id(), "AI_REVIEW_RECORDED", id);
  }

  @GetMapping("/ai-usage")
  public Map<String, Object> usage() {
    var a = identity.staff();
    var rows =
        db.jdbc.queryForList(
            "select reserved_units,spent_units from ai_usage_ledger where organization_id=?",
            a.org());
    return Map.of(
        "limit",
        budget,
        "unit",
        "conservative application quota units, not currency",
        "usage",
        rows);
  }
}
