package edu.mbu.library.web;

import edu.mbu.library.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    private final boolean showDemoLogins;

    public AuthController(@Value("${app.show-demo-logins:false}") boolean showDemoLogins) {
        this.showDemoLogins = showDemoLogins;
    }

    @GetMapping("/login")
    public String login(Model model) {
        // Only for public demos: shows the sample accounts' logins on the sign-in page.
        model.addAttribute("showDemoLogins", showDemoLogins);
        return "login";
    }

    /** "/" sends a logged-in user to the dashboard for their role. */
    @GetMapping("/")
    public String home(Authentication authentication) {
        return "redirect:" + SecurityConfig.homeFor(authentication);
    }
}
