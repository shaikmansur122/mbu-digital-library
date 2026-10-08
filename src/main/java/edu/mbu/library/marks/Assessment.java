package edu.mbu.library.marks;

import edu.mbu.library.modules.Subject;
import jakarta.persistence.*;

/** One graded component of a subject, e.g. "Internal 1" out of 25. Its maximum marks are also its weight. */
@Entity
@Table(name = "assessments")
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private int maxMarks;

    @Column(nullable = false)
    private int position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getMaxMarks() { return maxMarks; }
    public void setMaxMarks(int maxMarks) { this.maxMarks = maxMarks; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
