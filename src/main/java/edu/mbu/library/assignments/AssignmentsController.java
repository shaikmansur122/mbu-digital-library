package edu.mbu.library.assignments;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.modules.SubjectRepository;
import edu.mbu.library.storage.FileStorageService;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assignments: faculty post them per subject, students upload their work, faculty download, grade and give feedback.
 * Uploaded files are private: only the student who submitted and the subject's faculty can download them.
 */
@Controller
@RequestMapping("/assignments")
public class AssignmentsController {

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final AssignmentService service;
    private final FileStorageService storage;

    public AssignmentsController(UserRepository users, SubjectRepository subjects, AssignmentRepository assignments,
                                 AssignmentSubmissionRepository submissions, AssignmentService service,
                                 FileStorageService storage) {
        this.users = users;
        this.subjects = subjects;
        this.assignments = assignments;
        this.submissions = submissions;
        this.service = service;
        this.storage = storage;
    }

    // ---------- Pages ----------

    @GetMapping
    public String list(Authentication auth, Model model) {
        User me = currentUser(auth);
        model.addAttribute("active", "assignments");

        if (me.getRole() == Role.FACULTY) {
            List<Assignment> list = assignments.findBySubjectFacultyOrderByCreatedAtDesc(me);
            Map<Long, Long> submitted = new HashMap<>();
            Map<Long, Long> classSize = new HashMap<>();
            for (Assignment a : list) {
                submitted.put(a.getId(), submissions.countByAssignment(a));
                classSize.put(a.getId(), users.countByRoleAndSemester(Role.STUDENT, a.getSubject().getSemester()));
            }
            model.addAttribute("isFaculty", true);
            model.addAttribute("assignmentList", list);
            model.addAttribute("submittedCounts", submitted);
            model.addAttribute("classSizes", classSize);
            model.addAttribute("ownedSubjects", subjects.findByFacultyOrderBySemesterAscNameAsc(me));
            return "assignments";
        }

        List<Subject> semesterSubjects = me.getSemester() == null ? List.of()
                : subjects.findBySemesterOrderByNameAsc(me.getSemester());
        List<Assignment> list = semesterSubjects.isEmpty() ? List.of()
                : assignments.findBySubjectInOrderByCreatedAtDesc(semesterSubjects);
        Map<Long, AssignmentSubmission> mine = list.isEmpty() ? Map.of()
                : submissions.findByStudentAndAssignmentIn(me, list).stream()
                    .collect(Collectors.toMap(s -> s.getAssignment().getId(), Function.identity()));
        model.addAttribute("isFaculty", false);
        model.addAttribute("assignmentList", list);
        model.addAttribute("mySubmissions", mine);
        model.addAttribute("noSemester", me.getSemester() == null);
        return "assignments";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        User me = currentUser(auth);
        Assignment a = findAssignment(id);
        model.addAttribute("active", "assignments");
        model.addAttribute("assignment", a);

        if (me.getRole() == Role.FACULTY) {
            requireOwner(me, a);
            List<AssignmentSubmission> subs = submissions.findByAssignment(a);
            Map<Long, AssignmentSubmission> byStudent = subs.stream()
                    .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));
            model.addAttribute("isFaculty", true);
            model.addAttribute("roster", users.findAllByRoleAndSemesterOrderByRollNoAscFullNameAsc(
                    Role.STUDENT, a.getSubject().getSemester()));
            model.addAttribute("submissionByStudent", byStudent);
            model.addAttribute("submittedCount", subs.size());
            model.addAttribute("gradedCount", subs.stream().filter(AssignmentSubmission::isGraded).count());
            return "assignment";
        }

        requireStudentOfSemester(me, a);
        model.addAttribute("isFaculty", false);
        model.addAttribute("submission", submissions.findByAssignmentAndStudent(a, me).orElse(null));
        return "assignment";
    }

    // ---------- Changes ----------

    @PostMapping
    public String create(@RequestParam Long subjectId, @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String dueDate,
                         @RequestParam(required = false) Integer maxMarks,
                         @RequestParam(required = false) MultipartFile attachment,
                         Authentication auth, RedirectAttributes redirect) {
        User me = currentUser(auth);
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!subject.getFaculty().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only post assignments for your own subjects");
        }
        try {
            Assignment a = service.create(subject, title, description, dueDate, maxMarks, attachment);
            redirect.addFlashAttribute("message", "Assignment '" + a.getTitle() + "' posted for semester "
                    + subject.getSemester() + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Could not save the file. Please try again.");
        }
        return "redirect:/assignments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        Assignment a = findAssignment(id);
        requireOwner(currentUser(auth), a);
        service.delete(a);
        redirect.addFlashAttribute("message", "Assignment '" + a.getTitle() + "' deleted.");
        return "redirect:/assignments";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, @RequestParam(required = false) MultipartFile file,
                         @RequestParam(required = false) String note,
                         Authentication auth, RedirectAttributes redirect) {
        User me = currentUser(auth);
        Assignment a = findAssignment(id);
        requireStudentOfSemester(me, a);
        try {
            AssignmentSubmission s = service.submit(a, me, file, note);
            redirect.addFlashAttribute("message", s.isLate() ? "Submitted (after the due date)." : "Submitted successfully.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Could not save the file. Please try again.");
        }
        return "redirect:/assignments/" + id;
    }

    @PostMapping("/submissions/{sid}/grade")
    public String grade(@PathVariable Long sid, @RequestParam(required = false) Integer marks,
                        @RequestParam(required = false) String feedback,
                        Authentication auth, RedirectAttributes redirect) {
        AssignmentSubmission s = findSubmission(sid);
        requireOwner(currentUser(auth), s.getAssignment());
        try {
            service.grade(s, marks, feedback);
            redirect.addFlashAttribute("message", "Graded " + s.getStudent().getFullName() + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/assignments/" + s.getAssignment().getId();
    }

    @PostMapping("/submissions/{sid}/reopen")
    public String reopen(@PathVariable Long sid, Authentication auth, RedirectAttributes redirect) {
        AssignmentSubmission s = findSubmission(sid);
        requireOwner(currentUser(auth), s.getAssignment());
        service.removeGrade(s);
        redirect.addFlashAttribute("message", "Grade removed. " + s.getStudent().getFullName() + " can resubmit.");
        return "redirect:/assignments/" + s.getAssignment().getId();
    }

    // ---------- Private downloads ----------

    /** The question sheet: the subject's faculty and the students of that semester. */
    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> attachment(@PathVariable Long id, Authentication auth) {
        User me = currentUser(auth);
        Assignment a = findAssignment(id);
        if (me.getRole() == Role.FACULTY) {
            requireOwner(me, a);
        } else {
            requireStudentOfSemester(me, a);
        }
        return download(FileStorageService.ASSIGNMENTS, a.getAttachmentFile(), a.getAttachmentName());
    }

    /** A student's work: only that student and the subject's faculty. */
    @GetMapping("/submissions/{sid}/file")
    public ResponseEntity<Resource> submissionFile(@PathVariable Long sid, Authentication auth) {
        User me = currentUser(auth);
        AssignmentSubmission s = findSubmission(sid);
        boolean owner = me.getRole() == Role.FACULTY
                && s.getAssignment().getSubject().getFaculty().getId().equals(me.getId());
        boolean author = me.getRole() == Role.STUDENT && s.getStudent().getId().equals(me.getId());
        if (!owner && !author) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your submission");
        }
        return download(FileStorageService.SUBMISSIONS, s.getFileName(), s.getOriginalName());
    }

    private ResponseEntity<Resource> download(String kind, String storedName, String downloadName) {
        Resource resource = storage.loadAny(kind, storedName);
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Always a download with a neutral type, so an uploaded file can never be displayed as a web page.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(downloadName == null ? "file" : downloadName, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    // ---------- Helpers ----------

    private User currentUser(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow();
    }

    private Assignment findAssignment(Long id) {
        return assignments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private AssignmentSubmission findSubmission(Long id) {
        return submissions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void requireOwner(User user, Assignment a) {
        if (user.getRole() != Role.FACULTY || !a.getSubject().getFaculty().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the subject's faculty can do this");
        }
    }

    private static void requireStudentOfSemester(User user, Assignment a) {
        if (user.getRole() != Role.STUDENT || user.getSemester() == null
                || user.getSemester() != a.getSubject().getSemester()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This assignment is not for you");
        }
    }
}
