package edu.mbu.library.marks;

import edu.mbu.library.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** A student's marks in one assessment. No row means "not entered yet". */
@Entity
@Table(name = "mark_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"assessment_id", "student_id"}))
public class MarkEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
}
