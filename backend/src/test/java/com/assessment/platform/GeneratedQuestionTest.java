package com.assessment.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class GeneratedQuestionTest {
  final Store db = new Store(null, new ObjectMapper());
  final ProviderAdapters provider = new ProviderAdapters(db, "fake", "", "", false, "local");

  @Test
  void allRequestedFormatsAndLanguagesHaveValidSchemas() throws Exception {
    var map = provider.generate("COMPETENCY", "java coding system-design").draft();
    for (String language : List.of("java", "python", "javascript"))
      for (String kind : List.of("SINGLE", "MULTIPLE", "PROBLEM_SOLVING", "SYSTEM_DESIGN")) {
        var settings = new AiProvider.Generation(kind, "INTERMEDIATE", 1, language);
        settings.validate();
        var output =
            provider.generate("QUESTION", db.encode(new AiProvider.QuestionSource(map, settings)));
        assertTrue(output.fake());
        AiValidation.validate("QUESTION", "", output.draft());
        var q = output.draft().questions().getFirst();
        assertEquals(kind.equals("SINGLE") || kind.equals("MULTIPLE") ? kind : "CODING", q.type());
        if (q.coding() != null) assertEquals(language, q.coding().language());
      }
  }

  @Test
  void invalidConstraintsFailClosed() {
    assertThrows(
        Problem.class, () -> new AiProvider.Generation(null, "INTERMEDIATE", 1, "java").validate());
    assertThrows(
        Problem.class,
        () -> new AiProvider.Generation("SYSTEM_DESIGN", "ADVANCED", 2, "java").validate());
    assertThrows(
        Problem.class, () -> new AiProvider.Generation("SINGLE", "ADVANCED", 4, "java").validate());
    assertThrows(
        Problem.class,
        () -> new AiProvider.Generation("SINGLE", "ADVANCED", 1, "shell").validate());
  }

  @Test
  void codingWithoutReferenceOrTestsCannotPassValidation() {
    var q =
        new AiProvider.Question(
            "CODING", "Problem", "coding", List.of(), List.of(), "Rationale", "Description", null);
    assertThrows(
        Problem.class,
        () ->
            AiValidation.validate(
                "QUESTION", "", new AiProvider.Draft("Draft", List.of(), List.of(q), List.of())));
  }
}
