package com.assessment.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ExecutionEvidenceTest {
  @Test
  void weightedEvidenceUsesOnlyMatchingSuccessfulExecutions() {
    var tests =
        List.of(
            new AssessmentContent.TestCase("a", "yes", 3),
            new AssessmentContent.TestCase("b", "no", 1));
    assertEquals(
        .75,
        CodeRunner.fraction(
            new CodeRunner.Result(
                List.of(
                    new CodeRunner.CaseResult("OK", "yes\r\n"),
                    new CodeRunner.CaseResult("TIME_LIMIT", "no"))),
            tests));
  }

  @Test
  void infrastructureOrMissingEvidenceCannotBecomeZero() {
    var tests = List.of(new AssessmentContent.TestCase("", "", 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> CodeRunner.fraction(new CodeRunner.Result(List.of()), tests));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CodeRunner.fraction(
                new CodeRunner.Result(
                    List.of(new CodeRunner.CaseResult("INFRASTRUCTURE_FAILURE", ""))),
                tests));
  }

  @Test
  void feedbackRequiresExactOwnedLineEvidence() {
    UUID question = UUID.randomUUID();
    var source =
        new FeedbackEvidence.Source(
            4,
            List.of(new FeedbackEvidence.Code(question, "java", "line one\nline two")),
            Map.of());
    var valid =
        new AiProvider.Finding(
            question, 2, 2, "line two", "READABILITY", "Consider a descriptive name.");
    FeedbackEvidence.validate(source, draft(valid));
    assertThrows(
        Problem.class,
        () ->
            FeedbackEvidence.validate(
                source,
                draft(
                    new AiProvider.Finding(
                        UUID.randomUUID(), 2, 2, "line two", "READABILITY", "Other candidate"))));
    assertThrows(
        Problem.class,
        () ->
            FeedbackEvidence.validate(
                source,
                draft(
                    new AiProvider.Finding(
                        question, 2, 2, "invented", "READABILITY", "Forged quotation"))));
    assertThrows(
        Problem.class,
        () ->
            FeedbackEvidence.validate(
                source,
                draft(
                    new AiProvider.Finding(
                        question, 2, 2, "line two", "TRUSTWORTHINESS", "Disallowed inference"))));
  }

  private AiProvider.Draft draft(AiProvider.Finding finding) {
    return new AiProvider.Draft(
        "Draft", List.of(), List.of(), List.of("Human review required"), List.of(finding));
  }
}
