package com.assessment.platform;

import java.time.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class GeneratedAssessments {
  final Store db;
  final Identity identity;
  final CodeRunner runner;
  final Clock clock;

  public GeneratedAssessments(Store db, Identity identity, CodeRunner runner, Clock clock) {
    this.db = db;
    this.identity = identity;
    this.runner = runner;
    this.clock = clock;
  }

  static void approvable(Store db, Map<String, Object> row) {
    var draft = db.decode(row.get("content"), AiProvider.Draft.class);
    AiValidation.validate("QUESTION", "", draft);
    if (draft.questions().stream().anyMatch(q -> q.type().equals("CODING"))
        && db.jdbc
            .queryForList(
                "select artifact_id from generated_question_validations where artifact_id=? and"
                    + " state='PASSED' and content_hash=?",
                row.get("id"),
                Identity.hash(db.encode(draft)))
            .isEmpty())
      throw new Problem(
          409, "Coding reference validation must pass in the isolated runner before approval");
  }

  @PostMapping("/ai-artifacts/{id}/coding-validation")
  @Transactional
  public ResponseEntity<?> validate(@PathVariable UUID id) {
    var a = identity.staff();
    var row =
        db.one(
            "select * from ai_artifacts where id=? and organization_id=? and purpose='QUESTION' for"
                + " update",
            id,
            a.org());
    var content = db.decode(row.get("content"), AiProvider.Draft.class);
    if (!row.get("state").equals("REVIEW_REQUIRED")
        || content.questions().stream().noneMatch(q -> q.type().equals("CODING")))
      throw new Problem(409, "A coding draft awaiting review is required");
    db.jdbc.update(
        "insert into generated_question_validations(artifact_id,content_hash,requested_by) values"
            + " (?,?,?) on conflict(artifact_id) do update set"
            + " state='QUEUED',finding=null,requested_by=excluded.requested_by where"
            + " generated_question_validations.state='UNAVAILABLE' and"
            + " generated_question_validations.attempts<3",
        id,
        Identity.hash(db.encode(content)),
        a.id());
    return ResponseEntity.accepted().body(Map.of("id", id));
  }

  @GetMapping("/ai-artifacts/{id}/coding-validation")
  public Map<String, Object> validation(@PathVariable UUID id) {
    var a = identity.staff();
    db.one("select id from ai_artifacts where id=? and organization_id=?", id, a.org());
    var rows =
        db.jdbc.queryForList(
            "select state,finding,attempts from generated_question_validations where artifact_id=?",
            id);
    return rows.isEmpty() ? Map.of("state", "NOT_RUN") : rows.getFirst();
  }

  @Scheduled(fixedDelay = 3000)
  public void validationWorker() {
    db.jdbc.update(
        "update generated_question_validations set state='UNAVAILABLE',finding='Validation lease"
            + " recovery exhausted; generate a new draft',completed_at=now() where state='RUNNING'"
            + " and lease_until<now() and attempts>=3");
    UUID lease = UUID.randomUUID();
    var rows =
        db.jdbc.queryForList(
            "update generated_question_validations set"
                + " state='RUNNING',attempts=attempts+1,lease_id=?,lease_until=now()+interval '1500"
                + " seconds' where artifact_id in (select artifact_id from"
                + " generated_question_validations where state='QUEUED' or (state='RUNNING' and"
                + " lease_until<now() and attempts<3) limit 1 for update skip locked) returning *",
            lease);
    if (rows.isEmpty()) return;
    var job = rows.getFirst();
    String state = "PASSED",
        finding =
            "Reference and public/hidden tests passed; empty-solution mutation rejected. Coverage"
                + " remains limited; human review required.";
    try {
      var row =
          db.one(
              "select a.* from ai_artifacts a join organization_members m on"
                  + " m.organization_id=a.organization_id and m.user_id=? where a.id=? and"
                  + " a.state='REVIEW_REQUIRED' and m.role in ('ADMIN','RECRUITER','EVALUATOR')",
              job.get("requested_by"),
              job.get("artifact_id"));
      var draft = db.decode(row.get("content"), AiProvider.Draft.class);
      AiValidation.validate("QUESTION", "", draft);
      if (!Identity.hash(db.encode(draft)).equals(job.get("content_hash")))
        throw new Problem(409, "Stale input");
      for (var q : draft.questions()) {
        if (q.coding() == null) continue;
        var c = q.coding();
        List<AssessmentContent.TestCase> tests = new ArrayList<>(c.hiddenTests());
        c.samples()
            .forEach(s -> tests.add(new AssessmentContent.TestCase(s.input(), s.output(), 1)));
        var input =
            new CodeRunner.Input(
                UUID.randomUUID(),
                c.language(),
                c.referenceSolution(),
                tests.stream().map(test -> Objects.requireNonNull(test).input()).toList(),
                c.timeoutMs(),
                c.memoryMb());
        if (CodeRunner.fraction(runner.execute(input), tests) != 1)
          throw new Problem(422, "Reference/test mismatch");
        var mutant =
            new CodeRunner.Input(
                UUID.randomUUID(), c.language(), "", input.inputs(), c.timeoutMs(), c.memoryMb());
        if (CodeRunner.fraction(runner.execute(mutant), tests) == 1)
          throw new Problem(422, "Tests accepted an empty implementation");
      }
      db.one(
          "select a.id from ai_artifacts a join organization_members m on"
              + " m.organization_id=a.organization_id and m.user_id=? where a.id=? and"
              + " a.state='REVIEW_REQUIRED' and m.role in ('ADMIN','RECRUITER','EVALUATOR')",
          job.get("requested_by"),
          job.get("artifact_id"));
    } catch (Problem e) {
      state = "FAILED";
      finding =
          "Correctness or authorization checks failed; inspect draft and tests. Generate a"
              + " corrected version.";
    } catch (Exception e) {
      state = "UNAVAILABLE";
      finding = "Isolated runner unavailable or invalid evidence. No correctness approval granted.";
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
    }
    db.jdbc.update(
        "update generated_question_validations set"
            + " state=?,finding=?,completed_at=now(),lease_id=null where artifact_id=? and"
            + " lease_id=? and state='RUNNING'",
        state,
        finding,
        job.get("artifact_id"),
        lease);
  }

  public record Item(UUID artifactId, long revision, int questionIndex) {}

  public record Prepare(
      String title,
      int durationMinutes,
      double passingPercent,
      Instant latestStart,
      Instant completionDeadline,
      List<Item> items) {}

  @PostMapping("/assessments/from-ai")
  @Transactional
  public Map<String, Object> prepare(@RequestBody Prepare request) {
    var a = identity.author();
    if (request.items() == null || request.items().isEmpty() || request.items().size() > 100)
      throw new Problem(422, "Select 1–100 approved questions");
    List<AssessmentContent.Question> questions = new ArrayList<>();
    Set<Item> unique = new HashSet<>();
    for (var item : request.items()) {
      if (item == null || item.artifactId() == null || !unique.add(item))
        throw new Problem(422, "Invalid or duplicate question selection");
      var row =
          db.one(
              "select * from ai_artifacts where id=? and organization_id=? and purpose='QUESTION'"
                  + " for update",
              item.artifactId(),
              a.org());
      if (!row.get("state").equals("APPROVED")
          || Attempts.number(row, "revision") != item.revision())
        throw new Problem(409, "Approved current question version required");
      approvable(db, row);
      var draft = db.decode(row.get("content"), AiProvider.Draft.class);
      if (item.questionIndex() < 0 || item.questionIndex() >= draft.questions().size())
        throw new Problem(422, "Invalid question index");
      var q = draft.questions().get(item.questionIndex());
      var c = q.coding();
      questions.add(
          new AssessmentContent.Question(
              UUID.randomUUID(),
              q.type(),
              q.title(),
              q.description() == null ? q.title() : q.description(),
              q.skill(),
              q.options(),
              q.correct(),
              10,
              0,
              false,
              c == null ? null : c.language(),
              c == null ? null : c.starterCode(),
              c == null ? List.of() : c.samples(),
              c == null
                  ? null
                  : new AssessmentContent.CodingSpec(
                      c.hiddenTests(), c.timeoutMs(), c.memoryMb())));
    }
    var content =
        new AssessmentContent(
            request.title(),
            "Complete the questions independently. Choice questions use exact-match scoring without"
                + " negative marking. Coding is weighted by hidden tests. Review all answers before"
                + " final submission.",
            request.durationMinutes(),
            "CONTROLLED",
            2,
            300,
            600,
            clock.instant(),
            request.latestStart(),
            request.completionDeadline(),
            false,
            false,
            true,
            request.passingPercent(),
            questions);
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
    for (int i = 0; i < request.items().size(); i++) {
      var item = request.items().get(i);
      db.jdbc.update(
          "insert into assessment_ai_sources values (?,?,?,?,?,?)",
          a.org(),
          id,
          item.artifactId(),
          item.revision(),
          item.questionIndex(),
          questions.get(i).id());
    }
    db.audit(a.org(), a.id(), "AI_ASSESSMENT_PREPARED", id);
    return Map.of("id", id, "revision", 0, "status", "DRAFT");
  }
}
