package com.vwsdigital.minecraftmods.playertimeonserver.util;

import com.mojang.authlib.GameProfile;
import com.vwsdigital.minecraftmods.playertimeonserver.PlayerTimeOnServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class StatsLookup {
	private StatsLookup() {}

	/**
	 * Snapshot every player who has stats on this server. Online players first
	 * (their counters are live), then offline players loaded from the world's
	 * stats directory.
	 */
	public static List<PlayerStatsView> all(MinecraftServer server) {
		Map<UUID, PlayerStatsView> byId = new LinkedHashMap<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			byId.put(
				player.getUUID(),
				new PlayerStatsView(player.getGameProfile(), player.getStats(), true)
			);
		}

		Path statsDir = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
		if (Files.isDirectory(statsDir)) {
			try (Stream<Path> files = Files.list(statsDir)) {
				files
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
					.forEach(file -> tryLoadOffline(server, file, byId));
			} catch (IOException e) {
				PlayerTimeOnServer.LOGGER.warn("Failed to list stats directory {}: {}", statsDir, e.toString());
			}
		}

		return new ArrayList<>(byId.values());
	}

	public static Optional<PlayerStatsView> byName(MinecraftServer server, String name) {
		String target = name.toLowerCase(Locale.ROOT);
		return all(server).stream()
			.filter(v -> {
				String n = v.profile().name();
				return n != null && n.toLowerCase(Locale.ROOT).equals(target);
			})
			.findFirst();
	}

	private static void tryLoadOffline(MinecraftServer server, Path file, Map<UUID, PlayerStatsView> byId) {
		String filename = file.getFileName().toString();
		String uuidStr = filename.substring(0, filename.length() - ".json".length());

		UUID uuid;
		try {
			uuid = UUID.fromString(uuidStr);
		} catch (IllegalArgumentException ignored) {
			return;
		}

		if (byId.containsKey(uuid)) return;

		GameProfile profile = resolveProfile(server, uuid);

		try {
			ServerStatsCounter counter = new ServerStatsCounter(server, file);
			byId.put(uuid, new PlayerStatsView(profile, counter, false));
		} catch (Throwable t) {
			PlayerTimeOnServer.LOGGER.warn("Failed to load stats for {}: {}", uuid, t.toString());
		}
	}

	private static GameProfile resolveProfile(MinecraftServer server, UUID uuid) {
		try {
			var cache = server.services().nameToIdCache();
			if (cache != null) {
				Optional<net.minecraft.server.players.NameAndId> cached = cache.get(uuid);
				if (cached.isPresent()) {
					return new GameProfile(cached.get().id(), cached.get().name());
				}
			}
		} catch (Throwable ignored) {
		}
		return new GameProfile(uuid, uuid.toString());
	}
}
