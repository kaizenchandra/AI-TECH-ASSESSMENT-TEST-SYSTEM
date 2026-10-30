package com.assessment.platform;

import java.util.*;

public interface CodeRunner {
  record Input(
      UUID jobId,
      String language,
      String source,
      List<String> inputs,
      int timeoutMs,
      int memoryMb) {}

  record CaseResult(String status, String stdout) {}

  record Result(List<CaseResult> cases) {}

  Result execute(Input input) throws Exception;

  static double fraction(Result result, List<AssessmentContent.TestCase> tests) {
    if (result == null || result.cases() == null || result.cases().size() != tests.size())
      throw new IllegalArgumentException("Invalid execution evidence");
    int passed = 0, total = 0;
    for (int i = 0; i < tests.size(); i++) {
      var actual = result.cases().get(i);
      var expected = tests.get(i);
      if (actual == null
          || !Set.of(
                  "OK",
                  "COMPILE_ERROR",
                  "RUNTIME_ERROR",
                  "TIME_LIMIT",
                  "MEMORY_LIMIT",
                  "OUTPUT_LIMIT")
              .contains(actual.status())
          || actual.stdout() == null
          || actual.stdout().length() > 65536)
        throw new IllegalArgumentException("Invalid execution result");
      total += expected.weight();
      if (actual.status().equals("OK")
          && normalize(actual.stdout()).equals(normalize(expected.output())))
        passed += expected.weight();
    }
    return (double) passed / total;
  }

  static String normalize(String text) {
    return text.replace("\r\n", "\n").stripTrailing();
  }
}
