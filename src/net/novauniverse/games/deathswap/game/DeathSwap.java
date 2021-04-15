package net.novauniverse.games.deathswap.game;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.util.Vector;

import net.novauniverse.games.deathswap.NovaDeathSwap;
import net.novauniverse.games.deathswap.game.swapprovider.SwapResult;
import net.zeeraa.novacore.commons.log.Log;
import net.zeeraa.novacore.commons.timers.TickCallback;
import net.zeeraa.novacore.commons.utils.Callback;
import net.zeeraa.novacore.commons.utils.RandomGenerator;
import net.zeeraa.novacore.spigot.NovaCore;
import net.zeeraa.novacore.spigot.abstraction.VersionIndependantUtils;
import net.zeeraa.novacore.spigot.abstraction.enums.VersionIndependantSound;
import net.zeeraa.novacore.spigot.command.AllowedSenders;
import net.zeeraa.novacore.spigot.debug.DebugCommandRegistrator;
import net.zeeraa.novacore.spigot.debug.DebugTrigger;
import net.zeeraa.novacore.spigot.language.LanguageManager;
import net.zeeraa.novacore.spigot.module.modules.game.Game;
import net.zeeraa.novacore.spigot.module.modules.game.GameEndReason;
import net.zeeraa.novacore.spigot.module.modules.game.elimination.PlayerQuitEliminationAction;
import net.zeeraa.novacore.spigot.module.modules.game.triggers.DelayedGameTrigger;
import net.zeeraa.novacore.spigot.module.modules.game.triggers.GameTrigger;
import net.zeeraa.novacore.spigot.module.modules.game.triggers.TriggerCallback;
import net.zeeraa.novacore.spigot.module.modules.game.triggers.TriggerFlag;
import net.zeeraa.novacore.spigot.module.modules.multiverse.MultiverseManager;
import net.zeeraa.novacore.spigot.module.modules.multiverse.MultiverseWorld;
import net.zeeraa.novacore.spigot.module.modules.multiverse.PlayerUnloadOption;
import net.zeeraa.novacore.spigot.module.modules.multiverse.WorldOptions;
import net.zeeraa.novacore.spigot.module.modules.multiverse.WorldUnloadOption;
import net.zeeraa.novacore.spigot.teams.Team;
import net.zeeraa.novacore.spigot.teams.TeamManager;
import net.zeeraa.novacore.spigot.timers.BasicTimer;
import net.zeeraa.novacore.spigot.utils.LocationUtils;
import net.zeeraa.novacore.spigot.utils.PlayerUtils;
import net.zeeraa.novacore.spigot.utils.RandomFireworkEffect;
import net.zeeraa.novacore.spigot.world.worldgenerator.worldpregenerator.WorldPreGenerator;

public class DeathSwap extends Game implements Listener {
	/* -=-= Variables =-=- */
	private boolean started;
	private boolean ended;

	private int worldSizeChunks;

	private WorldPreGenerator worldPreGenerator;
	private World netherWorld = null;

	private HashMap<UUID, Location> teamStarterLocations;

	private boolean invulnerabilityEnabled;

	/* -=-= Getters and setters =-=- */
	public WorldPreGenerator getWorldPreGenerator() {
		return worldPreGenerator;
	}

	/* -=-= Game settings =-=- */
	@Override
	public String getName() {
		return "deathswap";
	}

	@Override
	public String getDisplayName() {
		return "Death Swap";
	}

	@Override
	public PlayerQuitEliminationAction getPlayerQuitEliminationAction() {
		return NovaDeathSwap.getInstance().isAllowReconnect() ? PlayerQuitEliminationAction.DELAYED : PlayerQuitEliminationAction.INSTANT;
	}
	
	@Override
	public int getPlayerEliminationDelay() {
		return NovaDeathSwap.getInstance().getReconnectTime();
	}

	@Override
	public boolean eliminatePlayerOnDeath(Player player) {
		return true;
	}

	@Override
	public boolean eliminateIfCombatLogging() {
		return NovaDeathSwap.getInstance().isCombatTagging();
	}

	@Override
	public boolean isPVPEnabled() {
		return false;
	}

	@Override
	public boolean autoEndGame() {
		return true;
	}

	@Override
	public boolean hasStarted() {
		return started;
	}

	@Override
	public boolean hasEnded() {
		return ended;
	}

	@Override
	public boolean isFriendlyFireAllowed() {
		return false;
	}

	@Override
	public boolean canAttack(LivingEntity attacker, LivingEntity target) {
		return true;
	}

	@Override
	public void onLoad() {
		started = false;
		ended = false;

		teamStarterLocations = new HashMap<UUID, Location>();

		this.worldSizeChunks = 64;

		// Create world
		WorldOptions worldOptions = new WorldOptions("deathswap_world");

		/*
		 * if (NovaUHC.getInstance().getSeeds().size() > 0) { long seed =
		 * NovaUHC.getInstance().getSeeds().get(new
		 * Random().nextInt(NovaUHC.getInstance().getSeeds().size()));
		 * 
		 * Log.info("UHC", "Using seed: " + seed);
		 * 
		 * worldOptions.withSeed(seed); }
		 */

		MultiverseWorld multiverseWorld = MultiverseManager.getInstance().createWorld(worldOptions);

		multiverseWorld.setSaveOnUnload(false);
		multiverseWorld.setUnloadOption(WorldUnloadOption.DELETE);

		this.worldPreGenerator = new WorldPreGenerator(multiverseWorld.getWorld(), worldSizeChunks + 10, 32, 1, new Callback() {
			@Override
			public void execute() {
				LanguageManager.broadcast("novauniverse.game.deathswap.world_loaded");

				world.getWorldBorder().setSize((worldSizeChunks * 16) * 2);
				world.getWorldBorder().setCenter(0.5, 0.5);
				world.getWorldBorder().setWarningDistance(20);
				world.getWorldBorder().setDamageBuffer(5);
				world.getWorldBorder().setDamageAmount(5);

				Log.debug("World name: " + world.getName());
				Log.debug("Border size: " + world.getWorldBorder().getSize());
			}
		});

		worldPreGenerator.start();

		world = multiverseWorld.getWorld();
		world.setAutoSave(false);

		// Create nether
		if (NovaDeathSwap.getInstance().isNetherEnabled()) {
			WorldOptions netherOptions = new WorldOptions("deathswap_nether");

			netherOptions.withEnvironment(Environment.NETHER);
			netherOptions.setPlayerUnloadOption(PlayerUnloadOption.SEND_TO_FIRST);

			MultiverseWorld netherMultiverseWorld = MultiverseManager.getInstance().createWorld(netherOptions);

			netherMultiverseWorld.setSaveOnUnload(false);
			netherMultiverseWorld.setUnloadOption(WorldUnloadOption.DELETE);

			netherWorld = netherMultiverseWorld.getWorld();

			netherWorld.setAutoSave(false);
		}

		DelayedGameTrigger swapTrigger = new DelayedGameTrigger("novauniverse.deathswap.swap", getRandomSwapDelay());
		swapTrigger.addCallback(new TriggerCallback() {
			@Override
			public void run(GameTrigger trigger, TriggerFlag reason) {
				swapTrigger.setDelay(getRandomSwapDelay());
				swapTrigger.start();

				if(NovaDeathSwap.getInstance().isUseCountdown()) {
					BasicTimer timer = new BasicTimer(6);
					
					timer.addTickCallback(new TickCallback() {
						@Override
						public void execute(long timeLeft) {
							if(timeLeft == 0) {
								return;
							}
							LanguageManager.broadcast("novauniverse.game.deathswap.swap_countdown", (timeLeft));
						}
					});
					
					timer.addFinishCallback(new Callback() {
						@Override
						public void execute() {
							doSwap();
						}
					});
					
					timer.start();
				} else {
					doSwap();
				}
			}
		});
		swapTrigger.addFlag(TriggerFlag.START_ON_GAME_START);
		swapTrigger.addFlag(TriggerFlag.STOP_ON_GAME_END);

		this.addTrigger(swapTrigger);
		
		DebugCommandRegistrator.getInstance().addDebugTrigger(new DebugTrigger() {
			@Override
			public void onExecute(CommandSender sender, String commandLabel, String[] args) {
				teamStarterLocations.clear();
				tpAllToArena();
			}

			@Override
			public PermissionDefault getPermissionDefault() {
				return PermissionDefault.OP;
			}

			@Override
			public String getPermission() {
				return "novauniverse.debug.deathswap.spawnplayers";
			}

			@Override
			public String getName() {
				return "spawnplayers";
			}

			@Override
			public AllowedSenders getAllowedSenders() {
				return AllowedSenders.ALL;
			}
		});
	}

	@Override
	public void onStart() {
		if (started) {
			return;
		}

		// Nether enabled or disabled message
		LanguageManager.broadcast(NovaDeathSwap.getInstance().isNetherEnabled() ? "novauniverse.game.deathswap.nether_enabled_broadcast" : "novauniverse.game.deathswap.nether_disabled_broadcast");

		tpAllToArena();

		NovaDeathSwap.getInstance().getSwapProvider().onGameStart();

		this.sendBeginEvent();
		
		started = true;
	}
	
	private void tpAllToArena() {
		startInvulnerability();
		for (UUID uuid : players) {
			Player player = Bukkit.getServer().getPlayer(uuid);
			if (player != null) {
				if (player.isOnline()) {
					Location location = null;

					if (TeamManager.hasTeamManager()) {
						Team team = TeamManager.getTeamManager().getPlayerTeam(player);

						if (teamStarterLocations.containsKey(team.getTeamUuid())) {
							location = teamStarterLocations.get(team.getTeamUuid());
						}
					}

					if (location == null) {
						for (int i = 0; i < 30000; i++) {
							location = tryGetSpawnLocation();
							if (location == null) {
								continue;
							}

							if (TeamManager.hasTeamManager()) {
								Team team = TeamManager.getTeamManager().getPlayerTeam(player);
								if (team != null) {
									teamStarterLocations.put(team.getTeamUuid(), location);
								}
							}
							break;
						}
					}

					if (location == null) {
						player.sendMessage(ChatColor.RED + "Failed to teleport within 30000 attempts, Sending you to default world spawn");
						tpToArenaLocation(player, world.getSpawnLocation());
					} else {
						tpToArenaLocation(player, location);
					}
				}
			}
		}
	}


	@Override
	public void onEnd(GameEndReason reason) {
		if (ended) {
			return;
		}

		NovaDeathSwap.getInstance().getSwapProvider().onGameEnd();

		if (worldPreGenerator != null) {
			if (!worldPreGenerator.isFinished()) {
				worldPreGenerator.stop();
			}
		}

		try {
			for (Player p : Bukkit.getServer().getOnlinePlayers()) {
				p.setHealth(p.getMaxHealth());
				p.setFoodLevel(20);
				PlayerUtils.clearPlayerInventory(p);
				PlayerUtils.resetPlayerXP(p);
				p.setGameMode(GameMode.SPECTATOR);
				VersionIndependantUtils.get().playSound(p, p.getLocation(), VersionIndependantSound.WITHER_DEATH, 1F, 1F);

				Firework fw = (Firework) p.getLocation().getWorld().spawnEntity(p.getLocation(), EntityType.FIREWORK);
				FireworkMeta fwm = fw.getFireworkMeta();

				fwm.setPower(2);
				fwm.addEffect(RandomFireworkEffect.randomFireworkEffect());

				fw.setFireworkMeta(fwm);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		ended = true;
	}
	
	@Override
	public boolean canStart() {
		return worldPreGenerator.isFinished();
	}

	/* -=-= Functions =-=- */
	public void doSwap() {
		LanguageManager.broadcast("novauniverse.game.deathswap.swap");

		SwapResult result = NovaDeathSwap.getInstance().getSwapProvider().swap();

		switch (result) {
		case ERROR:
			tryStartInvulnerability();
			LanguageManager.broadcast("novauniverse.game.deathswap.swap.error.failed");
			break;

		case NOT_ENOUGH_PLAYERS:
			LanguageManager.broadcast("novauniverse.game.deathswap.swap.error.not_enough_players");
			break;

		case NOT_ENOUGH_TEAMS:
			LanguageManager.broadcast("novauniverse.game.deathswap.swap.error.not_enough_teams");
			break;

		case SUCCESS:
			tryStartInvulnerability();
			break;

		default:
			break;
		}
	}

	private void tryStartInvulnerability() {
		if (NovaDeathSwap.getInstance().isUseInvulnerability()) {
			startInvulnerability();
		}
	}

	public long getRandomSwapDelay() {
		return RandomGenerator.generate(NovaDeathSwap.MIN_SWAP_TIME_SECONDS, NovaDeathSwap.MAX_SWAP_TIME_SECONDS - NovaDeathSwap.MIN_SWAP_TIME_SECONDS) * 20;
	}

	public void startInvulnerability() {
		invulnerabilityEnabled = true;

		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (player.getGameMode() == GameMode.SURVIVAL) {
				NovaCore.getInstance().getActionBar().sendMessage(player, LanguageManager.getString(player, "novauniverse.game.deathswap.invulnerability_started"));
			}
		}

		Bukkit.getScheduler().scheduleSyncDelayedTask(NovaDeathSwap.getInstance(), new Runnable() {
			@Override
			public void run() {
				invulnerabilityEnabled = false;
				for (Player player : Bukkit.getServer().getOnlinePlayers()) {
					if (player.getGameMode() == GameMode.SURVIVAL) {
						NovaCore.getInstance().getActionBar().sendMessage(player, LanguageManager.getString(player, "novauniverse.game.deathswap.invulnerability_ended"));
					}
				}
			}
		}, 20 * 5);
	}

	private void tpToArenaLocation(Player player, Location location) {
		world.loadChunk(location.getChunk());

		Log.trace("DeathSwap", "tpToArenaLocation() " + player.getName() + " loc: " + location.getBlockX() + " " + location.getY() + " " + location.getBlockZ() + " center: " + LocationUtils.blockCenter(location.getBlockX()) + " " + location.getY() + " " + LocationUtils.blockCenter(location.getBlockZ()));

		Location locationCenter = new Location(world, LocationUtils.blockCenter(location.getBlockX()), location.getY(), LocationUtils.blockCenter(location.getBlockZ()));
		
		player.teleport(locationCenter);
		player.setGameMode(GameMode.SURVIVAL);
		NovaCore.getInstance().getVersionIndependentUtils().setEntityMaxHealth(player, 20);
		PlayerUtils.clearPlayerInventory(player);
		PlayerUtils.resetPlayerXP(player);
		PlayerUtils.clearPotionEffects(player);
		player.setHealth(20);
		player.setFoodLevel(20);
		player.setSaturation(20);
		player.setFallDistance(0);
		
		Bukkit.getScheduler().scheduleSyncDelayedTask(NovaDeathSwap.getInstance(), new Runnable() {
			@Override
			public void run() {
				player.teleport(locationCenter);
			}
		}, 5L);
		
	}

	public Location tryGetSpawnLocation() {
		int max = (worldSizeChunks * 16) - 100;

		Random random = new Random();
		int x = max - random.nextInt(max * 2);
		int z = max - random.nextInt(max * 2);

		Log.trace("Trying location X: " + x + " Z: " + z);

		Location location = new Location(world, x, 256, z);

		for (int i = 256; i > 40; i++) {
			location.setY(location.getY() - 1);

			Block b = location.clone().add(0, -1, 0).getBlock();

			if (b.getType() != Material.AIR) {
				if (b.isLiquid()) {
					break;
				}

				if (b.getType().isSolid()) {
					return location.add(new Vector(0, 2, 0));
				}
			}
		}

		return null;
	}

	@Override
	public void tpToSpectator(Player player) {
		PlayerUtils.setMaxHealth(player, 20);
		PlayerUtils.clearPlayerInventory(player);
		player.setGameMode(GameMode.SPECTATOR);
		player.teleport(world.getSpawnLocation());
	}

	/* -=-= Events =-=- */

	// Prevent death after end and handle invulnerability period
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player) {
			if (this.hasEnded() || invulnerabilityEnabled) {
				e.setCancelled(true);
			}
		}
	}

	// Prevent nether
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onPortal(PlayerPortalEvent e) {
		if (!NovaDeathSwap.getInstance().isNetherEnabled()) {
			e.setCancelled(true);
			e.getPlayer().sendMessage(LanguageManager.getString(e.getPlayer(), "novauniverse.game.deathswap.nether_disabled"));
		} else {
			Player player = e.getPlayer();

			if (e.getCause() == PlayerPortalEvent.TeleportCause.NETHER_PORTAL) {
				e.useTravelAgent(true);
				e.getPortalTravelAgent().setCanCreatePortal(true);
				Location location;
				if (player.getWorld() == getWorld()) {
					if (netherWorld == null) {
						e.getPlayer().sendMessage(ChatColor.DARK_RED + "ERR:NETHER_NOT_INITIALIZED");
						return;
					}

					location = new Location(netherWorld, e.getFrom().getBlockX() / 8, e.getFrom().getBlockY(), e.getFrom().getBlockZ() / 8);

					player.sendMessage(LanguageManager.getString(player, "novauniverse.game.deathswap.portal_border_warning"));
				} else {
					location = new Location(getWorld(), e.getFrom().getBlockX() * 8, e.getFrom().getBlockY(), e.getFrom().getBlockZ() * 8);
				}
				e.setTo(e.getPortalTravelAgent().findOrCreate(location));
			}
		}
	}

	// Handle respawn
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerRespawn(PlayerRespawnEvent e) {
		if (hasStarted()) {
			Player player = e.getPlayer();

			Log.debug("Respawn location for " + e.getPlayer().getName() + " is " + world.getSpawnLocation() + " at world " + world.getName());

			e.setRespawnLocation(world.getSpawnLocation());

			Bukkit.getScheduler().scheduleSyncDelayedTask(NovaDeathSwap.getInstance(), new Runnable() {
				@Override
				public void run() {
					tpToSpectator(player);
				}
			}, 3L);
		}
	}

	// Respawn players on death
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerDeath(PlayerDeathEvent e) {
		e.getEntity().spigot().respawn();
	}
}
