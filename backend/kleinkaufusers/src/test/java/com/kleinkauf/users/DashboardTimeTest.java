package com.kleinkauf.users;

import com.kleinkauf.users.util.DashboardTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Dashboard days are in Argentine time even though the server stores in UTC
class DashboardTimeTest {

	private final DashboardTime time = new DashboardTime(ZoneId.of("America/Argentina/Buenos_Aires"), ZoneOffset.UTC);

	@Test
	void signUpAt22InArgentinaBelongsToThatDay() {
		// 22:30 on the 1st in Argentina = 01:30 on the 2nd in UTC
		assertEquals(LocalDate.of(2026, 10, 1), time.dayOf(LocalDateTime.of(2026, 10, 2, 1, 30)));
		assertEquals(LocalDate.of(2026, 10, 2), time.dayOf(LocalDateTime.of(2026, 10, 2, 3, 0)));
	}

	// The query range: the Argentine day starts at 3 UTC
	@Test
	void dayStartsAt3Utc() {
		assertEquals(LocalDateTime.of(2026, 10, 1, 3, 0), time.startOfDay(LocalDate.of(2026, 10, 1)));
	}
}
