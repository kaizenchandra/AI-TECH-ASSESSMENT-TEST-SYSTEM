package com.assessment.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class Identity {
  final Store db;
  final PasswordEncoder passwords;
  final Clock clock;

  public Identity(Store db, PasswordEncoder passwords, Clock clock) {
    this.db = db;
    this.passwords = passwords;
    this.clock = clock;
  }

  public record Actor(UUID id, UUID org, String role, boolean verified, String email) {}

  public Actor actor() {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    var row =
        db.one(
            "select u.*,m.organization_id,m.role from users u join organization_members m on"
                + " m.user_id=u.id where u.email=?",
            email);
    return new Actor(
        (UUID) row.get("id"),
        (UUID) row.get("organization_id"),
        (String) row.get("role"),
        (boolean) row.get("verified"),
        email);
  }

  public Actor staff() {
    var a = actor();
    if (!a.verified() || !Set.of("ADMIN", "RECRUITER", "EVALUATOR").contains(a.role()))
      throw new Problem(403, "Verified staff access required");
    return a;
  }

  public Actor author() {
    var a = staff();
    if (a.role().equals("EVALUATOR")) throw new Problem(403, "Authoring access required");
    return a;
  }

  public static String token() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record Registration(
      @Email @NotBlank @Size(max = 254) String email,
      @Size(min = 12, max = 128) @NotNull String password,
      @Size(max = 200) String organization,
      String invitation) {}

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "header", token.getHeaderName());
  }

  @GetMapping("/me")
  public Actor me() {
    return actor();
  }

  @PostMapping("/register")
  @Transactional
  public Map<String, String> register(@Valid @RequestBody Registration request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    UUID org;
    String role;
    if (request.invitation() != null && !request.invitation().isBlank()) {
      var inv =
          db.one(
              "select * from invitations where token_hash=? and revoked=false and expires_at>?",
              hash(request.invitation()),
              Timestamp.from(clock.instant()));
      if (!email.equals(inv.get("email")))
        throw new Problem(403, "Invitation email does not match");
      org = (UUID) inv.get("organization_id");
      role = "CANDIDATE";
    } else {
      if (request.organization() == null || request.organization().isBlank())
        throw new Problem(400, "Organization required");
      org = UUID.randomUUID();
      role = "ADMIN";
      db.jdbc.update(
          "insert into organizations(id,name) values (?,?)", org, request.organization());
    }
    UUID user = UUID.randomUUID();
    db.jdbc.update(
        "insert into users(id,email,password_hash) values (?,?,?)",
        user,
        email,
        passwords.encode(request.password()));
    db.jdbc.update("insert into organization_members values (?,?,?)", org, user, role);
    String secret = token();
    db.jdbc.update(
        "insert into verification_tokens values (?,?,?)",
        hash(secret),
        user,
        Timestamp.from(clock.instant().plusSeconds(3600)));
    db.jdbc.update(
        "insert into notifications(id,recipient,subject,body) values (?,?,?,?)",
        UUID.randomUUID(),
        email,
        "Verify your assessment account",
        "Your single-use verification token (valid for one hour): " + secret);
    return Map.of("status", "VERIFICATION_REQUIRED");
  }

  public record Verification(@NotBlank String token) {}

  @PostMapping("/verify")
  @Transactional
  public Map<String, String> verify(@Valid @RequestBody Verification request) {
    var row =
        db.one(
            "select * from verification_tokens where token_hash=? and expires_at>? for update",
            hash(request.token()),
            Timestamp.from(clock.instant()));
    db.jdbc.update("update users set verified=true where id=?", row.get("user_id"));
    db.jdbc.update("delete from verification_tokens where token_hash=?", hash(request.token()));
    return Map.of("status", "VERIFIED");
  }
}
