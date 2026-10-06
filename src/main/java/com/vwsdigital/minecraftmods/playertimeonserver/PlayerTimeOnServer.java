package com.vwsdigital.minecraftmods.playertimeonserver;

import com.vwsdigital.minecraftmods.playertimeonserver.command.StatsCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerTimeOnServer implements ModInitializer {
	public static final String MOD_ID = "player-time-on-server";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			StatsCommand.register(dispatcher));

		LOGGER.info("[{}] Loaded. Admin commands: /pstats list | /pstats player <name> [full] | /pstats top <category> [limit] | /pstats export",
			MOD_ID);
	}
}
