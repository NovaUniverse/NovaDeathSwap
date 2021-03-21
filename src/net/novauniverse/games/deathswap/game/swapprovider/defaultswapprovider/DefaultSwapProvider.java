package net.novauniverse.games.deathswap.game.swapprovider.defaultswapprovider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import net.novauniverse.games.deathswap.game.swapprovider.SwapProvider;
import net.novauniverse.games.deathswap.game.swapprovider.SwapResult;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.commons.utils.platformindependent.PlatformIndependentPlayerAPI;
import net.zeeraa.novacore.spigot.module.modules.game.GameManager;
import net.zeeraa.novacore.spigot.teams.Team;
import net.zeeraa.novacore.spigot.teams.TeamManager;

public class DefaultSwapProvider implements SwapProvider {
	public static final int MAX_TRIES = 10000;

	private Random random = new Random();

	@Override
	public void onGameStart() {
	}

	@Override
	public void onGameEnd() {
	}

	@Override
	public SwapResult swap() {
		if (GameManager.getInstance().isUseTeams()) {
			return swapWithTeams();
		} else {
			return swapWithoutTeams();
		}
	}

	public SwapResult swapWithoutTeams() {
		List<Player> players = new ArrayList<Player>();

		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (player.isOnline()) {
				if (GameManager.getInstance().getActiveGame().getPlayers().contains(player.getUniqueId())) {
					players.add(player);
				}
			}
		}

		if (players.size() < 2) {
			return SwapResult.NOT_ENOUGH_PLAYERS;
		}

		int tries = 0;
		while (true) {
			/*
			 * Should not happen but i dont want the server to crash if my shitty code does
			 * not work
			 */
			if (tries > MAX_TRIES) {
				Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + "Error: Swap provider " + this.getClass().getName() + " timed out after " + tries + " tries");
				return SwapResult.ERROR;
			}

			tries++;
			List<Integer> toAdd = new ArrayList<Integer>();
			Map<Integer, Integer> swapData = new HashMap<Integer, Integer>();

			// Setup toAdd list
			for (int i = 0; i < players.size(); i++) {
				toAdd.add(i);
			}

			// Insert random data and hope that it forks first try
			for (int i = 0; i < players.size(); i++) {
				swapData.put(i, toAdd.remove(random.nextInt(toAdd.size())));
			}

			/*
			 * Validate the data because we use brute force instead of carefully created
			 * algorithms
			 */
			boolean isFailure = false;
			for (Integer i : swapData.keySet()) {
				if (((int) swapData.get(i)) == ((int) i)) {
					isFailure = true; // Relatable
					break;
				}
			}

			// Reject failures
			if (isFailure) {
				continue;
			}

			// Check where to teleport players to
			Map<Player, Location> tpTo = new HashMap<Player, Location>();
			for (Integer i : swapData.keySet()) {
				tpTo.put(players.get(i), players.get(swapData.get(i)).getLocation().clone());
			}

			// Teleport them
			for (Player player : tpTo.keySet()) {
				player.teleport(tpTo.get(player), TeleportCause.PLUGIN);
			}

			Log.debug("DefaultSwapProvider", "Success after " + tries + " tries");

			return SwapResult.SUCCESS;
		}
	}

	public SwapResult swapWithTeams() {
		List<Team> teams = new ArrayList<Team>();
		for (Team team : TeamManager.getTeamManager().getTeams()) {
			for (UUID uuid : team.getMembers()) {
				if (GameManager.getInstance().getActiveGame().getPlayers().contains(uuid)) {
					// I could use the bukkit api but this will do it in one line
					if (PlatformIndependentPlayerAPI.get().isOnline(uuid)) {
						teams.add(team);
						break; // Almost forgot
					}
				}
			}
		}

		if (teams.size() < 2) {
			return SwapResult.NOT_ENOUGH_TEAMS;
		}

		int tries = 0;
		while (true) {
			/*
			 * Should not happen but i dont want the server to crash if my shitty code does
			 * not work
			 */
			if (tries > MAX_TRIES) {
				Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + "Error: Swap provider " + this.getClass().getName() + " timed out after " + tries + " tries");
				return SwapResult.ERROR;
			}

			tries++;
			List<Integer> toAdd = new ArrayList<Integer>();
			Map<Integer, Integer> swapData = new HashMap<Integer, Integer>();

			// Setup toAdd list
			for (int i = 0; i < teams.size(); i++) {
				toAdd.add(i);
			}

			// Insert random data and hope that it works first try
			for (int i = 0; i < teams.size(); i++) {
				swapData.put(i, toAdd.remove(random.nextInt(toAdd.size())));
			}

			/*
			 * Validate the data because we use brute force instead of carefully created
			 * algorithms
			 */
			boolean isFailure = false;
			for (Integer i : swapData.keySet()) {
				if (((int) swapData.get(i)) == ((int) i)) {
					isFailure = true; // Relatable
					break;
				}
			}

			// Reject failures
			if (isFailure) {
				continue;
			}

			// Check where to teleport players to
			Map<Player, Location> tpTo = new HashMap<Player, Location>();
			for (Integer i : swapData.keySet()) {

				Team team1 = teams.get(i);
				Team team2 = teams.get(swapData.get(i));

				List<Player> team1Players = getTeamOnlineAndAlivePlayers(team1);
				List<Player> team2Players = getTeamOnlineAndAlivePlayers(team2);

				if (team1Players.size() == 0 || team2Players.size() == 0) {
					Log.error("DefaultSwapProvider", "team1Player or team2Players is empty in DefaultSwapProvider");
					continue;
				}

				if (team2Players.size() >= team1Players.size()) {
					// Use real swap
					for (Player p : team1Players) {
						tpTo.put(p, team2Players.remove(random.nextInt(team2Players.size())).getLocation().clone());
					}
				} else {
					// Use random
					for (Player p : team1Players) {
						tpTo.put(p, team2Players.get(random.nextInt(team2Players.size())).getLocation().clone());
					}
				}
			}

			// Teleport them
			for (Player player : tpTo.keySet()) {
				player.teleport(tpTo.get(player), TeleportCause.PLUGIN);
			}

			Log.debug("DefaultSwapProvider", "Success after " + tries + " tries");

			return SwapResult.SUCCESS;
		}
	}

	public List<Player> getTeamOnlineAndAlivePlayers(Team team) {
		List<Player> result = new ArrayList<Player>();

		for (UUID uuid : team.getMembers()) {
			Player player = Bukkit.getServer().getPlayer(uuid);

			if (player != null) {
				if (player.isOnline()) {
					if (GameManager.getInstance().getActiveGame().getPlayers().contains(player.getUniqueId())) {
						if (player.getGameMode() != GameMode.SPECTATOR) {
							result.add(player);
						}
					}
				}
			}
		}

		return result;
	}
}