package com.vwsdigital.minecraftmods.playertimeonserver;

import com.vwsdigital.minecraftmods.playertimeonserver.command.StatsCommand;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PlayerTimeOnServer.MOD_ID)
public class PlayerTimeOnServer {
	public static final String MOD_ID = "player-time-on-server";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public PlayerTimeOnServer() {
		NeoForge.EVENT_BUS.addListener(PlayerTimeOnServer::registerCommands);

		LOGGER.info("[{}] Loaded. Admin commands: /pstats list | /pstats player <name> [full] | /pstats top <category> [limit] | /pstats export",
			MOD_ID);
	}

	private static void registerCommands(RegisterCommandsEvent event) {
		StatsCommand.register(event.getDispatcher());
	}
}
