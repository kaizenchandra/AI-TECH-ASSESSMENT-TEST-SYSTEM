package com.assessment.platform;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReportsApi {
  final Store db;
  final Identity identity;
  final AiJobs jobs;

  public ReportsApi(Store db, Identity identity, AiJobs jobs) {
    this.db = db;
    this.identity = identity;
    this.jobs = jobs;
  }

  public record Adjustment(UUID questionId, double score, String reason) {}

  public record Review(String feedback, String recommendation, List<Adjustment> adjustments) {}

  @GetMapping("/evaluations/{id}")
  public Map<String, Object> evaluation(@PathVariable UUID id) {
    var a = identity.staff();
    var row =
        db.one(
            "select * from evaluation_results where attempt_id=? and organization_id=?",
            id,
            a.org());
    row.put("baseline", db.decode(row.get("baseline"), Map.class));
    if (row.get("final_summary") != null)
      row.put("final_summary", db.decode(row.get("final_summary"), Map.class));
    row.put(
        "versions",
        db.jdbc.queryForList(
            "select version,reviewer_id,feedback,created_at from evaluation_versions where"
                + " attempt_id=? order by version desc",
            id));
    row.put(
        "feedbackArtifacts",
        db
            .jdbc
            .queryForList(
                "select a.id,a.state,a.revision,a.content,a.provenance from ai_artifacts a join"
                    + " ai_job_evaluations j on j.job_id=a.job_id where j.attempt_id=? and"
                    + " a.organization_id=? order by a.created_at desc",
                id,
                a.org())
            .stream()
            .map(
                r -> {
                  r.put("content", db.decode(r.get("content"), Map.class));
                  r.put("provenance", db.decode(r.get("provenance"), Map.class));
                  return r;
                })
            .toList());
    return row;
  }

  @PostMapping("/evaluations/{id}/insight-jobs")
  public ResponseEntity<?> insights(
      @PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key) {
    return jobs.request(key, new AiJobs.Request("REPORT", null, null, id));
  }

  @PostMapping("/coding-submissions/{id}/ai-feedback-jobs")
  public ResponseEntity<?> feedback(
      @PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key) {
    var a = identity.author();
    var row =
        db.one(
            "select attempt_id from code_execution_jobs where id=? and organization_id=? and"
                + " purpose='GRADE' and state='SUCCEEDED'",
            id,
            a.org());
    return jobs.request(
        key, new AiJobs.Request("FEEDBACK", null, null, (UUID) row.get("attempt_id")));
  }

  @SuppressWarnings("unchecked")
  @PostMapping("/evaluations/{id}/reviews")
  @Transactional
  public void review(
      @PathVariable UUID id, @RequestHeader("If-Match") long expected, @RequestBody Review review) {
    var a = identity.staff();
    if (!Set.of("ADMIN", "EVALUATOR").contains(a.role()))
      throw new Problem(403, "Evaluator permission required");
    var e =
        db.one(
            "select * from evaluation_results where attempt_id=? and organization_id=? for update",
            id,
            a.org());
    if (!e.get("status").equals("EVALUATED") || Attempts.number(e, "revision") != expected)
      throw new Problem(409, "Evaluation incomplete or revision stale");
    if (review.feedback() == null
        || review.feedback().isBlank()
        || review.feedback().length() > 6000
        || !Set.of("UNDECIDED", "SHORTLIST", "REJECT", "INTERVIEW")
            .contains(review.recommendation())
        || review.adjustments() == null
        || review.adjustments().size() > 200)
      throw new Problem(422, "Feedback, human recommendation and bounded adjustments required");
    Map<String, Object> summary =
        db.decode(
            e.get("final_summary") != null ? e.get("final_summary") : e.get("baseline"), Map.class);
    var items = (List<Map<String, Object>>) summary.get("questions");
    Set<UUID> seen = new HashSet<>();
    for (var adjustment : review.adjustments()) {
      if (!seen.add(adjustment.questionId())
          || adjustment.reason() == null
          || adjustment.reason().isBlank()
          || adjustment.reason().length() > 4000
          || !Double.isFinite(adjustment.score()))
        throw new Problem(422, "Adjustment reason required");
      var item =
          items.stream()
              .filter(
                  q -> q.get("questionId").toString().equals(adjustment.questionId().toString()))
              .findFirst()
              .orElseThrow(() -> new Problem(422, "Unknown criterion"));
      double max = ((Number) item.get("maximum")).doubleValue();
      if (adjustment.score() < 0 || adjustment.score() > max)
        throw new Problem(422, "Score outside approved range");
      item.put("score", adjustment.score());
      item.put("status", "HUMAN_REVIEWED");
    }
    double earned = items.stream().mapToDouble(q -> ((Number) q.get("score")).doubleValue()).sum();
    double percent = Math.max(0, 100 * earned / ((Number) summary.get("maximum")).doubleValue());
    summary.put("earned", earned);
    summary.put("percentage", percent);
    var content =
        db.decode(
            db.one(
                    "select v.content from assessment_attempts a join invitations i on"
                        + " i.id=a.invitation_id join assessment_versions v on"
                        + " v.id=i.assessment_version_id where a.id=?",
                    id)
                .get("content"),
            AssessmentContent.class);
    Map<String, double[]> skills = new TreeMap<>();
    for (var item : items) {
      var q = content.question(UUID.fromString(item.get("questionId").toString()));
      var skill = skills.computeIfAbsent(q.skill(), k -> new double[2]);
      skill[0] += ((Number) item.get("score")).doubleValue();
      skill[1] += q.points();
    }
    summary.put("skills", skills);
    summary.put("passed", percent >= content.passingPercent());
    db.jdbc.update(
        "insert into evaluation_reviews values (?,?,?,?,?,?,?,now())",
        UUID.randomUUID(),
        a.org(),
        id,
        a.id(),
        expected,
        review.feedback(),
        review.recommendation());
    db.jdbc.update(
        "insert into"
            + " evaluation_versions(id,organization_id,attempt_id,version,reviewer_id,summary,adjustments,feedback)"
            + " values (?,?,?,?,?,?::jsonb,?::jsonb,?)",
        UUID.randomUUID(),
        a.org(),
        id,
        expected + 1,
        a.id(),
        db.encode(summary),
        db.encode(review.adjustments()),
        review.feedback());
    db.jdbc.update(
        "update evaluation_results set"
            + " release_state='APPROVED',approved_by=?,approved_feedback=?,final_summary=?::jsonb,revision=revision+1,scoring_revision=scoring_revision+?,released_at=null"
            + " where attempt_id=?",
        a.id(),
        review.feedback(),
        db.encode(summary),
        review.adjustments().isEmpty() ? 0 : 1,
        id);
    db.audit(a.org(), a.id(), "EVALUATION_REVIEWED", id);
  }

  @SuppressWarnings("unchecked")
  @PostMapping("/reports/{id}/release")
  @Transactional
  public Map<String, Object> release(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long expected,
      @RequestHeader("Idempotency-Key") UUID key) {
    var a = identity.staff();
    if (!Set.of("ADMIN", "EVALUATOR").contains(a.role()))
      throw new Problem(403, "Report release permission required");
    var e =
        db.one(
            "select * from evaluation_results where attempt_id=? and organization_id=? for update",
            id,
            a.org());
    var prior =
        db.jdbc.queryForList(
            "select id,attempt_id,evaluation_revision from report_releases where organization_id=?"
                + " and idempotency_key=?",
            a.org(),
            key);
    if (!prior.isEmpty()) {
      var r = prior.getFirst();
      if (!id.equals(r.get("attempt_id")) || Attempts.number(r, "evaluation_revision") != expected)
        throw new Problem(409, "Idempotency key conflict");
      return Map.of("id", r.get("id"));
    }
    if (!e.get("release_state").equals("APPROVED") || Attempts.number(e, "revision") != expected)
      throw new Problem(409, "Approved current report required");
    var summary = db.decode(e.get("final_summary"), Map.class);
    List<Map<String, Object>> skills = new ArrayList<>();
    var scoreSkills = (Map<String, List<Number>>) summary.get("skills");
    Set<String> all = new TreeSet<>(AiValidation.SKILLS);
    all.addAll(scoreSkills.keySet());
    for (String skill : all) {
      var score = scoreSkills.get(skill);
      skills.add(
          score == null
              ? Map.of(
                  "skill", skill, "status", "NOT_ASSESSED", "limitation", "Insufficient evidence")
              : Map.of(
                  "skill",
                  skill,
                  "status",
                  "ASSESSED",
                  "earned",
                  score.get(0),
                  "maximum",
                  score.get(1),
                  "limitation",
                  "Measured task performance, not validated proficiency"));
    }
    List<Map<String, Object>> feedback = new ArrayList<>();
    for (var artifact :
        db.jdbc.queryForList(
            "select a.content,a.provenance from ai_artifacts a join ai_job_evaluations j on"
                + " j.job_id=a.job_id where j.attempt_id=? and a.organization_id=? and"
                + " a.state='APPROVED' and j.evaluation_revision=? and exists (select 1 from"
                + " assessment_attempts t join candidate_acknowledgments k on"
                + " k.invitation_id=t.invitation_id where t.id=j.attempt_id and"
                + " k.optional_ai=true and k.consent_revision=j.consent_revision)",
            id,
            a.org(),
            e.get("scoring_revision"))) {
      var content = db.decode(artifact.get("content"), AiProvider.Draft.class);
      var provenance = db.decode(artifact.get("provenance"), Map.class);
      feedback.add(
          Map.of(
              "summary",
              content.summary(),
              "findings",
              content.findings(),
              "limitations",
              content.limitations(),
              "developmentFake",
              provenance.get("fake")));
    }
    var snapshot =
        Map.of(
            "attemptId",
            id,
            "percentage",
            summary.get("percentage"),
            "earned",
            summary.get("earned"),
            "maximum",
            summary.get("maximum"),
            "passed",
            summary.get("passed"),
            "skills",
            skills,
            "feedback",
            e.get("approved_feedback"),
            "aiFeedback",
            feedback,
            "evaluationVersion",
            expected);
    UUID release = UUID.randomUUID();
    db.jdbc.update(
        "insert into"
            + " report_releases(id,organization_id,attempt_id,evaluation_revision,released_by,snapshot,idempotency_key)"
            + " values (?,?,?,?,?,?::jsonb,?)",
        release,
        a.org(),
        id,
        expected,
        a.id(),
        db.encode(snapshot),
        key);
    db.jdbc.update(
        "update evaluation_results set release_state='RELEASED',released_at=now() where"
            + " attempt_id=?",
        id);
    db.audit(a.org(), a.id(), "REPORT_RELEASED", id);
    return Map.of("id", release);
  }

  @GetMapping("/candidate/results")
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> results() {
    var a = identity.actor();
    return db
        .jdbc
        .queryForList(
            "select distinct on (r.attempt_id) r.snapshot from report_releases r join"
                + " assessment_attempts t on t.id=r.attempt_id where t.candidate_id=? order by"
                + " r.attempt_id,r.evaluation_revision desc",
            a.id())
        .stream()
        .map(r -> (Map<String, Object>) db.decode(r.get("snapshot"), Map.class))
        .toList();
  }

  @GetMapping(value = "/reports/export", produces = "text/csv")
  public String export() {
    var a = identity.staff();
    StringBuilder csv = new StringBuilder("attempt_id,status,percentage,release_state\r\n");
    for (var row :
        db.jdbc.queryForList(
            "select attempt_id,status,baseline,release_state from evaluation_results where"
                + " organization_id=? order by created_at limit 10000",
            a.org())) {
      var baseline = db.decode(row.get("baseline"), Map.class);
      csv.append(row.get("attempt_id"))
          .append(',')
          .append(row.get("status"))
          .append(',')
          .append(((Number) baseline.get("percentage")).doubleValue())
          .append(',')
          .append(row.get("release_state"))
          .append("\r\n");
    }
    return csv.toString();
  }
}
