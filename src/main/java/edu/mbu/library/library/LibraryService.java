package edu.mbu.library.library;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.storage.FileStorageService;
import edu.mbu.library.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Pattern;

/** Rules for sharing tools and resources. Failures are IllegalArgumentExceptions with a friendly message. */
@Service
public class LibraryService {

    private static final Pattern WEB_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private final LibraryItemRepository items;
    private final FileStorageService storage;

    public LibraryService(LibraryItemRepository items, FileStorageService storage) {
        this.items = items;
        this.storage = storage;
    }

    /** A tool needs a link; a resource needs a file or a link (or both). */
    @Transactional
    public LibraryItem create(ItemKind kind, User poster, Subject subject, String title, String description,
                              String url, MultipartFile file) throws IOException {
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > 150) {
            throw new IllegalArgumentException("Title is required (up to 150 characters).");
        }
        description = description == null ? "" : description.trim();
        if (description.length() > 1000) {
            throw new IllegalArgumentException("Description is too long (up to 1000 characters).");
        }
        url = url == null ? "" : url.trim();
        if (!url.isEmpty() && (url.length() > 500 || !WEB_URL.matcher(url).matches())) {
            throw new IllegalArgumentException("The link must start with http:// or https:// (up to 500 characters).");
        }
        boolean hasFile = kind == ItemKind.RESOURCE && file != null && !file.isEmpty();
        if (kind == ItemKind.TOOL && url.isEmpty()) {
            throw new IllegalArgumentException("A tool needs a link.");
        }
        if (kind == ItemKind.RESOURCE && url.isEmpty() && !hasFile) {
            throw new IllegalArgumentException("Upload a file or give a link.");
        }

        LibraryItem item = new LibraryItem();
        item.setKind(kind);
        item.setPostedBy(poster);
        item.setSubject(subject);
        item.setTitle(title);
        item.setDescription(description.isEmpty() ? null : description);
        item.setUrl(url.isEmpty() ? null : url);
        if (hasFile) {
            item.setFileName(storage.saveAssignmentFile(FileStorageService.LIBRARY, file));
            item.setOriginalName(FileStorageService.cleanOriginalName(file));
        }
        return items.save(item);
    }

    @Transactional
    public void delete(LibraryItem item) {
        storage.deleteQuietly(FileStorageService.LIBRARY, item.getFileName());
        items.delete(item);
    }

    /** Removes everything tagged with a subject; used when the subject is deleted. */
    @Transactional
    public void deleteAllForSubject(Subject subject) {
        items.findBySubject(subject).forEach(this::delete);
    }

    /** Removes everything a user posted; used when the account is deleted. */
    @Transactional
    public void deleteAllPostedBy(User user) {
        items.findByPostedBy(user).forEach(this::delete);
    }
}
