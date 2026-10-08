package edu.mbu.library.attendance;

import edu.mbu.library.attendance.AttendanceService.Summary;
import edu.mbu.library.modules.Subject;
import edu.mbu.library.modules.SubjectRepository;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import edu.mbu.library.web.CsvUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Attendance: faculty take it per subject and date and see a report; students see their percentage per subject.
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final AttendanceService service;
    private final int threshold;

    public AttendanceController(UserRepository users, SubjectRepository subjects, AttendanceService service,
                                @Value("${app.attendance.threshold:75}") int threshold) {
        this.users = users;
        this.subjects = subjects;
        this.service = service;
        this.threshold = threshold;
    }

    // ---------- Pages ----------

    @GetMapping
    public String index(@RequestParam(required = false) Long subjectId, @RequestParam(required = false) String date,
                        Authentication auth, Model model) {
        User me = currentUser(auth);
        model.addAttribute("active", "attendance");
        model.addAttribute("threshold", threshold);

        if (me.getRole() == Role.FACULTY) {
            List<Subject> owned = subjects.findByFacultyOrderBySemesterAscNameAsc(me);
            model.addAttribute("isFaculty", true);
            model.addAttribute("ownedSubjects", owned);
            if (owned.isEmpty()) {
                return "attendance";
            }
            Subject subject = owned.stream().filter(s -> s.getId().equals(subjectId)).findFirst().orElse(owned.get(0));
            LocalDate day = parseDate(date);
            List<User> roster = roster(subject);
            Map<Long, AttendanceStatus> existing = service.statusesOn(subject, day);

            model.addAttribute("subject", subject);
            model.addAttribute("day", day);
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("roster", roster);
            model.addAttribute("existing", existing);
            model.addAttribute("alreadyTaken", !existing.isEmpty());
            model.addAttribute("recentDays", service.recentClassDays(subject, 10));
            return "attendance";
        }

        // Student: percentage per subject of their semester
        List<Subject> semesterSubjects = me.getSemester() == null ? List.of()
                : subjects.findBySemesterOrderByNameAsc(me.getSemester());
        List<AttendanceRecord> mine = service.recordsOf(me, semesterSubjects);
        Map<Long, Summary> summaries = service.summarise(mine);
        Map<Long, List<AttendanceRecord>> bySubject = mine.stream()
                .collect(Collectors.groupingBy(r -> r.getSubject().getId()));
        Summary overall = summaries.values().stream().reduce(Summary.EMPTY, Summary::plus);

        model.addAttribute("isFaculty", false);
        model.addAttribute("subjectList", semesterSubjects);
        model.addAttribute("summaries", summaries);
        model.addAttribute("recordsBySubject", bySubject);
        model.addAttribute("overall", overall);
        model.addAttribute("noSemester", me.getSemester() == null);
        model.addAttribute("emptySummary", Summary.EMPTY);
        return "attendance";
    }

    @GetMapping("/report")
    public String report(@RequestParam Long subjectId, Authentication auth, Model model) {
        Subject subject = ownedSubject(subjectId, auth);
        List<User> roster = roster(subject);
        model.addAttribute("active", "attendance");
        model.addAttribute("subject", subject);
        model.addAttribute("roster", roster);
        model.addAttribute("summaries", service.summariesByStudent(subject));
        model.addAttribute("emptySummary", Summary.EMPTY);
        model.addAttribute("classDays", service.classDays(subject));
        model.addAttribute("threshold", threshold);
        return "attendance-report";
    }

    @GetMapping("/report.csv")
    public ResponseEntity<byte[]> reportCsv(@RequestParam Long subjectId, Authentication auth) {
        Subject subject = ownedSubject(subjectId, auth);
        Map<Long, Summary> summaries = service.summariesByStudent(subject);
        StringBuilder csv = new StringBuilder("Roll no,Name,Present,Absent,Total,Percentage\r\n");
        for (User s : roster(subject)) {
            Summary sum = summaries.getOrDefault(s.getId(), Summary.EMPTY);
            csv.append(cell(s.getRollNo())).append(',').append(cell(s.getFullName())).append(',')
               .append(sum.present()).append(',').append(sum.absent()).append(',')
               .append(sum.total()).append(',').append(sum.percentText()).append("\r\n");
        }
        String fileName = "attendance-" + subject.getCode().replaceAll("[^A-Za-z0-9_-]", "_") + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Taking attendance ----------

    @PostMapping("/mark")
    public String mark(@RequestParam Long subjectId, @RequestParam String date,
                       @RequestParam Map<String, String> params,
                       Authentication auth, RedirectAttributes redirect) {
        Subject subject = ownedSubject(subjectId, auth);
        LocalDate day;
        try {
            day = LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "The date is not valid.");
            return "redirect:/attendance?subjectId=" + subjectId;
        }
        Map<Long, AttendanceStatus> statuses = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!e.getKey().startsWith("status_")) {
                continue;
            }
            try {
                statuses.put(Long.parseLong(e.getKey().substring("status_".length())),
                        AttendanceStatus.valueOf(e.getValue()));
            } catch (IllegalArgumentException ignored) {
                // a malformed field is treated as "no status" and reported below
            }
        }
        try {
            List<User> roster = roster(subject);
            service.mark(subject, day, roster, statuses);
            long absent = roster.stream().filter(s -> statuses.get(s.getId()) == AttendanceStatus.ABSENT).count();
            redirect.addFlashAttribute("message", "Attendance saved for " + subject.getName() + " on " + day
                    + " (" + (roster.size() - absent) + " present, " + absent + " absent).");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/attendance?subjectId=" + subjectId + "&date=" + day;
    }

    // ---------- Helpers ----------

    private List<User> roster(Subject subject) {
        return users.findAllByRoleAndSemesterOrderByRollNoAscFullNameAsc(Role.STUDENT, subject.getSemester());
    }

    private User currentUser(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow();
    }

    private Subject ownedSubject(Long id, Authentication auth) {
        Subject subject = subjects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User me = currentUser(auth);
        if (me.getRole() != Role.FACULTY || !subject.getFaculty().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the subject's faculty can do this");
        }
        return subject;
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    private static String cell(String value) {
        return CsvUtil.cell(value);
    }
}
