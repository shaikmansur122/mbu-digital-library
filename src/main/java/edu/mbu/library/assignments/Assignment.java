package edu.mbu.library.assignments;

import edu.mbu.library.modules.Subject;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** An assignment posted by the faculty who teaches the subject. */
@Entity
@Table(name = "assignments")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 4000)
    private String description;

    private LocalDateTime dueDate;

    @Column(nullable = false)
    private int maxMarks = 10;

    /** Optional question sheet (stored name inside uploads/assignments). */
    @Column(length = 255)
    private String attachmentFile;

    @Column(length = 150)
    private String attachmentName;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public int getMaxMarks() { return maxMarks; }
    public void setMaxMarks(int maxMarks) { this.maxMarks = maxMarks; }

    public String getAttachmentFile() { return attachmentFile; }
    public void setAttachmentFile(String attachmentFile) { this.attachmentFile = attachmentFile; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
