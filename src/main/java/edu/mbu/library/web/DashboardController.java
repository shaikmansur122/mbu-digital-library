package edu.mbu.library.web;

import edu.mbu.library.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final UserRepository users;

    public DashboardController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/student/dashboard")
    public String student(Authentication auth, Model model) {
        model.addAttribute("user", users.findByUsername(auth.getName()).orElseThrow());
        return "student/dashboard";
    }

    @GetMapping("/faculty/dashboard")
    public String faculty(Authentication auth, Model model) {
        model.addAttribute("user", users.findByUsername(auth.getName()).orElseThrow());
        return "faculty/dashboard";
    }
}
