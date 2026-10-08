package edu.mbu.library.labs;

import edu.mbu.library.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A student's latest submission for a lab experiment (one row per student per experiment). */
@Entity
@Table(name = "lab_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"experiment_id", "student_id"}))
public class LabSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "experiment_id")
    private LabExperiment experiment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String code;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    /** True when the latest submission came after the due date. */
    private boolean late;

    /** How many times the student has submitted. */
    private int attempts = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LabExperiment getExperiment() { return experiment; }
    public void setExperiment(LabExperiment experiment) { this.experiment = experiment; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public boolean isLate() { return late; }
    public void setLate(boolean late) { this.late = late; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
