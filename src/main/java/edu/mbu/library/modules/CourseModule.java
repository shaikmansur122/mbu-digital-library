package edu.mbu.library.modules;

import jakarta.persistence.*;

/** A module (unit) of a subject, e.g. "Module 1: Introduction". Named CourseModule to avoid clashing with java.lang.Module. */
@Entity
@Table(name = "course_modules")
public class CourseModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false, length = 150)
    private String title;

    /** Display order inside the subject, starting at 1. */
    @Column(nullable = false)
    private int position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
