package edu.mbu.library.user;

public enum Role {
    STUDENT,
    FACULTY,
    ADMIN;

    /** Page a user of this role lands on after login. */
    public String homePath() {
        return switch (this) {
            case STUDENT, FACULTY -> "/home";
            case ADMIN -> "/admin/dashboard";
        };
    }
}
