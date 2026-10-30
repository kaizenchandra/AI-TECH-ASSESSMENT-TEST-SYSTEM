package com.assessment.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Store {
  final JdbcTemplate jdbc;
  final ObjectMapper json;

  public Store(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON");
    }
  }

  public <T> T decode(Object o, Class<T> type) {
    try {
      return json.readValue(o.toString(), type);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid stored content", e);
    }
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = jdbc.queryForList(Objects.requireNonNull(sql), args);
    if (rows.isEmpty()) throw new Problem(404, "Resource unavailable");
    return rows.getFirst();
  }

  public void audit(UUID org, UUID actor, String action, UUID resource) {
    jdbc.update(
        "insert into audit_events(organization_id,actor_id,action,resource_id) values (?,?,?,?)",
        org,
        actor,
        action,
        resource);
  }
}
