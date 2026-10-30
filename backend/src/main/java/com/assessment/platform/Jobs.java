package com.assessment.platform;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class Jobs {
  final Store db;
  final Identity identity;

  public Jobs(Store db, Identity identity) {
    this.db = db;
    this.identity = identity;
  }

  public record Skill(String skill, int weight, boolean required, String proficiency) {}

  public record Details(
      String title,
      String department,
      String description,
      String experienceLevel,
      double minYears,
      double maxYears,
      String difficulty,
      int durationMinutes,
      double passingPercent,
      Instant availableFrom,
      Instant latestStart,
      Instant completionDeadline,
      List<Skill> skills) {}

  static void validate(Details d) {
    if (d.title() == null
        || d.title().isBlank()
        || d.title().length() > 200
        || d.department() == null
        || d.department().length() > 120
        || d.description() == null
        || d.description().isBlank()
        || d.description().length() > 20000
        || d.experienceLevel() == null
        || d.experienceLevel().isBlank()
        || d.experienceLevel().length() > 80
        || !Double.isFinite(d.minYears())
        || !Double.isFinite(d.maxYears())
        || d.minYears() < 0
        || d.maxYears() < d.minYears()
        || d.maxYears() > 60
        || d.difficulty() == null
        || !Set.of("FOUNDATION", "INTERMEDIATE", "ADVANCED").contains(d.difficulty())
        || d.durationMinutes() < 1
        || d.durationMinutes() > 480
        || !Double.isFinite(d.passingPercent())
        || d.passingPercent() < 0
        || d.passingPercent() > 100
        || d.availableFrom() == null
        || d.latestStart() == null
        || d.completionDeadline() == null
        || !d.availableFrom().isBefore(d.latestStart())
        || !d.latestStart().isBefore(d.completionDeadline())
        || d.skills() == null
        || d.skills().isEmpty()
        || d.skills().size() > 20)
      throw new Problem(422, "Invalid job details, scoring or availability window");
    int weight = 0;
    Set<String> ids = new HashSet<>();
    for (var s : d.skills()) {
      if (s == null
          || s.skill() == null
          || !AiValidation.SKILLS.contains(s.skill())
          || !ids.add(s.skill())
          || s.weight() < 0
          || s.weight() > 100
          || s.proficiency() == null
          || s.proficiency().isBlank()
          || s.proficiency().length() > 120)
        throw new Problem(422, "Invalid or duplicate skill requirement");
      weight += s.weight();
    }
    if (weight != 100) throw new Problem(422, "Skill weights must total 100%");
  }

  @GetMapping("/skills")
  public List<String> skills() {
    identity.staff();
    return AiValidation.SKILLS.stream().sorted().toList();
  }

  @GetMapping("/jobs")
  public List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String search) {
    var a = identity.staff();
    if (search.length() > 200) throw new Problem(422, "Search too long");
    var rows =
        db.jdbc.queryForList(
            "select * from jobs where organization_id=? and title ilike ? order by created_at"
                + " desc,id limit 25 offset ?",
            a.org(),
            "%" + search + "%",
            HumanReviews.offset(page));
    for (var r : rows)
      r.put(
          "skills",
          db.jdbc.queryForList(
              "select skill,weight,required,proficiency from job_skills where job_id=? order by"
                  + " skill",
              r.get("id")));
    return rows;
  }

  @PostMapping("/jobs")
  @Transactional
  public Map<String, Object> create(@RequestBody Details d) {
    var a = identity.author();
    validate(d);
    UUID id = UUID.randomUUID();
    db.jdbc.update(
        "insert into"
            + " jobs(id,organization_id,author_id,title,department,description,experience_level,min_years,max_years,difficulty,duration_minutes,passing_percent,available_from,latest_start,completion_deadline)"
            + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        a.org(),
        a.id(),
        d.title(),
        d.department(),
        d.description(),
        d.experienceLevel(),
        d.minYears(),
        d.maxYears(),
        d.difficulty(),
        d.durationMinutes(),
        d.passingPercent(),
        Timestamp.from(d.availableFrom()),
        Timestamp.from(d.latestStart()),
        Timestamp.from(d.completionDeadline()));
    saveSkills(id, a.org(), d.skills());
    db.audit(a.org(), a.id(), "JOB_CREATED", id);
    return Map.of("id", id, "revision", 0);
  }

  @PutMapping("/jobs/{id}")
  @Transactional
  public Map<String, Object> edit(
      @PathVariable UUID id, @RequestHeader("If-Match") long revision, @RequestBody Details d) {
    var a = identity.author();
    validate(d);
    var row = db.one("select * from jobs where id=? and organization_id=? for update", id, a.org());
    if (Attempts.number(row, "revision") != revision || !row.get("state").equals("OPEN"))
      throw new Problem(409, "Stale or archived job");
    db.jdbc.update(
        "update jobs set"
            + " title=?,department=?,description=?,experience_level=?,min_years=?,max_years=?,difficulty=?,duration_minutes=?,passing_percent=?,available_from=?,latest_start=?,completion_deadline=?,revision=revision+1,updated_at=now()"
            + " where id=?",
        d.title(),
        d.department(),
        d.description(),
        d.experienceLevel(),
        d.minYears(),
        d.maxYears(),
        d.difficulty(),
        d.durationMinutes(),
        d.passingPercent(),
        Timestamp.from(d.availableFrom()),
        Timestamp.from(d.latestStart()),
        Timestamp.from(d.completionDeadline()),
        id);
    db.jdbc.update("delete from job_skills where job_id=?", id);
    saveSkills(id, a.org(), d.skills());
    db.audit(a.org(), a.id(), "JOB_UPDATED", id);
    return Map.of("id", id, "revision", revision + 1);
  }

  void saveSkills(UUID id, UUID org, List<Skill> skills) {
    for (var s : skills)
      db.jdbc.update(
          "insert into job_skills values (?,?,?,?,?,?)",
          org,
          id,
          s.skill(),
          s.weight(),
          s.required(),
          s.proficiency());
  }

  @PostMapping("/jobs/{id}/archive")
  @Transactional
  public void archive(@PathVariable UUID id, @RequestHeader("If-Match") long revision) {
    var a = identity.author();
    db.one("select id from jobs where id=? and organization_id=?", id, a.org());
    if (db.jdbc.update(
            "update jobs set state='ARCHIVED',revision=revision+1,updated_at=now() where id=? and"
                + " revision=? and state='OPEN'",
            id,
            revision)
        != 1) throw new Problem(409, "Stale or archived job");
    db.audit(a.org(), a.id(), "JOB_ARCHIVED", id);
  }

  public record Link(UUID assessmentId) {}

  @PostMapping("/jobs/{id}/assessments")
  @Transactional
  public void link(@PathVariable UUID id, @RequestBody Link link) {
    var a = identity.author();
    var assessment =
        db.one(
            "select status from assessments where id=? and organization_id=? for update",
            link.assessmentId(),
            a.org());
    if (!assessment.get("status").equals("DRAFT"))
      throw new Problem(409, "Only draft assessments can change job association");
    db.one(
        "select id from jobs where id=? and organization_id=? and state='OPEN' for share",
        id,
        a.org());
    db.jdbc.update(
        "insert into assessment_jobs values (?,?,?) on conflict(assessment_id) do update set"
            + " job_id=excluded.job_id",
        a.org(),
        link.assessmentId(),
        id);
    db.jdbc.update("update assessments set revision=revision+1 where id=?", link.assessmentId());
    db.audit(a.org(), a.id(), "ASSESSMENT_JOB_LINKED", link.assessmentId());
  }

  static Map<String, Object> snapshot(Store db, UUID assessment) {
    var rows =
        db.jdbc.queryForList(
            "select j.id,j.title,j.department,j.experience_level,j.revision,j.state from jobs j"
                + " join assessment_jobs l on l.job_id=j.id and l.organization_id=j.organization_id"
                + " where l.assessment_id=? for share of j",
            assessment);
    if (rows.isEmpty()) return Map.of();
    var row = rows.getFirst();
    if (!row.remove("state").equals("OPEN")) throw new Problem(409, "Linked job is archived");
    row.put(
        "skills",
        db.jdbc.queryForList(
            "select skill,weight,required,proficiency from job_skills where job_id=? order by"
                + " skill",
            row.get("id")));
    return row;
  }
}
