package edu.mbu.library.modules;

import edu.mbu.library.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** 1 to 8. */
    @Column(nullable = false)
    private int semester;

    /** Credit weight used for SGPA / CGPA. Null on subjects created before credits existed (treated as 3). */
    private Integer credits;

    public int getCreditsOrDefault() { return credits == null ? 3 : credits; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }

    /** The faculty member who teaches this subject and manages its content. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "faculty_id")
    private User faculty;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }

    public User getFaculty() { return faculty; }
    public void setFaculty(User faculty) { this.faculty = faculty; }
}
