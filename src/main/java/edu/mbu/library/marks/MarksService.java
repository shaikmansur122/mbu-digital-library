package edu.mbu.library.marks;

import edu.mbu.library.marks.GradeScale.Grade;
import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Rules for assessments, marks entry and the total / percentage / grade calculations. */
@Service
public class MarksService {

    /** A student's standing in one subject. Totals only cover the assessments entered so far. */
    public record SubjectResult(Subject subject, int entered, int totalAssessments, BigDecimal obtained,
                                int maxOfEntered, double percent, Grade grade) {

        /** True when every assessment of the subject has marks, so the grade is final. */
        public boolean complete() { return totalAssessments > 0 && entered == totalAssessments; }

        public boolean hasMarks() { return entered > 0; }

        public String percentText() { return String.format(Locale.ROOT, "%.1f", percent); }

        public String obtainedText() { return obtained.stripTrailingZeros().toPlainString(); }
    }

    private final AssessmentRepository assessments;
    private final MarkEntryRepository entries;

    public MarksService(AssessmentRepository assessments, MarkEntryRepository entries) {
        this.assessments = assessments;
        this.entries = entries;
    }

    // ---------- Assessments ----------

    @Transactional
    public Assessment addAssessment(Subject subject, String name, Integer maxMarks) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || name.length() > 60) {
            throw new IllegalArgumentException("Assessment name is required (up to 60 characters).");
        }
        if (maxMarks == null || maxMarks < 1 || maxMarks > 200) {
            throw new IllegalArgumentException("Maximum marks must be between 1 and 200.");
        }
        if (assessments.existsBySubjectAndNameIgnoreCase(subject, name)) {
            throw new IllegalArgumentException("This subject already has an assessment called '" + name + "'.");
        }
        Assessment a = new Assessment();
        a.setSubject(subject);
        a.setName(name);
        a.setMaxMarks(maxMarks);
        a.setPosition((int) assessments.countBySubject(subject) + 1);
        return assessments.save(a);
    }

    @Transactional
    public void deleteAssessment(Assessment assessment) {
        entries.deleteAll(entries.findByAssessment(assessment));
        assessments.delete(assessment);
    }

    // ---------- Entering marks ----------

    /**
     * Saves the whole grid at once. {@code input} maps "assessmentId-studentId" to the typed text; blank text
     * clears that entry. Nothing is saved if any value is invalid.
     */
    @Transactional
    public int saveGrid(List<Assessment> subjectAssessments, List<User> roster, Map<String, String> input) {
        Map<Long, User> rosterById = roster.stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<String, MarkEntry> existing = new HashMap<>();
        for (MarkEntry e : entries.findByAssessmentIn(subjectAssessments)) {
            existing.put(e.getAssessment().getId() + "-" + e.getStudent().getId(), e);
        }

        List<MarkEntry> toSave = new ArrayList<>();
        List<MarkEntry> toDelete = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (Assessment a : subjectAssessments) {
            for (User s : roster) {
                String key = a.getId() + "-" + s.getId();
                if (!input.containsKey(key)) {
                    continue;   // field not part of the form: leave as is
                }
                String text = input.get(key) == null ? "" : input.get(key).trim();
                MarkEntry current = existing.get(key);
                if (text.isEmpty()) {
                    if (current != null) {
                        toDelete.add(current);
                    }
                    continue;
                }
                BigDecimal value;
                try {
                    value = new BigDecimal(text);
                } catch (NumberFormatException e) {
                    problems.add(s.getFullName() + " / " + a.getName() + ": '" + text + "' is not a number");
                    continue;
                }
                if (value.scale() > 2) {
                    problems.add(s.getFullName() + " / " + a.getName() + ": use at most 2 decimal places");
                } else if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(a.getMaxMarks())) > 0) {
                    problems.add(s.getFullName() + " / " + a.getName() + ": must be between 0 and " + a.getMaxMarks());
                } else {
                    MarkEntry entry = current;
                    if (entry == null) {
                        entry = new MarkEntry();
                        entry.setAssessment(a);
                        entry.setStudent(rosterById.get(s.getId()));
                    }
                    entry.setMarks(value.setScale(2, RoundingMode.UNNECESSARY));
                    toSave.add(entry);
                }
            }
        }
        if (!problems.isEmpty()) {
            String shown = String.join("; ", problems.stream().limit(3).toList());
            throw new IllegalArgumentException("Nothing was saved. " + shown
                    + (problems.size() > 3 ? " (and " + (problems.size() - 3) + " more)" : "") + ".");
        }
        entries.deleteAll(toDelete);
        entries.saveAll(toSave);
        return toSave.size();
    }

    // ---------- Reading ----------

    public List<Assessment> assessmentsOf(Subject subject) {
        return assessments.findBySubjectOrderByPositionAscIdAsc(subject);
    }

    /** All marks of a subject keyed "assessmentId-studentId". */
    public Map<String, BigDecimal> gridOf(List<Assessment> subjectAssessments) {
        Map<String, BigDecimal> grid = new HashMap<>();
        if (subjectAssessments.isEmpty()) {
            return grid;
        }
        for (MarkEntry e : entries.findByAssessmentIn(subjectAssessments)) {
            grid.put(e.getAssessment().getId() + "-" + e.getStudent().getId(), e.getMarks());
        }
        return grid;
    }

    /** Works out one student's result in a subject from the grid of marks. */
    public SubjectResult resultFor(Subject subject, List<Assessment> subjectAssessments,
                                   Map<String, BigDecimal> grid, Long studentId) {
        BigDecimal obtained = BigDecimal.ZERO;
        int maxEntered = 0;
        int entered = 0;
        for (Assessment a : subjectAssessments) {
            BigDecimal m = grid.get(a.getId() + "-" + studentId);
            if (m != null) {
                obtained = obtained.add(m);
                maxEntered += a.getMaxMarks();
                entered++;
            }
        }
        double percent = maxEntered == 0 ? 0 : obtained.doubleValue() * 100.0 / maxEntered;
        return new SubjectResult(subject, entered, subjectAssessments.size(), obtained, maxEntered, percent,
                GradeScale.of(percent));
    }

    /** One student's results for the given subjects. */
    public List<SubjectResult> resultsOf(User student, List<Subject> subjects) {
        if (subjects.isEmpty()) {
            return List.of();
        }
        List<Assessment> all = assessments.findBySubjectInOrderByPositionAscIdAsc(subjects);
        Map<Long, List<Assessment>> bySubject = all.stream().collect(Collectors.groupingBy(a -> a.getSubject().getId()));
        Map<String, BigDecimal> grid = new HashMap<>();
        if (!all.isEmpty()) {
            for (MarkEntry e : entries.findByStudentAndAssessmentIn(student, all)) {
                grid.put(e.getAssessment().getId() + "-" + student.getId(), e.getMarks());
            }
        }
        List<SubjectResult> results = new ArrayList<>();
        for (Subject s : subjects) {
            results.add(resultFor(s, bySubject.getOrDefault(s.getId(), List.of()), grid, student.getId()));
        }
        return results;
    }

    /** Subjects in which the student has at least one mark (any semester). */
    public List<Subject> subjectsWithMarks(User student) {
        // De-duplicate by id: the same subject comes back as different Java objects from different rows.
        Map<Long, Subject> byId = new LinkedHashMap<>();
        for (MarkEntry e : entries.findByStudent(student)) {
            Subject s = e.getAssessment().getSubject();
            byId.putIfAbsent(s.getId(), s);
        }
        return new ArrayList<>(byId.values());
    }

    /** Credit-weighted average of grade points over the subjects whose grades are final, or null if there are none. */
    public static Double gpa(Collection<SubjectResult> results) {
        int credits = 0;
        int weighted = 0;
        for (SubjectResult r : results) {
            if (r.complete()) {
                credits += r.subject().getCreditsOrDefault();
                weighted += r.grade().points() * r.subject().getCreditsOrDefault();
            }
        }
        return credits == 0 ? null : weighted * 1.0 / credits;
    }

    // ---------- Clean-up ----------

    @Transactional
    public void deleteAllForSubject(Subject subject) {
        for (Assessment a : assessments.findBySubjectOrderByPositionAscIdAsc(subject)) {
            deleteAssessment(a);
        }
    }

    @Transactional
    public void deleteAllForStudent(User student) {
        entries.deleteAll(entries.findByStudent(student));
    }
}
