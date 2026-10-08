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
            "/tools", new Section("tools", "Tools", "Phase 8",
                    "Useful tools shared by faculty."),
            "/resources", new Section("resources", "Resources", "Phase 8",
                    "Reference material uploaded by faculty."),
            "/results", new Section("results", "Results", "Phase 7",
                    "Internal and exam results."),
            "/codes", new Section("codes", "Codes", "Phase 8",
                    "Your saved code from lab experiments and the editor."),
            "/attendance", new Section("attendance", "Attendance", "Phase 6",
                    "Subject-wise attendance."),
            "/marks", new Section("marks", "Marks", "Phase 7",
                    "Marks entered by faculty."));

    @GetMapping("/home")
    public String home() {
        return "home";
    }

    @GetMapping({"/tools", "/resources", "/results", "/codes", "/attendance", "/marks"})
    public String section(HttpServletRequest request, Model model) {
        Section s = SECTIONS.get(request.getServletPath());
        model.addAttribute("active", s.key());
        model.addAttribute("title", s.title());
        model.addAttribute("phase", s.phase());
        model.addAttribute("description", s.description());
        return "placeholder";
    }
}
