package edu.mbu.library.library;

import edu.mbu.library.modules.Subject;
import edu.mbu.library.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A tool or resource shared by faculty with everyone, optionally tagged with a subject. */
@Entity
@Table(name = "library_items")
public class LibraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemKind kind;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    /** External link (tools, and resources that are links). */
    @Column(length = 500)
    private String url;

    /** Stored file name inside uploads/library (resources that are files). */
    @Column(length = 255)
    private String fileName;

    /** The uploaded file's own name, used for the download. */
    @Column(length = 150)
    private String originalName;

    /** Optional subject this item belongs to; null means general. */
    @ManyToOne
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(optional = false)
    @JoinColumn(name = "posted_by_id")
    private User postedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isFile() { return fileName != null; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ItemKind getKind() { return kind; }
    public void setKind(ItemKind kind) { this.kind = kind; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public User getPostedBy() { return postedBy; }
    public void setPostedBy(User postedBy) { this.postedBy = postedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
