package edu.mbu.library.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The home page shown after login to students and faculty. */
@Controller
public class PageController {

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}
