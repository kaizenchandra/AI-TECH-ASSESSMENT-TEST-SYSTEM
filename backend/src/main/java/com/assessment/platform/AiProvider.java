package com.assessment.platform;

import java.util.*;

public interface AiProvider {
  record Skill(String id, int weight, String evidence, String observation) {}

  record Question(
      String type,
      String title,
      String skill,
      List<String> options,
      List<Integer> correct,
      String rationale,
      String description,
      Coding coding) {
    public Question(
        String type,
        String title,
        String skill,
        List<String> options,
        List<Integer> correct,
        String rationale) {
      this(type, title, skill, options, correct, rationale, title, null);
    }
  }

  record Coding(
      String language,
      String starterCode,
      String referenceSolution,
      List<AssessmentContent.Sample> samples,
      List<AssessmentContent.TestCase> hiddenTests,
      int timeoutMs,
      int memoryMb) {}

  record Generation(String kind, String difficulty, int count, String language) {
    public void validate() {
      if (kind == null
          || difficulty == null
          || language == null
          || !Set.of("SINGLE", "MULTIPLE", "PROBLEM_SOLVING", "SYSTEM_DESIGN").contains(kind)
          || !Set.of("FOUNDATION", "INTERMEDIATE", "ADVANCED").contains(difficulty)
          || count < 1
          || count > (kind.equals("SINGLE") || kind.equals("MULTIPLE") ? 3 : 1)
          || !Set.of("java", "python", "javascript").contains(language))
        throw new Problem(422, "Invalid generation constraints (choice batch 1–3, coding batch 1)");
    }
  }

  record QuestionSource(Draft competencyMap, Generation generation) {}

  record Finding(
      UUID questionId,
      int firstLine,
      int lastLine,
      String excerpt,
      String criterion,
      String observation) {}

  record Draft(
      String summary,
      List<Skill> skills,
      List<Question> questions,
      List<String> limitations,
      List<Finding> findings) {
    public Draft(
        String summary, List<Skill> skills, List<Question> questions, List<String> limitations) {
      this(summary, skills, questions, limitations, List.of());
    }
  }

  record Output(
      Draft draft, String provider, String model, String requestId, int tokens, boolean fake) {}

  Output generate(String purpose, String source) throws Exception;
}
