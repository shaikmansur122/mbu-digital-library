package edu.mbu.library.modules;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A PDF or YouTube link inside a module. */
@Entity
@Table(name = "materials")
public class Material {

    private static final Pattern YOUTUBE_ID =
            Pattern.compile("(?:youtu\\.be/|[?&]v=|/embed/|/shorts/)([A-Za-z0-9_-]{11})");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "module_id")
    private CourseModule module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MaterialType type;

    @Column(nullable = false, length = 150)
    private String title;

    /** Stored PDF file name (type PDF). */
    @Column(length = 255)
    private String fileName;

    /** YouTube link (type VIDEO). */
    @Column(length = 500)
    private String url;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** The 11-character YouTube video id, or null when this is not a recognisable YouTube link. */
    public String getYoutubeId() {
        if (url == null) {
            return null;
        }
        Matcher m = YOUTUBE_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CourseModule getModule() { return module; }
    public void setModule(CourseModule module) { this.module = module; }

    public MaterialType getType() { return type; }
    public void setType(MaterialType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
