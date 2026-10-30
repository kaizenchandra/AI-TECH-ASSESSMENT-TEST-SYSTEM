package com.assessment.platform;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BlueprintApi {
  final Store db;
  final Identity identity;
  final GeneratedAssessments generated;

  public BlueprintApi(Store db, Identity identity, GeneratedAssessments generated) {
    this.db = db;
    this.identity = identity;
    this.generated = generated;
  }

  public record Estimate(UUID artifactId, long revision, int questionIndex, int minutes) {}

  public record Request(
      UUID competencyId,
      long competencyRevision,
      List<Blueprint.Requirement> requirements,
      List<Estimate> pool,
      int maxMinutes,
      int reviewMinutes,
      long seed,
      String language) {}

  public record Computed(Blueprint.Result result, List<GeneratedAssessments.Item> items) {}

  Computed compute(Request request, UUID org) {
    if (request.requirements() == null
        || request.requirements().isEmpty()
        || request.requirements().size() > 30
        || request.pool() == null
        || request.pool().isEmpty()
        || request.pool().size() > 200
        || request.maxMinutes() < 1
        || request.maxMinutes() > 480
        || request.reviewMinutes() < 0
        || request.reviewMinutes() >= request.maxMinutes()
        || request.language() == null
        || !Set.of("java", "python", "javascript").contains(request.language()))
      throw new Problem(422, "Invalid bounded blueprint constraints");
    var map =
        db.one(
            "select content,revision,state from ai_artifacts where organization_id=? and id=? and"
                + " purpose='COMPETENCY'",
            org,
            request.competencyId());
    if (!map.get("state").equals("APPROVED")
        || Attempts.number(map, "revision") != request.competencyRevision())
      throw new Problem(409, "Current approved competency map required");
    var skills = db.decode(map.get("content"), AiProvider.Draft.class).skills();
    Set<String> requested = new HashSet<>();
    int count = 0;
    Map<String, Integer> counts = new HashMap<>();
    for (var r : request.requirements()) {
      if (r == null
          || r.skill() == null
          || r.type() == null
          || !Set.of("SINGLE", "MULTIPLE", "CODING").contains(r.type())
          || r.count() < 1
          || r.count() > 100
          || !requested.add(r.skill() + ":" + r.type())
          || skills.stream().noneMatch(s -> s.id().equals(r.skill())))
        throw new Problem(422, "Unique skill/type rows from the approved map are required");
      count += r.count();
      counts.put(r.skill(), counts.getOrDefault(r.skill(), 0) + r.count());
    }
    if (count > 100) throw new Problem(422, "At most 100 questions per form");
    List<String> unmet = new ArrayList<>();
    for (var s : skills)
      if (counts.getOrDefault(s.id(), 0) * 100 != s.weight() * count)
        unmet.add(
            "Requested item coverage for "
                + s.id()
                + " differs from approved weight "
                + s.weight()
                + "%; each item has equal points");
    List<Blueprint.Item> bank = new ArrayList<>();
    Map<UUID, GeneratedAssessments.Item> refs = new HashMap<>();
    for (var e : request.pool()) {
      if (e == null || e.artifactId() == null || e.minutes() < 1 || e.minutes() > 240)
        throw new Problem(422, "Each pool item needs a solving-time estimate of 1–240 minutes");
      var row =
          db.one(
              "select * from ai_artifacts where id=? and organization_id=? and purpose='QUESTION'",
              e.artifactId(),
              org);
      if (!row.get("state").equals("APPROVED") || Attempts.number(row, "revision") != e.revision())
        throw new Problem(409, "Pool contains a stale or unapproved question");
      GeneratedAssessments.approvable(db, row);
      var questions = db.decode(row.get("content"), AiProvider.Draft.class).questions();
      if (e.questionIndex() < 0 || e.questionIndex() >= questions.size())
        throw new Problem(422, "Invalid item index");
      var q = questions.get(e.questionIndex());
      UUID key =
          UUID.nameUUIDFromBytes(
              (e.artifactId() + ":" + e.revision() + ":" + e.questionIndex())
                  .getBytes(StandardCharsets.UTF_8));
      if (refs.put(
              key, new GeneratedAssessments.Item(e.artifactId(), e.revision(), e.questionIndex()))
          != null) throw new Problem(422, "Duplicate pool item");
      if (q.coding() == null || q.coding().language().equals(request.language()))
        bank.add(new Blueprint.Item(key, q.skill(), q.type(), e.minutes(), true));
    }
    var selected =
        Blueprint.select(
            bank,
            request.requirements(),
            request.maxMinutes() - request.reviewMinutes(),
            Set.of(),
            request.seed());
    unmet.addAll(selected.unmet());
    var result =
        new Blueprint.Result(
            unmet.isEmpty(),
            unmet.isEmpty() ? selected.versions() : List.of(),
            unmet,
            selected.seed(),
            selected.algorithm());
    return new Computed(result, result.versions().stream().map(refs::get).toList());
  }

  @PostMapping("/blueprints")
  @Transactional
  public Map<String, Object> create(@RequestBody Request request) {
    var a = identity.author();
    var computed = compute(request, a.org());
    UUID id = UUID.randomUUID();
    db.jdbc.update(
        "insert into"
            + " assessment_blueprints(id,organization_id,author_id,competency_id,competency_revision,constraints,result,selected_items,state)"
            + " values (?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?)",
        id,
        a.org(),
        a.id(),
        request.competencyId(),
        request.competencyRevision(),
        db.encode(request),
        db.encode(computed.result()),
        db.encode(computed.items()),
        computed.result().feasible() ? "REVIEW_REQUIRED" : "INFEASIBLE");
    db.audit(a.org(), a.id(), "BLUEPRINT_PROPOSED", id);
    return Map.of("id", id, "result", computed.result());
  }

  @GetMapping("/blueprints")
  public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page) {
    var a = identity.staff();
    var rows =
        db.jdbc.queryForList(
            "select * from assessment_blueprints where organization_id=? order by created_at"
                + " desc,id limit 25 offset ?",
            a.org(),
            HumanReviews.offset(page));
    for (var r : rows) {
      r.put("result", db.decode(r.get("result"), Map.class));
      r.put("constraints", db.decode(r.get("constraints"), Map.class));
      r.put("selected_items", db.decode(r.get("selected_items"), List.class));
    }
    return rows;
  }

  public record Decision(String decision, String reason) {}

  @PostMapping("/blueprint-versions/{id}/review-decisions")
  @Transactional
  public void review(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long revision,
      @RequestBody Decision decision) {
    var a = identity.staff();
    var row =
        db.one(
            "select * from assessment_blueprints where id=? and organization_id=? for update",
            id,
            a.org());
    if (a.id().equals(row.get("author_id")))
      throw new Problem(403, "Independent reviewer required");
    if (Attempts.number(row, "revision") != revision || !row.get("state").equals("REVIEW_REQUIRED"))
      throw new Problem(409, "Stale or non-reviewable blueprint");
    if (decision.decision() == null
        || !Set.of("APPROVED", "REJECTED").contains(decision.decision())
        || decision.reason() == null
        || decision.reason().isBlank()
        || decision.reason().length() > 4000)
      throw new Problem(422, "Decision and reason required");
    if (decision.decision().equals("APPROVED")) {
      var computed = compute(db.decode(row.get("constraints"), Request.class), a.org());
      if (!computed.result().feasible()
          || !db.encode(computed.items())
              .equals(
                  db.encode(
                      db.decode(row.get("selected_items"), GeneratedAssessments.Item[].class))))
        throw new Problem(409, "Blueprint dependencies changed");
    }
    db.jdbc.update(
        "update assessment_blueprints set"
            + " state=?,revision=revision+1,reviewer_id=?,review_reason=?,reviewed_at=now() where"
            + " id=?",
        decision.decision(),
        a.id(),
        decision.reason(),
        id);
    db.audit(a.org(), a.id(), "BLUEPRINT_REVIEWED", id);
  }

  @PostMapping("/blueprints/{id}/assessments")
  @Transactional
  public Map<String, Object> prepare(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long revision,
      @RequestBody GeneratedAssessments.Prepare settings) {
    var a = identity.author();
    var row =
        db.one(
            "select * from assessment_blueprints where id=? and organization_id=? for update",
            id,
            a.org());
    if (!row.get("state").equals("APPROVED") || Attempts.number(row, "revision") != revision)
      throw new Problem(409, "Approved current blueprint required");
    var request = db.decode(row.get("constraints"), Request.class);
    var computed = compute(request, a.org());
    if (!computed.result().feasible() || settings.durationMinutes() != request.maxMinutes())
      throw new Problem(409, "Assessment duration must match the approved blueprint");
    var result =
        generated.prepare(
            new GeneratedAssessments.Prepare(
                settings.title(),
                settings.durationMinutes(),
                settings.passingPercent(),
                settings.latestStart(),
                settings.completionDeadline(),
                computed.items()));
    db.jdbc.update("update assessments set blueprint_id=? where id=?", id, result.get("id"));
    return result;
  }
}
