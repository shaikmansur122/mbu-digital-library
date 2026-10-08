package edu.mbu.library.web;

import edu.mbu.library.security.SecurityConfig;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** "/" sends a logged-in user to the dashboard for their role. */
    @GetMapping("/")
    public String home(Authentication authentication) {
        return "redirect:" + SecurityConfig.homeFor(authentication);
    }
}
