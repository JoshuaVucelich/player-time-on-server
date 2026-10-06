package com.vwsdigital.minecraftmods.playertimeonserver.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.vwsdigital.minecraftmods.playertimeonserver.PlayerTimeOnServer;
import com.vwsdigital.minecraftmods.playertimeonserver.util.PlayerStatsView;
import com.vwsdigital.minecraftmods.playertimeonserver.util.StatsLookup;
import com.vwsdigital.minecraftmods.playertimeonserver.util.TimeFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class StatsCommand {
	private StatsCommand() {}

	/** A leaderboard category: how to pull the value from a stats counter, and how to format it for display. */
	private record Category(String label, ToIntFunction<ServerStatsCounter> extract, IntFunction<String> format) {}

	private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();
	static {
		CATEGORIES.put("playtime",      new Category("Play Time",     h -> h.getValue(Stats.CUSTOM, Stats.PLAY_TIME),     TimeFormatter::formatTicks));
		CATEGORIES.put("deaths",        new Category("Deaths",        h -> h.getValue(Stats.CUSTOM, Stats.DEATHS),        StatsCommand::asInt));
		CATEGORIES.put("mob_kills",     new Category("Mob Kills",     h -> h.getValue(Stats.CUSTOM, Stats.MOB_KILLS),     StatsCommand::asInt));
		CATEGORIES.put("player_kills",  new Category("Player Kills",  h -> h.getValue(Stats.CUSTOM, Stats.PLAYER_KILLS),  StatsCommand::asInt));
		CATEGORIES.put("jumps",         new Category("Jumps",         h -> h.getValue(Stats.CUSTOM, Stats.JUMP),          StatsCommand::asInt));
		CATEGORIES.put("damage_dealt",  new Category("Damage Dealt",  h -> h.getValue(Stats.CUSTOM, Stats.DAMAGE_DEALT),  TimeFormatter::formatDamage));
		CATEGORIES.put("damage_taken",  new Category("Damage Taken",  h -> h.getValue(Stats.CUSTOM, Stats.DAMAGE_TAKEN),  TimeFormatter::formatDamage));
		CATEGORIES.put("walked",        new Category("Walked",        h -> h.getValue(Stats.CUSTOM, Stats.WALK_ONE_CM),   TimeFormatter::formatCentimeters));
		CATEGORIES.put("sprinted",      new Category("Sprinted",      h -> h.getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM), TimeFormatter::formatCentimeters));
		CATEGORIES.put("crouched",      new Category("Crouched",      h -> h.getValue(Stats.CUSTOM, Stats.CROUCH_ONE_CM), TimeFormatter::formatCentimeters));
		CATEGORIES.put("blocks_mined",  new Category("Blocks Mined",  h -> sumOver(BuiltInRegistries.BLOCK,       Stats.BLOCK_MINED,     h), StatsCommand::asInt));
		CATEGORIES.put("items_used",    new Category("Items Used",    h -> sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_USED, h), StatsCommand::asInt));
		CATEGORIES.put("items_broken",  new Category("Items Broken",  h -> sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_BROKEN, h), StatsCommand::asInt));
		CATEGORIES.put("items_crafted", new Category("Items Crafted", h -> sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_CRAFTED, h), StatsCommand::asInt));
		CATEGORIES.put("items_picked",  new Category("Items Picked",  h -> sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_PICKED_UP, h), StatsCommand::asInt));
		CATEGORIES.put("items_dropped", new Category("Items Dropped", h -> sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_DROPPED, h), StatsCommand::asInt));
	}

	private static String asInt(int v) { return Integer.toString(v); }

	private static <T> int sumOver(Registry<T> registry, StatType<T> type, ServerStatsCounter counter) {
		int total = 0;
		for (T entry : registry) {
			total += counter.getValue(type.get(entry));
		}
		return total;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("pstats")
			.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
			.executes(StatsCommand::help)
			.then(literal("help").executes(StatsCommand::help))
			.then(literal("list").executes(StatsCommand::cmdList))
			.then(literal("player")
				.then(argument("name", StringArgumentType.word())
					.suggests(suggestPlayers())
					.executes(ctx -> cmdPlayer(ctx, false))
					.then(literal("full")
						.executes(ctx -> cmdPlayer(ctx, true)))))
			.then(literal("top")
				.then(argument("category", StringArgumentType.word())
					.suggests(suggestCategories())
					.executes(ctx -> cmdTop(ctx, 10))
					.then(argument("limit", IntegerArgumentType.integer(1, 100))
						.executes(ctx -> cmdTop(ctx, IntegerArgumentType.getInteger(ctx, "limit"))))))
			.then(literal("export").executes(StatsCommand::cmdExport))
		);
	}

	private static SuggestionProvider<CommandSourceStack> suggestPlayers() {
		return (ctx, builder) -> {
			for (PlayerStatsView v : StatsLookup.all(ctx.getSource().getServer())) {
				String n = v.profile().name();
				if (n != null) builder.suggest(n);
			}
			return builder.buildFuture();
		};
	}

	private static SuggestionProvider<CommandSourceStack> suggestCategories() {
		return (ctx, builder) -> {
			for (String k : CATEGORIES.keySet()) builder.suggest(k);
			return builder.buildFuture();
		};
	}

	// -------------------------- handlers --------------------------

	private static int help(CommandContext<CommandSourceStack> ctx) {
		var src = ctx.getSource();
		src.sendSuccess(() -> Component.literal("Player Time on Server — admin commands").withStyle(ChatFormatting.GOLD), false);
		src.sendSuccess(() -> Component.literal("  /pstats list").withStyle(ChatFormatting.YELLOW), false);
		src.sendSuccess(() -> Component.literal("  /pstats player <name> [full]").withStyle(ChatFormatting.YELLOW), false);
		src.sendSuccess(() -> Component.literal("  /pstats top <category> [limit]").withStyle(ChatFormatting.YELLOW), false);
		src.sendSuccess(() -> Component.literal("  /pstats export").withStyle(ChatFormatting.YELLOW), false);
		src.sendSuccess(() -> Component.literal("Categories: " + String.join(", ", CATEGORIES.keySet())).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int cmdList(CommandContext<CommandSourceStack> ctx) {
		var src = ctx.getSource();
		List<PlayerStatsView> all = StatsLookup.all(src.getServer());
		src.sendSuccess(() -> Component.literal("Players with stats (" + all.size() + ")").withStyle(ChatFormatting.GOLD), false);
		for (PlayerStatsView v : all) {
			ChatFormatting color = v.online() ? ChatFormatting.GREEN : ChatFormatting.GRAY;
			String tag = v.online() ? " (online)" : " (offline)";
			int ticks = v.stats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
			src.sendSuccess(() -> Component.literal("  " + v.displayName() + tag + "  " + TimeFormatter.formatTicks(ticks))
				.withStyle(color), false);
		}
		return all.size();
	}

	private static int cmdPlayer(CommandContext<CommandSourceStack> ctx, boolean full) throws CommandSyntaxException {
		var src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		var maybe = StatsLookup.byName(src.getServer(), name);
		if (maybe.isEmpty()) {
			src.sendFailure(Component.literal("No player found with name '" + name + "'"));
			return 0;
		}
		PlayerStatsView v = maybe.get();
		ServerStatsCounter h = v.stats();

		String header = "=== " + v.displayName() + " " + (v.online() ? "(online)" : "(offline)") + " ===";
		src.sendSuccess(() -> Component.literal(header).withStyle(ChatFormatting.GOLD), false);

		line(src, "Play time",           TimeFormatter.formatTicks(h.getValue(Stats.CUSTOM, Stats.PLAY_TIME)));
		line(src, "Deaths",              String.valueOf(h.getValue(Stats.CUSTOM, Stats.DEATHS)));
		line(src, "Mob kills",           String.valueOf(h.getValue(Stats.CUSTOM, Stats.MOB_KILLS)));
		line(src, "Player kills",        String.valueOf(h.getValue(Stats.CUSTOM, Stats.PLAYER_KILLS)));
		line(src, "Damage dealt",        TimeFormatter.formatDamage(h.getValue(Stats.CUSTOM, Stats.DAMAGE_DEALT)));
		line(src, "Damage taken",        TimeFormatter.formatDamage(h.getValue(Stats.CUSTOM, Stats.DAMAGE_TAKEN)));
		line(src, "Walked",              TimeFormatter.formatCentimeters(h.getValue(Stats.CUSTOM, Stats.WALK_ONE_CM)));
		line(src, "Sprinted",            TimeFormatter.formatCentimeters(h.getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM)));
		line(src, "Jumps",               String.valueOf(h.getValue(Stats.CUSTOM, Stats.JUMP)));
		line(src, "Blocks mined (all)",  String.valueOf(sumOver(BuiltInRegistries.BLOCK,       Stats.BLOCK_MINED,         h)));
		line(src, "Items used (all)",    String.valueOf(sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_USED,     h)));
		line(src, "Items crafted (all)", String.valueOf(sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_CRAFTED,  h)));
		line(src, "Tools broken (all)",  String.valueOf(sumOver(BuiltInRegistries.ITEM,        Stats.ITEM_BROKEN,   h)));

		// Top 5 mined blocks
		List<TopEntry<net.minecraft.world.level.block.Block>> topMined = topEntries(BuiltInRegistries.BLOCK, Stats.BLOCK_MINED, h, 5);
		if (!topMined.isEmpty()) {
			src.sendSuccess(() -> Component.literal("Top mined blocks:").withStyle(ChatFormatting.AQUA), false);
			for (var e : topMined) {
				String id = BuiltInRegistries.BLOCK.getKey(e.key()).toString();
				src.sendSuccess(() -> Component.literal("    " + id + " — " + e.value()).withStyle(ChatFormatting.WHITE), false);
			}
		}

		// Top 5 killed entities
		List<TopEntry<net.minecraft.world.entity.EntityType<?>>> topKilled = topEntries(BuiltInRegistries.ENTITY_TYPE, Stats.ENTITY_KILLED, h, 5);
		if (!topKilled.isEmpty()) {
			src.sendSuccess(() -> Component.literal("Top killed mobs:").withStyle(ChatFormatting.AQUA), false);
			for (var e : topKilled) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.key()).toString();
				src.sendSuccess(() -> Component.literal("    " + id + " — " + e.value()).withStyle(ChatFormatting.WHITE), false);
			}
		}

		if (full) {
			src.sendSuccess(() -> Component.literal("--- Full stats (non-zero only) ---").withStyle(ChatFormatting.GOLD), false);
			dumpCustom(src, h);
			dumpRegistryType(src, "mined",     BuiltInRegistries.BLOCK,       Stats.BLOCK_MINED,            h);
			dumpRegistryType(src, "used",      BuiltInRegistries.ITEM,        Stats.ITEM_USED,        h);
			dumpRegistryType(src, "broken",    BuiltInRegistries.ITEM,        Stats.ITEM_BROKEN,      h);
			dumpRegistryType(src, "crafted",   BuiltInRegistries.ITEM,        Stats.ITEM_CRAFTED,     h);
			dumpRegistryType(src, "picked_up", BuiltInRegistries.ITEM,        Stats.ITEM_PICKED_UP,   h);
			dumpRegistryType(src, "dropped",   BuiltInRegistries.ITEM,        Stats.ITEM_DROPPED,     h);
			dumpRegistryType(src, "killed",    BuiltInRegistries.ENTITY_TYPE, Stats.ENTITY_KILLED,    h);
			dumpRegistryType(src, "killed_by", BuiltInRegistries.ENTITY_TYPE, Stats.ENTITY_KILLED_BY, h);
		}

		return 1;
	}

	private static int cmdTop(CommandContext<CommandSourceStack> ctx, int limit) {
		var src = ctx.getSource();
		String key = StringArgumentType.getString(ctx, "category").toLowerCase(Locale.ROOT);
		Category cat = CATEGORIES.get(key);
		if (cat == null) {
			src.sendFailure(Component.literal("Unknown category '" + key + "'. Try one of: " + String.join(", ", CATEGORIES.keySet())));
			return 0;
		}

		record Row(PlayerStatsView v, int value) {}
		List<Row> rows = new ArrayList<>();
		for (PlayerStatsView v : StatsLookup.all(src.getServer())) {
			rows.add(new Row(v, cat.extract().applyAsInt(v.stats())));
		}
		rows.sort(Comparator.comparingInt(Row::value).reversed());

		src.sendSuccess(() -> Component.literal("Top " + limit + " — " + cat.label()).withStyle(ChatFormatting.GOLD), false);
		int shown = 0;
		for (Row r : rows) {
			if (shown >= limit) break;
			if (r.value() == 0 && shown > 0) break;
			final int rank = shown + 1;
			final int val = r.value();
			final String display = r.v().displayName();
			ChatFormatting color = r.v().online() ? ChatFormatting.GREEN : ChatFormatting.WHITE;
			src.sendSuccess(() -> Component.literal(String.format("  %2d. %-16s  %s", rank, display, cat.format().apply(val)))
				.withStyle(color), false);
			shown++;
		}
		if (shown == 0) src.sendSuccess(() -> Component.literal("  (no data)").withStyle(ChatFormatting.GRAY), false);
		return shown;
	}

	private static int cmdExport(CommandContext<CommandSourceStack> ctx) {
		var src = ctx.getSource();
		MinecraftServer server = src.getServer();
		List<PlayerStatsView> all = StatsLookup.all(server);

		Path root = server.getWorldPath(LevelResource.ROOT);
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path out = root.resolve("player-time-on-server-export-" + stamp + ".tsv");

		StringBuilder sb = new StringBuilder();
		sb.append("name\tuuid\tonline");
		for (String k : CATEGORIES.keySet()) sb.append('\t').append(k);
		sb.append('\n');

		for (PlayerStatsView v : all) {
			sb.append(v.displayName()).append('\t')
			  .append(v.profile().id()).append('\t')
			  .append(v.online() ? "yes" : "no");
			for (Category cat : CATEGORIES.values()) {
				sb.append('\t').append(cat.extract().applyAsInt(v.stats()));
			}
			sb.append('\n');
		}

		try {
			Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
			src.sendSuccess(() -> Component.literal("Exported " + all.size() + " players to " + out.getFileName()).withStyle(ChatFormatting.GREEN), true);
			PlayerTimeOnServer.LOGGER.info("Wrote stats export to {}", out);
			return all.size();
		} catch (IOException e) {
			src.sendFailure(Component.literal("Export failed: " + e.getMessage()));
			PlayerTimeOnServer.LOGGER.error("Stats export failed", e);
			return 0;
		}
	}

	// -------------------------- helpers --------------------------

	private record TopEntry<T>(T key, int value) {}

	private static <T> List<TopEntry<T>> topEntries(Registry<T> registry, StatType<T> type, ServerStatsCounter h, int limit) {
		List<TopEntry<T>> entries = new ArrayList<>();
		for (T entry : registry) {
			int v = h.getValue(type.get(entry));
			if (v > 0) entries.add(new TopEntry<>(entry, v));
		}
		entries.sort(Comparator.<TopEntry<T>>comparingInt(TopEntry::value).reversed());
		return entries.subList(0, Math.min(limit, entries.size()));
	}

	private static void line(CommandSourceStack src, String label, String value) {
		src.sendSuccess(() -> Component.literal(label + ": ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
	}

	private static void dumpCustom(CommandSourceStack src, ServerStatsCounter h) {
		for (Identifier id : BuiltInRegistries.CUSTOM_STAT) {
			int v = h.getValue(Stats.CUSTOM, id);
			if (v == 0) continue;
			String formatted = formatCustom(id, v);
			src.sendSuccess(() -> Component.literal("  custom " + id + ": " + formatted).withStyle(ChatFormatting.GRAY), false);
		}
	}

	private static String formatCustom(Identifier id, int v) {
		String path = id.getPath();
		if (path.endsWith("_one_cm")) return TimeFormatter.formatCentimeters(v);
		if (path.contains("time") || path.contains("since")) return TimeFormatter.formatTicks(v);
		if (path.startsWith("damage_")) return TimeFormatter.formatDamage(v);
		return Integer.toString(v);
	}

	private static <T> void dumpRegistryType(CommandSourceStack src, String label, Registry<T> registry, StatType<T> type, ServerStatsCounter h) {
		for (T entry : registry) {
			int v = h.getValue(type.get(entry));
			if (v == 0) continue;
			String id = registry.getKey(entry).toString();
			src.sendSuccess(() -> Component.literal("  " + label + " " + id + ": " + v).withStyle(ChatFormatting.GRAY), false);
		}
	}
}
