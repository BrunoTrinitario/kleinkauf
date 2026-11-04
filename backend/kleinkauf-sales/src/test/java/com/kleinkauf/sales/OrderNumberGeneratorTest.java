package com.kleinkauf.sales;

import com.kleinkauf.sales.services.OrderNumberGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNumberGeneratorTest {

	private final OrderNumberGenerator generator = new OrderNumberGenerator();

	@Test
	void formatXXXX_XXXX_withoutConfusingCharacters() {
		Set<String> numbers = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String number = generator.next();
			assertTrue(number.matches("^[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$"), number);
			numbers.add(number);
		}
		// Random: none should repeat in 1000
		assertTrue(numbers.size() > 990);
	}
}
