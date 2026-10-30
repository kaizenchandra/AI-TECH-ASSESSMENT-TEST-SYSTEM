package com.assessment.platform;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidate")
public class CandidateApi {
  final Store db;
  final Identity identity;
  final Attempts attempts;
  final Clock clock;

  public CandidateApi(Store db, Identity identity, Attempts attempts, Clock clock) {
    this.db = db;
    this.identity = identity;
    this.attempts = attempts;
    this.clock = clock;
  }

  public record Claim(String token) {}

  @PostMapping("/invitations/claim")
  @Transactional
  public Map<String, Object> claim(@RequestBody Claim claim) {
    var a = identity.actor();
    if (!a.verified()) throw new Problem(403, "Verify your email first");
    var inv =
        db.one(
            "select * from invitations where token_hash=? and email=? and organization_id=? for"
                + " update",
            Identity.hash(claim.token),
            a.email(),
            a.org());
    if ((boolean) inv.get("revoked")
        || !Attempts.instant(inv, "expires_at").isAfter(clock.instant()))
      throw new Problem(410, "Invitation expired or revoked");
    db.jdbc.update("update invitations set claimed_by=? where id=?", a.id(), inv.get("id"));
    return Map.of("id", inv.get("id"));
  }

  @GetMapping("/assignments")
  public List<Map<String, Object>> assignments() {
    var a = identity.actor();
    return db
        .jdbc
        .queryForList(
            "select i.id,i.expires_at,i.revoked,i.assessment_version_id,o.name as"
                + " company,v.content,v.job_context,k.optional_ai as consent,t.id as"
                + " attempt_id,t.status from invitations i join organizations o on"
                + " o.id=i.organization_id join assessment_versions v on"
                + " v.id=i.assessment_version_id left join assessment_attempts t on"
                + " t.invitation_id=i.id left join candidate_acknowledgments k on"
                + " k.invitation_id=i.id where i.claimed_by=? order by i.created_at desc limit 100",
            a.id())
        .stream()
        .map(
            r -> {
              var overview =
                  new LinkedHashMap<>(
                      db.decode(r.remove("content"), AssessmentContent.class).candidateOverview());
              overview.put("job", db.decode(r.remove("job_context"), Map.class));
              r.put("assessment", overview);
              r.put("exceptions", ReadinessPolicy.exceptions(db, (UUID) r.get("id")));
              return r;
            })
        .toList();
  }

  public record Checks(
      boolean instructions,
      boolean browser,
      boolean cookies,
      boolean network,
      boolean camera,
      boolean microphone,
      boolean optionalAi,
      boolean secureContext,
      boolean viewport,
      boolean microphoneLevel,
      boolean playback,
      UUID assessmentVersionId,
      String policyVersion) {}

  @PostMapping("/invitations/{id}/acknowledgment")
  @Transactional
  public void acknowledge(@PathVariable UUID id, @RequestBody Checks checks) {
    var a = identity.actor();
    var i =
        db.one(
            "select v.content,i.assessment_version_id from invitations i join assessment_versions v"
                + " on v.id=i.assessment_version_id where i.id=? and i.claimed_by=?",
            id,
            a.id());
    var c = db.decode(i.get("content"), AssessmentContent.class);
    if (!Objects.equals(i.get("assessment_version_id"), checks.assessmentVersionId())
        || !"candidate-policy-v2".equals(checks.policyVersion()))
      throw new Problem(422, "Acknowledge the current assessment and policy versions");
    ReadinessPolicy.validate(db, id, c, db.decode(db.encode(checks), Map.class));
    db.jdbc.update(
        "insert into"
            + " candidate_acknowledgments(invitation_id,candidate_id,disclosure_version,optional_ai,system_checks,acknowledged_at,assessment_version_id,policy_version)"
            + " values (?,?, 'disclosure-v1',?,?::jsonb,?,?,?) on conflict (invitation_id) do"
            + " update set"
            + " system_checks=excluded.system_checks,acknowledged_at=excluded.acknowledged_at,assessment_version_id=excluded.assessment_version_id,policy_version=excluded.policy_version",
        id,
        a.id(),
        checks.optionalAi,
        db.encode(checks),
        Timestamp.from(clock.instant()),
        checks.assessmentVersionId(),
        checks.policyVersion());
  }

  @PostMapping("/invitations/{id}/start")
  public Map<String, Object> start(@PathVariable UUID id, @RequestHeader("X-Tab-ID") UUID tab) {
    return attempts.start(id, tab, identity.actor());
  }

  @PostMapping("/attempts/{id}/resume")
  public Map<String, Object> resume(@PathVariable UUID id, @RequestHeader("X-Tab-ID") UUID tab) {
    return attempts.resume(id, tab, identity.actor().id());
  }

  @PostMapping("/attempts/{id}/heartbeat")
  public Map<String, Object> heartbeat(@PathVariable UUID id, @RequestHeader("X-Tab-ID") UUID tab) {
    return attempts.heartbeat(id, tab, identity.actor().id());
  }

  @PutMapping("/attempts/{id}/answers")
  public Map<String, Object> save(
      @PathVariable UUID id,
      @RequestHeader("X-Tab-ID") UUID tab,
      @RequestHeader("Idempotency-Key") UUID key,
      @RequestBody Attempts.Save save) {
    return attempts.save(id, tab, key, identity.actor().id(), save);
  }

  @PostMapping("/attempts/{id}/submit")
  public Map<String, Object> submit(@PathVariable UUID id, @RequestHeader("X-Tab-ID") UUID tab) {
    return attempts.submit(id, tab, identity.actor().id());
  }

  @PostMapping("/attempts/{id}/run")
  public org.springframework.http.ResponseEntity<Map<String, Object>> run(
      @PathVariable UUID id,
      @RequestHeader("X-Tab-ID") UUID tab,
      @RequestHeader("Idempotency-Key") UUID key,
      @RequestBody Run request) {
    return org.springframework.http.ResponseEntity.accepted()
        .body(
            attempts.runSamples(
                id,
                identity.actor().id(),
                tab,
                request.questionId(),
                request.expectedRevision(),
                key));
  }

  public record Run(UUID questionId, long expectedRevision) {}

  @GetMapping("/executions/{id}")
  public Map<String, Object> execution(@PathVariable UUID id) {
    return attempts.coding.candidateResult(id, identity.actor().id());
  }

  @GetMapping("/ai-disclosures")
  public Map<String, Object> disclosures() {
    return Map.of(
        "version",
        "disclosure-v1",
        "purposes",
        List.of("Optional post-submission feedback, reviewed by an evaluator"),
        "limitations",
        "AI cannot change scores, timing or employment decisions. No camera or microphone data is"
            + " sent to AI. Contact your recruiter for accommodations or human review.",
        "optional",
        true);
  }

  public record Appeal(String reason) {}

  @PostMapping("/attempts/{id}/review-requests")
  @Transactional
  public Map<String, UUID> appeal(@PathVariable UUID id, @RequestBody Appeal request) {
    var a = identity.actor();
    var context = attempts.lock(id, a.id());
    if (request.reason == null || request.reason.isBlank() || request.reason.length() > 4000)
      throw new Problem(422, "A reason of at most 4000 characters is required");
    var open =
        db.jdbc.queryForList(
            "select id,reason from candidate_review_requests where attempt_id=? and candidate_id=?"
                + " and state in ('OPEN','IN_REVIEW') order by created_at desc limit 1",
            id,
            a.id());
    if (!open.isEmpty()) {
      if (open.getFirst().get("reason").equals(request.reason))
        return Map.of("id", (UUID) open.getFirst().get("id"));
      throw new Problem(
          409,
          "A human-review request is already open for this attempt; check its status before"
              + " submitting another");
    }
    if (Objects.requireNonNull(db.jdbc.queryForObject(
            "select count(*) from candidate_review_requests where attempt_id=? and"
                + " created_at>now()-interval '24 hours'",
            Integer.class,
            id))
        >= 10) throw new Problem(429, "Review request limit reached; contact your recruiter");
    UUID appeal = UUID.randomUUID();
    db.jdbc.update(
        "insert into candidate_review_requests(id,candidate_id,attempt_id,reason,organization_id)"
            + " values (?,?,?,?,?)",
        appeal,
        a.id(),
        id,
        request.reason,
        context.row().get("organization_id"));
    db.audit((UUID) context.row().get("organization_id"), a.id(), "HUMAN_REVIEW_REQUESTED", appeal);
    return Map.of("id", appeal);
  }
}
