package edu.mbu.library.web;

import edu.mbu.library.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** Serves uploaded photos and resumes to logged-in students and faculty. */
@Controller
public class FileController {

    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/files/{kind}/{name:.+}")
    public ResponseEntity<Resource> file(@PathVariable String kind, @PathVariable String name) {
        Resource resource = storage.load(kind, name);
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        MediaType type = lower.endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : lower.endsWith(".png") ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}
