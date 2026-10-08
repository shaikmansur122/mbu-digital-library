package edu.mbu.library.attendance;

import edu.mbu.library.attendance.AttendanceService.Summary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceSummaryTest {

    @Test
    void percentageIsPresentOverTotal() {
        Summary s = new Summary(6, 2);
        assertThat(s.total()).isEqualTo(8);
        assertThat(s.percent()).isEqualTo(75.0);
        assertThat(s.percentText()).isEqualTo("75.0");
        assertThat(s.barWidth()).isEqualTo(75);
    }

    @Test
    void nothingRecordedIsZeroNotAnError() {
        assertThat(Summary.EMPTY.total()).isZero();
        assertThat(Summary.EMPTY.percent()).isZero();
    }

    @Test
    void summariesAdd() {
        Summary sum = new Summary(1, 1).plus(new Summary(3, 0));
        assertThat(sum.present()).isEqualTo(4);
        assertThat(sum.absent()).isEqualTo(1);
    }
}
