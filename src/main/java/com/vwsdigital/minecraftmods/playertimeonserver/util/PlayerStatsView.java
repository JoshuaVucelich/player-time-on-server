package com.vwsdigital.minecraftmods.playertimeonserver.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.stats.ServerStatsCounter;

/**
 * A read-only handle to a player's stats. Wraps both online players (live counter)
 * and offline players (counter loaded from disk).
 */
public record PlayerStatsView(GameProfile profile, ServerStatsCounter stats, boolean online) {

	public String displayName() {
		String name = profile.name();
		if (name == null || name.isEmpty()) {
			return profile.id().toString();
		}
		return name;
	}
}
