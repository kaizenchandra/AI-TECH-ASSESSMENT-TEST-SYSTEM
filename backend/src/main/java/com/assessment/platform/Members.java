package com.assessment.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
public class Members {
  final Store db;
  final Identity identity;
  final PasswordEncoder encoder;
  final Clock clock;

  public Members(Store db, Identity identity, PasswordEncoder encoder, Clock clock) {
    this.db = db;
    this.identity = identity;
    this.encoder = encoder;
    this.clock = clock;
  }

  public record Member(
      @Email @NotBlank String email,
      @Size(min = 12, max = 128) @NotNull String password,
      String role) {}

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page) {
    var a = identity.staff();
    if (!a.role().equals("ADMIN")) throw new Problem(403, "Organization administrator required");
    if (page < 0 || page > 10000) throw new Problem(422, "Invalid page");
    return db.jdbc.queryForList(
        "select m.user_id,u.email,u.verified,m.role,(select count(*) from member_role_decisions d"
            + " where d.organization_id=m.organization_id and d.user_id=m.user_id) as revision from"
            + " organization_members m join users u on u.id=m.user_id where m.organization_id=? and"
            + " m.role<>'CANDIDATE' order by u.email limit 25 offset ?",
        a.org(),
        page * 25);
  }

  public record RoleDecision(String role, String reason) {}

  @PostMapping("/{id}/role-decisions")
  @Transactional
  public void changeRole(
      @PathVariable UUID id,
      @RequestHeader("If-Match") long expected,
      @RequestBody RoleDecision request) {
    var a = identity.staff();
    db.one("select id from organizations where id=? for update", a.org());
    var own =
        db.one(
            "select role from organization_members where organization_id=? and user_id=?",
            a.org(),
            a.id());
    if (!own.get("role").equals("ADMIN"))
      throw new Problem(403, "Organization administrator required");
    if (id.equals(a.id())) throw new Problem(403, "Another administrator must change your role");
    var target =
        db.one(
            "select role from organization_members where organization_id=? and user_id=? and"
                + " role<>'CANDIDATE' for update",
            a.org(),
            id);
    if (request.role() == null
        || !Set.of("ADMIN", "RECRUITER", "EVALUATOR").contains(request.role())
        || request.reason() == null
        || request.reason().isBlank()
        || request.reason().length() > 2000)
      throw new Problem(422, "Staff role and bounded reason required");
    long revision =
        Objects.requireNonNull(
            db.jdbc.queryForObject(
                "select count(*) from member_role_decisions where organization_id=? and user_id=?",
                Long.class,
                a.org(),
                id));
    if (revision != expected) throw new Problem(409, "Membership revision stale");
    if (target.get("role").equals(request.role())) return;
    db.jdbc.update(
        "insert into"
            + " member_role_decisions(id,organization_id,user_id,revision,actor_id,previous_role,resulting_role,reason)"
            + " values (?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        a.org(),
        id,
        revision + 1,
        a.id(),
        target.get("role"),
        request.role(),
        request.reason());
    db.jdbc.update(
        "update organization_members set role=? where organization_id=? and user_id=?",
        request.role(),
        a.org(),
        id);
    db.audit(a.org(), a.id(), "MEMBER_ROLE_CHANGED", id);
  }

  @GetMapping("/{id}/role-decisions")
  public List<Map<String, Object>> history(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    var a = identity.staff();
    if (!a.role().equals("ADMIN")) throw new Problem(403, "Organization administrator required");
    if (page < 0 || page > 10000) throw new Problem(422, "Invalid page");
    db.one(
        "select user_id from organization_members where organization_id=? and user_id=?",
        a.org(),
        id);
    return db.jdbc.queryForList(
        "select revision,actor_id,previous_role,resulting_role,reason,created_at from"
            + " member_role_decisions where organization_id=? and user_id=? order by revision desc"
            + " limit 25 offset ?",
        a.org(),
        id,
        page * 25);
  }

  @PostMapping
  @Transactional
  public Map<String, UUID> add(@Valid @RequestBody Member request) {
    var a = identity.author();
    if (!a.role().equals("ADMIN") || !Set.of("RECRUITER", "EVALUATOR").contains(request.role))
      throw new Problem(403, "Organization administrator required");
    UUID id = UUID.randomUUID();
    String email = request.email.toLowerCase(Locale.ROOT);
    db.jdbc.update(
        "insert into users(id,email,password_hash) values (?,?,?)",
        id,
        email,
        encoder.encode(request.password));
    db.jdbc.update("insert into organization_members values (?,?,?)", a.org(), id, request.role);
    String token = Identity.token();
    db.jdbc.update(
        "insert into verification_tokens values (?,?,?)",
        Identity.hash(token),
        id,
        Timestamp.from(clock.instant().plusSeconds(3600)));
    db.jdbc.update(
        "insert into notifications(id,recipient,subject,body) values (?,?,?,?)",
        UUID.randomUUID(),
        email,
        "Verify staff account",
        "Verification token: " + token);
    db.audit(a.org(), a.id(), "MEMBER_CREATED", id);
    return Map.of("id", id);
  }
}
