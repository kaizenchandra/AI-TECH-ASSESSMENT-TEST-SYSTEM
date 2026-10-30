package com.assessment.platform;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Attempts {
  final Store db;
  final Clock clock;
  final CodingService coding;

  public Attempts(Store db, Clock clock, CodingService coding) {
    this.db = db;
    this.clock = clock;
    this.coding = coding;
  }

  static Instant instant(Map<String, Object> row, String name) {
    return ((Timestamp) row.get(name)).toInstant();
  }

  static long number(Map<String, Object> row, String name) {
    return ((Number) row.get(name)).longValue();
  }

  public record Context(Map<String, Object> row, AssessmentContent content) {}

  Context lock(UUID id, UUID owner) {
    var row =
        db.one(
            "select t.*,v.content from assessment_attempts t join invitations i on"
                + " i.id=t.invitation_id join assessment_versions v on v.id=i.assessment_version_id"
                + " where t.id=? and t.candidate_id=? for update of t",
            id,
            owner);
    return new Context(row, db.decode(row.get("content"), AssessmentContent.class));
  }

  TimerRules.Result reconcile(Context context, boolean activity) {
    var r = context.row;
    var c = context.content;
    var now = clock.instant();
    var state =
        new TimerRules.State(
            instant(r, "last_activity_at"),
            instant(r, "accounted_at"),
            number(r, "consumed_ms"),
            number(r, "pause_ms"),
            (int) number(r, "pause_count"),
            (boolean) r.get("outage_open"),
            number(r, "outage_pause_ms"));
    var result =
        TimerRules.reconcile(
            state,
            new TimerRules.Policy(
                c.timerPolicy().equals("CONTROLLED"),
                c.durationMinutes() * 60_000L,
                c.maxPauses(),
                c.maxPauseSeconds() * 1000L,
                c.totalPauseSeconds() * 1000L,
                instant(r, "deadline")),
            now,
            activity);
    if (r.get("status").equals("IN_PROGRESS")) {
      var s = result.state();
      db.jdbc.update(
          "update assessment_attempts set"
              + " last_activity_at=?,accounted_at=?,consumed_ms=?,pause_ms=?,pause_count=?,outage_open=?,outage_pause_ms=?"
              + " where id=?",
          Timestamp.from(s.lastActivity()),
          Timestamp.from(s.accountedAt()),
          s.consumed(),
          s.paused(),
          s.pauseCount(),
          s.outageOpen(),
          s.outagePaused(),
          r.get("id"));
      if (s.paused() > state.paused())
        db.jdbc.update(
            "insert into attempt_timer_events(attempt_id,event,active_ms,paused_ms,occurred_at)"
                + " values (?,'PAUSE_ACCOUNTED',?,?,?)",
            r.get("id"),
            s.consumed(),
            s.paused(),
            Timestamp.from(now));
      if (result.expired()) finish(context, "EXPIRED");
    }
    return result;
  }

  void lease(Context c, UUID lease) {
    if (!c.row.get("lease_id").equals(lease)
        || !instant(c.row, "lease_until").isAfter(clock.instant()))
      throw new Problem(409, "Session lease expired or another tab is active; resume first");
  }

  void finish(Context context, String status) {
    var r = context.row;
    if (!r.get("status").equals("IN_PROGRESS")) return;
    UUID id = (UUID) r.get("id");
    db.jdbc.update(
        "update assessment_attempts set status=?,submitted_at=? where id=?",
        status,
        Timestamp.from(clock.instant()),
        id);
    r.put("status", status);
    var answers =
        db.jdbc.queryForList(
            "select question_id,answer from candidate_answers where attempt_id=?", id);
    Map<UUID, Scoring.Answer> byId = new HashMap<>();
    for (var a : answers)
      byId.put((UUID) a.get("question_id"), db.decode(a.get("answer"), Scoring.Answer.class));
    double earned = 0, total = 0;
    boolean pending = false;
    List<Map<String, Object>> items = new ArrayList<>();
    Map<String, double[]> skills = new TreeMap<>();
    for (var q : context.content.questions()) {
      total += q.points();
      var skill = skills.computeIfAbsent(q.skill(), k -> new double[2]);
      skill[1] += q.points();
      if (q.type().equals("CODING")) {
        pending = true;
        items.add(
            Map.of("questionId", q.id(), "status", "EXECUTION_PENDING", "maximum", q.points()));
        continue;
      }
      double score =
          Scoring.score(
              q, byId.getOrDefault(q.id(), new Scoring.Answer(List.of(), null, null, false)));
      earned += score;
      skill[0] += score;
      items.add(
          Map.of("questionId", q.id(), "status", "SCORED", "score", score, "maximum", q.points()));
    }
    double percent = Math.max(0, 100 * earned / total);
    var baseline =
        Map.of(
            "earned",
            earned,
            "maximum",
            total,
            "percentage",
            percent,
            "passed",
            !pending && percent >= context.content.passingPercent(),
            "provisional",
            pending,
            "questions",
            items,
            "skills",
            skills);
    db.jdbc.update(
        "insert into evaluation_results(attempt_id,organization_id,final_revision,baseline,status)"
            + " values (?,?,?,?::jsonb,?) on conflict do nothing",
        id,
        r.get("organization_id"),
        r.get("revision"),
        db.encode(baseline),
        pending ? "EXECUTION_PENDING" : "EVALUATED");
    db.audit((UUID) r.get("organization_id"), (UUID) r.get("candidate_id"), status, id);
    coding.queueFinal(
        (UUID) r.get("organization_id"), id, number(r, "revision"), context.content, byId);
  }

  Map<String, Object> view(Context c, long remaining) {
    UUID id = (UUID) c.row.get("id");
    var answers =
        db
            .jdbc
            .queryForList(
                "select question_id,answer,revision,saved_at from candidate_answers where"
                    + " attempt_id=?",
                id)
            .stream()
            .map(
                r -> {
                  r.put("answer", db.decode(r.get("answer"), Scoring.Answer.class));
                  return r;
                })
            .toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", c.row.get("status"));
    result.put("revision", c.row.get("revision"));
    result.put("currentQuestion", c.row.get("current_question"));
    result.put("remainingMs", c.row.get("status").equals("IN_PROGRESS") ? remaining : 0);
    result.put("serverTime", clock.instant());
    result.put("assessment", c.content.candidateView());
    result.put("answers", answers);
    result.put(
        "monitoringAllowed",
        !db.jdbc
            .queryForList(
                "select invitation_id from candidate_acknowledgments where invitation_id=? and"
                    + " policy_version='candidate-policy-v2'",
                c.row.get("invitation_id"))
            .isEmpty());
    result.put(
        "visited",
        db.jdbc.queryForList(
            "select question_id from question_visits where attempt_id=?", UUID.class, id));
    return result;
  }

  @Transactional
  public Map<String, Object> start(UUID invitation, UUID lease, Identity.Actor actor) {
    var i =
        db.one(
            "select i.*,v.content from invitations i join assessment_versions v on"
                + " v.id=i.assessment_version_id where i.id=? and i.claimed_by=? for update of i",
            invitation,
            actor.id());
    var existing =
        db.jdbc.queryForList(
            "select id from assessment_attempts where invitation_id=?", invitation);
    if (!existing.isEmpty()) return resume((UUID) existing.getFirst().get("id"), lease, actor.id());
    var c = db.decode(i.get("content"), AssessmentContent.class);
    Instant now = clock.instant();
    if (!actor.verified()
        || (boolean) i.get("revoked")
        || !instant(i, "expires_at").isAfter(now)
        || now.isBefore(c.availableFrom())
        || !now.isBefore(c.latestStart()))
      throw new Problem(403, "Assessment unavailable for start");
    var readiness =
        db.one(
            "select * from candidate_acknowledgments where invitation_id=? and candidate_id=?",
            invitation,
            actor.id());
    if (!Objects.equals(readiness.get("assessment_version_id"), i.get("assessment_version_id"))
        || !"candidate-policy-v2".equals(readiness.get("policy_version"))
        || instant(readiness, "acknowledged_at").isBefore(now.minusSeconds(600)))
      throw new Problem(422, "Readiness expired or instructions changed; repeat checks");
    ReadinessPolicy.validate(
        db, invitation, c, db.decode(readiness.get("system_checks"), Map.class));
    UUID id = UUID.randomUUID();
    db.jdbc.update(
        "insert into"
            + " assessment_attempts(id,organization_id,invitation_id,candidate_id,status,started_at,last_activity_at,accounted_at,deadline,lease_id,lease_until)"
            + " values (?,?,?,?,'IN_PROGRESS',?,?,?,?,?,?)",
        id,
        i.get("organization_id"),
        invitation,
        actor.id(),
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(c.completionDeadline()),
        lease,
        Timestamp.from(now.plusSeconds(30)));
    db.audit((UUID) i.get("organization_id"), actor.id(), "ATTEMPT_STARTED", id);
    db.jdbc.update(
        "update assessment_attempts set current_question=? where id=?",
        c.questions().getFirst().id(),
        id);
    db.jdbc.update(
        "insert into question_visits(attempt_id,question_id) values (?,?)",
        id,
        c.questions().getFirst().id());
    return view(
        lock(id, actor.id()),
        Math.min(
            c.durationMinutes() * 60_000L,
            Duration.between(now, c.completionDeadline()).toMillis()));
  }

  @Transactional
  public Map<String, Object> resume(UUID id, UUID lease, UUID owner) {
    var c = lock(id, owner);
    if (c.row.get("status").equals("IN_PROGRESS")
        && !c.row.get("lease_id").equals(lease)
        && instant(c.row, "lease_until").isAfter(clock.instant()))
      throw new Problem(409, "Another tab is active; close it and wait 30 seconds");
    var time = reconcile(c, true);
    if (c.row.get("status").equals("IN_PROGRESS"))
      db.jdbc.update(
          "update assessment_attempts set lease_id=?,lease_until=? where id=?",
          lease,
          Timestamp.from(clock.instant().plusSeconds(30)),
          id);
    return view(c, time.remainingMs());
  }

  @Transactional
  public Map<String, Object> heartbeat(UUID id, UUID lease, UUID owner) {
    var c = lock(id, owner);
    if (c.row.get("status").equals("IN_PROGRESS")) lease(c, lease);
    var time = reconcile(c, true);
    if (c.row.get("status").equals("IN_PROGRESS"))
      db.jdbc.update(
          "update assessment_attempts set lease_until=? where id=?",
          Timestamp.from(clock.instant().plusSeconds(30)),
          id);
    return view(c, time.remainingMs());
  }

  public record Save(
      UUID questionId, long expectedRevision, Scoring.Answer answer, UUID nextQuestionId) {
    public Save(UUID questionId, long expectedRevision, Scoring.Answer answer) {
      this(questionId, expectedRevision, answer, null);
    }
  }

  @Transactional
  public Map<String, Object> save(UUID id, UUID lease, UUID key, UUID owner, Save request) {
    var c = lock(id, owner);
    String hash = Identity.hash(db.encode(request));
    var receipts =
        db.jdbc.queryForList(
            "select * from mutation_receipts where attempt_id=? and key=?", id, key);
    if (!receipts.isEmpty()) {
      var receipt = receipts.getFirst();
      if (!receipt.get("payload_hash").equals(hash))
        throw new Problem(409, "Idempotency key reused with different content");
      return Map.of("revision", receipt.get("revision"), "acknowledged", true);
    }
    lease(c, lease);
    var timing = reconcile(c, true);
    if (!c.row.get("status").equals("IN_PROGRESS") || timing.expired())
      throw new Problem(409, "Attempt is finalized");
    if (number(c.row, "revision") != request.expectedRevision)
      throw new Problem(409, "Stale answer revision; reload acknowledged progress");
    var q = c.content.question(request.questionId);
    Scoring.validate(q, request.answer);
    UUID target = request.nextQuestionId == null ? request.questionId : request.nextQuestionId;
    c.content.question(target);
    if (!c.content.backNavigation() && c.row.get("current_question") != null) {
      var ids =
          c.content.questions().stream().map(item -> Objects.requireNonNull(item).id()).toList();
      if (ids.indexOf(request.questionId) < ids.indexOf(c.row.get("current_question")))
        throw new Problem(409, "Back navigation is disabled");
      if (ids.indexOf(target) < ids.indexOf(request.questionId))
        throw new Problem(409, "Back navigation is disabled");
    }
    long revision = request.expectedRevision + 1;
    Timestamp now = Timestamp.from(clock.instant());
    String payload = db.encode(request.answer);
    db.jdbc.update(
        "insert into candidate_answers values (?,?,?::jsonb,?,?) on conflict"
            + " (attempt_id,question_id) do update set"
            + " answer=excluded.answer,revision=excluded.revision,saved_at=excluded.saved_at",
        id,
        request.questionId,
        payload,
        revision,
        now);
    db.jdbc.update(
        "insert into answer_revisions values (?,?,?,?::jsonb,?)",
        id,
        revision,
        request.questionId,
        payload,
        now);
    db.jdbc.update("insert into mutation_receipts values (?,?,?,?)", id, key, hash, revision);
    db.jdbc.update(
        "update assessment_attempts set revision=?,current_question=?,lease_until=? where id=?",
        revision,
        target,
        Timestamp.from(clock.instant().plusSeconds(30)),
        id);
    db.jdbc.update(
        "insert into question_visits(attempt_id,question_id) values (?,?),(?,?) on conflict do"
            + " nothing",
        id,
        request.questionId,
        id,
        target);
    db.audit((UUID) c.row.get("organization_id"), owner, "ANSWER_ACKNOWLEDGED", id);
    return Map.of("revision", revision, "acknowledged", true, "remainingMs", timing.remainingMs());
  }

  @Transactional
  public Map<String, Object> submit(UUID id, UUID lease, UUID owner) {
    var c = lock(id, owner);
    if (c.row.get("status").equals("IN_PROGRESS")) {
      lease(c, lease);
      reconcile(c, true);
      finish(c, "SUBMITTED");
    }
    return view(c, 0);
  }

  @Transactional
  public void expire(UUID id, UUID owner) {
    var c = lock(id, owner);
    reconcile(c, false);
  }

  @Transactional
  public Map<String, Object> runSamples(
      UUID id, UUID owner, UUID tab, UUID questionId, long expectedRevision, UUID key) {
    var c = lock(id, owner);
    lease(c, tab);
    var time = reconcile(c, true);
    if (!c.row.get("status").equals("IN_PROGRESS") || time.expired())
      throw new Problem(409, "Attempt is finalized");
    if (number(c.row, "revision") != expectedRevision)
      throw new Problem(409, "Save and acknowledge your code before running");
    var question = c.content.question(questionId);
    if (!question.type().equals("CODING")) throw new Problem(422, "Coding question required");
    var answer =
        db.decode(
            db.one(
                    "select answer from candidate_answers where attempt_id=? and question_id=?",
                    id,
                    questionId)
                .get("answer"),
            Scoring.Answer.class);
    var previous =
        db.jdbc.queryForList(
            "select id,state from code_execution_jobs where attempt_id=? and question_id=? and"
                + " answer_revision=? and purpose='SAMPLE'",
            id,
            questionId,
            expectedRevision);
    if (!previous.isEmpty()) return previous.getFirst();
    Long active =
        db.jdbc.queryForObject(
            "select count(*) from code_execution_jobs where attempt_id=? and state in"
                + " ('QUEUED','RUNNING','RETRY_WAIT')",
            Long.class,
            id);
    if (active == null || active >= 3) throw new Problem(429, "Execution queue limit reached");
    UUID job =
        coding.enqueue(
            (UUID) c.row.get("organization_id"),
            id,
            question,
            expectedRevision,
            answer,
            "SAMPLE",
            key);
    return Map.of("id", job, "state", "QUEUED");
  }
}
