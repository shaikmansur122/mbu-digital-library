package edu.mbu.library.codes;

import edu.mbu.library.labs.Language;
import edu.mbu.library.labs.LabSubmissionRepository;
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

/**
 * Codes: each user's own saved snippets from the editor (private to the owner), plus a student's lab submissions.
 */
@Controller
@RequestMapping("/codes")
public class CodesController {

    private final UserRepository users;
    private final SavedCodeRepository codes;
    private final CodesService service;
    private final LabSubmissionRepository labSubmissions;

    public CodesController(UserRepository users, SavedCodeRepository codes, CodesService service,
                           LabSubmissionRepository labSubmissions) {
        this.users = users;
        this.codes = codes;
        this.service = service;
        this.labSubmissions = labSubmissions;
    }

    @GetMapping
    public String list(Authentication auth, Model model) {
        User me = currentUser(auth);
        model.addAttribute("active", "codes");
        model.addAttribute("savedList", codes.findByOwnerOrderByUpdatedAtDesc(me));
        model.addAttribute("maxCodes", CodesService.MAX_PER_USER);
        boolean student = me.getRole() == Role.STUDENT;
        model.addAttribute("isStudent", student);
        if (student) {
            model.addAttribute("labList", labSubmissions.findByStudentOrderBySubmittedAtDesc(me));
        }
        return "codes";
    }

    @GetMapping("/{id}")
    public String open(@PathVariable Long id, Authentication auth, Model model) {
        SavedCode saved = owned(id, auth);
        model.addAttribute("active", "codes");
        model.addAttribute("saved", saved);
        return "saved-code";
    }

    /** Saves a new snippet from the practice editor. */
    @PostMapping
    public String create(@RequestParam String title, @RequestParam Language language, @RequestParam String code,
                         Authentication auth, RedirectAttributes redirect) {
        try {
            SavedCode s = service.create(currentUser(auth), title, language, code);
            redirect.addFlashAttribute("message", "Saved '" + s.getTitle() + "'.");
            return "redirect:/codes/" + s.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/editor";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String title, @RequestParam Language language,
                         @RequestParam String code, Authentication auth, RedirectAttributes redirect) {
        SavedCode saved = owned(id, auth);
        try {
            service.update(saved, title, language, code);
            redirect.addFlashAttribute("message", "Saved.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/codes/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        SavedCode saved = owned(id, auth);
        codes.delete(saved);
        redirect.addFlashAttribute("message", "Deleted '" + saved.getTitle() + "'.");
        return "redirect:/codes";
    }

    private User currentUser(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow();
    }

    /** Saved code is private: anyone but the owner gets "not found". */
    private SavedCode owned(Long id, Authentication auth) {
        User me = currentUser(auth);
        return codes.findById(id)
                .filter(s -> s.getOwner().getId().equals(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
