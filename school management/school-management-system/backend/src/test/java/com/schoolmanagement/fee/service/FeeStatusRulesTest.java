package com.schoolmanagement.fee.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.schoolmanagement.fee.entity.FeeStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FeeStatusRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    private static FeeStatus status(boolean waived, String paid, String due) {
        return FeeStatusRules.of(waived, new BigDecimal("1000.00"), new BigDecimal(paid), LocalDate.parse(due), TODAY,
                14);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 2026-12-04, NOT_DUE", "0, 2026-12-03, DUE", "0, 2026-11-19, DUE", "0, 2026-11-18, OVERDUE",
            "400, 2026-11-19, PARTIALLY_PAID", "400, 2026-11-18, OVERDUE", "400, 2027-03-01, PARTIALLY_PAID",
            "1000, 2026-11-18, PAID", "1000, 2027-03-01, PAID", "999.99, 2026-11-20, PARTIALLY_PAID"})
    void statusFollowsPaymentsAndTheDueDate(String paid, String due, FeeStatus expected) {
        assertThat(status(false, paid, due)).isEqualTo(expected);
    }

    @Test
    void aWaivedFeeIsWaivedWhateverTheDatesSay() {
        assertThat(status(true, "0", "2026-01-01")).isEqualTo(FeeStatus.WAIVED);
        assertThat(status(true, "0", "2027-01-01")).isEqualTo(FeeStatus.WAIVED);
    }

    @Test
    void theDueSoonWindowIsConfigurable() {
        assertThat(FeeStatusRules.of(false, BigDecimal.TEN, BigDecimal.ZERO, TODAY.plusDays(30), TODAY, 30))
                .isEqualTo(FeeStatus.DUE);
        assertThat(FeeStatusRules.of(false, BigDecimal.TEN, BigDecimal.ZERO, TODAY.plusDays(30), TODAY, 29))
                .isEqualTo(FeeStatus.NOT_DUE);
    }
}
