package edu.mbu.library.library;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.modules.SubjectRepository;
import edu.mbu.library.storage.FileStorageService;
import edu.mbu.library.user.Role;
import edu.mbu.library.user.User;
import edu.mbu.library.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tools and Resources: faculty share links and files, everyone logged in can browse, search and open them.
 * Only the faculty member who posted an item can delete it.
 */
@Controller
public class LibraryController {

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final LibraryItemRepository items;
    private final LibraryService service;
    private final FileStorageService storage;

    public LibraryController(UserRepository users, SubjectRepository subjects, LibraryItemRepository items,
                             LibraryService service, FileStorageService storage) {
        this.users = users;
        this.subjects = subjects;
        this.items = items;
        this.service = service;
        this.storage = storage;
    }

    // ---------- Pages ----------

    @GetMapping("/tools")
    public String tools(@RequestParam(required = false) String subjectId, @RequestParam(required = false) String q,
                        Authentication auth, Model model) {
        return page(ItemKind.TOOL, subjectId, q, auth, model);
    }

    @GetMapping("/resources")
    public String resources(@RequestParam(required = false) String subjectId, @RequestParam(required = false) String q,
                            Authentication auth, Model model) {
        return page(ItemKind.RESOURCE, subjectId, q, auth, model);
    }

    private String page(ItemKind kind, String subjectFilter, String query, Authentication auth, Model model) {
        User me = currentUser(auth);
        List<LibraryItem> all = items.findByKindOrderByCreatedAtDesc(kind);

        // the filter offers the subjects that actually have items, plus "General"
        Map<Long, Subject> withItems = new TreeMap<>();
        boolean hasGeneral = false;
        for (LibraryItem i : all) {
            if (i.getSubject() == null) {
                hasGeneral = true;
            } else {
                withItems.putIfAbsent(i.getSubject().getId(), i.getSubject());
            }
        }

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<LibraryItem> shown = all.stream()
                .filter(i -> matchesSubject(i, subjectFilter))
                .filter(i -> q.isEmpty() || i.getTitle().toLowerCase(Locale.ROOT).contains(q)
                        || (i.getDescription() != null && i.getDescription().toLowerCase(Locale.ROOT).contains(q)))
                .toList();

        model.addAttribute("active", kind == ItemKind.TOOL ? "tools" : "resources");
        model.addAttribute("kind", kind.name());
        model.addAttribute("isTools", kind == ItemKind.TOOL);
        model.addAttribute("itemList", shown);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("filterSubjects", withItems.values());
        model.addAttribute("hasGeneral", hasGeneral);
        model.addAttribute("selectedSubject", subjectFilter == null ? "" : subjectFilter);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("isFaculty", me.getRole() == Role.FACULTY);
        model.addAttribute("meId", me.getId());
        if (me.getRole() == Role.FACULTY) {
            model.addAttribute("ownedSubjects", subjects.findByFacultyOrderBySemesterAscNameAsc(me));
        }
        return "library";
    }

    private static boolean matchesSubject(LibraryItem item, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if (filter.equals("general")) {
            return item.getSubject() == null;
        }
        return item.getSubject() != null && String.valueOf(item.getSubject().getId()).equals(filter);
    }

    // ---------- Posting and deleting (faculty) ----------

    @PostMapping("/tools")
    public String addTool(@RequestParam String title, @RequestParam(required = false) String description,
                          @RequestParam(required = false) String url, @RequestParam(required = false) Long subjectId,
                          Authentication auth, RedirectAttributes redirect) {
        return add(ItemKind.TOOL, title, description, url, null, subjectId, auth, redirect);
    }

    @PostMapping("/resources")
    public String addResource(@RequestParam String title, @RequestParam(required = false) String description,
                              @RequestParam(required = false) String url,
                              @RequestParam(required = false) MultipartFile file,
                              @RequestParam(required = false) Long subjectId,
                              Authentication auth, RedirectAttributes redirect) {
        return add(ItemKind.RESOURCE, title, description, url, file, subjectId, auth, redirect);
    }

    private String add(ItemKind kind, String title, String description, String url, MultipartFile file,
                       Long subjectId, Authentication auth, RedirectAttributes redirect) {
        User me = currentUser(auth);
        String back = kind == ItemKind.TOOL ? "redirect:/tools" : "redirect:/resources";
        Subject subject = null;
        if (subjectId != null) {
            subject = subjects.findById(subjectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!subject.getFaculty().getId().equals(me.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only tag your own subjects");
            }
        }
        try {
            LibraryItem item = service.create(kind, me, subject, title, description, url, file);
            redirect.addFlashAttribute("message", "Shared '" + item.getTitle() + "'.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Could not save the file. Please try again.");
        }
        return back;
    }

    @PostMapping("/library/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        User me = currentUser(auth);
        LibraryItem item = findItem(id);
        if (me.getRole() != Role.FACULTY || !item.getPostedBy().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the person who shared it can remove it");
        }
        service.delete(item);
        redirect.addFlashAttribute("message", "Removed '" + item.getTitle() + "'.");
        return item.getKind() == ItemKind.TOOL ? "redirect:/tools" : "redirect:/resources";
    }

    // ---------- Downloads ----------

    /** PDFs open in the browser (they are verified when uploaded); every other type is a download. */
    @GetMapping("/library/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        LibraryItem item = findItem(id);
        Resource resource = item.isFile() ? storage.loadAny(FileStorageService.LIBRARY, item.getFileName()) : null;
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String name = item.getOriginalName() == null ? "file" : item.getOriginalName();
        boolean pdf = item.getFileName().toLowerCase(Locale.ROOT).endsWith(".pdf");
        ContentDisposition disposition = (pdf ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    // ---------- Helpers ----------

    private User currentUser(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow();
    }

    private LibraryItem findItem(Long id) {
        return items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
