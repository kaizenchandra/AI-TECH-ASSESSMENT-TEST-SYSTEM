package com.assessment.platform;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FeedbackEvidence {
  final Store db;

  public FeedbackEvidence(Store db) {
    this.db = db;
  }

  public record Code(UUID questionId, String skill, String source) {}

  public record Source(long finalRevision, List<Code> code, Map<String, Object> scores) {}

  @SuppressWarnings("unchecked")
  public Source source(UUID attempt, UUID org) {
    var e =
        db.one(
            "select e.*,a.status as attempt_status,k.optional_ai from evaluation_results e join"
                + " assessment_attempts a on a.id=e.attempt_id join candidate_acknowledgments k on"
                + " k.invitation_id=a.invitation_id where e.attempt_id=? and e.organization_id=?",
            attempt,
            org);
    if (!e.get("status").equals("EVALUATED") || e.get("attempt_status").equals("IN_PROGRESS"))
      throw new Problem(409, "Deterministic evaluation must finish first");
    if (!(boolean) e.get("optional_ai"))
      throw new Problem(403, "Optional AI consent not granted; use human review");
    var content =
        db.decode(
            db.one(
                    "select v.content from assessment_attempts a join invitations i on"
                        + " i.id=a.invitation_id join assessment_versions v on"
                        + " v.id=i.assessment_version_id where a.id=?",
                    attempt)
                .get("content"),
            AssessmentContent.class);
    List<Code> code = new ArrayList<>();
    for (var row :
        db.jdbc.queryForList(
            "select question_id,answer from candidate_answers where attempt_id=?", attempt)) {
      var q = content.question((UUID) row.get("question_id"));
      if (!q.type().equals("CODING")) continue;
      var answer = db.decode(row.get("answer"), Scoring.Answer.class);
      String text = answer.code();
      if (text == null
          || text.length() > 10000
          || text.matches(
              "(?s).*(?:sk-[A-Za-z0-9]{12,}|AKIA[A-Z0-9]{16}|-----BEGIN [A-Z ]*PRIVATE"
                  + " KEY-----).*"))
        throw new Problem(
            422, "Source exceeds safe processing bounds or contains a potential secret");
      code.add(new Code(q.id(), q.skill(), text));
    }
    Map<String, Object> baseline = db.decode(e.get("baseline"), Map.class);
    return new Source(
        Attempts.number(e, "final_revision"),
        code,
        Map.of(
            "skills",
            baseline.get("skills"),
            "earned",
            baseline.get("earned"),
            "maximum",
            baseline.get("maximum")));
  }

  public boolean current(UUID job) {
    return !db.jdbc
        .queryForList(
            "select j.job_id from ai_job_evaluations j join evaluation_results e on"
                + " e.attempt_id=j.attempt_id join assessment_attempts a on a.id=e.attempt_id join"
                + " candidate_acknowledgments k on k.invitation_id=a.invitation_id where j.job_id=?"
                + " and e.final_revision=j.final_revision and"
                + " e.scoring_revision=j.evaluation_revision and e.status='EVALUATED' and"
                + " k.optional_ai=true and k.consent_revision=j.consent_revision",
            job)
        .isEmpty();
  }

  public static void validate(Source source, AiProvider.Draft draft) {
    if (draft == null
        || draft.summary() == null
        || draft.summary().length() > 6000
        || draft.findings() == null
        || draft.findings().size() > 30
        || draft.limitations() == null
        || draft.limitations().size() > 20
        || draft.skills() == null
        || !draft.skills().isEmpty()
        || draft.questions() == null
        || !draft.questions().isEmpty()) throw new Problem(422, "Invalid feedback schema");
    for (var finding : draft.findings()) {
      var code =
          source.code().stream()
              .filter(c -> c.questionId().equals(finding.questionId()))
              .findFirst()
              .orElseThrow(() -> new Problem(422, "Unauthorized evidence reference"));
      var lines = code.source().split("\n", -1);
      if (finding.firstLine() < 1
          || finding.lastLine() < finding.firstLine()
          || finding.lastLine() > lines.length
          || finding.lastLine() - finding.firstLine() > 20
          || !String.join(
                  "\n", Arrays.copyOfRange(lines, finding.firstLine() - 1, finding.lastLine()))
              .equals(finding.excerpt())
          || !Set.of("READABILITY", "MAINTAINABILITY", "COMPLEXITY").contains(finding.criterion())
          || finding.observation() == null
          || finding.observation().isBlank()
          || finding.observation().length() > 3000)
        throw new Problem(422, "Unsupported feedback citation or criterion");
    }
  }
}
