package com.kleinkauf.users;

import com.kleinkauf.users.util.DashboardTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Los dias de los paneles son en hora argentina aunque el servidor guarde en UTC
class DashboardTimeTest {

	private final DashboardTime time = new DashboardTime(ZoneId.of("America/Argentina/Buenos_Aires"), ZoneOffset.UTC);

	@Test
	void unaAltaALas22EnArgentinaEsDeEseDia() {
		// 22:30 del 1 en Argentina = 01:30 del 2 en UTC
		assertEquals(LocalDate.of(2026, 10, 1), time.dayOf(LocalDateTime.of(2026, 10, 2, 1, 30)));
		assertEquals(LocalDate.of(2026, 10, 2), time.dayOf(LocalDateTime.of(2026, 10, 2, 3, 0)));
	}

	// El rango de la consulta: el dia argentino empieza a las 3 UTC
	@Test
	void elDiaEmpiezaALas3UTC() {
		assertEquals(LocalDateTime.of(2026, 10, 1, 3, 0), time.startOfDay(LocalDate.of(2026, 10, 1)));
	}
}
