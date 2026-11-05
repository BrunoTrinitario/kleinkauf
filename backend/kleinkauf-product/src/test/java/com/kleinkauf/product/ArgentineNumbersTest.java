package com.kleinkauf.product;

import com.kleinkauf.product.util.ArgentineNumbers;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArgentineNumbersTest {

	@Test
	void dotIsThousandsAndCommaIsDecimal() {
		assertEquals(new BigDecimal("15000"), ArgentineNumbers.parse("15.000"));
		assertEquals(new BigDecimal("15000.50"), ArgentineNumbers.parse("15.000,50"));
		assertEquals(new BigDecimal("1500000"), ArgentineNumbers.parse("1.500.000"));
		assertEquals(new BigDecimal("15.5"), ArgentineNumbers.parse("15,5"));
		assertEquals(new BigDecimal("15000.5"), ArgentineNumbers.parse("15000,5"));
		assertEquals(new BigDecimal("7"), ArgentineNumbers.parse(" 7 "));
	}

	@Test
	void ambiguousOrInvalidFormats_areAnError() {
		for (String value : List.of("15.5", "1.50", "15.0000", "15,000.50", "abc", "", "15,", ",5", "$100", "1 000")) {
			assertThrows(NumberFormatException.class, () -> ArgentineNumbers.parse(value), value);
		}
	}

	@Test
	void formatsWithoutThousandsToAllowReimport() {
		assertEquals("15000,5", ArgentineNumbers.format(new BigDecimal("15000.5")));
		assertEquals("15000,50", ArgentineNumbers.format(new BigDecimal("15000.50")));
		assertEquals("", ArgentineNumbers.format((BigDecimal) null));
		assertEquals("1,5", ArgentineNumbers.format(1.5));
		assertEquals("80", ArgentineNumbers.format(80.0));
		assertEquals("0", ArgentineNumbers.format(0.0));
	}
}
