package edu.mbu.library.attendance;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Rules for taking attendance and calculating percentages. Failures are IllegalArgumentExceptions with a friendly message. */
@Service
public class AttendanceService {

    /** How many days back attendance may still be entered or corrected. */
    private static final int MAX_DAYS_BACK = 400;

    /** Present / absent counts, with the percentage worked out from them. */
    public record Summary(long present, long absent) {
        public static final Summary EMPTY = new Summary(0, 0);

        public long total() { return present + absent; }

        /** Percentage of classes attended, or 0 when nothing has been recorded. */
        public double percent() { return total() == 0 ? 0 : present * 100.0 / total(); }

        public String percentText() { return String.format(Locale.ROOT, "%.1f", percent()); }

        /** Whole-number width for the progress bar. */
        public int barWidth() { return (int) Math.round(Math.min(100, percent())); }

        public Summary plus(Summary other) { return new Summary(present + other.present, absent + other.absent); }
    }

    private final AttendanceRepository records;

    public AttendanceService(AttendanceRepository records) {
        this.records = records;
    }

    /** Saves (or corrects) attendance for a subject on a day. Every student of the roster needs a status. */
    @Transactional
    public void mark(Subject subject, LocalDate date, List<User> roster, Map<Long, AttendanceStatus> statuses) {
        validateDate(date);
        if (roster.isEmpty()) {
            throw new IllegalArgumentException("There are no students in semester " + subject.getSemester() + " yet.");
        }
        Map<Long, AttendanceRecord> existing = records.findBySubjectAndClassDate(subject, date).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r));

        List<AttendanceRecord> toSave = new ArrayList<>();
        for (User student : roster) {
            AttendanceStatus status = statuses.get(student.getId());
            if (status == null) {
                throw new IllegalArgumentException("Choose Present or Absent for " + student.getFullName() + ".");
            }
            AttendanceRecord r = existing.get(student.getId());
            if (r == null) {
                r = new AttendanceRecord();
                r.setSubject(subject);
                r.setStudent(student);
                r.setClassDate(date);
            }
            r.setStatus(status);
            toSave.add(r);
        }
        records.saveAll(toSave);
    }

    /** Present / absent totals per student for one subject. */
    public Map<Long, Summary> summariesByStudent(Subject subject) {
        Map<Long, long[]> counts = new HashMap<>();
        for (AttendanceRecord r : records.findBySubject(subject)) {
            long[] c = counts.computeIfAbsent(r.getStudent().getId(), k -> new long[2]);
            c[r.getStatus() == AttendanceStatus.PRESENT ? 0 : 1]++;
        }
        Map<Long, Summary> result = new HashMap<>();
        counts.forEach((id, c) -> result.put(id, new Summary(c[0], c[1])));
        return result;
    }

    /** One student's records for the given subjects, newest first. */
    public List<AttendanceRecord> recordsOf(User student, Collection<Subject> subjects) {
        return subjects.isEmpty() ? List.of()
                : records.findByStudentAndSubjectInOrderByClassDateDesc(student, subjects);
    }

    public Map<Long, Summary> summarise(List<AttendanceRecord> list) {
        Map<Long, Summary> result = new HashMap<>();
        for (AttendanceRecord r : list) {
            Summary add = r.getStatus() == AttendanceStatus.PRESENT ? new Summary(1, 0) : new Summary(0, 1);
            result.merge(r.getSubject().getId(), add, Summary::plus);
        }
        return result;
    }

    public long classDays(Subject subject) {
        return records.countClassDays(subject);
    }

    public List<LocalDate> recentClassDays(Subject subject, int limit) {
        return records.findClassDays(subject).stream().limit(limit).toList();
    }

    public Map<Long, AttendanceStatus> statusesOn(Subject subject, LocalDate date) {
        return records.findBySubjectAndClassDate(subject, date).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), AttendanceRecord::getStatus));
    }

    /** Removes all attendance of a subject; used when the subject is deleted. */
    @Transactional
    public void deleteAllForSubject(Subject subject) {
        records.deleteAll(records.findBySubject(subject));
    }

    /** Removes a student's attendance; used when the student's account is deleted. */
    @Transactional
    public void deleteAllForStudent(User student) {
        records.deleteAll(records.findByStudent(student));
    }

    private static void validateDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date == null) {
            throw new IllegalArgumentException("Choose a date.");
        }
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Attendance cannot be taken for a future date.");
        }
        if (date.isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw new IllegalArgumentException("That date is too far in the past.");
        }
    }
}
