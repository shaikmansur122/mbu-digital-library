package edu.mbu.library.codes;

import edu.mbu.library.labs.Language;
import edu.mbu.library.labs.LabService;
import edu.mbu.library.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Rules for saving code snippets. Failures are IllegalArgumentExceptions with a friendly message. */
@Service
public class CodesService {

    public static final int MAX_PER_USER = 100;

    private final SavedCodeRepository codes;

    public CodesService(SavedCodeRepository codes) {
        this.codes = codes;
    }

    @Transactional
    public SavedCode create(User owner, String title, Language language, String code) {
        if (codes.countByOwner(owner) >= MAX_PER_USER) {
            throw new IllegalArgumentException("You can keep up to " + MAX_PER_USER + " saved codes. Delete some first.");
        }
        SavedCode s = new SavedCode();
        s.setOwner(owner);
        fill(s, title, language, code);
        return codes.save(s);
    }

    @Transactional
    public SavedCode update(SavedCode existing, String title, Language language, String code) {
        fill(existing, title, language, code);
        existing.setUpdatedAt(LocalDateTime.now());
        return codes.save(existing);
    }

    private static void fill(SavedCode s, String title, Language language, String code) {
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > 100) {
            throw new IllegalArgumentException("Give your code a title (up to 100 characters).");
        }
        if (language == null) {
            throw new IllegalArgumentException("Choose the programming language.");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("There is no code to save.");
        }
        if (code.length() > LabService.MAX_CODE_CHARS) {
            throw new IllegalArgumentException("The code is too long (up to " + LabService.MAX_CODE_CHARS + " characters).");
        }
        s.setTitle(title);
        s.setLanguage(language);
        s.setCode(code);
    }

    @Transactional
    public void deleteAllOf(User owner) {
        codes.deleteAll(codes.findByOwner(owner));
    }
}
