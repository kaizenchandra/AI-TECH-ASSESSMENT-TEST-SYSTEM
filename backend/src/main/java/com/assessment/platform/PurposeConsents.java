package com.assessment.platform;

import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidate")
public class PurposeConsents {
  final Store db;
  final Identity identity;

  public PurposeConsents(Store db, Identity identity) {
    this.db = db;
    this.identity = identity;
  }

  @GetMapping("/purpose-consents")
  public List<Map<String, Object>> list() {
    var a = identity.actor();
    return db.jdbc.queryForList(
        "select k.invitation_id,k.optional_ai,k.consent_revision,k.disclosure_version from"
            + " candidate_acknowledgments k where k.candidate_id=? order by k.acknowledged_at desc"
            + " limit 100",
        a.id());
  }

  public record Change(
      UUID invitationId, String purpose, String disclosureVersion, boolean granted) {}

  @PostMapping("/purpose-consents")
  @Transactional
  public Map<String, Object> change(
      @RequestHeader("If-Match") long revision, @RequestBody Change change) {
    var a = identity.actor();
    if (!a.verified()) throw new Problem(403, "Verified account required");
    if (!"POST_SUBMISSION_FEEDBACK".equals(change.purpose())
        || !"disclosure-v1".equals(change.disclosureVersion()))
      throw new Problem(422, "Unsupported purpose or disclosure; review the current notice");
    var row =
        db.one(
            "select k.*,i.organization_id from candidate_acknowledgments k join invitations i on"
                + " i.id=k.invitation_id where k.invitation_id=? and k.candidate_id=? for update of"
                + " k",
            change.invitationId(),
            a.id());
    if (Attempts.number(row, "consent_revision") != revision)
      throw new Problem(409, "Consent changed; refresh before updating");
    if (Objects.equals(row.get("optional_ai"), change.granted()))
      return Map.of("revision", revision, "granted", change.granted());
    db.jdbc.update(
        "update candidate_acknowledgments set optional_ai=?,consent_revision=consent_revision+1"
            + " where invitation_id=?",
        change.granted(),
        change.invitationId());
    db.jdbc.update(
        "insert into"
            + " purpose_consent_events(id,organization_id,invitation_id,candidate_id,revision,granted,disclosure_version)"
            + " values (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        row.get("organization_id"),
        change.invitationId(),
        a.id(),
        revision + 1,
        change.granted(),
        change.disclosureVersion());
    db.audit(
        (UUID) row.get("organization_id"),
        a.id(),
        change.granted() ? "OPTIONAL_AI_GRANTED" : "OPTIONAL_AI_WITHDRAWN",
        change.invitationId());
    return Map.of("revision", revision + 1, "granted", change.granted());
  }
}
