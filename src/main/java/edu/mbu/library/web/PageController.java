package edu.mbu.library.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/** Home page plus the sections that are built in later phases. */
@Controller
public class PageController {

    private record Section(String key, String title, String phase, String description) {}

    private static final Map<String, Section> SECTIONS = Map.of(
            "/modules", new Section("modules", "Modules", "Phase 3",
                    "Semester-wise subjects with PDFs and YouTube links posted by faculty."),
            "/labs", new Section("labs", "Lab Experiments", "Phase 4",
                    "Lab experiments assigned by faculty, solved and submitted in the built-in code editor."),
            "/tools", new Section("tools", "Tools", "Phase 8",
                    "Useful tools shared by faculty."),
            "/resources", new Section("resources", "Resources", "Phase 8",
                    "Reference material uploaded by faculty."),
            "/results", new Section("results", "Results", "Phase 7",
                    "Internal and exam results."),
            "/codes", new Section("codes", "Codes", "Phase 8",
                    "Your saved code from lab experiments and the editor."),
            "/editor", new Section("editor", "Editor", "Phase 4",
                    "Practice code editor for Python, Java, C++, C and SQL."),
            "/attendance", new Section("attendance", "Attendance", "Phase 6",
                    "Subject-wise attendance."),
            "/marks", new Section("marks", "Marks", "Phase 7",
                    "Marks entered by faculty."),
            "/assignments", new Section("assignments", "Assignments", "Phase 5",
                    "Assignments posted by faculty, with submission and grading."));

    @GetMapping("/home")
    public String home() {
        return "home";
    }

    @GetMapping({"/modules", "/labs", "/tools", "/resources", "/results", "/codes", "/editor",
            "/attendance", "/marks", "/assignments"})
    public String section(HttpServletRequest request, Model model) {
        Section s = SECTIONS.get(request.getServletPath());
        model.addAttribute("active", s.key());
        model.addAttribute("title", s.title());
        model.addAttribute("phase", s.phase());
        model.addAttribute("description", s.description());
        return "placeholder";
    }
}
