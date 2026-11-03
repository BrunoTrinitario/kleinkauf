package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.dtos.platform.PeriodCountDto;
import com.kleinkauf.users.dtos.platform.RegistrationsDto;
import com.kleinkauf.users.util.Periods;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeriodsTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

	@Test
	void sinParametros_ultimos30DiasPorDia() throws ApiException {
		Periods.Range range = Periods.parse(null, null, null, TODAY);

		assertEquals(LocalDate.of(2026, 10, 4), range.from());
		assertEquals(TODAY, range.to());
		assertEquals(Periods.GroupBy.DAY, range.groupBy());
		assertEquals(30, Periods.series(range, List.of()).periods().size());
	}

	@Test
	void porDia_incluyeLosPeriodosEnCero() throws ApiException {
		Periods.Range range = Periods.parse("2026-10-01", "2026-10-03", "day", TODAY);

		RegistrationsDto series = Periods.series(range, List.of(
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3)));

		assertEquals(List.of(
				new PeriodCountDto(LocalDate.of(2026, 10, 1), 1),
				new PeriodCountDto(LocalDate.of(2026, 10, 2), 0),
				new PeriodCountDto(LocalDate.of(2026, 10, 3), 2)), series.periods());
		assertEquals("DAY", series.groupBy());
	}

	@Test
	void porSemana_empiezaElLunesYCruzaElAnio() throws ApiException {
		// 2025-12-31 es miercoles: su semana empieza el lunes 29; 2026-01-05 es lunes
		Periods.Range range = Periods.parse("2025-12-31", "2026-01-06", "WEEK", TODAY);

		RegistrationsDto series = Periods.series(range, List.of(
				LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5)));

		assertEquals(List.of(
				new PeriodCountDto(LocalDate.of(2025, 12, 29), 2),
				new PeriodCountDto(LocalDate.of(2026, 1, 5), 1)), series.periods());
	}

	@Test
	void porMes() throws ApiException {
		Periods.Range range = Periods.parse("2026-08-15", "2026-10-02", "MONTH", TODAY);

		RegistrationsDto series = Periods.series(range, List.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1)));

		assertEquals(List.of(
				new PeriodCountDto(LocalDate.of(2026, 8, 1), 0),
				new PeriodCountDto(LocalDate.of(2026, 9, 1), 1),
				new PeriodCountDto(LocalDate.of(2026, 10, 1), 1)), series.periods());
	}

	@Test
	void unSoloDia_yFechasFueraDelRangoNoCuentan() throws ApiException {
		Periods.Range range = Periods.parse("2026-10-01", "2026-10-01", null, TODAY);

		RegistrationsDto series = Periods.series(range, List.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1)));

		assertEquals(List.of(new PeriodCountDto(LocalDate.of(2026, 10, 1), 1)), series.periods());
	}

	@Test
	void fromDespuesDeTo_respondeBadRequest() {
		ApiException ex = assertThrows(ApiException.class, () -> Periods.parse("2026-10-02", "2026-10-01", null, TODAY));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
	}

	@Test
	void topeDePeriodos() throws ApiException {
		// 1000 dias entran justo; 1001 no. Por mes, el mismo rango es corto
		Periods.parse("2024-02-06", "2026-11-01", "DAY", TODAY);
		assertThrows(ApiException.class, () -> Periods.parse("2024-02-05", "2026-11-01", "DAY", TODAY));
		Periods.parse("2000-01-01", "2026-11-01", "MONTH", TODAY);
	}

	@Test
	void parametrosInvalidos_respondenBadRequest() {
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ApiException.class, () -> Periods.parse(null, null, "YEAR", TODAY)).getStatus());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ApiException.class, () -> Periods.parse("02/10/2026", null, null, TODAY)).getStatus());
	}
}
