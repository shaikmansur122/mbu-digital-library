package edu.mbu.library.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/** Saves uploaded files under the upload directory and hands them back by name. */
@Service
public class FileStorageService {

    public static final String PHOTOS = "photos";
    public static final String RESUMES = "resumes";
    public static final String MATERIALS = "materials";
    /** Question sheets attached by faculty. */
    public static final String ASSIGNMENTS = "assignments";
    /** Resources shared by faculty with everyone. */
    public static final String LIBRARY = "library";
    /** Student work. Private: only the student and the subject's faculty may download it. */
    public static final String SUBMISSIONS = "submissions";

    private static final String[] ASSIGNMENT_EXTENSIONS = {
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "zip", "png", "jpg", "jpeg",
            "ipynb", "py", "java", "c", "cpp", "sql"};

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** Saves a profile photo (jpg or png, verified to be a real image). Returns the stored file name. */
    public String savePhoto(MultipartFile file) throws IOException {
        String ext = extensionOf(file, "jpg", "jpeg", "png");
        try (InputStream in = file.getInputStream()) {
            if (ImageIO.read(in) == null) {
                throw new IllegalArgumentException("That file is not a valid image.");
            }
        }
        return store(PHOTOS, file, ext);
    }

    /** Saves a resume (PDF, verified by its header). Returns the stored file name. */
    public String saveResume(MultipartFile file) throws IOException {
        return savePdf(RESUMES, file);
    }

    /** Saves a module PDF uploaded by faculty. Returns the stored file name. */
    public String saveMaterial(MultipartFile file) throws IOException {
        return savePdf(MATERIALS, file);
    }

    private String savePdf(String kind, MultipartFile file) throws IOException {
        String ext = extensionOf(file, "pdf");
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(5);
            if (!new String(head, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
                throw new IllegalArgumentException("That file is not a valid PDF.");
            }
        }
        return store(kind, file, ext);
    }

    /**
     * Saves an assignment question sheet or a student's submission. Any of the allowed document, archive,
     * image or source-code types is accepted; these files are only ever served as downloads, never displayed.
     */
    public String saveAssignmentFile(String kind, MultipartFile file) throws IOException {
        String ext = extensionOf(file, ASSIGNMENT_EXTENSIONS);
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(8);
            String start = new String(head, StandardCharsets.ISO_8859_1);
            boolean ok = switch (ext) {
                case "pdf" -> start.startsWith("%PDF-");
                case "zip", "docx", "pptx", "xlsx" -> start.startsWith("PK");
                case "png" -> head.length >= 4 && (head[0] & 0xFF) == 0x89 && start.startsWith("\u0089PNG");
                case "jpg", "jpeg" -> head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
                default -> true;
            };
            if (!ok) {
                throw new IllegalArgumentException("That file does not look like a real ." + ext + " file.");
            }
        }
        return store(kind, file, ext);
    }

    /** The uploaded file's own name, cleaned for showing to people and for the download header. */
    public static String cleanOriginalName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>:*?|]", "_").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    /** Returns a stored file from the public kinds (photos, resumes, module PDFs), or null. */
    public Resource load(String kind, String name) {
        if (!PHOTOS.equals(kind) && !RESUMES.equals(kind) && !MATERIALS.equals(kind)) {
            return null;
        }
        return loadAny(kind, name);
    }

    /** Returns a stored file of any kind. Callers must check permissions themselves (used for private files). */
    public Resource loadAny(String kind, String name) {
        if (name == null) {
            return null;
        }
        Path dir = root.resolve(kind).normalize();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            return null;
        }
        return new FileSystemResource(file);
    }

    public void deleteQuietly(String kind, String name) {
        if (name == null) {
            return;
        }
        Path dir = root.resolve(kind).normalize();
        Path file = dir.resolve(name).normalize();
        if (file.startsWith(dir)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // an orphaned file is harmless
            }
        }
    }

    private String store(String kind, MultipartFile file, String ext) throws IOException {
        Path dir = root.resolve(kind);
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        return name;
    }

    private static String extensionOf(MultipartFile file, String... allowed) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String a : allowed) {
            if (a.equals(ext)) {
                return ext;
            }
        }
        throw new IllegalArgumentException("Allowed file types: " + String.join(", ", allowed));
    }
}
