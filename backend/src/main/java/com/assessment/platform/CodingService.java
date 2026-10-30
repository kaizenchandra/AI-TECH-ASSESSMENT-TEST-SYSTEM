package com.assessment.platform;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CodingService {
  final Store db;

  public CodingService(Store db) {
    this.db = db;
  }

  public record Payload(
      String language,
      String source,
      List<AssessmentContent.TestCase> tests,
      int timeoutMs,
      int memoryMb,
      double points) {}

  UUID enqueue(
      UUID org,
      UUID attempt,
      AssessmentContent.Question question,
      long revision,
      Scoring.Answer answer,
      String purpose,
      UUID key) {
    var previous =
        db.jdbc.queryForList(
            "select id from code_execution_jobs where attempt_id=? and question_id=? and"
                + " answer_revision=? and purpose=?",
            attempt,
            question.id(),
            revision,
            purpose);
    if (!previous.isEmpty()) return (UUID) previous.getFirst().get("id");
    var tests =
        purpose.equals("SAMPLE")
            ? question.samples().stream()
                .map(s -> new AssessmentContent.TestCase(s.input(), s.output(), 1))
                .toList()
            : question.coding().hiddenTests();
    int timeout = question.coding() == null ? 2000 : question.coding().timeoutMs(),
        memory = question.coding() == null ? 256 : question.coding().memoryMb();
    UUID id = UUID.randomUUID();
    var payload =
        new Payload(
            question.language(),
            answer.code() == null ? "" : answer.code(),
            tests,
            timeout,
            memory,
            question.points());
    db.jdbc.update(
        "insert into"
            + " code_execution_jobs(id,organization_id,attempt_id,question_id,answer_revision,purpose,idempotency_key,payload)"
            + " values (?,?,?,?,?,?,?,?::jsonb)",
        id,
        org,
        attempt,
        question.id(),
        revision,
        purpose,
        key,
        db.encode(payload));
    return id;
  }

  void queueFinal(
      UUID org,
      UUID attempt,
      long revision,
      AssessmentContent content,
      Map<UUID, Scoring.Answer> answers) {
    for (var question : content.questions())
      if (question.type().equals("CODING") && question.coding() != null)
        enqueue(
            org,
            attempt,
            question,
            revision,
            answers.getOrDefault(
                question.id(), new Scoring.Answer(List.of(), question.language(), "", false)),
            "GRADE",
            UUID.randomUUID());
  }

  public Map<String, Object> candidateResult(UUID id, UUID owner) {
    var row =
        db.one(
            "select j.id,j.state,j.failure_code,j.result from code_execution_jobs j join"
                + " assessment_attempts a on a.id=j.attempt_id where j.id=? and a.candidate_id=?"
                + " and j.purpose='SAMPLE'",
            id,
            owner);
    if (row.get("result") != null) row.put("result", db.decode(row.get("result"), Map.class));
    return row;
  }

  @SuppressWarnings("unchecked")
  void updateEvaluation(UUID attempt) {
    var evaluation =
        db.one("select * from evaluation_results where attempt_id=? for update", attempt);
    if (!evaluation.get("status").equals("EXECUTION_PENDING")) return;
    var content =
        db.decode(
            db.one(
                    "select v.content from assessment_attempts a join invitations i on"
                        + " i.id=a.invitation_id join assessment_versions v on"
                        + " v.id=i.assessment_version_id where a.id=?",
                    attempt)
                .get("content"),
            AssessmentContent.class);
    var rows =
        db.jdbc.queryForList(
            "select question_id,state,result from code_execution_jobs where attempt_id=? and"
                + " purpose='GRADE' and answer_revision=?",
            attempt,
            evaluation.get("final_revision"));
    Map<String, Map<String, Object>> completed = new HashMap<>();
    for (var row : rows)
      if (row.get("state").equals("SUCCEEDED"))
        completed.put(row.get("question_id").toString(), db.decode(row.get("result"), Map.class));
    var baseline = db.decode(evaluation.get("baseline"), Map.class);
    var items = (List<Map<String, Object>>) baseline.get("questions");
    Map<String, double[]> skills = new TreeMap<>();
    double earned = 0;
    boolean pending = false;
    for (var item : items) {
      var q = content.question(UUID.fromString(item.get("questionId").toString()));
      var skill = skills.computeIfAbsent(q.skill(), k -> new double[2]);
      skill[1] += q.points();
      if (q.type().equals("CODING")) {
        var result = completed.get(q.id().toString());
        if (result == null) {
          pending = true;
          continue;
        }
        item.put("score", ((Number) result.get("fraction")).doubleValue() * q.points());
        item.put("status", "SCORED");
        item.put("passedTests", result.get("passedTests"));
        item.put("totalTests", result.get("totalTests"));
      }
      double score = ((Number) item.get("score")).doubleValue();
      earned += score;
      skill[0] += score;
    }
    // Apply the complete baseline once, preserving original choice results and exact final
    // revision.
    if (pending) return;
    double percent = Math.max(0, 100 * earned / ((Number) baseline.get("maximum")).doubleValue());
    baseline.put("earned", earned);
    baseline.put("percentage", percent);
    baseline.put("skills", skills);
    baseline.put("passed", percent >= content.passingPercent());
    baseline.put("provisional", false);
    db.jdbc.update(
        "update evaluation_results set baseline=?::jsonb,status='EVALUATED',revision=revision+1"
            + " where attempt_id=? and status='EXECUTION_PENDING'",
        db.encode(baseline),
        attempt);
    db.audit((UUID) evaluation.get("organization_id"), null, "CODING_GRADED", attempt);
  }
}
