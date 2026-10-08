package edu.mbu.library.web;

import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Authentication auth, Model model) {
        model.addAttribute("user", users.findByUsername(auth.getName()).orElseThrow());
        model.addAttribute("accounts", users.findAllByOrderByRoleAscFullNameAsc());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CreateUserForm());
        }
        model.addAttribute("roles", Role.values());
        return "admin/dashboard";
    }

    /** After a failed submit the browser URL is /admin/users, so a refresh must not 405. */
    @GetMapping("/admin/users")
    public String usersRedirect() {
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/users")
    public String create(@Valid @ModelAttribute("form") CreateUserForm form,
                         BindingResult result,
                         Authentication auth,
                         Model model,
                         RedirectAttributes redirect) {
        if (users.existsByUsername(form.getUsername())) {
            result.rejectValue("username", "duplicate", "That username is already taken");
        }
        if (result.hasErrors()) {
            // Re-render the page (not a redirect) so the field errors and entered values stay visible.
            return dashboard(auth, model);
        }

        User u = new User();
        u.setUsername(form.getUsername());
        u.setPassword(encoder.encode(form.getPassword()));
        u.setFullName(form.getFullName());
        u.setRole(form.getRole());
        u.setEmail(blankToNull(form.getEmail()));
        u.setRollNo(blankToNull(form.getRollNo()));
        u.setDepartment(blankToNull(form.getDepartment()));
        u.setSection(blankToNull(form.getSection()));
        u.setSemester(form.getSemester());
        users.save(u);

        redirect.addFlashAttribute("message", "Created " + form.getRole() + " account '" + u.getUsername() + "'");
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/users/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        User target = users.findById(id).orElse(null);
        if (target == null) {
            redirect.addFlashAttribute("error", "Account not found");
        } else if (target.getUsername().equals(auth.getName())) {
            redirect.addFlashAttribute("error", "You cannot delete your own account");
        } else {
            users.delete(target);
            redirect.addFlashAttribute("message", "Deleted account '" + target.getUsername() + "'");
        }
        return "redirect:/admin/dashboard";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
