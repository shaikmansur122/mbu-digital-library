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
        String ext = extensionOf(file, "pdf");
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(5);
            if (!new String(head, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
                throw new IllegalArgumentException("That file is not a valid PDF.");
            }
        }
        return store(RESUMES, file, ext);
    }

    /** Returns the stored file, or null if the kind is unknown, the name is unsafe, or the file is missing. */
    public Resource load(String kind, String name) {
        if (!PHOTOS.equals(kind) && !RESUMES.equals(kind)) {
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
