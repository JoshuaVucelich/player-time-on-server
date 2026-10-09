package com.vwsdigital.minecraftmods.playertimeonserver.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatterTest {
	@BeforeAll
	static void useUsLocale() {
		Locale.setDefault(Locale.US);
	}

	@Test
	void formatsTicksAsDaysHoursMinutesSeconds() {
		assertEquals("0s", TimeFormatter.formatTicks(0));
		assertEquals("0s", TimeFormatter.formatTicks(-40));
		assertEquals("1s", TimeFormatter.formatTicks(20));
		assertEquals("1m 0s", TimeFormatter.formatTicks(20 * 60));
		assertEquals("1h 0m 0s", TimeFormatter.formatTicks(20 * 3600));
		assertEquals("1d 0h 0m 0s", TimeFormatter.formatTicks(20 * 86400));
		assertEquals("1d 2h 3m 4s", TimeFormatter.formatTicks(20 * (86400 + 2 * 3600 + 3 * 60 + 4)));
	}

	@Test
	void formatsCentimeters() {
		assertEquals("0 cm", TimeFormatter.formatCentimeters(0));
		assertEquals("99 cm", TimeFormatter.formatCentimeters(99));
		assertEquals("1.5 m", TimeFormatter.formatCentimeters(150));
		assertEquals("999.0 m", TimeFormatter.formatCentimeters(99900));
		assertEquals("1.00 km", TimeFormatter.formatCentimeters(100000));
		assertEquals("1.50 km", TimeFormatter.formatCentimeters(150000));
	}

	@Test
	void formatsDamageInTenthsOfAHeart() {
		assertEquals("0.0", TimeFormatter.formatDamage(0));
		assertEquals("1.5", TimeFormatter.formatDamage(15));
		assertEquals("10.0", TimeFormatter.formatDamage(100));
	}
}
