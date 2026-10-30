package com.assessment.platform;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IntegrityApi {
  final Store db;
  final Identity identity;
  final Clock clock;

  public IntegrityApi(Store db, Identity identity, Clock clock) {
    this.db = db;
    this.identity = identity;
    this.clock = clock;
  }

  public record Event(UUID id, String type, Instant observedAt) {}

  public record Batch(List<Event> events) {}

  static final Set<String> TYPES =
      Set.of(
          "HIDDEN",
          "VISIBLE",
          "BLUR",
          "FOCUS",
          "OFFLINE",
          "ONLINE",
          "MEDIA_LOST",
          "FULLSCREEN_EXIT");

  @PostMapping("/candidate/attempts/{id}/integrity-events")
  @Transactional
  public void ingest(@PathVariable UUID id, @RequestBody Batch batch) {
    var actor = identity.actor();
    var row =
        db.one(
            "select organization_id,status from assessment_attempts where id=? and candidate_id=?",
            id,
            actor.id());
    if (!row.get("status").equals("IN_PROGRESS")) throw new Problem(409, "Attempt finalized");
    if (db.jdbc
        .queryForList(
            "select k.invitation_id from candidate_acknowledgments k join assessment_attempts a on"
                + " a.invitation_id=k.invitation_id where a.id=? and"
                + " k.policy_version='candidate-policy-v2'",
            id)
        .isEmpty()) throw new Problem(403, "Monitoring disclosure has not been acknowledged");
    if (batch.events() == null || batch.events().isEmpty() || batch.events().size() > 20)
      throw new Problem(422, "Event batch must contain 1–20 events");
    for (var e : batch.events())
      if (e == null
          || e.id() == null
          || e.type() == null
          || !TYPES.contains(e.type())
          || e.observedAt() == null
          || e.observedAt().isBefore(clock.instant().minusSeconds(86400))
          || e.observedAt().isAfter(clock.instant().plusSeconds(300)))
        throw new Problem(422, "Invalid observation");
    db.jdbc.queryForList("select pg_advisory_xact_lock(hashtext(?))", "integrity:" + id);
    // Deduplicate before quotas. PostgreSQL stores timestamps at microsecond precision.
    Map<UUID, Event> fresh = new LinkedHashMap<>();
    for (var original : batch.events()) {
      var e =
          new Event(
              original.id(),
              original.type(),
              original.observedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
      var prior = fresh.putIfAbsent(e.id(), e);
      if (prior != null && !prior.equals(e)) throw new Problem(409, "Observation ID conflict");
    }
    for (var e : new ArrayList<>(fresh.values())) {
      var stored =
          db.jdbc.queryForList(
              "select event_type,observed_at from integrity_events where attempt_id=? and id=?",
              id,
              e.id());
      if (!stored.isEmpty()) {
        var prior = stored.getFirst();
        if (!e.type().equals(prior.get("event_type"))
            || !e.observedAt().equals(((Timestamp) prior.get("observed_at")).toInstant()))
          throw new Problem(409, "Observation ID conflict");
        fresh.remove(e.id());
      }
    }
    var count =
        db.one(
            "select count(*) as total,count(*) filter(where received_at>now()-interval '1 minute')"
                + " as recent from integrity_events where attempt_id=?",
            id);
    if (Attempts.number(count, "total") + fresh.size() > 5000
        || Attempts.number(count, "recent") + fresh.size() > 60)
      throw new Problem(429, "Observation limit reached; test access is unaffected");
    for (var e : fresh.values())
      db.jdbc.update(
          "insert into integrity_events(attempt_id,organization_id,id,event_type,observed_at)"
              + " values (?,?,?,?,?) on conflict do nothing",
          id,
          row.get("organization_id"),
          e.id(),
          e.type(),
          Timestamp.from(e.observedAt()));
  }

  @GetMapping("/evaluations/{id}/integrity")
  public Map<String, Object> timeline(@PathVariable UUID id) {
    var a = identity.staff();
    db.one("select id from assessment_attempts where id=? and organization_id=?", id, a.org());
    return Map.of(
        "limitation",
        "Unverified browser observations; not proof of cheating and never scoring inputs.",
        "events",
        db.jdbc.queryForList(
            "select id,event_type,observed_at,received_at,source from integrity_events where"
                + " attempt_id=? order by received_at desc limit 500",
            id),
        "notes",
        db.jdbc.queryForList(
            "select actor_id,note,created_at from integrity_reviews where attempt_id=? order by"
                + " created_at desc limit 100",
            id));
  }

  public record Note(String note) {}

  @PostMapping("/candidate/attempts/{id}/integrity-explanations")
  @Transactional
  public void explain(@PathVariable UUID id, @RequestBody Note note) {
    var a = identity.actor();
    var row =
        db.one(
            "select organization_id from assessment_attempts where id=? and candidate_id=?",
            id,
            a.id());
    recordNote(id, (UUID) row.get("organization_id"), a.id(), note);
  }

  @PostMapping("/evaluations/{id}/integrity-reviews")
  @Transactional
  public void review(@PathVariable UUID id, @RequestBody Note note) {
    var a = identity.staff();
    if (!Set.of("ADMIN", "EVALUATOR").contains(a.role()))
      throw new Problem(403, "Evaluator required");
    db.one("select id from assessment_attempts where id=? and organization_id=?", id, a.org());
    recordNote(id, a.org(), a.id(), note);
  }

  void recordNote(UUID id, UUID org, UUID actor, Note note) {
    if (note.note() == null || note.note().isBlank() || note.note().length() > 2000)
      throw new Problem(422, "A bounded explanation is required");
    db.jdbc.update(
        "insert into integrity_reviews(id,organization_id,attempt_id,actor_id,note) values"
            + " (?,?,?,?,?)",
        UUID.randomUUID(),
        org,
        id,
        actor,
        note.note());
    db.audit(org, actor, "INTEGRITY_REVIEW_NOTE", id);
  }

  @Scheduled(fixedDelay = 3600000)
  public void retention() {
    db.jdbc.update("delete from integrity_events where received_at<now()-interval '30 days'");
    db.jdbc.update("delete from integrity_reviews where created_at<now()-interval '30 days'");
  }

  public record ExceptionRequest(List<String> checks, String reason) {}

  @PostMapping("/candidate/invitations/{id}/accommodation-requests")
  public void request(@PathVariable UUID id, @RequestBody Note note) {
    var a = identity.actor();
    db.one("select id from invitations where id=? and claimed_by=?", id, a.id());
    if (note.note() == null || note.note().isBlank() || note.note().length() > 2000)
      throw new Problem(422, "Reason required, maximum 2000 characters");
    db.jdbc.update(
        "insert into readiness_requests(id,invitation_id,candidate_id,reason) values (?,?,?,?)",
        UUID.randomUUID(),
        id,
        a.id(),
        note.note());
  }

  @GetMapping("/readiness-requests")
  public List<Map<String, Object>> requests() {
    var a = identity.staff();
    return db.jdbc.queryForList(
        "select r.id,r.invitation_id,r.reason,r.created_at,e.checks,e.expires_at from"
            + " readiness_requests r join invitations i on i.id=r.invitation_id left join"
            + " readiness_exceptions e on e.invitation_id=i.id where i.organization_id=? order by"
            + " r.created_at desc limit 100",
        a.org());
  }

  @PostMapping("/invitations/{id}/readiness-exception")
  @Transactional
  public void exception(@PathVariable UUID id, @RequestBody ExceptionRequest request) {
    var a = identity.staff();
    if (!a.role().equals("ADMIN")) throw new Problem(403, "Administrator required");
    db.one("select id from invitations where id=? and organization_id=? for update", id, a.org());
    if (request.checks() == null
        || request.checks().isEmpty()
        || request.checks().size() > 4
        || !Set.of("camera", "microphone", "microphoneLevel", "playback")
            .containsAll(request.checks())
        || request.reason() == null
        || request.reason().isBlank()
        || request.reason().length() > 2000)
      throw new Problem(422, "Select media checks and provide a reason");
    db.jdbc.update(
        "insert into readiness_exceptions(invitation_id,approved_by,reason,checks,expires_at)"
            + " values (?,?,?,?::jsonb,now()+interval '24 hours') on conflict(invitation_id) do"
            + " update set"
            + " approved_by=excluded.approved_by,reason=excluded.reason,checks=excluded.checks,expires_at=excluded.expires_at",
        id,
        a.id(),
        request.reason(),
        db.encode(request.checks()));
    db.audit(a.org(), a.id(), "READINESS_EXCEPTION_APPROVED", id);
  }
}
