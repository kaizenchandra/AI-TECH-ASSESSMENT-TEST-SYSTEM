package com.assessment.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@Testcontainers
class PersistenceIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("app.ai.mode", () -> "disabled");
    r.add("app.coding.initial-delay-ms", () -> "3600000");
  }

  @Autowired Store db;

  @Test
  void staffRoleDecisionsAreScopedVersionedAndCannotDemoteTheActingAdministrator() {
    db.jdbc.update(
        "update organization_members set role='ADMIN' where organization_id=? and user_id=?",
        org,
        user);
    UUID target = UUID.randomUUID();
    db.jdbc.update(
        "insert into users(id,email,password_hash,verified) values (?,?,'unused',true)",
        target,
        target + "@example.test");
    db.jdbc.update("insert into organization_members values (?,?,'RECRUITER')", org, target);
    var auth = mock(Identity.class);
    when(auth.staff()).thenReturn(new Identity.Actor(user, org, "ADMIN", true, actor.email()));
    var members =
        new Members(
            db,
            auth,
            mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            Clock.systemUTC());
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s ->
                    members.changeRole(
                        user, 0, new Members.RoleDecision("EVALUATOR", "Self demotion"))));
    tx.executeWithoutResult(
        s ->
            members.changeRole(
                target, 0, new Members.RoleDecision("EVALUATOR", "Assigned to evaluation duties")));
    assertEquals(
        "EVALUATOR",
        db.one(
                "select role from organization_members where organization_id=? and user_id=?",
                org,
                target)
            .get("role"));
    assertEquals(1, members.history(target, 0).size());
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s ->
                    members.changeRole(
                        target, 0, new Members.RoleDecision("ADMIN", "Stale promotion"))));
    when(auth.staff())
        .thenReturn(new Identity.Actor(target, org, "EVALUATOR", true, "staff@example.test"));
    assertThrows(Problem.class, () -> members.list(0));
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s ->
                    members.changeRole(
                        user, 0, new Members.RoleDecision("EVALUATOR", "Unauthorized"))));
    when(auth.staff())
        .thenReturn(new Identity.Actor(user, UUID.randomUUID(), "ADMIN", true, actor.email()));
    assertThrows(Problem.class, () -> members.history(target, 0));
  }

  @Test
  void retakeIsAuditedIdempotentBoundedAndPreservesTheFinalizedAttempt() {
    var auth = mock(Identity.class);
    when(auth.author()).thenReturn(new Identity.Actor(user, org, "ADMIN", true, actor.email()));
    var api = new Authoring(db, auth, Clock.systemUTC(), "http://localhost");
    db.jdbc.update("update assessments set status='PUBLISHED' where organization_id=?", org);
    UUID tab = UUID.randomUUID();
    UUID attempt = (UUID) service.start(inv, tab, actor).get("id");
    var request =
        new Authoring.Retake(
            Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
            "Reviewed interruption; permit one new attempt");
    UUID key = UUID.randomUUID();
    assertThrows(Problem.class, () -> tx.execute(s -> api.retake(inv, key, request)));
    service.submit(attempt, tab, user);
    var result = Objects.requireNonNull(tx.execute(s -> api.retake(inv, key, request)));
    assertEquals(result, tx.execute(s -> api.retake(inv, key, request)));
    assertThrows(Problem.class, () -> tx.execute(s -> api.retake(inv, UUID.randomUUID(), request)));
    assertEquals(
        "SUBMITTED",
        db.one("select status from assessment_attempts where id=?", attempt).get("status"));
    var child = db.one("select * from invitations where id=?", result.get("id"));
    assertNull(child.get("claimed_by"));
    assertEquals(
        db.one("select assessment_version_id from invitations where id=?", inv)
            .get("assessment_version_id"),
        child.get("assessment_version_id"));
    assertEquals(
        1,
        db.jdbc.queryForObject(
            "select count(*) from invitation_retakes where source_id=?", Integer.class, inv));
    UUID aid = (UUID) db.one("select id from assessments where organization_id=?", org).get("id");
    assertThrows(
        Problem.class,
        () ->
            tx.execute(
                s -> api.invite(new Authoring.Invite(aid, actor.email(), request.expiresAt()))));
    when(auth.author())
        .thenReturn(new Identity.Actor(user, UUID.randomUUID(), "ADMIN", true, actor.email()));
    assertThrows(Problem.class, () -> tx.execute(s -> api.retake(inv, key, request)));
  }

  @Test
  void bankVersionsRequireIndependentReviewAndArchiveBlocksPublication() {
    var auth = mock(Identity.class);
    var author = new Identity.Actor(user, org, "ADMIN", true, actor.email());
    when(auth.author()).thenReturn(author);
    when(auth.staff()).thenReturn(author);
    var bank = new QuestionBank(db, auth);
    assertThrows(Problem.class, () -> QuestionBank.validate(null));
    var question =
        new AssessmentContent.Question(
            UUID.randomUUID(),
            "SINGLE",
            "Reusable Java",
            "Select one",
            "java",
            List.of("A", "B"),
            List.of(0),
            10,
            0,
            false,
            null,
            null,
            List.of());
    var imported =
        Objects.requireNonNull(
                tx.execute(s -> bank.importQuestions(new QuestionBank.Import(List.of(question)))))
            .getFirst();
    UUID bankId = (UUID) imported.get("id"), versionId = (UUID) imported.get("versionId");
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s -> bank.review(bankId, 0, new QuestionBank.Decision(true, "Reviewed"))));
    UUID reviewer = UUID.randomUUID();
    db.jdbc.update(
        "insert into users(id,email,password_hash,verified) values (?,?,'unused',true)",
        reviewer,
        reviewer + "@example.test");
    when(auth.staff())
        .thenReturn(new Identity.Actor(reviewer, org, "EVALUATOR", true, "reviewer@example.test"));
    tx.executeWithoutResult(
        s -> bank.review(bankId, 0, new QuestionBank.Decision(true, "Answer key checked")));
    assertThrows(Problem.class, () -> tx.execute(s -> bank.revise(bankId, 0, question)));
    var assessment = db.one("select id,content from assessments where organization_id=?", org);
    UUID aid = (UUID) assessment.get("id");
    tx.executeWithoutResult(s -> bank.attach(aid, 0, new QuestionBank.Selection(versionId)));
    assertEquals(
        2,
        db.decode(
                db.one("select content from assessments where id=?", aid).get("content"),
                AssessmentContent.class)
            .questions()
            .size());
    tx.executeWithoutResult(s -> bank.archive(bankId, 1));
    var authoring = new Authoring(db, auth, Clock.systemUTC(), "http://localhost");
    assertEquals(
        "A question-bank source is archived or unapproved",
        assertThrows(Problem.class, () -> tx.execute(s -> authoring.publish(aid, 1))).getMessage());
    assertThrows(
        Exception.class,
        () ->
            db.jdbc.update(
                "update question_bank_versions set content='{}'::jsonb where id=?", versionId));
    when(auth.staff())
        .thenReturn(
            new Identity.Actor(
                reviewer, UUID.randomUUID(), "ADMIN", true, "reviewer@example.test"));
    assertTrue(bank.list(0, "").isEmpty());
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s -> bank.review(bankId, 2, new QuestionBank.Decision(true, "Cross tenant"))));
  }

  @Test
  void integrityRetriesAtQuotaAreFreeButConflictingIdsFail() {
    UUID id = (UUID) service.start(inv, UUID.randomUUID(), actor).get("id");
    var auth = mock(Identity.class);
    when(auth.actor()).thenReturn(actor);
    var api = new IntegrityApi(db, auth, Clock.systemUTC());
    var event = new IntegrityApi.Event(UUID.randomUUID(), "BLUR", Instant.now());
    tx.executeWithoutResult(s -> api.ingest(id, new IntegrityApi.Batch(List.of(event, event))));
    for (int batch = 0; batch < 3; batch++) {
      var events = new ArrayList<IntegrityApi.Event>();
      for (int i = 0; i < (batch == 2 ? 19 : 20); i++)
        events.add(new IntegrityApi.Event(UUID.randomUUID(), "FOCUS", Instant.now()));
      tx.executeWithoutResult(s -> api.ingest(id, new IntegrityApi.Batch(events)));
    }
    tx.executeWithoutResult(s -> api.ingest(id, new IntegrityApi.Batch(List.of(event))));
    assertEquals(
        60,
        db.jdbc.queryForObject(
            "select count(*) from integrity_events where attempt_id=?", Integer.class, id));
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s ->
                    api.ingest(
                        id,
                        new IntegrityApi.Batch(
                            List.of(
                                new IntegrityApi.Event(
                                    event.id(), "HIDDEN", event.observedAt()))))));
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s ->
                    api.ingest(
                        id,
                        new IntegrityApi.Batch(
                            List.of(
                                new IntegrityApi.Event(
                                    UUID.randomUUID(), "BLUR", Instant.now()))))));
    assertEquals(
        0L, db.one("select revision from assessment_attempts where id=?", id).get("revision"));
  }

  @Test
  void repeatingReadinessCannotOverwriteASeparateConsentDecision() {
    var auth = mock(Identity.class);
    when(auth.actor()).thenReturn(actor);
    var consents = new PurposeConsents(db, auth);
    tx.execute(
        s ->
            consents.change(
                0,
                new PurposeConsents.Change(
                    inv, "POST_SUBMISSION_FEEDBACK", "disclosure-v1", true)));
    UUID version =
        (UUID)
            db.one("select assessment_version_id from invitations where id=?", inv)
                .get("assessment_version_id");
    var api = new CandidateApi(db, auth, service, Clock.systemUTC());
    tx.executeWithoutResult(
        s ->
            api.acknowledge(
                inv,
                new CandidateApi.Checks(
                    true,
                    true,
                    true,
                    true,
                    false,
                    false,
                    false,
                    true,
                    true,
                    false,
                    false,
                    version,
                    "candidate-policy-v2")));
    assertEquals(true, consents.list().getFirst().get("optional_ai"));
    assertEquals(1L, consents.list().getFirst().get("consent_revision"));
  }

  @Test
  void jobWeightsRevisionsAndPublishedJobContextStayTenantSafe() {
    var auth = mock(Identity.class);
    var author = new Identity.Actor(user, org, "RECRUITER", true, actor.email());
    when(auth.author()).thenReturn(author);
    when(auth.staff()).thenReturn(author);
    var jobs = new Jobs(db, auth);
    Instant now = Instant.now();
    var details =
        new Jobs.Details(
            "Java backend engineer",
            "Engineering",
            "Build backend services",
            "Intermediate",
            2,
            5,
            "INTERMEDIATE",
            60,
            60,
            now,
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            List.of(new Jobs.Skill("java", 100, true, "Implement collection semantics")));
    UUID job = (UUID) tx.execute(s -> jobs.create(details)).get("id");
    assertEquals(1, jobs.list(0, "Java").size());
    var original =
        db.one(
            "select a.content from assessments a join assessment_versions v on v.assessment_id=a.id"
                + " join invitations i on i.assessment_version_id=v.id where i.id=?",
            inv);
    var authoring = new Authoring(db, auth, Clock.systemUTC(), "http://localhost");
    UUID assessment =
        (UUID)
            tx.execute(
                    s ->
                        authoring.create(
                            db.decode(original.get("content"), AssessmentContent.class)))
                .get("id");
    tx.executeWithoutResult(s -> jobs.link(job, new Jobs.Link(assessment)));
    UUID version = (UUID) tx.execute(s -> authoring.publish(assessment, 1)).get("versionId");
    var changed =
        new Jobs.Details(
            "Renamed role",
            details.department(),
            details.description(),
            details.experienceLevel(),
            2,
            6,
            details.difficulty(),
            60,
            60,
            details.availableFrom(),
            details.latestStart(),
            details.completionDeadline(),
            details.skills());
    tx.execute(s -> jobs.edit(job, 0, changed));
    assertThrows(Problem.class, () -> tx.execute(s -> jobs.edit(job, 0, changed)));
    assertEquals(
        "Java backend engineer",
        db.decode(
                db.one("select job_context from assessment_versions where id=?", version)
                    .get("job_context"),
                Map.class)
            .get("title"));
    assertThrows(
        Problem.class,
        () -> tx.executeWithoutResult(s -> jobs.link(job, new Jobs.Link(assessment))));
    when(auth.staff())
        .thenReturn(new Identity.Actor(user, UUID.randomUUID(), "RECRUITER", true, actor.email()));
    assertTrue(jobs.list(0, "").isEmpty());
    assertThrows(
        Problem.class,
        () ->
            Jobs.validate(
                new Jobs.Details(
                    details.title(),
                    details.department(),
                    details.description(),
                    details.experienceLevel(),
                    2,
                    5,
                    details.difficulty(),
                    60,
                    60,
                    details.availableFrom(),
                    details.latestStart(),
                    details.completionDeadline(),
                    List.of(new Jobs.Skill("java", 99, true, "Explain")))));
  }

  @Test
  void persistedBlueprintRequiresFeasibilityIndependentApprovalAndCurrentDependencies()
      throws Exception {
    var auth = mock(Identity.class);
    var author = new Identity.Actor(user, org, "RECRUITER", true, actor.email());
    when(auth.author()).thenReturn(author);
    when(auth.staff()).thenReturn(author);
    var provider = new ProviderAdapters(db, "fake", "", "", false, "local");
    var map = provider.generate("COMPETENCY", "java").draft();
    var draft =
        provider
            .generate(
                "QUESTION",
                db.encode(
                    new AiProvider.QuestionSource(
                        map, new AiProvider.Generation("SINGLE", "INTERMEDIATE", 1, "java"))))
            .draft();
    UUID mapId = UUID.randomUUID(), itemId = UUID.randomUUID();
    for (var pair : Map.of(mapId, map, itemId, draft).entrySet()) {
      UUID job = UUID.randomUUID();
      String purpose = pair.getKey().equals(mapId) ? "COMPETENCY" : "QUESTION";
      db.jdbc.update(
          "insert into"
              + " ai_jobs(id,organization_id,requester_id,purpose,idempotency_key,payload_hash,input,reservation,state)"
              + " values (?,?,?,?,?,'fixture','{}',0,'SUCCEEDED')",
          job,
          org,
          user,
          purpose,
          UUID.randomUUID());
      db.jdbc.update(
          "insert into"
              + " ai_artifacts(id,organization_id,job_id,author_id,purpose,content,provenance,state)"
              + " values (?,?,?,?,?,?::jsonb,'{}','APPROVED')",
          pair.getKey(),
          org,
          job,
          user,
          purpose,
          db.encode(pair.getValue()));
    }
    var generated =
        new GeneratedAssessments(
            db,
            auth,
            input -> {
              throw new IllegalStateException("No coding execution expected");
            },
            Clock.systemUTC());
    var api = new BlueprintApi(db, auth, generated);
    var pool = List.of(new BlueprintApi.Estimate(itemId, 0, 0, 3));
    var infeasible =
        new BlueprintApi.Request(
            mapId,
            0,
            List.of(new Blueprint.Requirement("java", "SINGLE", 2)),
            pool,
            10,
            2,
            7,
            "java");
    assertFalse(api.compute(infeasible, org).result().feasible());
    var constraints =
        new BlueprintApi.Request(
            mapId,
            0,
            List.of(new Blueprint.Requirement("java", "SINGLE", 1)),
            pool,
            10,
            2,
            7,
            "java");
    UUID blueprint = (UUID) tx.execute(s -> api.create(constraints)).get("id");
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s ->
                    api.review(
                        blueprint, 0, new BlueprintApi.Decision("APPROVED", "Self review"))));
    UUID reviewer = UUID.randomUUID();
    db.jdbc.update(
        "insert into users(id,email,password_hash,verified) values (?,?,'unused',true)",
        reviewer,
        reviewer + "@example.test");
    db.jdbc.update("insert into organization_members values (?,?,'EVALUATOR')", org, reviewer);
    when(auth.staff())
        .thenReturn(
            new Identity.Actor(reviewer, org, "EVALUATOR", true, reviewer + "@example.test"));
    tx.executeWithoutResult(
        s ->
            api.review(
                blueprint,
                0,
                new BlueprintApi.Decision("APPROVED", "Verified coverage and time estimates")));
    var settings =
        new GeneratedAssessments.Prepare(
            "Blueprint test",
            10,
            60,
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            List.of());
    var assessment = tx.execute(s -> api.prepare(blueprint, 1, settings));
    assertEquals(
        blueprint,
        db.one("select blueprint_id from assessments where id=?", assessment.get("id"))
            .get("blueprint_id"));
    assertThrows(Problem.class, () -> tx.execute(s -> api.prepare(blueprint, 0, settings)));
    db.jdbc.update("update ai_artifacts set revision=revision+1 where id=?", mapId);
    assertThrows(Problem.class, () -> tx.execute(s -> api.prepare(blueprint, 1, settings)));
    var authoring = new Authoring(db, auth, Clock.systemUTC(), "http://localhost");
    assertThrows(
        Problem.class, () -> tx.execute(s -> authoring.publish((UUID) assessment.get("id"), 0)));
    assertThrows(Problem.class, () -> api.compute(constraints, UUID.randomUUID()));
  }

  @Test
  void consentWithdrawalInvalidatesOldFeedbackEvenAfterRegrant() {
    UUID tab = UUID.randomUUID(), job = UUID.randomUUID();
    UUID attempt = (UUID) service.start(inv, tab, actor).get("id");
    service.submit(attempt, tab, user);
    db.jdbc.update(
        "update candidate_acknowledgments set optional_ai=true where invitation_id=?", inv);
    db.jdbc.update(
        "insert into"
            + " ai_jobs(id,organization_id,requester_id,purpose,idempotency_key,payload_hash,input,reservation,state)"
            + " values (?,?,?,'FEEDBACK',?,'test','{}',0,'SUCCEEDED')",
        job,
        org,
        user,
        UUID.randomUUID());
    db.jdbc.update(
        "insert into"
            + " ai_job_evaluations(job_id,organization_id,attempt_id,final_revision,evaluation_revision,consent_revision)"
            + " values (?,?,?,0,0,0)",
        job,
        org,
        attempt);
    var evidence = new FeedbackEvidence(db);
    assertTrue(evidence.current(job));
    var identity = mock(Identity.class);
    when(identity.actor()).thenReturn(actor);
    var consents = new PurposeConsents(db, identity);
    tx.execute(
        s ->
            consents.change(
                0,
                new PurposeConsents.Change(
                    inv, "POST_SUBMISSION_FEEDBACK", "disclosure-v1", false)));
    assertFalse(evidence.current(job));
    assertThrows(
        Problem.class,
        () ->
            tx.execute(
                s ->
                    consents.change(
                        0,
                        new PurposeConsents.Change(
                            inv, "POST_SUBMISSION_FEEDBACK", "disclosure-v1", true))));
    tx.execute(
        s ->
            consents.change(
                1,
                new PurposeConsents.Change(
                    inv, "POST_SUBMISSION_FEEDBACK", "disclosure-v1", true)));
    assertFalse(evidence.current(job));
    assertEquals(
        "SUBMITTED",
        db.one("select status from assessment_attempts where id=?", attempt).get("status"));
    assertEquals(2L, consents.list().getFirst().get("consent_revision"));
    when(identity.actor())
        .thenReturn(
            new Identity.Actor(UUID.randomUUID(), org, "CANDIDATE", true, "other@example.test"));
    assertTrue(consents.list().isEmpty());
    assertThrows(
        Problem.class,
        () ->
            tx.execute(
                s ->
                    consents.change(
                        2,
                        new PurposeConsents.Change(
                            inv, "POST_SUBMISSION_FEEDBACK", "disclosure-v1", false))));
  }

  @Test
  void humanReviewAssignmentResolutionAndTenantIsolation() {
    UUID tab = UUID.randomUUID();
    var attempt = service.start(inv, tab, actor);
    UUID attemptId = (UUID) attempt.get("id");
    var identity = mock(Identity.class);
    when(identity.actor()).thenReturn(actor);
    var candidate = new CandidateApi(db, identity, service, Clock.systemUTC());
    var request =
        tx.execute(
            s -> candidate.appeal(attemptId, new CandidateApi.Appeal("Please review my result")));
    assertEquals(
        request,
        tx.execute(
            s -> candidate.appeal(attemptId, new CandidateApi.Appeal("Please review my result"))));
    assertThrows(
        Problem.class,
        () ->
            tx.execute(
                s ->
                    candidate.appeal(
                        attemptId, new CandidateApi.Appeal("A different concurrent request"))));
    var reviews = new HumanReviews(db, identity);
    assertEquals("OPEN", reviews.mine(0).getFirst().get("state"));
    db.jdbc.update(
        "update organization_members set role='EVALUATOR' where organization_id=? and user_id=?",
        org,
        user);
    when(identity.staff())
        .thenReturn(new Identity.Actor(user, org, "EVALUATOR", true, actor.email()));
    assertThrows(
        Problem.class,
        () ->
            tx.execute(
                s ->
                    reviews.decide(
                        request.get("id"), 0, new HumanReviews.Decision("RESOLVE", "Reviewed"))));
    tx.execute(
        s -> reviews.decide(request.get("id"), 0, new HumanReviews.Decision("ASSIGN_TO_ME", null)));
    assertThrows(
        Problem.class,
        () ->
            tx.execute(
                s ->
                    reviews.decide(
                        request.get("id"),
                        0,
                        new HumanReviews.Decision("RESOLVE", "Stale response"))));
    tx.execute(
        s ->
            reviews.decide(
                request.get("id"),
                1,
                new HumanReviews.Decision(
                    "RESOLVE",
                    "An evaluator reviewed your concern; no score change was warranted.")));
    assertEquals("RESOLVED", reviews.mine(0).getFirst().get("state"));
    assertFalse(reviews.mine(0).getFirst().containsKey("assigned_to"));
    assertEquals(
        0,
        Attempts.number(
            db.one("select revision from assessment_attempts where id=?", attemptId), "revision"));
    when(identity.staff())
        .thenReturn(new Identity.Actor(user, UUID.randomUUID(), "EVALUATOR", true, actor.email()));
    assertTrue(reviews.queue(0).isEmpty());
    assertThrows(
        Problem.class,
        () ->
            tx.execute(
                s ->
                    reviews.decide(
                        request.get("id"), 2, new HumanReviews.Decision("ASSIGN_TO_ME", null))));
  }

  @Test
  void generatedQuestionsPrepareOnlyApprovedVersionsAndProtectCodingEvidence() throws Exception {
    var identity = mock(Identity.class);
    when(identity.author()).thenReturn(actor);
    var service =
        new GeneratedAssessments(
            db,
            identity,
            input -> {
              throw new java.io.IOException("Unavailable");
            },
            Clock.systemUTC());
    var provider = new ProviderAdapters(db, "fake", "", "", false, "local");
    var map = provider.generate("COMPETENCY", "java coding system-design").draft();
    for (String kind : List.of("SINGLE", "MULTIPLE", "PROBLEM_SOLVING", "SYSTEM_DESIGN")) {
      var draft =
          provider
              .generate(
                  "QUESTION",
                  db.encode(
                      new AiProvider.QuestionSource(
                          map, new AiProvider.Generation(kind, "INTERMEDIATE", 1, "java"))))
              .draft();
      AiValidation.validate("QUESTION", "", draft);
      UUID job = UUID.randomUUID(), artifact = UUID.randomUUID();
      db.jdbc.update(
          "insert into"
              + " ai_jobs(id,organization_id,requester_id,purpose,idempotency_key,payload_hash,input,reservation)"
              + " values (?,?,?,'QUESTION',?,'test','{}',0)",
          job,
          org,
          user,
          UUID.randomUUID());
      db.jdbc.update(
          "insert into ai_artifacts(id,organization_id,job_id,author_id,purpose,content,provenance)"
              + " values (?,?,?,?,'QUESTION',?::jsonb,'{}')",
          artifact,
          org,
          job,
          user,
          db.encode(draft));
      var request =
          new GeneratedAssessments.Prepare(
              "Generated test",
              60,
              60,
              Instant.now().plusSeconds(3600),
              Instant.now().plusSeconds(7200),
              List.of(new GeneratedAssessments.Item(artifact, 0, 0)));
      assertThrows(Problem.class, () -> tx.execute(s -> service.prepare(request)));
      db.jdbc.update("update ai_artifacts set state='APPROVED' where id=?", artifact);
      if (draft.questions().getFirst().coding() != null) {
        assertThrows(Problem.class, () -> tx.execute(s -> service.prepare(request)));
        // A fixture attestation tests the gate, not live sandbox execution.
        db.jdbc.update(
            "insert into"
                + " generated_question_validations(artifact_id,state,content_hash,requested_by)"
                + " values (?,'PASSED',?,?)",
            artifact,
            Identity.hash(db.encode(draft)),
            user);
      }
      var prepared = tx.execute(s -> service.prepare(request));
      var content =
          db.decode(
              db.one("select content from assessments where id=?", prepared.get("id"))
                  .get("content"),
              AssessmentContent.class);
      assertEquals(1, content.questions().size());
      String safe = db.encode(content.candidateView());
      assertFalse(safe.contains("referenceSolution"));
      assertFalse(safe.contains("hiddenTests"));
      assertFalse(safe.contains("\"correct\":"));
      db.jdbc.update("update ai_artifacts set revision=1 where id=?", artifact);
      assertThrows(Problem.class, () -> tx.execute(s -> service.prepare(request)));
      when(identity.author())
          .thenReturn(
              new Identity.Actor(user, UUID.randomUUID(), "RECRUITER", true, "test@example.test"));
      assertThrows(Problem.class, () -> tx.execute(s -> service.prepare(request)));
      when(identity.author()).thenReturn(actor);
    }
  }

  @Autowired Attempts service;
  @Autowired CodingService coding;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;
  UUID org, user, inv, q;
  Identity.Actor actor;

  @BeforeEach
  void fixture(TestInfo info) {
    org = UUID.randomUUID();
    user = UUID.randomUUID();
    inv = UUID.randomUUID();
    q = UUID.randomUUID();
    UUID assessment = UUID.randomUUID(), version = UUID.randomUUID();
    var now = Instant.now();
    db.jdbc.update("insert into organizations values (?, 'Test',now())", org);
    db.jdbc.update(
        "insert into users(id,email,password_hash,verified) values (?,?, 'not-used',true)",
        user,
        user + "@example.test");
    db.jdbc.update("insert into organization_members values (?,?,'CANDIDATE')", org, user);
    var content =
        new AssessmentContent(
            "Test",
            "Instructions",
            60,
            "CONTROLLED",
            2,
            300,
            600,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            false,
            false,
            true,
            60,
            List.of(
                info.getTestMethod().orElseThrow().getName().startsWith("coding")
                    ? new AssessmentContent.Question(
                        q,
                        "CODING",
                        "Echo",
                        "Echo stdin",
                        "java",
                        List.of(),
                        List.of(),
                        10,
                        0,
                        false,
                        "java",
                        "",
                        List.of(new AssessmentContent.Sample("ok", "ok")),
                        new AssessmentContent.CodingSpec(
                            List.of(
                                new AssessmentContent.TestCase(
                                    "hidden-input", "hidden-output-canary", 1)),
                            1000,
                            256))
                    : new AssessmentContent.Question(
                        q,
                        "SINGLE",
                        "Question",
                        "Description",
                        "java",
                        List.of("A", "B"),
                        List.of(1),
                        10,
                        0,
                        false,
                        null,
                        null,
                        List.of())));
    db.jdbc.update(
        "insert into assessments(id,organization_id,title,author_id,content) values"
            + " (?,?,'Test',?,?::jsonb)",
        assessment,
        org,
        user,
        db.encode(content));
    db.jdbc.update(
        "insert into"
            + " assessment_versions(id,organization_id,assessment_id,version,content,published_by)"
            + " values (?,?,?,1,?::jsonb,?)",
        version,
        org,
        assessment,
        db.encode(content),
        user);
    db.jdbc.update(
        "insert into"
            + " invitations(id,organization_id,assessment_version_id,email,token_hash,expires_at,claimed_by)"
            + " values (?,?,?,?,?,?,?)",
        inv,
        org,
        version,
        user + "@example.test",
        UUID.randomUUID().toString(),
        Timestamp.from(now.plusSeconds(3600)),
        user);
    db.jdbc.update(
        "insert into"
            + " candidate_acknowledgments(invitation_id,candidate_id,disclosure_version,optional_ai,system_checks,acknowledged_at,assessment_version_id,policy_version)"
            + " values"
            + " (?,?,'disclosure-v1',false,'{\"instructions\":true,\"browser\":true,\"cookies\":true,\"network\":true,\"secureContext\":true,\"viewport\":true}',now(),?,'candidate-policy-v2')",
        inv,
        user,
        version);
    actor = new Identity.Actor(user, org, "CANDIDATE", true, user + "@example.test");
  }

  @Test
  void durableSaveRetryStaleWriteAndDuplicateSubmit() {
    UUID tab = UUID.randomUUID();
    UUID id = (UUID) service.start(inv, tab, actor).get("id");
    UUID key = UUID.randomUUID();
    var request = new Attempts.Save(q, 0, new Scoring.Answer(List.of(1), null, null, true));
    assertEquals(1L, service.save(id, tab, key, user, request).get("revision"));
    assertEquals(1L, service.save(id, tab, key, user, request).get("revision"));
    assertThrows(Problem.class, () -> service.save(id, tab, UUID.randomUUID(), user, request));
    assertEquals(1, ((List<?>) service.resume(id, tab, user).get("answers")).size());
    service.submit(id, tab, user);
    service.submit(id, tab, user);
    assertEquals(
        1,
        db.jdbc.queryForObject(
            "select count(*) from evaluation_results where attempt_id=?", Integer.class, id));
    assertThrows(
        Problem.class,
        () ->
            service.save(
                id, tab, UUID.randomUUID(), user, new Attempts.Save(q, 1, request.answer())));
  }

  @Test
  void unauthorizedOwnerAndConcurrentTabRejected() {
    UUID tab = UUID.randomUUID();
    UUID id = (UUID) service.start(inv, tab, actor).get("id");
    assertThrows(Problem.class, () -> service.resume(id, tab, UUID.randomUUID()));
    assertThrows(Problem.class, () -> service.resume(id, UUID.randomUUID(), user));
  }

  @Test
  void concurrentStartsCreateExactlyOneAttempt() throws Exception {
    UUID tab = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one = pool.submit(() -> service.start(inv, tab, actor));
      var two = pool.submit(() -> service.start(inv, tab, actor));
      assertEquals(one.get().get("id"), two.get().get("id"));
    }
    assertEquals(
        1,
        db.jdbc.queryForObject(
            "select count(*) from assessment_attempts where invitation_id=?", Integer.class, inv));
  }

  @Test
  void expiredInvitationCannotStart() {
    db.jdbc.update("update invitations set expires_at=now()-interval '1 second' where id=?", inv);
    assertThrows(Problem.class, () -> service.start(inv, UUID.randomUUID(), actor));
  }

  @Test
  void serverFinalizesWithoutReconnect() {
    UUID id = (UUID) service.start(inv, UUID.randomUUID(), actor).get("id");
    db.jdbc.update(
        "update assessment_attempts set deadline=now()-interval '1 second' where id=?", id);
    service.expire(id, user);
    assertEquals(
        "EXPIRED", db.one("select status from assessment_attempts where id=?", id).get("status"));
  }

  @Test
  void publishedSnapshotRejectsMutation() {
    UUID version =
        (UUID)
            db.one("select assessment_version_id from invitations where id=?", inv)
                .get("assessment_version_id");
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> db.jdbc.update("update assessment_versions set content='{}' where id=?", version));
  }

  @Test
  void staleReadinessCannotStartButCannotResetAnActiveAttempt() {
    db.jdbc.update(
        "update candidate_acknowledgments set acknowledged_at=now()-interval '11 minutes' where"
            + " invitation_id=?",
        inv);
    assertThrows(Problem.class, () -> service.start(inv, UUID.randomUUID(), actor));
    db.jdbc.update(
        "update candidate_acknowledgments set acknowledged_at=now() where invitation_id=?", inv);
    UUID tab = UUID.randomUUID();
    var started = service.start(inv, tab, actor);
    db.jdbc.update(
        "update candidate_acknowledgments set acknowledged_at=now()-interval '11 minutes' where"
            + " invitation_id=?",
        inv);
    assertEquals(started.get("id"), service.start(inv, tab, actor).get("id"));
  }

  @Test
  void skippedAndClearedAnswersPersistWithoutAFalseAnsweredState() {
    UUID tab = UUID.randomUUID();
    UUID id = (UUID) service.start(inv, tab, actor).get("id");
    var skip = new Attempts.Save(q, 0, new Scoring.Answer(List.of(), null, null, false, true), q);
    UUID key = UUID.randomUUID();
    service.save(id, tab, key, user, skip);
    service.save(id, tab, key, user, skip);
    var restored = service.resume(id, tab, user);
    assertEquals(q, restored.get("currentQuestion"));
    assertTrue(((List<?>) restored.get("visited")).contains(q));
    var saved =
        db.decode(
            db.one("select answer from candidate_answers where attempt_id=?", id).get("answer"),
            Scoring.Answer.class);
    assertTrue(saved.skipped());
    service.save(
        id,
        tab,
        UUID.randomUUID(),
        user,
        new Attempts.Save(q, 1, new Scoring.Answer(List.of(1), null, null, false, true)));
    assertThrows(Problem.class, () -> service.save(id, tab, UUID.randomUUID(), user, skip));
    service.save(
        id,
        tab,
        UUID.randomUUID(),
        user,
        new Attempts.Save(q, 2, new Scoring.Answer(List.of(), null, null, false, false)));
    assertEquals(3L, service.resume(id, tab, user).get("revision"));
  }

  @Test
  void integrityIsDeduplicatedOwnedAndDoesNotChangeScoreOrRevision() {
    UUID tab = UUID.randomUUID();
    UUID id = (UUID) service.start(inv, tab, actor).get("id");
    var auth = mock(Identity.class);
    when(auth.actor()).thenReturn(actor);
    var api = new IntegrityApi(db, auth, Clock.systemUTC());
    var event = new IntegrityApi.Event(UUID.randomUUID(), "BLUR", Instant.now());
    tx.executeWithoutResult(s -> api.ingest(id, new IntegrityApi.Batch(List.of(event))));
    tx.executeWithoutResult(s -> api.ingest(id, new IntegrityApi.Batch(List.of(event))));
    assertEquals(
        1,
        db.jdbc.queryForObject(
            "select count(*) from integrity_events where attempt_id=?", Integer.class, id));
    assertEquals(0L, service.resume(id, tab, user).get("revision"));
    assertThrows(
        Problem.class,
        () ->
            api.ingest(
                id,
                new IntegrityApi.Batch(
                    List.of(
                        new IntegrityApi.Event(
                            UUID.randomUUID(), "CLIPBOARD_CONTENT", Instant.now())))));
    when(auth.actor())
        .thenReturn(
            new Identity.Actor(UUID.randomUUID(), org, "CANDIDATE", true, "other@example.test"));
    assertThrows(Problem.class, () -> api.ingest(id, new IntegrityApi.Batch(List.of(event))));
  }

  @Test
  @SuppressWarnings("unchecked")
  void onlyCurrentAuthorizedMediaExceptionsBypassDeviceChecks() {
    var raw =
        db.decode(
            db.one(
                    "select v.content from assessment_versions v join invitations i on"
                        + " i.assessment_version_id=v.id where i.id=?",
                    inv)
                .get("content"),
            Map.class);
    raw.put("cameraRequired", true);
    var content = db.decode(db.encode(raw), AssessmentContent.class);
    Map<String, Boolean> checks = new HashMap<>();
    for (var key :
        List.of("instructions", "browser", "cookies", "network", "secureContext", "viewport"))
      checks.put(key, true);
    assertThrows(Problem.class, () -> ReadinessPolicy.validate(db, inv, content, checks));
    var auth = mock(Identity.class);
    when(auth.staff())
        .thenReturn(new Identity.Actor(user, org, "ADMIN", true, user + "@example.test"));
    var api = new IntegrityApi(db, auth, Clock.systemUTC());
    assertThrows(
        Problem.class,
        () ->
            api.exception(
                inv,
                new IntegrityApi.ExceptionRequest(
                    List.of("network"), "Cannot waive security prerequisites")));
    tx.executeWithoutResult(
        s ->
            api.exception(
                inv,
                new IntegrityApi.ExceptionRequest(
                    List.of("camera"), "Authorized accessible alternative")));
    ReadinessPolicy.validate(db, inv, content, checks);
    db.jdbc.update(
        "update readiness_exceptions set expires_at=now()-interval '1 second' where"
            + " invitation_id=?",
        inv);
    assertThrows(Problem.class, () -> ReadinessPolicy.validate(db, inv, content, checks));
  }

  @Test
  void codingFinalRevisionGradesOnlyFromSuccessfulEvidence() {
    UUID tab = UUID.randomUUID();
    UUID id = (UUID) service.start(inv, tab, actor).get("id");
    service.save(
        id,
        tab,
        UUID.randomUUID(),
        user,
        new Attempts.Save(
            q,
            0,
            new Scoring.Answer(List.of(), "java", "synthetic fixture; never executed", false)));
    service.submit(id, tab, user);
    var job = db.one("select * from code_execution_jobs where attempt_id=?", id);
    assertEquals(1L, Attempts.number(job, "answer_revision"));
    assertThrows(Problem.class, () -> coding.candidateResult((UUID) job.get("id"), user));
    CodeRunner fake =
        input ->
            new CodeRunner.Result(List.of(new CodeRunner.CaseResult("OK", "hidden-output-canary")));
    new CodingWorker(db, coding, fake, tx).tick();
    var evaluation = db.one("select * from evaluation_results where attempt_id=?", id);
    assertEquals("EVALUATED", evaluation.get("status"));
    assertEquals(
        100.0,
        ((Number) db.decode(evaluation.get("baseline"), Map.class).get("percentage"))
            .doubleValue());
    assertFalse(evaluation.get("baseline").toString().contains("hidden-output-canary"));
    var evidence = new FeedbackEvidence(db);
    assertThrows(Problem.class, () -> evidence.source(id, org));
    db.jdbc.update(
        "update candidate_acknowledgments set optional_ai=true where invitation_id=?", inv);
    var minimized = db.encode(evidence.source(id, org));
    assertFalse(minimized.contains("hidden-output-canary"));
    assertFalse(minimized.contains(user + "@example.test"));
    assertThrows(Problem.class, () -> evidence.source(id, UUID.randomUUID()));
    service.submit(id, tab, user);
    assertEquals(
        1,
        db.jdbc.queryForObject(
            "select count(*) from code_execution_jobs where attempt_id=?", Integer.class, id));
  }

  @Test
  void codingInfrastructureFailureRetainsPendingEvaluation() {
    UUID tab = UUID.randomUUID();
    UUID id = (UUID) service.start(inv, tab, actor).get("id");
    service.submit(id, tab, user);
    new CodingWorker(
            db,
            coding,
            input -> {
              throw new java.io.IOException("Unavailable");
            },
            tx)
        .tick();
    assertEquals(
        "EXECUTION_PENDING",
        db.one("select status from evaluation_results where attempt_id=?", id).get("status"));
    assertEquals(
        "RETRY_WAIT",
        db.one("select state from code_execution_jobs where attempt_id=?", id).get("state"));
  }

  @Test
  void reviewedScoresPersistAndReleasedReportOmitsKeys() {
    UUID tab = UUID.randomUUID();
    UUID id = (UUID) service.start(inv, tab, actor).get("id");
    service.submit(id, tab, user);
    var auth = mock(Identity.class);
    var evaluator = new Identity.Actor(user, org, "EVALUATOR", true, user + "@example.test");
    when(auth.staff()).thenReturn(evaluator);
    when(auth.actor()).thenReturn(actor);
    var api = new ReportsApi(db, auth, null);
    tx.executeWithoutResult(
        s ->
            api.review(
                id,
                0,
                new ReportsApi.Review(
                    "Rubric correction",
                    "UNDECIDED",
                    List.of(new ReportsApi.Adjustment(q, 7, "Verified criterion")))));
    tx.executeWithoutResult(
        s -> api.review(id, 1, new ReportsApi.Review("Approved feedback", "INTERVIEW", List.of())));
    UUID key = UUID.randomUUID();
    var release = tx.execute(s -> api.release(id, 2, key));
    assertEquals(release, tx.execute(s -> api.release(id, 2, key)));
    var result = api.results().getFirst();
    assertEquals(70.0, ((Number) result.get("percentage")).doubleValue());
    assertFalse(result.containsKey("questions"));
    assertFalse(result.containsKey("recommendation"));
    assertThrows(
        Problem.class,
        () ->
            tx.executeWithoutResult(
                s -> api.review(id, 1, new ReportsApi.Review("Stale", "UNDECIDED", List.of()))));
  }
}
