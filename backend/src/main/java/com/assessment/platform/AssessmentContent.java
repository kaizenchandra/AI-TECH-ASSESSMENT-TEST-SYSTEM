package com.assessment.platform;

import java.time.Instant;
import java.util.*;

public record AssessmentContent(
    String title,
    String instructions,
    int durationMinutes,
    String timerPolicy,
    int maxPauses,
    int maxPauseSeconds,
    int totalPauseSeconds,
    Instant availableFrom,
    Instant latestStart,
    Instant completionDeadline,
    boolean cameraRequired,
    boolean microphoneRequired,
    boolean backNavigation,
    double passingPercent,
    List<Question> questions) {
  public record Question(
      UUID id,
      String type,
      String title,
      String description,
      String skill,
      List<String> options,
      List<Integer> correct,
      double points,
      double penalty,
      boolean partialCredit,
      String language,
      String starterCode,
      List<Sample> samples,
      CodingSpec coding) {
    public Question(
        UUID id,
        String type,
        String title,
        String description,
        String skill,
        List<String> options,
        List<Integer> correct,
        double points,
        double penalty,
        boolean partialCredit,
        String language,
        String starterCode,
        List<Sample> samples) {
      this(
          id,
          type,
          title,
          description,
          skill,
          options,
          correct,
          points,
          penalty,
          partialCredit,
          language,
          starterCode,
          samples,
          null);
    }
  }

  public record TestCase(String input, String output, int weight) {}

  public record CodingSpec(List<TestCase> hiddenTests, int timeoutMs, int memoryMb) {
    public void validate() {
      if (hiddenTests == null
          || hiddenTests.isEmpty()
          || hiddenTests.size() > 30
          || timeoutMs < 100
          || timeoutMs > 10000
          || memoryMb < 64
          || memoryMb > 512)
        throw new Problem(422, "Coding tests and bounded execution limits are required");
      for (var test : hiddenTests) {
        if (test == null
            || test.input() == null
            || test.output() == null
            || test.input().length() > 16384
            || test.output().length() > 16384
            || test.weight() < 1
            || test.weight() > 100) throw new Problem(422, "Invalid hidden test");
      }
    }
  }

  public record Sample(String input, String output) {}

  public void validate() {
    if (title == null
        || title.isBlank()
        || title.length() > 200
        || instructions == null
        || instructions.length() > 20000
        || durationMinutes < 1
        || durationMinutes > 480
        || !Set.of("STRICT", "CONTROLLED").contains(timerPolicy)
        || maxPauses < 0
        || maxPauses > 10
        || maxPauseSeconds < 0
        || maxPauseSeconds > 1800
        || totalPauseSeconds < 0
        || totalPauseSeconds > 7200
        || passingPercent < 0
        || passingPercent > 100
        || availableFrom == null
        || latestStart == null
        || completionDeadline == null
        || latestStart.isBefore(availableFrom)
        || completionDeadline.isBefore(latestStart)
        || questions == null
        || questions.isEmpty()
        || questions.size() > 200) throw new Problem(422, "Invalid assessment configuration");
    Set<UUID> ids = new HashSet<>();
    for (var q : questions) {
      if (q.id == null
          || !ids.add(q.id)
          || q.title == null
          || q.title.isBlank()
          || q.description == null
          || q.description.length() > 30000
          || q.skill == null
          || q.skill.isBlank()
          || !Double.isFinite(q.points)
          || q.points <= 0
          || q.points > 1000
          || !Double.isFinite(q.penalty)
          || q.penalty < 0
          || q.penalty > q.points
          || !Set.of("SINGLE", "MULTIPLE", "CODING").contains(q.type))
        throw new Problem(422, "Invalid question");
      if (q.type.equals("CODING")) {
        if (q.language == null
            || !Set.of("java", "javascript", "python").contains(q.language)
            || q.starterCode == null) throw new Problem(422, "Invalid coding question");
        if (q.coding != null) q.coding.validate();
        if (q.coding != null && q.language.equals("java") && q.coding.memoryMb() < 256)
          throw new Problem(422, "The Java runtime requires a memory limit of at least 256 MiB");
        if (q.samples == null
            || q.samples.isEmpty()
            || q.samples.size() > 10
            || q.samples.stream()
                .anyMatch(
                    s ->
                        s == null
                            || s.input() == null
                            || s.output() == null
                            || s.input().length() > 16384
                            || s.output().length() > 16384))
          throw new Problem(422, "Bounded public sample tests are required");
      } else if (q.options == null
          || q.options.size() < 2
          || q.options.size() > 12
          || q.options.stream().anyMatch(o -> o == null || o.isBlank() || o.length() > 4000)
          || new HashSet<>(q.options).size() != q.options.size()
          || q.correct == null
          || q.correct.isEmpty()
          || new HashSet<>(q.correct).size() != q.correct.size()
          || q.correct.stream().anyMatch(i -> i == null || i < 0 || i >= q.options.size())
          || q.type.equals("SINGLE") && q.correct.size() != 1)
        throw new Problem(422, "Invalid options or answer key");
    }
  }

  public Question question(UUID id) {
    return questions.stream()
        .filter(q -> q.id.equals(id))
        .findFirst()
        .orElseThrow(() -> new Problem(404, "Question unavailable"));
  }

  public Map<String, Object> candidateOverview() {
    Map<String, Object> overview = new LinkedHashMap<>(candidateView());
    overview.put("questions", List.of());
    overview.put("questionCount", questions.size());
    overview.put(
        "categories",
        questions.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    q -> Objects.requireNonNull(q).type(),
                    java.util.TreeMap::new,
                    java.util.stream.Collectors.counting())));
    overview.put(
        "skills",
        questions.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    q -> Objects.requireNonNull(q).skill(),
                    java.util.TreeMap::new,
                    java.util.stream.Collectors.counting())));
    overview.put("passingPercent", passingPercent);
    overview.put("latestStart", latestStart);
    overview.put("completionDeadline", completionDeadline);
    overview.put("maxPauses", maxPauses);
    overview.put("maxPauseSeconds", maxPauseSeconds);
    overview.put("totalPauseSeconds", totalPauseSeconds);
    overview.put(
        "languages",
        questions.stream()
            .filter(q -> q.type().equals("CODING"))
            .map(q -> Objects.requireNonNull(q).language())
            .distinct()
            .toList());
    overview.put(
        "marking",
        questions.stream()
            .map(
                q ->
                    Map.of(
                        "category",
                        q.type(),
                        "points",
                        q.points(),
                        "penalty",
                        q.penalty(),
                        "partialCredit",
                        q.partialCredit()))
            .distinct()
            .toList());
    overview.put("policyVersion", "candidate-policy-v2");
    overview.put("attemptLimit", 1);
    return overview;
  }

  public Map<String, Object> candidateView() {
    return Map.of(
        "title",
        title,
        "instructions",
        instructions,
        "durationMinutes",
        durationMinutes,
        "timerPolicy",
        timerPolicy,
        "backNavigation",
        backNavigation,
        "cameraRequired",
        cameraRequired,
        "microphoneRequired",
        microphoneRequired,
        "questions",
        questions.stream()
            .map(
                q -> {
                  Map<String, Object> view = new LinkedHashMap<>();
                  view.put("id", q.id);
                  view.put("type", q.type);
                  view.put("title", q.title);
                  view.put("description", q.description);
                  view.put("skill", q.skill);
                  view.put("options", q.options == null ? List.of() : q.options);
                  view.put("language", q.language);
                  view.put("starterCode", q.starterCode);
                  view.put("samples", q.samples == null ? List.of() : q.samples);
                  return view;
                })
            .toList());
  }
}
