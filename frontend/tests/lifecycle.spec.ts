import { test, expect, APIRequestContext } from "@playwright/test";
async function csrf(request: APIRequestContext) {
  return (await request.get("/api/v1/csrf")).json() as Promise<{
    header: string;
    token: string;
  }>;
}
async function send(
  request: APIRequestContext,
  path: string,
  data?: unknown,
  extra: Record<string, string> = {},
) {
  const token = await csrf(request);
  const response = await request.post("/api/v1" + path, {
    data,
    headers: { [token.header]: token.token, ...extra },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
async function email(
  request: APIRequestContext,
  address: string,
  subject: string,
) {
  let body = "";
  await expect
    .poll(
      async () => {
        const result = await request.get(
          "http://localhost:8025/api/v1/messages",
        );
        const messages = (await result.json()).messages as {
          ID: string;
          Subject: string;
          To: { Address: string }[];
        }[];
        const found = messages.find(
          (m) =>
            m.Subject === subject && m.To.some((to) => to.Address === address),
        );
        if (found) {
          const full = await request.get(
            "http://localhost:8025/api/v1/message/" + found.ID,
          );
          body = (await full.json()).Text;
        }
        return body;
      },
      { timeout: 20000 },
    )
    .not.toBe("");
  return body;
}
test("published choice assessment survives refresh and produces deterministic results", async ({
  browser,
}) => {
  test.setTimeout(120000);
  const recruiter = await browser.newContext({
    baseURL: "http://localhost:8080",
  });
  const candidate = await browser.newContext({
    baseURL: "http://localhost:8080",
    viewport: { width: 1440, height: 1000 },
  });
  const page = await candidate.newPage();
  const suffix = Date.now();
  const staffEmail = `recruiter-${suffix}@example.test`,
    candidateEmail = `candidate-${suffix}@example.test`,
    password = `Synthetic-demo-${suffix}!`;
  try {
    await send(recruiter.request, "/register", {
      email: staffEmail,
      password,
      organization: "E2E synthetic organization",
    });
    const verify = (
      await email(
        recruiter.request,
        staffEmail,
        "Verify your assessment account",
      )
    ).match(/[A-Za-z0-9_-]{43}/)![0];
    await send(recruiter.request, "/verify", { token: verify });
    const auth = await csrf(recruiter.request);
    const login = await recruiter.request.post("/api/v1/login", {
      form: { username: staffEmail, password },
      headers: { [auth.header]: auth.token },
    });
    expect(login.ok()).toBeTruthy();
    const q1 = crypto.randomUUID(),
      q2 = crypto.randomUUID();
    const content = {
      title: "Java Backend Developer — E2E",
      instructions:
        "Choose answers independently. This test uses controlled pauses.",
      durationMinutes: 60,
      timerPolicy: "CONTROLLED",
      maxPauses: 2,
      maxPauseSeconds: 300,
      totalPauseSeconds: 600,
      availableFrom: new Date(Date.now() - 60000).toISOString(),
      latestStart: new Date(Date.now() + 3600000).toISOString(),
      completionDeadline: new Date(Date.now() + 7200000).toISOString(),
      cameraRequired: false,
      microphoneRequired: false,
      backNavigation: true,
      passingPercent: 60,
      questions: [
        {
          id: q1,
          type: "SINGLE",
          title: "Unique collection",
          description: "Which collection excludes duplicates?",
          skill: "java",
          options: ["List", "Set"],
          correct: [1],
          points: 10,
          penalty: 0,
          partialCredit: false,
          language: null,
          starterCode: null,
          samples: [],
        },
        {
          id: q2,
          type: "MULTIPLE",
          title: "Transaction guarantees",
          description: "Select ACID properties.",
          skill: "sql",
          options: ["Atomicity", "Durability", "Inheritance"],
          correct: [0, 1],
          points: 10,
          penalty: 2,
          partialCredit: true,
          language: null,
          starterCode: null,
          samples: [],
        },
      ],
    };
    const assessment = await send(recruiter.request, "/assessments", content);
    const jobPage = await recruiter.newPage();
    await jobPage.goto("/");
    await jobPage.getByRole("button", { name: "Jobs", exact: true }).click();
    await jobPage
      .getByRole("button", { name: "Create job opening", exact: true })
      .click();
    await jobPage
      .getByLabel("Job title", { exact: true })
      .fill("Java Backend Developer");
    await jobPage
      .getByLabel("Job description", { exact: true })
      .fill(
        "Build maintainable Java services and transactional SQL workflows.",
      );
    await jobPage
      .getByLabel("Job latest start (local time)", { exact: true })
      .fill("2030-01-01T10:00");
    await jobPage
      .getByLabel("Job completion deadline (local time)", { exact: true })
      .fill("2030-01-01T12:00");
    await jobPage
      .getByRole("checkbox", { name: "Include java", exact: true })
      .check();
    await jobPage.getByLabel("java weight %", { exact: true }).fill("50");
    await jobPage
      .getByRole("checkbox", { name: "Include sql", exact: true })
      .check();
    await jobPage.getByLabel("sql weight %", { exact: true }).fill("50");
    await jobPage
      .getByRole("button", { name: "Save job", exact: true })
      .click();
    await expect(
      jobPage.getByRole("heading", {
        name: "Java Backend Developer · OPEN",
        exact: true,
      }),
    ).toBeVisible();
    await jobPage
      .getByRole("combobox", {
        name: "Link draft assessment to Java Backend Developer",
        exact: true,
      })
      .selectOption(assessment.id);
    await jobPage
      .getByRole("button", { name: "Link assessment", exact: true })
      .click();
    await expect
      .poll(async () => {
        const rows = await (
          await recruiter.request.get("/api/v1/assessments")
        ).json();
        return rows.find((r: { id: string }) => r.id === assessment.id)
          .revision;
      })
      .toBe(1);
    await send(
      recruiter.request,
      `/assessments/${assessment.id}/publish`,
      undefined,
      { "If-Match": "1" },
    );
    await send(recruiter.request, "/invitations", {
      assessmentId: assessment.id,
      email: candidateEmail,
      expiresAt: new Date(Date.now() + 1800000).toISOString(),
    });
    const invitation = (
      await email(recruiter.request, candidateEmail, "Assessment invitation")
    ).match(/#invite=([A-Za-z0-9_-]+)/)![1];
    await send(candidate.request, "/register", {
      email: candidateEmail,
      password,
      organization: "",
      invitation,
    });
    const candidateToken = (
      await email(
        candidate.request,
        candidateEmail,
        "Verify your assessment account",
      )
    ).match(/[A-Za-z0-9_-]{43}/)![0];
    await send(candidate.request, "/verify", { token: candidateToken });
    await page.goto("/#invite=" + invitation);
    await page.getByLabel("Email address").fill(candidateEmail);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Sign in →" }).click();
    await page
      .getByRole("button", { name: "Validate and claim invitation" })
      .click();
    await page.getByRole("button", { name: "Read instructions →" }).click();
    await expect(
      page.getByRole("heading", { name: "Question categories" }),
    ).toBeVisible();
    await expect(
      page.getByText("Job role: Java Backend Developer", { exact: true }),
    ).toBeVisible();
    await expect(
      page
        .locator(".category-cards > div")
        .filter({ hasText: "Single choice" }),
    ).toContainText("1");
    await page.screenshot({
      path: test.info().outputPath("instructions-overview.png"),
      fullPage: true,
    });
    const before = await (
      await candidate.request.get("/api/v1/candidate/assignments")
    ).json();
    expect(before[0].attempt_id).toBeNull();
    const token = await csrf(candidate.request);
    const deniedStart = await candidate.request.post(
      `/api/v1/candidate/invitations/${before[0].id}/start`,
      {
        headers: {
          [token.header]: token.token,
          "X-Tab-ID": crypto.randomUUID(),
        },
      },
    );
    expect(deniedStart.ok()).toBeFalsy();
    await page.getByLabel("I have read and accept these instructions.").check();
    await page.setViewportSize({ width: 740, height: 1000 });
    await page.getByRole("button", { name: "Run system checks" }).click();
    await expect(
      page.getByRole("article", { name: "Screen size: Failed", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Start assessment →", exact: true }),
    ).toBeDisabled();
    await page
      .locator(".readiness-section")
      .screenshot({ path: test.info().outputPath("readiness-failed.png") });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page
      .getByRole("button", { name: "Retry Screen size", exact: true })
      .click();
    await expect(
      page.getByRole("article", { name: "Screen size: Passed", exact: true }),
    ).toBeVisible();
    await page
      .locator(".readiness-section")
      .screenshot({ path: test.info().outputPath("readiness-passed.png") });
    await page.getByRole("button", { name: "Start assessment →" }).click();
    await expect(
      page.getByRole("heading", { name: "Unique collection" }),
    ).toBeVisible();
    await page.screenshot({
      path: test.info().outputPath("assessment-dark-desktop.png"),
      fullPage: true,
    });
    await page.setViewportSize({ width: 740, height: 1000 });
    await page.screenshot({
      path: test.info().outputPath("assessment-dark-narrow.png"),
      fullPage: true,
    });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.getByRole("button", { name: "Skip", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Question 1, Skipped", exact: true }),
    ).toHaveClass(/skipped/);
    await expect(
      page.getByRole("button", { name: "Question 1, Skipped", exact: true }),
    ).toHaveText("1");
    await page.reload();
    await page.getByRole("button", { name: "Resume assessment" }).click();
    await expect(
      page.getByRole("heading", { name: "Transaction guarantees" }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Question 1, Skipped", exact: true })
      .click();
    await page.getByRole("radio", { name: "Set", exact: true }).check();
    await expect(page.getByRole("status")).toHaveText("Saved");
    await expect(
      page.getByRole("button", { name: "Question 1, Answered", exact: true }),
    ).toHaveClass(/answered/);
    await expect(
      page.getByRole("button", { name: "Question 1, Answered", exact: true }),
    ).toHaveText("1");
    await page.screenshot({
      path: test.info().outputPath("numbered-palette-saved.png"),
      fullPage: true,
    });
    await page
      .getByRole("button", { name: "Clear answer", exact: true })
      .click();
    await expect(page.getByRole("status")).toHaveText("Saved");
    await expect(
      page.getByRole("button", {
        name: "Question 1, Viewed, unanswered",
        exact: true,
      }),
    ).not.toHaveClass(/answered/);
    await page.getByRole("radio", { name: "Set", exact: true }).check();
    await expect(page.getByRole("status")).toHaveText("Saved");
    await page.reload();
    await page.getByRole("button", { name: "Resume assessment" }).click();
    await expect(
      page.getByRole("radio", { name: "Set", exact: true }),
    ).toBeChecked();
    await page.getByRole("button", { name: "Save & next →" }).click();
    await candidate.setOffline(true);
    await page
      .getByRole("checkbox", { name: "Atomicity", exact: true })
      .check();
    await page
      .getByRole("checkbox", { name: "Durability", exact: true })
      .check();
    await expect(page.getByRole("status")).toContainText("Offline / unsynced");
    await expect(
      page.getByRole("button", {
        name: "Question 2, Viewed, unanswered",
        exact: true,
      }),
    ).not.toHaveClass(/answered/);
    await candidate.setOffline(false);
    await expect(page.getByRole("status")).toHaveText("Saved");
    await page.getByRole("button", { name: "Finish assessment" }).click();
    const review = page.getByRole("dialog", { name: "Review your answers" });
    await expect(review.getByRole("article")).toHaveCount(2);
    await expect(review.getByRole("heading", { name: "Unique collection" })).toBeVisible();
    await expect(review.getByRole("heading", { name: "Transaction guarantees" })).toBeVisible();
    await expect(review.getByText("Selected", { exact: true })).toHaveCount(3);
    await page.screenshot({ path: test.info().outputPath("submission-review-desktop.png") });
    await page.setViewportSize({ width: 740, height: 900 });
    await page.screenshot({ path: test.info().outputPath("submission-review-narrow.png") });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await review.getByRole("button", { name: "Edit answer for question 1" }).click();
    await expect(page.getByRole("radio", { name: "Set", exact: true })).toBeChecked();
    await page.getByRole("button", { name: "Finish assessment" }).click();
    await page
      .getByRole("button", { name: "Submit assessment", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Submission received" }),
    ).toBeVisible();
    const reports = await recruiter.request.get("/api/v1/reports");
    const result = (await reports.json())[0];
    expect(result.baseline.percentage).toBe(100);
    expect(result.status).toBe("EVALUATED");
    const forbidden = await candidate.request.get("/api/v1/reports");
    expect(forbidden.status()).toBe(403);
    expect(
      await (await candidate.request.get("/api/v1/candidate/results")).json(),
    ).toEqual([]);
    await send(
      recruiter.request,
      `/evaluations/${result.attempt_id}/reviews`,
      {
        feedback: "Reviewed against the approved Java and SQL criteria.",
        recommendation: "INTERVIEW",
        adjustments: [],
      },
      { "If-Match": "0" },
    );
    const releaseKey = crypto.randomUUID();
    const released = await send(
      recruiter.request,
      `/reports/${result.attempt_id}/release`,
      undefined,
      { "If-Match": "1", "Idempotency-Key": releaseKey },
    );
    expect(
      await send(
        recruiter.request,
        `/reports/${result.attempt_id}/release`,
        undefined,
        { "If-Match": "1", "Idempotency-Key": releaseKey },
      ),
    ).toEqual(released);
    const results = await (
      await candidate.request.get("/api/v1/candidate/results")
    ).json();
    expect(results[0].percentage).toBe(100);
    expect(
      results[0].skills.some(
        (s: { status: string }) => s.status === "NOT_ASSESSED",
      ),
    ).toBeTruthy();
    expect(results[0]).not.toHaveProperty("questions");
    expect(results[0]).not.toHaveProperty("recommendation");
    await send(
      candidate.request,
      `/candidate/attempts/${result.attempt_id}/review-requests`,
      { reason: "Please explain the skill coverage in my assessment." },
    );
    const reviewerPage = await recruiter.newPage();
    await reviewerPage.goto("/");
    await reviewerPage
      .getByRole("button", { name: "Results", exact: true })
      .click();
    await reviewerPage
      .getByRole("button", { name: "Assign to me", exact: true })
      .click();
    await expect(
      reviewerPage.getByRole("heading", { name: /IN REVIEW/ }),
    ).toBeVisible();
    await reviewerPage
      .getByLabel("Candidate-visible response")
      .fill(
        "Only the listed Java and SQL criteria were assessed. Other skills remain not assessed.",
      );
    await reviewerPage
      .getByRole("button", { name: "Resolve request", exact: true })
      .click();
    await page.goto("/");
    await expect(
      page.getByText(
        "Only the listed Java and SQL criteria were assessed. Other skills remain not assessed.",
      ),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Allow optional AI feedback", exact: true })
      .click();
    await page
      .getByRole("button", {
        name: "Withdraw optional AI consent",
        exact: true,
      })
      .click();
    await expect(
      page.getByText(
        "Optional processing withdrawn. Assessment timing and scores are unchanged.",
      ),
    ).toBeVisible();
    await reviewerPage
      .getByRole("button", { name: "Candidates", exact: true })
      .click();
    await reviewerPage
      .getByRole("combobox", { name: "Original invitation", exact: true })
      .selectOption(before[0].id);
    const retakeDate = new Date(Date.now() + 1800000);
    const retakeLocal = new Date(
      retakeDate.getTime() - retakeDate.getTimezoneOffset() * 60000,
    )
      .toISOString()
      .slice(0, 16);
    await reviewerPage
      .getByLabel("Retake expiry (local time)")
      .fill(retakeLocal);
    await reviewerPage
      .getByLabel("Authorization reason")
      .fill("Authorized fixture retake after completed assessment");
    await reviewerPage
      .getByRole("button", { name: "Authorize and send retake", exact: true })
      .click();
    await expect(
      reviewerPage.getByText(
        "Retake invitation queued. The original submission remains unchanged.",
      ),
    ).toBeVisible();
    const invitationsAfter = await (
      await recruiter.request.get("/api/v1/invitations")
    ).json();
    expect(invitationsAfter).toHaveLength(2);
    const originalResult = await (
      await recruiter.request.get(`/api/v1/evaluations/${result.attempt_id}`)
    ).json();
    expect(originalResult.baseline.percentage).toBe(100);
  } finally {
    await recruiter.close();
    await candidate.close();
  }
});
