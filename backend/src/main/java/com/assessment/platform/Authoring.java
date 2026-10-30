package com.assessment.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class Authoring {
  final Store db;
  final Identity identity;
  final Clock clock;
  final String publicUrl;

  public Authoring(
      Store db, Identity identity, Clock clock, @Value("${app.public-url}") String publicUrl) {
    this.db = db;
    this.identity = identity;
    this.clock = clock;
    this.publicUrl = publicUrl;
  }

  @GetMapping("/assessments")
  public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page) {
    var a = identity.staff();
    return db.jdbc.queryForList(
        "select id,title,status,revision,created_at from assessments where organization_id=? order"
            + " by created_at desc limit 50 offset ?",
        a.org(),
        Math.max(0, Math.min(page, 10000)) * 50);
  }

  @PostMapping("/assessments")
  @Transactional
  public Map<String, Object> create(@RequestBody AssessmentContent content) {
    var a = identity.author();
    content.validate();
    UUID id = UUID.randomUUID();
    db.jdbc.update(
        "insert into assessments(id,organization_id,title,author_id,content) values"
            + " (?,?,?,?,?::jsonb)",
        id,
        a.org(),
        content.title(),
        a.id(),
        db.encode(content));
    db.audit(a.org(), a.id(), "ASSESSMENT_DRAFTED", id);
    return Map.of("id", id, "revision", 0);
  }

  @GetMapping("/assessments/{id}")
  public AssessmentContent get(@PathVariable UUID id) {
    var a = identity.staff();
    return db.decode(
        db.one("select content from assessments where id=? and organization_id=?", id, a.org())
            .get("content"),
        AssessmentContent.class);
  }

  @PostMapping("/assessments/{id}/publish")
  @Transactional
  public Map<String, Object> publish(
      @PathVariable UUID id, @RequestHeader("If-Match") long revision) {
    var a = identity.author();
    var row =
        db.one(
            "select * from assessments where id=? and organization_id=? for update", id, a.org());
    if (((Number) row.get("revision")).longValue() != revision
        || !row.get("status").equals("DRAFT"))
      throw new Problem(409, "Stale or already published assessment");
    var content = db.decode(row.get("content"), AssessmentContent.class);
    // Lock source rows so an archive cannot race publication.
    var bankSources =
        db.jdbc.queryForList(
            "select b.archived,v.state from assessment_bank_sources s join question_bank_versions v"
                + " on v.id=s.version_id join question_bank b on b.id=v.question_id where"
                + " s.assessment_id=? for share of b,v",
            id);
    if (bankSources.stream()
        .anyMatch(
            s -> Boolean.TRUE.equals(s.get("archived")) || !"APPROVED".equals(s.get("state"))))
      throw new Problem(409, "A question-bank source is archived or unapproved");
    if (row.get("blueprint_id") != null
        && db.jdbc
            .queryForList(
                "select b.id from assessment_blueprints b join ai_artifacts m on"
                    + " m.id=b.competency_id and m.organization_id=b.organization_id where b.id=?"
                    + " and b.organization_id=? and b.state='APPROVED' and m.state='APPROVED' and"
                    + " m.revision=b.competency_revision",
                row.get("blueprint_id"),
                a.org())
            .isEmpty())
      throw new Problem(409, "Blueprint or competency approval is no longer current");
    if (!db.jdbc
        .queryForList(
            "select s.question_id from assessment_ai_sources s join ai_artifacts a on"
                + " a.id=s.artifact_id where s.assessment_id=? and (a.state<>'APPROVED' or"
                + " a.revision<>s.artifact_revision)",
            id)
        .isEmpty()) throw new Problem(409, "An AI source approval is stale or revoked");
    content.validate();
    var jobContext = Jobs.snapshot(db, id);
    UUID version = UUID.randomUUID();
    for (var question : content.questions())
      if (question.type().equals("CODING") && question.coding() == null)
        throw new Problem(422, "Coding questions require hidden grading tests before publication");
    db.jdbc.update(
        "insert into"
            + " assessment_versions(id,organization_id,assessment_id,version,content,published_by,job_context)"
            + " values (?,?,?,1,?::jsonb,?,?::jsonb)",
        version,
        a.org(),
        id,
        db.encode(content),
        a.id(),
        db.encode(jobContext));
    db.jdbc.update("update assessments set status='PUBLISHED',revision=revision+1 where id=?", id);
    db.audit(a.org(), a.id(), "ASSESSMENT_PUBLISHED", version);
    return Map.of("versionId", version);
  }

  public record Invite(
      @NotNull UUID assessmentId, @Email @NotBlank String email, @NotNull Instant expiresAt) {}

  @PostMapping("/invitations")
  @Transactional
  public Map<String, Object> invite(@Valid @RequestBody Invite request) {
    var a = identity.author();
    var v =
        db.one(
            "select v.* from assessment_versions v join assessments a on a.id=v.assessment_id where"
                + " v.assessment_id=? and v.organization_id=? and a.status='PUBLISHED' order by"
                + " version desc limit 1",
            request.assessmentId(),
            a.org());
    invitationLock(a.org(), request.assessmentId(), request.email());
    if (!db.jdbc
        .queryForList(
            "select i.id from invitations i join assessment_versions v on"
                + " v.id=i.assessment_version_id where i.organization_id=? and v.assessment_id=?"
                + " and i.email=?",
            a.org(),
            request.assessmentId(),
            request.email().trim().toLowerCase(Locale.ROOT))
        .isEmpty())
      throw new Problem(409, "Candidate already invited; use the audited retake workflow");
    return deliver(a, v, request);
  }

  void invitationLock(UUID org, UUID assessment, String email) {
    db.jdbc.queryForList(
        "select pg_advisory_xact_lock(hashtext(?))",
        "invite:" + org + ":" + assessment + ":" + email.trim().toLowerCase(Locale.ROOT));
  }

  Map<String, Object> deliver(Identity.Actor a, Map<String, Object> v, Invite request) {
    db.one(
        "select id from assessments where id=? and organization_id=? and status='PUBLISHED' for"
            + " share",
        request.assessmentId(),
        a.org());
    var sources =
        db.jdbc.queryForList(
            "select b.archived from assessment_bank_sources s join question_bank_versions v on"
                + " v.id=s.version_id join question_bank b on b.id=v.question_id where"
                + " s.assessment_id=? for share of b",
            request.assessmentId());
    if (sources.stream().anyMatch(s -> Boolean.TRUE.equals(s.get("archived"))))
      throw new Problem(409, "An archived question prevents new assignments");
    var content = db.decode(v.get("content"), AssessmentContent.class);
    if (!request.expiresAt().isAfter(clock.instant())
        || request.expiresAt().isAfter(content.latestStart()))
      throw new Problem(422, "Invitation expiry must be within latest start");
    UUID id = UUID.randomUUID();
    String secret = Identity.token();
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    db.jdbc.update(
        "insert into"
            + " invitations(id,organization_id,assessment_version_id,email,token_hash,expires_at)"
            + " values (?,?,?,?,?,?)",
        id,
        a.org(),
        v.get("id"),
        email,
        Identity.hash(secret),
        Timestamp.from(request.expiresAt()));
    String org = (String) db.one("select name from organizations where id=?", a.org()).get("name");
    String body =
        "Invitation from "
            + org
            + "\nAssessment: "
            + content.title()
            + "\nDuration: "
            + content.durationMinutes()
            + " minutes\nAvailable from: "
            + content.availableFrom()
            + "\nExpires: "
            + request.expiresAt()
            + " (UTC)\nDesktop browser, cookies and reliable network required. Camera required: "
            + content.cameraRequired()
            + "; microphone required: "
            + content.microphoneRequired()
            + ".\nOpen: "
            + publicUrl
            + "/#invite="
            + secret
            + "\nRegister with "
            + email
            + " and verify email. Contact your recruiter for support.";
    db.jdbc.update(
        "insert into notifications(id,recipient,subject,body) values (?,?,?,?)",
        UUID.randomUUID(),
        email,
        "Assessment invitation",
        body);
    db.audit(a.org(), a.id(), "INVITATION_CREATED", id);
    return Map.of("id", id, "delivery", "QUEUED");
  }

  public record Retake(Instant expiresAt, String reason) {}

  @PostMapping("/invitations/{id}/retakes")
  @Transactional
  public Map<String, Object> retake(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") UUID key,
      @RequestBody Retake request) {
    var a = identity.author();
    var source =
        db.one(
            "select i.*,v.assessment_id from invitations i join assessment_versions v on"
                + " v.id=i.assessment_version_id where i.id=? and i.organization_id=?",
            id,
            a.org());
    invitationLock(a.org(), (UUID) source.get("assessment_id"), (String) source.get("email"));
    if (request.expiresAt() == null
        || request.reason() == null
        || request.reason().isBlank()
        || request.reason().length() > 2000)
      throw new Problem(422, "Expiry and retake reason required");
    var prior =
        db.jdbc.queryForList(
            "select * from invitation_retakes where organization_id=? and idempotency_key=?",
            a.org(),
            key);
    if (!prior.isEmpty()) {
      var p = prior.getFirst();
      if (!id.equals(p.get("source_id"))
          || !request.reason().equals(p.get("reason"))
          || !request.expiresAt().equals(((Timestamp) p.get("expires_at")).toInstant()))
        throw new Problem(409, "Idempotency key conflict");
      return Map.of("id", p.get("invitation_id"), "delivery", "QUEUED");
    }
    if (Boolean.TRUE.equals(source.get("revoked")))
      throw new Problem(409, "Revoked invitations cannot authorize retakes");
    if (!db.jdbc
        .queryForList(
            "select id from assessment_attempts where invitation_id=? and status in"
                + " ('SUBMITTED','EXPIRED')",
            id)
        .isEmpty()) {
      if (!db.jdbc
          .queryForList(
              "select source_id from invitation_retakes where source_id=? or invitation_id=?",
              id,
              id)
          .isEmpty())
        throw new Problem(409, "One authorized retake per original invitation is permitted");
      var version =
          db.one(
              "select * from assessment_versions where id=? and organization_id=?",
              source.get("assessment_version_id"),
              a.org());
      var result =
          deliver(
              a,
              version,
              new Invite(
                  (UUID) source.get("assessment_id"),
                  (String) source.get("email"),
                  request.expiresAt()));
      db.jdbc.update(
          "insert into"
              + " invitation_retakes(organization_id,source_id,invitation_id,authorized_by,reason,idempotency_key,expires_at)"
              + " values (?,?,?,?,?,?,?)",
          a.org(),
          id,
          result.get("id"),
          a.id(),
          request.reason(),
          key,
          Timestamp.from(request.expiresAt()));
      db.audit(a.org(), a.id(), "RETAKE_AUTHORIZED", id);
      return result;
    }
    throw new Problem(409, "A finalized original attempt is required");
  }

  @GetMapping("/invitations")
  public List<Map<String, Object>> invitations() {
    var a = identity.staff();
    return db.jdbc.queryForList(
        "select id,email,expires_at,revoked,claimed_by from invitations where organization_id=?"
            + " order by created_at desc limit 100",
        a.org());
  }

  @PostMapping("/invitations/{id}/revoke")
  @Transactional
  public void revoke(@PathVariable UUID id) {
    var a = identity.author();
    if (db.jdbc.update(
            "update invitations set revoked=true where id=? and organization_id=?", id, a.org())
        != 1) throw new Problem(404, "Invitation unavailable");
    db.audit(a.org(), a.id(), "INVITATION_REVOKED", id);
  }

  @GetMapping("/reports")
  public List<Map<String, Object>> reports() {
    var a = identity.staff();
    return db
        .jdbc
        .queryForList(
            "select e.attempt_id,e.status,e.final_revision,e.baseline,e.created_at from"
                + " evaluation_results e where organization_id=? order by created_at desc limit"
                + " 100",
            a.org())
        .stream()
        .map(
            r -> {
              r.put("baseline", db.decode(r.get("baseline"), Map.class));
              return r;
            })
        .toList();
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard() {
    var a = identity.staff();
    return Map.of(
        "assessments",
        db.jdbc.queryForObject(
            "select count(*) from assessments where organization_id=?", Long.class, a.org()),
        "invitations",
        db.jdbc.queryForObject(
            "select count(*) from invitations where organization_id=?", Long.class, a.org()),
        "inProgress",
        db.jdbc.queryForObject(
            "select count(*) from assessment_attempts where organization_id=? and"
                + " status='IN_PROGRESS'",
            Long.class,
            a.org()),
        "completed",
        db.jdbc.queryForObject(
            "select count(*) from evaluation_results where organization_id=?",
            Long.class,
            a.org()));
  }
}
