package edu.mbu.library.marks;

import edu.mbu.library.marks.GradeScale.Grade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GradeScaleTest {

    @Test
    void thresholdsMapToTheTenPointScale() {
        assertThat(GradeScale.of(100).letter()).isEqualTo("O");
        assertThat(GradeScale.of(90).letter()).isEqualTo("O");
        assertThat(GradeScale.of(89.9).letter()).isEqualTo("A+");
        assertThat(GradeScale.of(80).letter()).isEqualTo("A+");
        assertThat(GradeScale.of(70).letter()).isEqualTo("A");
        assertThat(GradeScale.of(60).letter()).isEqualTo("B+");
        assertThat(GradeScale.of(50).letter()).isEqualTo("B");
        assertThat(GradeScale.of(40).letter()).isEqualTo("C");
    }

    @Test
    void belowFortyIsAFail() {
        Grade f = GradeScale.of(39.99);
        assertThat(f.letter()).isEqualTo("F");
        assertThat(f.points()).isZero();
        assertThat(f.pass()).isFalse();
        assertThat(GradeScale.of(40).pass()).isTrue();
    }

    @Test
    void gradePointsDecreaseWithTheGrade() {
        assertThat(GradeScale.of(95).points()).isEqualTo(10);
        assertThat(GradeScale.of(85).points()).isEqualTo(9);
        assertThat(GradeScale.of(75).points()).isEqualTo(8);
        assertThat(GradeScale.of(65).points()).isEqualTo(7);
        assertThat(GradeScale.of(55).points()).isEqualTo(6);
        assertThat(GradeScale.of(45).points()).isEqualTo(5);
    }
}
