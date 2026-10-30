package com.assessment.platform;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KubernetesRunnerAdapter implements CodeRunner {
  private final Store db;
  private final String endpoint, token;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public KubernetesRunnerAdapter(
      Store db, @Value("${RUNNER_URL:}") String endpoint, @Value("${RUNNER_TOKEN:}") String token) {
    this.db = db;
    this.endpoint = endpoint;
    this.token = token;
    if (!endpoint.isBlank()) {
      var uri = URI.create(endpoint);
      if (!"https".equals(uri.getScheme())
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null)
        throw new IllegalArgumentException("Runner requires an approved HTTPS endpoint");
    }
  }

  public Result execute(Input input) throws Exception {
    if (endpoint.isBlank() || token.length() < 32)
      throw new java.io.IOException("RUNNER_NOT_CONFIGURED");
    var request =
        HttpRequest.newBuilder(URI.create(endpoint.replaceAll("/$", "") + "/v1/executions"))
            .timeout(Duration.ofSeconds(600))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(db.encode(input)))
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    byte[] bytes;
    try (var stream = response.body()) {
      bytes = stream.readNBytes(2_100_001);
    }
    if (response.statusCode() != 200 || bytes.length > 2_100_000)
      throw new java.io.IOException("RUNNER_INFRASTRUCTURE_FAILURE");
    return db.json.readValue(bytes, Result.class);
  }
}
