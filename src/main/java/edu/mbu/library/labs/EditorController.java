package edu.mbu.library.labs;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Free practice editor (nothing is saved or submitted from here). */
@Controller
public class EditorController {

    @GetMapping("/editor")
    public String editor(Model model) {
        model.addAttribute("active", "editor");
        model.addAttribute("languages", Language.values());
        return "editor";
    }
}
