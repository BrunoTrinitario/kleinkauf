package com.decor.sales;

import com.decor.sales.services.OrderNumberGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNumberGeneratorTest {

	private final OrderNumberGenerator generator = new OrderNumberGenerator();

	@Test
	void formatoXXXX_XXXX_sinCaracteresConfusos() {
		Set<String> numbers = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String number = generator.next();
			assertTrue(number.matches("^[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$"), number);
			numbers.add(number);
		}
		// Aleatorios: en 1000 no deberia repetirse ninguno
		assertTrue(numbers.size() > 990);
	}
}
