package edu.mbu.library.marks;

import edu.mbu.library.marks.MarksService.SubjectResult;
import edu.mbu.library.modules.Subject;
import edu.mbu.library.modules.SubjectRepository;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Results: students see grades, SGPA and CGPA; faculty see how each of their subjects went. */
@Controller
public class ResultsController {

    /** One semester of a student's results. */
    public record SemesterResults(int semester, List<SubjectResult> results, Double sgpa) {
        public String sgpaText() { return sgpa == null ? "-" : String.format(Locale.ROOT, "%.2f", sgpa); }
    }

    /** How a faculty member's subject went, over the students whose grade is final. */
    public record SubjectStats(Subject subject, int students, int complete, double average, double highest,
                               int passed, Map<String, Integer> distribution) {
        public String averageText() { return complete == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", average); }
        public String highestText() { return complete == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", highest); }
        public String passText() { return complete == 0 ? "-" : String.format(Locale.ROOT, "%.0f%%", passed * 100.0 / complete); }
    }

    private static final List<String> GRADE_ORDER = List.of("O", "A+", "A", "B+", "B", "C", "F");

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final MarksService service;

    public ResultsController(UserRepository users, SubjectRepository subjects, MarksService service) {
        this.users = users;
        this.subjects = subjects;
        this.service = service;
    }

    @GetMapping("/results")
    public String results(Authentication auth, Model model) {
        User me = users.findByUsername(auth.getName()).orElseThrow();
        model.addAttribute("active", "results");

        if (me.getRole() == Role.FACULTY) {
            List<SubjectStats> stats = new ArrayList<>();
            for (Subject s : subjects.findByFacultyOrderBySemesterAscNameAsc(me)) {
                stats.add(statsFor(s));
            }
            model.addAttribute("isFaculty", true);
            model.addAttribute("statsList", stats);
            model.addAttribute("gradeOrder", GRADE_ORDER);
            return "results";
        }

        // Student: current semester plus any earlier semester in which they have marks
        Map<Long, Subject> wanted = new LinkedHashMap<>();   // keyed by id: entities have no equals()
        if (me.getSemester() != null) {
            subjects.findBySemesterOrderByNameAsc(me.getSemester()).forEach(s -> wanted.putIfAbsent(s.getId(), s));
        }
        service.subjectsWithMarks(me).forEach(s -> wanted.putIfAbsent(s.getId(), s));
        List<SubjectResult> all = service.resultsOf(me, new ArrayList<>(wanted.values()));

        Map<Integer, List<SubjectResult>> bySemester = new TreeMap<>();
        for (SubjectResult r : all) {
            if (r.hasMarks() || (me.getSemester() != null && r.subject().getSemester() == me.getSemester())) {
                bySemester.computeIfAbsent(r.subject().getSemester(), k -> new ArrayList<>()).add(r);
            }
        }
        List<SemesterResults> semesters = bySemester.entrySet().stream()
                .map(e -> new SemesterResults(e.getKey(), e.getValue(), MarksService.gpa(e.getValue())))
                .toList();
        Double cgpa = MarksService.gpa(all);

        model.addAttribute("isFaculty", false);
        model.addAttribute("semesters", semesters);
        model.addAttribute("cgpaText", cgpa == null ? "-" : String.format(Locale.ROOT, "%.2f", cgpa));
        model.addAttribute("noSemester", me.getSemester() == null);
        return "results";
    }

    private SubjectStats statsFor(Subject subject) {
        List<Assessment> list = service.assessmentsOf(subject);
        Map<String, BigDecimal> grid = service.gridOf(list);
        List<User> roster = users.findAllByRoleAndSemesterOrderByRollNoAscFullNameAsc(Role.STUDENT, subject.getSemester());
        List<SubjectResult> complete = roster.stream()
                .map(u -> service.resultFor(subject, list, grid, u.getId()))
                .filter(SubjectResult::complete)
                .toList();
        Map<String, Integer> distribution = new LinkedHashMap<>();
        GRADE_ORDER.forEach(g -> distribution.put(g, 0));
        complete.forEach(r -> distribution.merge(r.grade().letter(), 1, Integer::sum));
        return new SubjectStats(subject, roster.size(), complete.size(),
                complete.stream().mapToDouble(SubjectResult::percent).average().orElse(0),
                complete.stream().mapToDouble(SubjectResult::percent).max().orElse(0),
                (int) complete.stream().filter(r -> r.grade().pass()).count(), distribution);
    }
}
