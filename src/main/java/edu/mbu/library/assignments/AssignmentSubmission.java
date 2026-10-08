package edu.mbu.library.assignments;

import edu.mbu.library.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A student's uploaded work for an assignment, with the faculty's marks and feedback once graded. */
@Entity
@Table(name = "assignment_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "student_id"}))
public class AssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    /** Stored file name inside uploads/submissions. */
    @Column(nullable = false, length = 255)
    private String fileName;

    /** The name the student's file had when uploaded (used for the download). */
    @Column(nullable = false, length = 150)
    private String originalName;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private boolean late;

    private int attempts = 1;

    /** Null until graded. */
    private Integer marks;

    @Column(length = 1000)
    private String feedback;

    private LocalDateTime gradedAt;

    public boolean isGraded() { return marks != null; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public boolean isLate() { return late; }
    public void setLate(boolean late) { this.late = late; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Integer getMarks() { return marks; }
    public void setMarks(Integer marks) { this.marks = marks; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public LocalDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(LocalDateTime gradedAt) { this.gradedAt = gradedAt; }
}
