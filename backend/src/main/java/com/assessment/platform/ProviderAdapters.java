package com.assessment.platform;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ProviderAdapters implements AiProvider {
  final Store db;
  final String mode, model, key;
  final boolean liveApproved;

  @Value("${AI_CANDIDATE_PROCESSING_APPROVED:false}")
  boolean candidateProcessingApproved;

  final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public ProviderAdapters(
      Store db,
      @Value("${app.ai.mode}") String mode,
      @Value("${app.ai.model}") String model,
      @Value("${app.ai.key}") String key,
      @Value("${AI_LIVE_APPROVED:false}") boolean liveApproved,
      @Value("${APP_ENV:local}") String environment) {
    this.db = db;
    this.mode = mode;
    this.model = model;
    this.key = key;
    this.liveApproved = liveApproved;
    if (environment.equals("production") && mode.equals("fake"))
      throw new IllegalStateException("Fake AI is forbidden in production");
  }

  @Override
  public Output generate(String purpose, String source) throws Exception {
    boolean feedback = Set.of("REPORT", "FEEDBACK").contains(purpose);
    if (mode.equals("fake")) {
      if (feedback) {
        var evidence = db.decode(source, FeedbackEvidence.Source.class);
        var findings =
            evidence.code().stream()
                .filter(c -> !c.source().isBlank())
                .map(
                    c ->
                        new Finding(
                            c.questionId(),
                            1,
                            1,
                            c.source().split("\n", -1)[0],
                            "READABILITY",
                            "Development fixture: ask the evaluator to review naming and explain"
                                + " the approach; no quality or complexity judgment was measured."))
                .toList();
        return new Output(
            new Draft(
                "DEVELOPMENT FAKE — evidence-linked review prompts, not proficiency judgments",
                List.of(),
                List.of(),
                List.of(
                    "No complexity inference or hiring decision",
                    "Skills without scored evidence are not assessed"),
                findings),
            "fake",
            "feedback-fixture-v1",
            "local",
            0,
            true);
      }
      List<Skill> skills = new ArrayList<>();
      List<Question> questions = new ArrayList<>();
      if (purpose.equals("COMPETENCY")) {
        var present =
            AiValidation.SKILLS.stream()
                .sorted()
                .filter(s -> source.toLowerCase(Locale.ROOT).contains(s))
                .toList();
        if (present.isEmpty())
          throw new Problem(422, "Fixture needs at least one canonical skill name");
        int assigned = 0;
        for (int i = 0; i < present.size(); i++) {
          String s = present.get(i);
          int start = source.toLowerCase(Locale.ROOT).indexOf(s);
          int weight = i == present.size() - 1 ? 100 - assigned : 100 / present.size();
          assigned += weight;
          skills.add(
              new Skill(
                  s,
                  weight,
                  source.substring(start, start + s.length()),
                  "Explicit source mention; proficiency requires human review"));
        }
      } else {
        var spec = db.decode(source, QuestionSource.class);
        var g = spec.generation();
        String skill = spec.competencyMap().skills().getFirst().id();
        if (Set.of("SINGLE", "MULTIPLE").contains(g.kind())) {
          String[] titles = {
            "Which approach avoids applying an event twice?",
            "Which response best handles a transient dependency failure?",
            "Which practice protects confidential credentials?"
          };
          List<List<String>> options =
              List.of(
                  List.of(
                      "Use an idempotency key", "Replay every event", "Ignore event identifiers"),
                  List.of(
                      "Bounded retries with backoff",
                      "Retry forever",
                      "Treat every failure as success"),
                  List.of(
                      "Use an approved secret store",
                      "Embed keys in client code",
                      "Print keys in logs"));
          for (int n = 0; n < g.count(); n++)
            questions.add(
                new Question(
                    g.kind(),
                    titles[n],
                    skill,
                    options.get(n),
                    List.of(0),
                    "The first option preserves a controlled boundary; the alternatives introduce"
                        + " duplicate effects, unbounded work or secret exposure.",
                    titles[n]
                        + (g.kind().equals("MULTIPLE")
                            ? " Select all applicable options (one correct option in this fixture)."
                            : " Select one option."),
                    null));
        } else {
          boolean design = g.kind().equals("SYSTEM_DESIGN");
          String title = design ? "Idempotent event ingestion core" : "Sum signed integers";
          String reference =
              switch (g.language()) {
                case "python" ->
                    design
                        ? "import sys\nprint(len(set(sys.stdin.read().split())))"
                        : "import sys\nprint(sum(map(int,sys.stdin.read().split())))";
                case "javascript" ->
                    design
                        ? "const s=require('fs').readFileSync(0,'utf8').trim();console.log(s?new"
                              + " Set(s.split(/\\s+/)).size:0);"
                        : "const"
                              + " s=require('fs').readFileSync(0,'utf8').trim();console.log(s?s.split(/\\s+/).map(Number).reduce((a,b)=>a+b,0):0);";
                default ->
                    design
                        ? "import java.util.*; public class Main { public static void main(String[]"
                              + " args) { Scanner s=new Scanner(System.in); Set<String> ids=new"
                              + " HashSet<>(); while(s.hasNext()) ids.add(s.next());"
                              + " System.out.println(ids.size()); }}"
                        : "import java.util.*; public class Main { public static void main(String[]"
                              + " args) { Scanner s=new Scanner(System.in); long sum=0;"
                              + " while(s.hasNextLong()) sum+=s.nextLong();"
                              + " System.out.println(sum); }}";
              };
          var samples =
              List.of(new AssessmentContent.Sample(design ? "evt1 evt1 evt2" : "1 2 -1", "2"));
          var hidden =
              List.of(
                  new AssessmentContent.TestCase("", "0", 1),
                  new AssessmentContent.TestCase(
                      design ? "x x x" : "-5 2", "" + (design ? 1 : -3), 2));
          questions.add(
              new Question(
                  "CODING",
                  title,
                  skill,
                  List.of(),
                  List.of(),
                  "Check empty input and repeated or negative values. This is a bounded"
                      + " implementation exercise, not a complete architecture evaluation.",
                  design
                      ? "Implement the deduplication core of an event ingestion service. Read up to"
                            + " 1000 whitespace-separated event IDs (1–64 alphanumeric characters)."
                            + " Output the count of distinct IDs. No external storage or networking"
                            + " is required. This tests an idempotency building block, not"
                            + " distributed-system guarantees."
                      : "Read up to 1000 whitespace-separated integers in [-1000000,1000000]."
                            + " Output their sum. Empty input must output 0.",
                  new Coding(
                      g.language(),
                      g.language().equals("java")
                          ? "public class Main { public static void main(String[] args) { } }"
                          : "",
                      reference,
                      samples,
                      hidden,
                      2000,
                      256)));
        }
      }
      return new Output(
          new Draft(
              "DEVELOPMENT FAKE — review required",
              skills,
              questions,
              List.of(
                  "Deterministic fixture, not a validated model result",
                  "Difficulty is an uncalibrated estimate")),
          "fake",
          "fixture-v1",
          "local",
          0,
          true);
    }
    if (!mode.equals("openai") || !liveApproved || key.isBlank() || model.isBlank())
      throw new Problem(503, "AI disabled or approved live configuration missing");
    if (feedback && !candidateProcessingApproved)
      throw new Problem(403, "External candidate processing is disabled");
    String prompt =
        new String(
            new ClassPathResource(feedback ? "ai/feedback-v1.txt" : "ai/authoring-v2.txt")
                .getInputStream()
                .readAllBytes(),
            StandardCharsets.UTF_8);
    String schema =
        new String(
            new ClassPathResource("ai/draft-schema.json").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    var requestBody =
        Map.of(
            "model",
            model,
            "store",
            false,
            "max_completion_tokens",
            2000,
            "messages",
            List.of(
                Map.of("role", "system", "content", prompt + "\nPurpose: " + purpose),
                Map.of("role", "user", "content", source)),
            "response_format",
            Map.of(
                "type",
                "json_schema",
                "json_schema",
                Map.of(
                    "name",
                    "assessment_draft",
                    "strict",
                    true,
                    "schema",
                    db.json.readTree(schema))));
    var request =
        HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
            .timeout(Duration.ofSeconds(35))
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(db.encode(requestBody)))
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    byte[] bytes;
    try (var stream = response.body()) {
      bytes = stream.readNBytes(131073);
    }
    if (bytes.length > 131072) throw new Problem(422, "Provider output too large");
    if (response.statusCode() == 429 || response.statusCode() >= 500)
      throw new java.io.IOException("Transient provider failure");
    if (response.statusCode() != 200) throw new Problem(422, "Provider rejected request");
    var root = db.json.readTree(bytes);
    var choice = root.path("choices").path(0);
    if (!choice.path("finish_reason").asText().equals("stop")
        || !choice.path("message").path("refusal").isNull()
            && !choice.path("message").path("refusal").isMissingNode())
      throw new Problem(422, "Incomplete or refused output");
    var draft = db.decode(choice.path("message").path("content").asText(), Draft.class);
    return new Output(
        draft,
        "openai",
        root.path("model").asText(model),
        root.path("id").asText(),
        root.path("usage").path("total_tokens").asInt(),
        false);
  }
}
