package edu.mbu.library.marks;

import edu.mbu.library.marks.MarksService.SubjectResult;
import edu.mbu.library.modules.Subject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MarksServiceGpaTest {

    private static Subject subject(int credits) {
        Subject s = new Subject();
        s.setCredits(credits);
        return s;
    }

    private static SubjectResult result(Subject s, int entered, int total, double obtained, int max) {
        double percent = obtained * 100.0 / max;
        return new SubjectResult(s, entered, total, BigDecimal.valueOf(obtained), max, percent, GradeScale.of(percent));
    }

    @Test
    void sgpaIsTheCreditWeightedAverageOfGradePoints() {
        // A+ (9 points) with 3 credits and A (8 points) with 4 credits: (27 + 32) / 7
        List<SubjectResult> results = List.of(
                result(subject(3), 3, 3, 83, 100),
                result(subject(4), 2, 2, 70, 100));
        assertThat(MarksService.gpa(results)).isCloseTo(8.4286, within(0.001));
    }

    @Test
    void provisionalSubjectsAreLeftOut() {
        List<SubjectResult> results = List.of(
                result(subject(3), 3, 3, 83, 100),
                result(subject(4), 1, 2, 20, 40));    // only 1 of 2 assessments entered
        assertThat(MarksService.gpa(results)).isEqualTo(9.0);
    }

    @Test
    void noFinalGradesMeansNoGpa() {
        assertThat(MarksService.gpa(List.of())).isNull();
        assertThat(MarksService.gpa(List.of(result(subject(3), 1, 3, 20, 25)))).isNull();
    }

    @Test
    void subjectsCreatedBeforeCreditsExistedCountAsThree() {
        assertThat(new Subject().getCreditsOrDefault()).isEqualTo(3);
    }
}
