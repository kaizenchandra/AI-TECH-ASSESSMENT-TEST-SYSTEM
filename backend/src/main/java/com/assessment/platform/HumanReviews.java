package com.assessment.platform;

import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HumanReviews {
  final Store db;
  final Identity identity;

  public HumanReviews(Store db, Identity identity) {
    this.db = db;
    this.identity = identity;
  }

  @GetMapping("/candidate/review-requests")
  public List<Map<String, Object>> mine(@RequestParam(defaultValue = "0") int page) {
    var a = identity.actor();
    return db.jdbc.queryForList(
        "select id,attempt_id,reason,state,response,created_at,updated_at,resolved_at,revision from"
            + " candidate_review_requests where candidate_id=? order by created_at desc,id limit 25"
            + " offset ?",
        a.id(),
        offset(page));
  }

  @GetMapping("/review-requests")
  public List<Map<String, Object>> queue(@RequestParam(defaultValue = "0") int page) {
    var a = identity.staff();
    return db.jdbc.queryForList(
        "select"
            + " r.id,r.attempt_id,r.reason,r.state,r.response,r.revision,r.assigned_to,r.created_at,r.updated_at"
            + " from candidate_review_requests r where r.organization_id=? order by r.created_at"
            + " desc,r.id limit 25 offset ?",
        a.org(),
        offset(page));
  }

  static int offset(int page) {
    if (page < 0 || page > 10000) throw new Problem(422, "Invalid page");
    return page * 25;
  }

  public record Decision(String action, String response) {}

  @PostMapping("/review-requests/{id}/decisions")
  @Transactional
  public Map<String, Object> decide(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long revision,
      @RequestBody Decision decision) {
    var a = identity.staff();
    if (!Set.of("ADMIN", "EVALUATOR").contains(a.role()))
      throw new Problem(403, "An evaluator or administrator must handle reviews");
    var r =
        db.one(
            "select * from candidate_review_requests where id=? and organization_id=? for update",
            id,
            a.org());
    if (Attempts.number(r, "revision") != revision || r.get("state").equals("RESOLVED"))
      throw new Problem(409, "Review changed or already resolved; refresh the queue");
    if ("ASSIGN_TO_ME".equals(decision.action())) {
      if (r.get("assigned_to") != null
          && !r.get("assigned_to").equals(a.id())
          && !a.role().equals("ADMIN")) throw new Problem(409, "Another reviewer is assigned");
      db.jdbc.update(
          "update candidate_review_requests set"
              + " assigned_to=?,state='IN_REVIEW',revision=revision+1,updated_at=now() where id=?",
          a.id(),
          id);
    } else if ("RESOLVE".equals(decision.action())) {
      if (!a.id().equals(r.get("assigned_to")))
        throw new Problem(403, "Assign this review to yourself before resolving it");
      if (decision.response() == null
          || decision.response().isBlank()
          || decision.response().length() > 4000)
        throw new Problem(422, "A candidate-visible response (1–4000 characters) is required");
      db.jdbc.update(
          "update candidate_review_requests set"
              + " state='RESOLVED',response=?,revision=revision+1,resolved_at=now(),updated_at=now()"
              + " where id=?",
          decision.response(),
          id);
    } else throw new Problem(422, "Unknown review action");
    db.audit(a.org(), a.id(), "HUMAN_REVIEW_" + decision.action(), id);
    return Map.of("id", id, "revision", revision + 1);
  }
}
