package net.novauniverse.games.deathswap;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.novauniverse.games.deathswap.game.DeathSwap;
import net.novauniverse.games.deathswap.game.swapprovider.SwapProvider;
import net.novauniverse.games.deathswap.game.swapprovider.defaultswapprovider.DefaultSwapProvider;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.GameManager;
import net.zeeraa.novacore.spigot.gameengine.module.modules.gamelobby.GameLobby;
import net.zeeraa.novacore.spigot.language.LanguageReader;
import net.zeeraa.novacore.spigot.module.ModuleManager;

public class NovaDeathSwap extends JavaPlugin implements Listener {
	private static NovaDeathSwap instance;

	public static NovaDeathSwap getInstance() {
		return instance;
	}

	/* Notes on how the game works from sethblings video */
	// 5 seconds of invulnerability on swap
	// Minimum swap time is 20 seconds
	// The message when swapping is: [@] Commencing swap!

	private DeathSwap game;

	private boolean allowReconnect;
	private boolean combatTagging;
	private boolean netherEnabled;
	private boolean useInvulnerability;
	private boolean useCountdown;
	private int reconnectTime;

	private SwapProvider swapProvider;

	public static final int MAX_SWAP_TIME_SECONDS = 60 * 5; /* 5 minutes */
	public static final int MIN_SWAP_TIME_SECONDS = 20; /* 20 seconds */

	public SwapProvider getSwapProvider() {
		return swapProvider;
	}

	public void setSwapProvider(SwapProvider swapProvider) {
		this.swapProvider = swapProvider;
	}

	public boolean isAllowReconnect() {
		return allowReconnect;
	}

	public boolean isCombatTagging() {
		return combatTagging;
	}

	public int getReconnectTime() {
		return reconnectTime;
	}

	public boolean isNetherEnabled() {
		return netherEnabled;
	}
	
	public boolean isUseCountdown() {
		return useCountdown;
	}
	
	public boolean isUseInvulnerability() {
		return useInvulnerability;
	}

	public DeathSwap getGame() {
		return game;
	}

	@Override
	public void onEnable() {
		NovaDeathSwap.instance = this;

		swapProvider = new DefaultSwapProvider();

		saveDefaultConfig();

		Log.info("DeathSwap", "Loading language files...");
		try {
			LanguageReader.readFromJar(this.getClass(), "/lang/en-us.json");
		} catch (Exception e) {
			e.printStackTrace();
		}

		allowReconnect = getConfig().getBoolean("allow_reconnect");
		combatTagging = getConfig().getBoolean("combat_tagging");
		netherEnabled = getConfig().getBoolean("nether_enabled");
		useInvulnerability = getConfig().getBoolean("use_invulnerability");
		useCountdown = getConfig().getBoolean("use_countdown");
		reconnectTime = getConfig().getInt("player_elimination_delay");
		
		GameManager.getInstance().setUseCombatTagging(combatTagging);

		try {
			FileUtils.forceMkdir(getDataFolder());
		} catch (IOException e1) {
			e1.printStackTrace();
			Log.fatal("DeathSwap", "Failed to setup data directory");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		// Enable required modules
		ModuleManager.require(GameManager.class);
		ModuleManager.require(GameLobby.class);

		// Init game
		this.game = new DeathSwap();

		GameManager.getInstance().loadGame(game);

		Bukkit.getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		Bukkit.getScheduler().cancelTasks(this);
		HandlerList.unregisterAll((Plugin) this);
	}
}