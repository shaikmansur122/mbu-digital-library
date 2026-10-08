package edu.mbu.library.web;

import edu.mbu.library.storage.FileStorageService;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class ProfileController {

    private final UserRepository users;
    private final FileStorageService storage;

    public ProfileController(UserRepository users, FileStorageService storage) {
        this.users = users;
        this.storage = storage;
    }

    @GetMapping("/profile")
    public String edit() {
        return "profile";
    }

    @PostMapping("/profile")
    public String save(Authentication auth,
                       @RequestParam(required = false) String githubUrl,
                       @RequestParam(required = false) String linkedinUrl,
                       @RequestParam(required = false) String resumeUrl,
                       @RequestParam(required = false) MultipartFile photo,
                       @RequestParam(required = false) MultipartFile resume,
                       RedirectAttributes redirect) {
        User me = users.findByUsername(auth.getName()).orElseThrow();

        String error = checkUrl("GitHub", githubUrl);
        if (error == null) error = checkUrl("LinkedIn", linkedinUrl);
        if (error == null) error = checkUrl("Resume link", resumeUrl);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
            return "redirect:/profile";
        }

        try {
            if (photo != null && !photo.isEmpty()) {
                String old = me.getPhotoFile();
                me.setPhotoFile(storage.savePhoto(photo));
                storage.deleteQuietly(FileStorageService.PHOTOS, old);
            }
            if (resume != null && !resume.isEmpty()) {
                String old = me.getResumeFile();
                me.setResumeFile(storage.saveResume(resume));
                storage.deleteQuietly(FileStorageService.RESUMES, old);
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/profile";
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Could not save the file. Please try again.");
            return "redirect:/profile";
        }

        me.setGithubUrl(blankToNull(githubUrl));
        me.setLinkedinUrl(blankToNull(linkedinUrl));
        me.setResumeUrl(blankToNull(resumeUrl));
        users.save(me);

        redirect.addFlashAttribute("message", "Profile updated");
        return "redirect:/profile";
    }

    /** "View more" page for a faculty member (details and resume). */
    @GetMapping("/people/{id}")
    public String person(@PathVariable Long id, Model model) {
        User person = users.findById(id)
                .filter(u -> u.getRole() == Role.FACULTY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("person", person);
        return "person";
    }

    private static String checkUrl(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toLowerCase();
        if (!(v.startsWith("http://") || v.startsWith("https://"))) {
            return label + " link must start with http:// or https://";
        }
        if (value.trim().length() > 255) {
            return label + " link is too long";
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
