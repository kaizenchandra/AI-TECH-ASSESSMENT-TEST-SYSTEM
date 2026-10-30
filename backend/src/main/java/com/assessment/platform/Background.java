package com.assessment.platform;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Background {
  final Store db;
  final Attempts attempts;
  final JavaMailSender mail;
  final String from;

  public Background(
      Store db, Attempts attempts, JavaMailSender mail, @Value("${app.mail-from}") String from) {
    this.db = db;
    this.attempts = attempts;
    this.mail = mail;
    this.from = from;
  }

  @Scheduled(fixedDelay = 5000)
  public void expire() {
    for (var r :
        db.jdbc.queryForList(
            "select id,candidate_id from assessment_attempts where status='IN_PROGRESS' order by"
                + " accounted_at limit 100")) {
      try {
        attempts.expire((UUID) r.get("id"), (UUID) r.get("candidate_id"));
      } catch (RuntimeException ignored) {
        /* Next durable scan retries; payloads intentionally not logged. */
      }
    }
  }

  @Scheduled(fixedDelay = 3000)
  public void email() {
    var rows =
        db.jdbc.queryForList(
            "update notifications set"
                + " state='SENDING',attempts=attempts+1,available_at=now()+interval '60 seconds'"
                + " where id in (select id from notifications where state in ('QUEUED','SENDING')"
                + " and attempts<5 and available_at<=now() order by created_at for update skip"
                + " locked limit 10) returning *");
    for (var row : rows) {
      try {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo((String) row.get("recipient"));
        message.setSubject((String) row.get("subject"));
        message.setText((String) row.get("body"));
        mail.send(message);
        db.jdbc.update("update notifications set state='SENT',body='' where id=?", row.get("id"));
      } catch (RuntimeException ex) {
        db.jdbc.update(
            "update notifications set state=case when attempts>=5 then 'FAILED' else 'QUEUED' end"
                + " where id=?",
            row.get("id"));
      }
    }
  }
}
