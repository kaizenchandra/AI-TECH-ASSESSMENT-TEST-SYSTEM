package com.assessment.platform;

import java.util.*;

/** Pure deterministic selection. Never relaxes declared count, skill or time constraints. */
public final class Blueprint {
  public record Item(UUID versionId, String skill, String type, int minutes, boolean approved) {}

  public record Requirement(String skill, String type, int count) {}

  public record Result(
      boolean feasible, List<UUID> versions, List<String> unmet, long seed, String algorithm) {}

  public static Result select(
      List<Item> bank,
      List<Requirement> requirements,
      int maxMinutes,
      Set<UUID> excluded,
      long seed) {
    if (maxMinutes <= 0
        || requirements.isEmpty()
        || requirements.stream().anyMatch(r -> r.count <= 0))
      throw new IllegalArgumentException("Invalid blueprint");
    var sorted =
        new ArrayList<>(
            bank.stream()
                .filter(i -> i.approved && !excluded.contains(i.versionId))
                .sorted(Comparator.comparing(i -> i.versionId.toString()))
                .toList());
    Collections.shuffle(sorted, new Random(seed));
    List<Item> chosen = new ArrayList<>();
    List<String> unmet = new ArrayList<>();
    Set<UUID> used = new HashSet<>();
    for (var r : requirements) {
      var matching =
          sorted.stream()
              .filter(
                  i ->
                      i.skill.equals(r.skill)
                          && i.type.equals(r.type)
                          && !used.contains(i.versionId))
              .sorted(Comparator.comparingInt(item -> Objects.requireNonNull(item).minutes()))
              .limit(r.count)
              .toList();
      if (matching.size() != r.count)
        unmet.add(
            "Insufficient approved "
                + r.skill
                + " / "
                + r.type
                + ": requested "
                + r.count
                + ", available "
                + matching.size());
      for (var i : matching) {
        chosen.add(i);
        used.add(i.versionId);
      }
    }
    int minutes = chosen.stream().mapToInt(item -> Objects.requireNonNull(item).minutes()).sum();
    if (minutes > maxMinutes)
      unmet.add("Minimum selected solving time " + minutes + " exceeds " + maxMinutes + " minutes");
    return new Result(
        unmet.isEmpty(),
        unmet.isEmpty()
            ? chosen.stream().map(item -> Objects.requireNonNull(item).versionId()).toList()
            : List.of(),
        unmet,
        seed,
        "min-time-seeded-v1");
  }

  private Blueprint() {}
}
