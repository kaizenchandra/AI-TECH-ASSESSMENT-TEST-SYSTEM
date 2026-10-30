package com.assessment.platform;

import java.util.*;

public final class Scoring {
  public record Answer(
      List<Integer> selected, String language, String code, boolean flagged, boolean skipped) {
    public Answer(List<Integer> selected, String language, String code, boolean flagged) {
      this(selected, language, code, flagged, false);
    }

    public Answer {
      selected = selected == null ? List.of() : List.copyOf(selected);
    }
  }

  public static boolean answered(AssessmentContent.Question q, Answer a) {
    return q.type().equals("CODING")
        ? a.code() != null
            && !a.code().isBlank()
            && !a.code().strip().equals(q.starterCode().strip())
        : !a.selected().isEmpty();
  }

  public static double score(AssessmentContent.Question q, Answer a) {
    if (q.type().equals("CODING"))
      throw new IllegalArgumentException("Coding needs sandbox evidence");
    Set<Integer> selected = new HashSet<>(a.selected()), correct = new HashSet<>(q.correct());
    if (selected.isEmpty()) return 0;
    if (selected.equals(correct)) return q.points();
    if (q.type().equals("SINGLE") || !q.partialCredit()) return -q.penalty();
    long hits = selected.stream().filter(correct::contains).count();
    long wrong = selected.size() - hits;
    return Math.max(-q.penalty(), q.points() * hits / correct.size() - q.penalty() * wrong);
  }

  public static void validate(AssessmentContent.Question q, Answer a) {
    if (a.selected.size() > 12 || new HashSet<>(a.selected).size() != a.selected.size())
      throw new Problem(422, "Invalid selections");
    if (q.type().equals("CODING")) {
      if (!a.selected.isEmpty()
          || !Objects.equals(q.language(), a.language)
          || a.code == null
          || a.code.length() > 100_000) throw new Problem(422, "Invalid code/language");
    } else if (a.code != null
        || a.language != null
        || a.selected.stream().anyMatch(i -> i < 0 || i >= q.options().size())
        || q.type().equals("SINGLE") && a.selected.size() > 1)
      throw new Problem(422, "Invalid answer");
  }

  private Scoring() {}
}
