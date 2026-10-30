package com.assessment.platform;

import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/question-bank")
public class QuestionBank {
  final Store db;
  final Identity identity;

  public QuestionBank(Store db, Identity identity) {
    this.db = db;
    this.identity = identity;
  }

  public record Import(List<AssessmentContent.Question> questions) {}

  public record Decision(boolean approve, String reason) {}

  public record Selection(UUID versionId) {}

  static void validate(AssessmentContent.Question q) {
    if (q == null
        || q.type() == null
        || q.skill() == null
        || !AiValidation.SKILLS.contains(q.skill()))
      throw new Problem(422, "Canonical skill required");
    new AssessmentContent(
            "Validation",
            "",
            1,
            "STRICT",
            0,
            0,
            0,
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH,
            false,
            false,
            true,
            60,
            List.of(q))
        .validate();
    if (q.title().length() > 200 || (q.starterCode() != null && q.starterCode().length() > 50000))
      throw new Problem(422, "Question too large");
    if (q.type().equals("CODING") && q.coding() == null)
      throw new Problem(422, "Hidden tests required");
  }

  @GetMapping
  public List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String search) {
    var a = identity.staff();
    if (page < 0 || page > 10000 || search.length() > 200) throw new Problem(422, "Invalid search");
    return db
        .jdbc
        .queryForList(
            "select b.id,b.archived,b.revision,v.id as"
                + " version_id,v.version,v.state,v.author_id,v.content from question_bank b join"
                + " lateral (select * from question_bank_versions where question_id=b.id order by"
                + " version desc limit 1) v on true where b.organization_id=? and position(lower(?)"
                + " in lower(v.content->>'title'))>0 order by b.created_at desc,b.id limit 25"
                + " offset ?",
            a.org(),
            search,
            page * 25)
        .stream()
        .map(
            r -> {
              r.put("content", db.decode(r.get("content"), AssessmentContent.Question.class));
              return r;
            })
        .toList();
  }

  @PostMapping("/import")
  @Transactional
  public List<Map<String, Object>> importQuestions(@RequestBody Import request) {
    var a = identity.author();
    if (request.questions() == null
        || request.questions().isEmpty()
        || request.questions().size() > 50) throw new Problem(422, "Import 1–50 questions");
    request.questions().forEach(QuestionBank::validate);
    List<Map<String, Object>> result = new ArrayList<>();
    for (var q : request.questions()) {
      UUID id = UUID.randomUUID();
      db.jdbc.update("insert into question_bank(id,organization_id) values (?,?)", id, a.org());
      UUID version = insert(a, id, 1, q);
      result.add(Map.of("id", id, "versionId", version, "revision", 0));
    }
    return result;
  }

  UUID insert(Identity.Actor a, UUID id, int version, AssessmentContent.Question q) {
    UUID vid = UUID.randomUUID();
    db.jdbc.update(
        "insert into"
            + " question_bank_versions(id,organization_id,question_id,version,author_id,content)"
            + " values (?,?,?,?,?,?::jsonb)",
        vid,
        a.org(),
        id,
        version,
        a.id(),
        db.encode(q));
    db.audit(a.org(), a.id(), "QUESTION_BANK_VERSION_DRAFTED", vid);
    return vid;
  }

  Map<String, Object> lock(UUID id, Identity.Actor a, long revision) {
    var b =
        db.one(
            "select * from question_bank where id=? and organization_id=? for update", id, a.org());
    if (Attempts.number(b, "revision") != revision)
      throw new Problem(409, "Question revision stale");
    return b;
  }

  @PostMapping("/{id}/versions")
  @Transactional
  public Map<String, Object> revise(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long revision,
      @RequestBody AssessmentContent.Question question) {
    var a = identity.author();
    var b = lock(id, a, revision);
    if (Boolean.TRUE.equals(b.get("archived"))) throw new Problem(409, "Question archived");
    validate(question);
    int next =
        Objects.requireNonNull(
            db.jdbc.queryForObject(
                "select max(version)+1 from question_bank_versions where question_id=?",
                Integer.class,
                id));
    UUID vid = insert(a, id, next, question);
    db.jdbc.update("update question_bank set revision=revision+1 where id=?", id);
    return Map.of("versionId", vid, "revision", revision + 1);
  }

  @PostMapping("/{id}/review")
  @Transactional
  public void review(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long revision,
      @RequestBody Decision decision) {
    var a = identity.staff();
    var b = lock(id, a, revision);
    if (Boolean.TRUE.equals(b.get("archived"))) throw new Problem(409, "Question archived");
    var v =
        db.one(
            "select * from question_bank_versions where question_id=? order by version desc limit"
                + " 1",
            id);
    if (a.id().equals(v.get("author_id")))
      throw new Problem(403, "A different authorized reviewer is required");
    if (!v.get("state").equals("DRAFT")) throw new Problem(409, "Draft review required");
    if (decision.reason() == null
        || decision.reason().isBlank()
        || decision.reason().length() > 2000) throw new Problem(422, "Review reason required");
    validate(db.decode(v.get("content"), AssessmentContent.Question.class));
    db.jdbc.update(
        "update question_bank_versions set state=?,reviewer_id=?,reason=?,reviewed_at=now() where"
            + " id=?",
        decision.approve() ? "APPROVED" : "REJECTED",
        a.id(),
        decision.reason(),
        v.get("id"));
    db.jdbc.update("update question_bank set revision=revision+1 where id=?", id);
    db.audit(a.org(), a.id(), "QUESTION_BANK_REVIEWED", (UUID) v.get("id"));
  }

  @PostMapping("/{id}/archive")
  @Transactional
  public void archive(@PathVariable UUID id, @RequestHeader("If-Match") long revision) {
    var a = identity.author();
    lock(id, a, revision);
    db.jdbc.update("update question_bank set archived=true,revision=revision+1 where id=?", id);
    db.audit(a.org(), a.id(), "QUESTION_BANK_ARCHIVED", id);
  }

  @PostMapping("/assessments/{id}/questions")
  @Transactional
  public void attach(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long revision,
      @RequestBody Selection selection) {
    var a = identity.author();
    var assessment =
        db.one(
            "select * from assessments where id=? and organization_id=? for update", id, a.org());
    if (!assessment.get("status").equals("DRAFT")
        || Attempts.number(assessment, "revision") != revision
        || assessment.get("blueprint_id") != null)
      throw new Problem(
          409, "Current manual draft required; approved blueprints cannot be changed");
    var v =
        db.one(
            "select v.content,v.id from question_bank_versions v join question_bank b on"
                + " b.id=v.question_id where v.id=? and v.organization_id=? and v.state='APPROVED'"
                + " and not b.archived for share of b,v",
            selection.versionId(),
            a.org());
    var q = db.decode(v.get("content"), AssessmentContent.Question.class);
    var c = db.decode(assessment.get("content"), AssessmentContent.class);
    if (c.questions().stream().anyMatch(item -> item.id().equals(q.id())))
      throw new Problem(409, "Question already present");
    var questions = new ArrayList<>(c.questions());
    questions.add(q);
    var updated =
        new AssessmentContent(
            c.title(),
            c.instructions(),
            c.durationMinutes(),
            c.timerPolicy(),
            c.maxPauses(),
            c.maxPauseSeconds(),
            c.totalPauseSeconds(),
            c.availableFrom(),
            c.latestStart(),
            c.completionDeadline(),
            c.cameraRequired(),
            c.microphoneRequired(),
            c.backNavigation(),
            c.passingPercent(),
            questions);
    updated.validate();
    db.jdbc.update(
        "update assessments set content=?::jsonb,revision=revision+1 where id=?",
        db.encode(updated),
        id);
    db.jdbc.update(
        "insert into assessment_bank_sources(organization_id,assessment_id,question_id,version_id)"
            + " values (?,?,?,?)",
        a.org(),
        id,
        q.id(),
        selection.versionId());
    db.audit(a.org(), a.id(), "QUESTION_BANK_ATTACHED", id);
  }
}
