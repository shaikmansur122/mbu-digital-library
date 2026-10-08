package edu.mbu.library.labs;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.modules.SubjectRepository;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lab Experiments: faculty assign them per subject, students solve and submit them in the code editor,
 * faculty see who has submitted and can open each student's code.
 */
@Controller
@RequestMapping("/labs")
public class LabsController {

    public record RunRequest(String language, String code, String stdin) {}

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final LabExperimentRepository experiments;
    private final LabSubmissionRepository submissions;
    private final LabService service;
    private final CodeRunnerService runner;

    public LabsController(UserRepository users, SubjectRepository subjects, LabExperimentRepository experiments,
                          LabSubmissionRepository submissions, LabService service, CodeRunnerService runner) {
        this.users = users;
        this.subjects = subjects;
        this.experiments = experiments;
        this.submissions = submissions;
        this.service = service;
        this.runner = runner;
    }

    // ---------- Lists and detail pages ----------

    @GetMapping
    public String list(Authentication auth, Model model) {
        User me = currentUser(auth);
        model.addAttribute("active", "labs");

        if (me.getRole() == Role.FACULTY) {
            List<LabExperiment> list = experiments.findBySubjectFacultyOrderByCreatedAtDesc(me);
            Map<Long, Long> submitted = new HashMap<>();
            Map<Long, Long> classSize = new HashMap<>();
            for (LabExperiment e : list) {
                submitted.put(e.getId(), submissions.countByExperiment(e));
                classSize.put(e.getId(), users.countByRoleAndSemester(Role.STUDENT, e.getSubject().getSemester()));
            }
            model.addAttribute("isFaculty", true);
            model.addAttribute("experimentList", list);
            model.addAttribute("submittedCounts", submitted);
            model.addAttribute("classSizes", classSize);
            model.addAttribute("ownedSubjects", subjects.findByFacultyOrderBySemesterAscNameAsc(me));
            model.addAttribute("languages", service.languages());
            return "labs";
        }

        // Student: experiments of the subjects in their own semester
        List<Subject> semesterSubjects = me.getSemester() == null ? List.of()
                : subjects.findBySemesterOrderByNameAsc(me.getSemester());
        List<LabExperiment> list = semesterSubjects.isEmpty() ? List.of()
                : experiments.findBySubjectInOrderByCreatedAtDesc(semesterSubjects);
        Map<Long, LabSubmission> mine = list.isEmpty() ? Map.of()
                : submissions.findByStudentAndExperimentIn(me, list).stream()
                    .collect(Collectors.toMap(s -> s.getExperiment().getId(), Function.identity()));
        model.addAttribute("isFaculty", false);
        model.addAttribute("experimentList", list);
        model.addAttribute("mySubmissions", mine);
        model.addAttribute("noSemester", me.getSemester() == null);
        return "labs";
    }

    @GetMapping("/{id}")
    public String experiment(@PathVariable Long id, Authentication auth, Model model) {
        User me = currentUser(auth);
        LabExperiment e = findExperiment(id);
        model.addAttribute("active", "labs");
        model.addAttribute("experiment", e);

        if (me.getRole() == Role.FACULTY) {
            requireOwner(me, e);
            List<LabSubmission> subs = submissions.findByExperiment(e);
            Map<Long, LabSubmission> byStudent = subs.stream()
                    .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));
            List<User> roster = users.findAllByRoleAndSemesterOrderByRollNoAscFullNameAsc(
                    Role.STUDENT, e.getSubject().getSemester());
            model.addAttribute("isFaculty", true);
            model.addAttribute("roster", roster);
            model.addAttribute("submissionByStudent", byStudent);
            model.addAttribute("submittedCount", subs.size());
            return "lab";
        }

        requireStudentOfSemester(me, e);
        LabSubmission mine = submissions.findByExperimentAndStudent(e, me).orElse(null);
        model.addAttribute("isFaculty", false);
        model.addAttribute("submission", mine);
        model.addAttribute("initialCode", mine != null ? mine.getCode()
                : (e.getStarterCode() == null ? "" : e.getStarterCode()));
        return "lab";
    }

    /** Faculty read a student's submitted code. */
    @GetMapping("/{id}/submissions/{submissionId}")
    public String submission(@PathVariable Long id, @PathVariable Long submissionId, Authentication auth, Model model) {
        User me = currentUser(auth);
        LabExperiment e = findExperiment(id);
        requireOwner(me, e);
        LabSubmission s = submissions.findById(submissionId)
                .filter(x -> x.getExperiment().getId().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("active", "labs");
        model.addAttribute("experiment", e);
        model.addAttribute("submission", s);
        return "lab-submission";
    }

    // ---------- Changes ----------

    @PostMapping
    public String create(@RequestParam Long subjectId, @RequestParam String title,
                         @RequestParam(required = false) String description, @RequestParam Language language,
                         @RequestParam(required = false) String starterCode,
                         @RequestParam(required = false) String dueDate,
                         Authentication auth, RedirectAttributes redirect) {
        User me = currentUser(auth);
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!subject.getFaculty().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only assign labs for your own subjects");
        }
        try {
            LabExperiment e = service.create(subject, title, description, language, starterCode, dueDate);
            redirect.addFlashAttribute("message", "Lab experiment '" + e.getTitle() + "' assigned to semester "
                    + subject.getSemester() + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/labs";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        LabExperiment e = findExperiment(id);
        requireOwner(currentUser(auth), e);
        service.delete(e);
        redirect.addFlashAttribute("message", "Lab experiment '" + e.getTitle() + "' deleted.");
        return "redirect:/labs";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, @RequestParam String code,
                         Authentication auth, RedirectAttributes redirect) {
        User me = currentUser(auth);
        LabExperiment e = findExperiment(id);
        requireStudentOfSemester(me, e);
        try {
            LabSubmission s = service.submit(e, me, code);
            redirect.addFlashAttribute("message", s.isLate()
                    ? "Submitted (after the due date)." : "Submitted successfully.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/labs/" + id;
    }

    // ---------- Running code (Java, C, C++ only; Python and SQL run in the browser) ----------

    @PostMapping("/run")
    @ResponseBody
    public CodeRunnerService.Result run(@RequestBody RunRequest request) {
        Language language;
        try {
            language = Language.valueOf(request.language() == null ? "" : request.language());
        } catch (IllegalArgumentException ex) {
            return new CodeRunnerService.Result("", "", null, false, false, "Unknown language.");
        }
        String code = request.code() == null ? "" : request.code();
        String stdin = request.stdin() == null ? "" : request.stdin();
        if (code.isBlank()) {
            return new CodeRunnerService.Result("", "", null, false, false, "There is no code to run.");
        }
        if (code.length() > LabService.MAX_CODE_CHARS || stdin.length() > 10_000) {
            return new CodeRunnerService.Result("", "", null, false, false, "The code or input is too long.");
        }
        return runner.run(language, code, stdin);
    }

    // ---------- Helpers ----------

    private User currentUser(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow();
    }

    private LabExperiment findExperiment(Long id) {
        return experiments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void requireOwner(User user, LabExperiment e) {
        if (user.getRole() != Role.FACULTY || !e.getSubject().getFaculty().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the subject's faculty can do this");
        }
    }

    private static void requireStudentOfSemester(User user, LabExperiment e) {
        if (user.getRole() != Role.STUDENT || user.getSemester() == null
                || user.getSemester() != e.getSubject().getSemester()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This lab is not assigned to you");
        }
    }
}
