package com.assessment.platform;

import java.time.*;

public final class TimerRules {
  public static final long GRACE_MS = 30_000;

  public record State(
      Instant lastActivity,
      Instant accountedAt,
      long consumed,
      long paused,
      int pauseCount,
      boolean outageOpen,
      long outagePaused) {}

  public record Policy(
      boolean controlled,
      long durationMs,
      int maxPauses,
      long perPauseMs,
      long totalPauseMs,
      Instant deadline) {}

  public record Result(State state, long remainingMs, boolean expired) {}

  public static Result reconcile(State old, Policy policy, Instant requestedNow, boolean activity) {
    Instant now = requestedNow.isBefore(old.accountedAt) ? old.accountedAt : requestedNow;
    long elapsed = Math.max(0, Duration.between(old.accountedAt, now).toMillis());
    Instant pauseStart = old.lastActivity.plusMillis(GRACE_MS);
    long eligible =
        Math.max(
            0,
            Duration.between(
                    old.accountedAt.isAfter(pauseStart) ? old.accountedAt : pauseStart, now)
                .toMillis());
    boolean available = policy.controlled && (old.outageOpen || old.pauseCount < policy.maxPauses);
    long granted =
        available
            ? Math.min(
                eligible,
                Math.max(
                    0,
                    Math.min(
                        policy.perPauseMs - old.outagePaused, policy.totalPauseMs - old.paused)))
            : 0;
    int count = old.pauseCount + (granted > 0 && !old.outageOpen ? 1 : 0);
    long consumed = old.consumed + elapsed - granted;
    long deadlineRemaining = Math.max(0, Duration.between(now, policy.deadline).toMillis());
    long remaining = Math.max(0, Math.min(policy.durationMs - consumed, deadlineRemaining));
    return new Result(
        new State(
            activity ? now : old.lastActivity,
            now,
            consumed,
            old.paused + granted,
            count,
            activity ? false : old.outageOpen || granted > 0,
            activity ? 0 : old.outagePaused + granted),
        remaining,
        remaining == 0);
  }

  private TimerRules() {}
}
