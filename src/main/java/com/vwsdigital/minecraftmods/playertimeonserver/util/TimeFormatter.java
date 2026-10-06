package com.vwsdigital.minecraftmods.playertimeonserver.util;

public final class TimeFormatter {
	private TimeFormatter() {}

	/** Minecraft stores play time in ticks; 20 ticks = 1 second. */
	public static String formatTicks(int ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		long days = seconds / 86400L;
		long hours = (seconds % 86400L) / 3600L;
		long minutes = (seconds % 3600L) / 60L;
		long secs = seconds % 60L;

		StringBuilder sb = new StringBuilder();
		if (days > 0) sb.append(days).append("d ");
		if (hours > 0 || days > 0) sb.append(hours).append("h ");
		if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
		sb.append(secs).append("s");
		return sb.toString();
	}

	/** Distance stats are stored in centimeters. */
	public static String formatCentimeters(int cm) {
		if (cm < 100) return cm + " cm";
		double meters = cm / 100.0;
		if (meters < 1000.0) return String.format("%.1f m", meters);
		return String.format("%.2f km", meters / 1000.0);
	}

	/** DAMAGE_DEALT / DAMAGE_TAKEN are stored as (hearts * 10). */
	public static String formatDamage(int rawTenths) {
		return String.format("%.1f", rawTenths / 10.0);
	}
}
