package edu.mbu.library.marks;

/** The grading scale (10-point). Change the thresholds here if the university uses a different one. */
public final class GradeScale {

    public record Grade(String letter, int points, boolean pass) {}

    private GradeScale() {}

    public static Grade of(double percent) {
        if (percent >= 90) return new Grade("O", 10, true);
        if (percent >= 80) return new Grade("A+", 9, true);
        if (percent >= 70) return new Grade("A", 8, true);
        if (percent >= 60) return new Grade("B+", 7, true);
        if (percent >= 50) return new Grade("B", 6, true);
        if (percent >= 40) return new Grade("C", 5, true);
        return new Grade("F", 0, false);
    }
}
