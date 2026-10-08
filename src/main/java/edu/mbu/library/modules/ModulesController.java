package edu.mbu.library.modules;

import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Modules section: Semester -> Subject -> Module -> PDFs and YouTube links.
 * Everyone logged in can read; only the faculty member who owns a subject can change it
 * (POST routes are also limited to the FACULTY role in SecurityConfig).
 */
@Controller
@RequestMapping("/modules")
public class ModulesController {

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final CourseModuleRepository modules;
    private final MaterialRepository materials;
    private final ModuleService service;

    public ModulesController(UserRepository users, SubjectRepository subjects, CourseModuleRepository modules,
                             MaterialRepository materials, ModuleService service) {
        this.users = users;
        this.subjects = subjects;
        this.modules = modules;
        this.materials = materials;
        this.service = service;
    }

    // ---------- Reading ----------

    @GetMapping
    public String list(@RequestParam(required = false) Integer sem, Authentication auth, Model model) {
        User me = currentUser(auth);
        int semester = (sem != null && sem >= 1 && sem <= 8) ? sem
                : (me.getSemester() != null && me.getSemester() >= 1 && me.getSemester() <= 8 ? me.getSemester() : 1);

        List<Subject> list = subjects.findBySemesterOrderByNameAsc(semester);
        Map<Long, Long> moduleCounts = new HashMap<>();
        for (Subject s : list) {
            moduleCounts.put(s.getId(), modules.countBySubject(s));
        }

        model.addAttribute("active", "modules");
        model.addAttribute("semester", semester);
        model.addAttribute("semesters", List.of(1, 2, 3, 4, 5, 6, 7, 8));
        model.addAttribute("subjectList", list);
        model.addAttribute("moduleCounts", moduleCounts);
        model.addAttribute("isFaculty", me.getRole() == Role.FACULTY);
        return "modules";
    }

    @GetMapping("/subjects/{id}")
    public String subject(@PathVariable Long id, Authentication auth, Model model) {
        User me = currentUser(auth);
        Subject subject = findSubject(id);
        List<CourseModule> moduleList = modules.findBySubjectOrderByPositionAscIdAsc(subject);
        Map<Long, List<Material>> byModule = moduleList.isEmpty() ? Map.of()
                : materials.findByModuleInOrderByIdAsc(moduleList).stream()
                    .collect(Collectors.groupingBy(m -> m.getModule().getId()));

        model.addAttribute("active", "modules");
        model.addAttribute("subject", subject);
        model.addAttribute("moduleList", moduleList);
        model.addAttribute("materialsByModule", byModule);
        model.addAttribute("canManage", owns(me, subject));
        return "subject";
    }

    // ---------- Changing (faculty who own the subject) ----------

    @PostMapping("/subjects")
    public String createSubject(@RequestParam String code, @RequestParam String name, @RequestParam int semester,
                                Authentication auth, RedirectAttributes redirect) {
        User me = currentUser(auth);
        try {
            Subject s = service.createSubject(me, code, name, semester);
            redirect.addFlashAttribute("message", "Subject '" + s.getName() + "' created. Add its modules below.");
            return "redirect:/modules/subjects/" + s.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/modules?sem=" + semester;
        }
    }

    @PostMapping("/subjects/{id}/delete")
    public String deleteSubject(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        Subject subject = ownedSubject(id, auth);
        service.deleteSubject(subject);
        redirect.addFlashAttribute("message", "Subject '" + subject.getName() + "' deleted.");
        return "redirect:/modules?sem=" + subject.getSemester();
    }

    @PostMapping("/subjects/{id}/modules")
    public String addModule(@PathVariable Long id, @RequestParam String title,
                            Authentication auth, RedirectAttributes redirect) {
        Subject subject = ownedSubject(id, auth);
        try {
            service.addModule(subject, title);
            redirect.addFlashAttribute("message", "Module added.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/modules/subjects/" + id;
    }

    @PostMapping("/modules/{id}/delete")
    public String deleteModule(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        CourseModule module = findModule(id);
        Subject subject = ownedSubject(module.getSubject().getId(), auth);
        service.deleteModule(module);
        redirect.addFlashAttribute("message", "Module deleted.");
        return "redirect:/modules/subjects/" + subject.getId();
    }

    @PostMapping("/modules/{id}/materials")
    public String addMaterial(@PathVariable Long id,
                              @RequestParam String title,
                              @RequestParam MaterialType type,
                              @RequestParam(required = false) MultipartFile file,
                              @RequestParam(required = false) String url,
                              Authentication auth, RedirectAttributes redirect) {
        CourseModule module = findModule(id);
        Subject subject = ownedSubject(module.getSubject().getId(), auth);
        try {
            service.addMaterial(module, title, type, file, url);
            redirect.addFlashAttribute("message", "Added '" + title.trim() + "' to " + module.getTitle() + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Could not save the file. Please try again.");
        }
        return "redirect:/modules/subjects/" + subject.getId();
    }

    @PostMapping("/materials/{id}/delete")
    public String deleteMaterial(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        Material material = materials.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Subject subject = ownedSubject(material.getModule().getSubject().getId(), auth);
        service.deleteMaterial(material);
        redirect.addFlashAttribute("message", "Removed '" + material.getTitle() + "'.");
        return "redirect:/modules/subjects/" + subject.getId();
    }

    // ---------- Helpers ----------

    private User currentUser(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow();
    }

    private Subject findSubject(Long id) {
        return subjects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private CourseModule findModule(Long id) {
        return modules.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean owns(User user, Subject subject) {
        return user.getRole() == Role.FACULTY && subject.getFaculty().getId().equals(user.getId());
    }

    /** Loads a subject and makes sure the current user is the faculty member who owns it. */
    private Subject ownedSubject(Long id, Authentication auth) {
        Subject subject = findSubject(id);
        if (!owns(currentUser(auth), subject)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the subject's faculty can change it");
        }
        return subject;
    }
}
