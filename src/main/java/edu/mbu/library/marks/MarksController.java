package edu.mbu.library.marks;

import edu.mbu.library.assignments.AssignmentSubmission;
import edu.mbu.library.assignments.AssignmentSubmissionRepository;
import edu.mbu.library.marks.MarksService.SubjectResult;
import edu.mbu.library.modules.Subject;
import edu.mbu.library.modules.SubjectRepository;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import edu.mbu.library.web.CsvUtil;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Marks: faculty define the assessments of their subjects and enter marks in a grid;
 * students see their marks, totals and grades per subject.
 */
@Controller
@RequestMapping("/marks")
public class MarksController {

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final AssessmentRepository assessmentRepo;
    private final MarksService service;
    private final AssignmentSubmissionRepository assignmentSubmissions;

    public MarksController(UserRepository users, SubjectRepository subjects, AssessmentRepository assessmentRepo,
                           MarksService service, AssignmentSubmissionRepository assignmentSubmissions) {
        this.users = users;
        this.subjects = subjects;
        this.assessmentRepo = assessmentRepo;
        this.service = service;
        this.assignmentSubmissions = assignmentSubmissions;
    }

    // ---------- Pages ----------

    @GetMapping
    public String index(@RequestParam(required = false) Long subjectId, Authentication auth, Model model) {
        User me = currentUser(auth);
        model.addAttribute("active", "marks");

        if (me.getRole() == Role.FACULTY) {
            List<Subject> owned = subjects.findByFacultyOrderBySemesterAscNameAsc(me);
            model.addAttribute("isFaculty", true);
            model.addAttribute("ownedSubjects", owned);
            if (owned.isEmpty()) {
                return "marks";
            }
            Subject subject = owned.stream().filter(s -> s.getId().equals(subjectId)).findFirst().orElse(owned.get(0));
            List<Assessment> list = service.assessmentsOf(subject);
            List<User> roster = roster(subject);
            Map<String, BigDecimal> grid = service.gridOf(list);
            Map<Long, SubjectResult> results = new HashMap<>();
            for (User s : roster) {
                results.put(s.getId(), service.resultFor(subject, list, grid, s.getId()));
            }
            model.addAttribute("subject", subject);
            model.addAttribute("assessmentList", list);
            model.addAttribute("totalMax", list.stream().mapToInt(Assessment::getMaxMarks).sum());
            model.addAttribute("roster", roster);
            model.addAttribute("grid", grid);
            model.addAttribute("results", results);
            return "marks";
        }

        // Student: marks for the subjects of their semester
        List<Subject> semesterSubjects = me.getSemester() == null ? List.of()
                : subjects.findBySemesterOrderByNameAsc(me.getSemester());
        List<Assessment> all = semesterSubjects.isEmpty() ? List.of()
                : assessmentRepo.findBySubjectInOrderByPositionAscIdAsc(semesterSubjects);
        Map<Long, List<Assessment>> bySubject = all.stream()
                .collect(Collectors.groupingBy(a -> a.getSubject().getId()));
        Map<Long, SubjectResult> results = service.resultsOf(me, semesterSubjects).stream()
                .collect(Collectors.toMap(r -> r.subject().getId(), r -> r));
        Map<String, BigDecimal> myMarks = new HashMap<>();
        for (Subject s : semesterSubjects) {
            myMarks.putAll(service.gridOf(bySubject.getOrDefault(s.getId(), List.of())).entrySet().stream()
                    .filter(e -> e.getKey().endsWith("-" + me.getId()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        // Graded assignments are shown for information; they are not part of the subject total.
        Set<Long> subjectIds = semesterSubjects.stream().map(Subject::getId).collect(Collectors.toSet());
        Map<Long, List<AssignmentSubmission>> assignmentMarks = assignmentSubmissions.findByStudent(me).stream()
                .filter(AssignmentSubmission::isGraded)
                .filter(s -> subjectIds.contains(s.getAssignment().getSubject().getId()))
                .collect(Collectors.groupingBy(s -> s.getAssignment().getSubject().getId()));

        model.addAttribute("isFaculty", false);
        model.addAttribute("subjectList", semesterSubjects);
        model.addAttribute("assessmentsBySubject", bySubject);
        model.addAttribute("results", results);
        model.addAttribute("myMarks", myMarks);
        model.addAttribute("meId", me.getId());
        model.addAttribute("assignmentMarks", assignmentMarks);
        model.addAttribute("noSemester", me.getSemester() == null);
        return "marks";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam Long subjectId, Authentication auth) {
        Subject subject = ownedSubject(subjectId, auth);
        List<Assessment> list = service.assessmentsOf(subject);
        Map<String, BigDecimal> grid = service.gridOf(list);

        StringBuilder csv = new StringBuilder("Roll no,Name");
        for (Assessment a : list) {
            csv.append(',').append(CsvUtil.cell(a.getName() + " (" + a.getMaxMarks() + ")"));
        }
        csv.append(",Total,Out of,Percentage,Grade,Status\r\n");

        for (User s : roster(subject)) {
            SubjectResult r = service.resultFor(subject, list, grid, s.getId());
            csv.append(CsvUtil.cell(s.getRollNo())).append(',').append(CsvUtil.cell(s.getFullName()));
            for (Assessment a : list) {
                BigDecimal m = grid.get(a.getId() + "-" + s.getId());
                csv.append(',').append(m == null ? "" : m.stripTrailingZeros().toPlainString());
            }
            csv.append(',').append(r.hasMarks() ? r.obtainedText() : "")
               .append(',').append(r.hasMarks() ? r.maxOfEntered() : "")
               .append(',').append(r.hasMarks() ? r.percentText() : "")
               .append(',').append(r.hasMarks() ? r.grade().letter() : "")
               .append(',').append(!r.hasMarks() ? "No marks" : r.complete() ? (r.grade().pass() ? "Pass" : "Fail") : "Provisional")
               .append("\r\n");
        }
        String fileName = "marks-" + subject.getCode().replaceAll("[^A-Za-z0-9_-]", "_") + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Changes (faculty who own the subject) ----------

    @PostMapping("/assessments")
    public String addAssessment(@RequestParam Long subjectId, @RequestParam String name,
                                @RequestParam(required = false) Integer maxMarks,
                                Authentication auth, RedirectAttributes redirect) {
        Subject subject = ownedSubject(subjectId, auth);
        try {
            Assessment a = service.addAssessment(subject, name, maxMarks);
            redirect.addFlashAttribute("message", "Added '" + a.getName() + "' (out of " + a.getMaxMarks() + ").");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/marks?subjectId=" + subjectId;
    }

    @PostMapping("/assessments/{id}/delete")
    public String deleteAssessment(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        Assessment a = assessmentRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Subject subject = ownedSubject(a.getSubject().getId(), auth);
        service.deleteAssessment(a);
        redirect.addFlashAttribute("message", "Deleted '" + a.getName() + "' and the marks entered for it.");
        return "redirect:/marks?subjectId=" + subject.getId();
    }

    @PostMapping("/save")
    public String save(@RequestParam Long subjectId, @RequestParam Map<String, String> params,
                       Authentication auth, RedirectAttributes redirect) {
        Subject subject = ownedSubject(subjectId, auth);
        Map<String, String> input = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            // form fields are named m_<assessmentId>_<studentId>
            if (e.getKey().startsWith("m_")) {
                String[] parts = e.getKey().substring(2).split("_");
                if (parts.length == 2) {
                    input.put(parts[0] + "-" + parts[1], e.getValue());
                }
            }
        }
        try {
            int saved = service.saveGrid(service.assessmentsOf(subject), roster(subject), input);
            redirect.addFlashAttribute("message", "Marks saved (" + saved + " entries).");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/marks?subjectId=" + subjectId;
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
}
