package com.assessment.platform;

import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CodingWorker {
  final Store db;
  final CodingService coding;
  final CodeRunner runner;
  final TransactionTemplate tx;

  public CodingWorker(Store db, CodingService coding, CodeRunner runner, TransactionTemplate tx) {
    this.db = db;
    this.coding = coding;
    this.runner = runner;
    this.tx = tx;
  }

  @Scheduled(fixedDelay = 2000, initialDelayString = "${app.coding.initial-delay-ms:0}")
  public void tick() {
    db.jdbc.update(
        "update code_execution_jobs set"
            + " state='FAILED',failure_code='RETRY_EXHAUSTED',lease_token=null where"
            + " state='RUNNING' and lease_until<now() and attempts>=3");
    UUID lease = UUID.randomUUID();
    var jobs =
        db.jdbc.queryForList(
            "update code_execution_jobs set"
                + " state='RUNNING',attempts=attempts+1,lease_token=?,lease_until=now()+interval"
                + " '660 seconds' where id in (select id from code_execution_jobs where ((state in"
                + " ('QUEUED','RETRY_WAIT') and available_at<=now()) or (state='RUNNING' and"
                + " lease_until<now())) and attempts<3 order by created_at for update skip locked"
                + " limit 1) returning *",
            lease);
    if (jobs.isEmpty()) return;
    var job = jobs.getFirst();
    var payload = db.decode(job.get("payload"), CodingService.Payload.class);
    Map<String, Object> result;
    try {
      var output =
          runner.execute(
              new CodeRunner.Input(
                  (UUID) job.get("id"),
                  payload.language(),
                  payload.source(),
                  payload.tests().stream().map(t -> t.input()).toList(),
                  payload.timeoutMs(),
                  payload.memoryMb()));
      double fraction = CodeRunner.fraction(output, payload.tests());
      int passed = 0;
      for (int i = 0; i < payload.tests().size(); i++)
        if (output.cases().get(i).status().equals("OK")
            && CodeRunner.normalize(output.cases().get(i).stdout())
                .equals(CodeRunner.normalize(payload.tests().get(i).output()))) passed++;
      result =
          new LinkedHashMap<>(
              Map.of(
                  "fraction",
                  fraction,
                  "passedTests",
                  passed,
                  "totalTests",
                  payload.tests().size()));
      if (job.get("purpose").equals("SAMPLE")) result.put("cases", output.cases());
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      db.jdbc.update(
          "update code_execution_jobs set state=case when attempts<3 then 'RETRY_WAIT' else"
              + " 'FAILED'"
              + " end,failure_code='RUNNER_UNAVAILABLE_OR_INVALID_EVIDENCE',available_at=now()+interval"
              + " '30 seconds',lease_token=null where id=? and state='RUNNING' and lease_token=?",
          job.get("id"),
          lease);
      return;
    }
    tx.executeWithoutResult(
        status -> {
          int applied =
              db.jdbc.update(
                  "update code_execution_jobs set"
                      + " state='SUCCEEDED',result=?::jsonb,completed_at=now(),failure_code=null,lease_token=null"
                      + " where id=? and state='RUNNING' and lease_token=?",
                  db.encode(result),
                  job.get("id"),
                  lease);
          if (applied == 1 && job.get("purpose").equals("GRADE"))
            coding.updateEvaluation((UUID) job.get("attempt_id"));
        });
  }
}
