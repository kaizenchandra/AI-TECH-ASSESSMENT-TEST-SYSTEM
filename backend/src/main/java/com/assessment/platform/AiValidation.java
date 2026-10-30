package com.assessment.platform;

import java.util.*;

public final class AiValidation {
  public static final Set<String> SKILLS =
      Set.of("java", "spring", "kafka", "sql", "coding", "system-design");

  public static void validate(String purpose, String source, AiProvider.Draft draft) {
    if (draft == null
        || draft.summary() == null
        || draft.summary().length() > 6000
        || draft.skills() == null
        || draft.questions() == null
        || draft.limitations() == null
        || draft.skills().size() > 20
        || draft.questions().size() > 10
        || draft.limitations().size() > 20) throw new Problem(422, "Invalid AI schema");
    Set<String> ids = new HashSet<>();
    int weight = 0;
    for (var s : draft.skills()) {
      if (s == null
          || s.id() == null
          || !SKILLS.contains(s.id())
          || !ids.add(s.id())
          || s.weight() < 0
          || s.weight() > 100
          || s.evidence() == null
          || s.evidence().isBlank()
          || !source.contains(s.evidence())
          || s.observation() == null
          || s.observation().length() > 3000) throw new Problem(422, "Invalid skill or evidence");
      weight += s.weight();
    }
    if (purpose.equals("COMPETENCY") && (draft.skills().isEmpty() || weight != 100))
      throw new Problem(422, "Competency weights must total 100");
    if (purpose.equals("QUESTION") && draft.questions().isEmpty())
      throw new Problem(422, "No question drafts returned");
    Set<String> titles = new HashSet<>();
    for (var q : draft.questions()) {
      if (q == null
          || q.type() == null
          || q.skill() == null
          || q.options() == null
          || q.correct() == null) throw new Problem(422, "Invalid question schema");
      if (q.type().equals("CODING")) {
        var c = q.coding();
        if (c == null
            || q.title() == null
            || q.title().isBlank()
            || !titles.add(q.title().trim().toLowerCase(Locale.ROOT))
            || !SKILLS.contains(q.skill())
            || q.description() == null
            || q.description().isBlank()
            || q.description().length() > 20000
            || q.rationale() == null
            || q.rationale().isBlank()
            || !q.options().isEmpty()
            || !q.correct().isEmpty()
            || !Set.of("java", "python", "javascript").contains(c.language())
            || c.starterCode() == null
            || c.referenceSolution() == null
            || c.referenceSolution().isBlank()
            || c.referenceSolution().length() > 20000
            || c.samples() == null
            || c.samples().isEmpty()
            || c.samples().size() > 10) throw new Problem(422, "Incomplete coding problem");
        new AssessmentContent.CodingSpec(c.hiddenTests(), c.timeoutMs(), c.memoryMb()).validate();
        if (c.samples().size() + c.hiddenTests().size() > 30
            || c.samples().stream()
                .anyMatch(
                    s ->
                        s == null
                            || s.input() == null
                            || s.output() == null
                            || s.input().length() > 16384
                            || s.output().length() > 16384))
          throw new Problem(422, "Invalid public test cases");
        if (c.language().equals("java") && c.memoryMb() < 256)
          throw new Problem(422, "Java requires at least 256 MiB");
        continue;
      }
      if (q.coding() != null
          || !Set.of("SINGLE", "MULTIPLE").contains(q.type())
          || q.title() == null
          || q.title().isBlank()
          || !titles.add(q.title().trim().toLowerCase(Locale.ROOT))
          || !SKILLS.contains(q.skill())
          || q.options() == null
          || q.options().size() < 2
          || q.options().size() > 10
          || q.options().stream().anyMatch(s -> s == null || s.isBlank())
          || new HashSet<>(q.options()).size() != q.options().size()
          || q.correct() == null
          || q.correct().isEmpty()
          || new HashSet<>(q.correct()).size() != q.correct().size()
          || q.correct().stream().anyMatch(i -> i == null || i < 0 || i >= q.options().size())
          || q.type().equals("SINGLE") && q.correct().size() != 1
          || q.rationale() == null
          || q.rationale().isBlank()) throw new Problem(422, "Invalid generated question");
    }
  }

  private AiValidation() {}
}
