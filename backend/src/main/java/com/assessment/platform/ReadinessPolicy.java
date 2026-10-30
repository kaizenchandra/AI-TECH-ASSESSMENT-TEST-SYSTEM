package com.assessment.platform;

import java.util.*;

public final class ReadinessPolicy {
  static final Set<String> MEDIA = Set.of("camera", "microphone", "microphoneLevel", "playback");

  static List<String> exceptions(Store db, UUID invitation) {
    var rows =
        db.jdbc.queryForList(
            "select checks from readiness_exceptions where invitation_id=? and expires_at>now()",
            invitation);
    if (rows.isEmpty()) return List.of();
    return Arrays.asList(db.decode(rows.getFirst().get("checks"), String[].class));
  }

  static void validate(Store db, UUID invitation, AssessmentContent content, Map<?, ?> checks) {
    for (String key :
        List.of("instructions", "browser", "cookies", "network", "secureContext", "viewport"))
      if (!Boolean.TRUE.equals(checks.get(key)))
        throw new Problem(422, "Required check has not passed: " + key);
    var waived = exceptions(db, invitation);
    for (String key : MEDIA) {
      boolean required =
          key.equals("camera") ? content.cameraRequired() : content.microphoneRequired();
      if (required && !Boolean.TRUE.equals(checks.get(key)) && !waived.contains(key))
        throw new Problem(422, "Required device check failed: " + key);
    }
  }

  private ReadinessPolicy() {}
}
