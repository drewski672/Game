package com.openrsc.server.plugins.commands;

import org.apache.commons.lang.StringUtils;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.openrsc.server.Constants;
import com.openrsc.server.Server;
import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.ItemDropDef;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.Region;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.PluginHandler;
import com.openrsc.server.plugins.listeners.action.CommandListener;
import com.openrsc.server.sql.DatabaseConnection;
import com.openrsc.server.sql.GameLogging;
import com.openrsc.server.sql.query.logs.StaffLog;
import com.openrsc.server.util.EntityList;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;

public final class Admins implements CommandListener {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final World world = World.getWorld();
	private DelayedEvent globalDropEvent;
	private int count = 0;
        
        @Override
	public void onCommand(String command, String[] args, final Player player) {
		if (!player.isAdmin()) {
			return;
		}
		if (command.equals("addbank")) {
			for (int i = 0; i < 180; i++) {
				player.getBank().add(new Item(i, 1));
			}
			player.message("Added bank items.");
		}
		if (command.equals("uniqueonline")) {
			ArrayList<String> IP_ADDRESSES = new ArrayList<String>();
			for (Player p : World.getWorld().getPlayers()) {
				if (!IP_ADDRESSES.contains(p.getCurrentIP()))
					IP_ADDRESSES.add(p.getCurrentIP());
			}
			player.message("There are " + IP_ADDRESSES.size() + " unique players online");
		}
                /*if (command.equals("online")) { // Only shows box with total number, doesn't list online players at this time.
			StringBuilder sb = new StringBuilder();
                        synchronized (World.getWorld().getPlayers()) {
                                EntityList<Player> players = World.getWorld().getPlayers();
                                sb.append("@gre@There are currently ").append(players.size()).append(" player(s) online.\n\n");
                                for (Player p : players) {
                                        Point loc = p.getLocation();
                                        sb.append("@whi@").append(p.getUsername()).append(" @yel@(").append(loc).append(")").append(loc.inWilderness() ? " @red@".concat("Wilderness").concat("\n") : "\n");
                                }
                        }
                        ActionSender.sendBox(player, sb.toString(), true);
		}*/
		if (command.equals("events")) {
			player.message("Total amount of events running: " + Server.getServer().getGameEventHandler().getEvents().size());
			HashMap<String, Integer> events = new HashMap<String, Integer>();
			for (GameTickEvent e : Server.getServer().getGameEventHandler().getEvents()) {
				String eventName = e.getClass().getName();
				if (e.getOwner() != null && e.getOwner().isUnregistering()) {
					if (!events.containsKey(eventName)) {
						events.put(eventName, 1);
					} else {
						events.put(eventName, events.get(eventName) + 1);
					}
				}
			}
			String s = "";
			for (Entry<String, Integer> entry : events.entrySet()) {
				String name = entry.getKey();
				Integer value = entry.getValue();
				s += name + ": " + value + "%";
			}
			ActionSender.sendBox(player, s, true);
		}
		if (command.equals("toggleaggro")) {
			player.setAttribute("no-aggro", !player.getAttribute("no-aggro", false));
			player.message("Aggressive: " + player.getAttribute("no-aggro", false));
		}
		if (command.equals("unban")) {
			if (args.length != 1) {
				return;
			}
			long user = DataConversions.usernameToHash(args[0]);
			player.message(Server.getPlayerDataProcessor().getDatabase().banPlayer(DataConversions.hashToUsername(user), 0));
			GameLogging.addQuery(new StaffLog(player, 19, "Unbanned: " + args[0]));
		}
		if (command.equals("ban")) {
			if (args.length != 1) {
				return;
			}
			long user = DataConversions.usernameToHash(args[0]);
			player.message(Server.getPlayerDataProcessor().getDatabase().banPlayer(DataConversions.hashToUsername(user), -1));
			Player bannedPlayer = World.getWorld().getPlayer(user);
			GameLogging.addQuery(new StaffLog(player, 20, "Permanently banned: " + args[0]));
			if (bannedPlayer != null) {
				bannedPlayer.unregister(true, "Banned by " + player.getUsername() + " permanently");
			}
		}
		if(command.equals("fish")) {
			player.getCache().remove("fishing_trawler_reward");
			player.getCache().set("fishing_trawler_reward", 37);
		}
		if (command.equals("cleannpcs")) {
			Server.getServer().submitTask(new Runnable() {
				@Override
				public void run() {
					int count = 0;
					for (Npc n : world.getNpcs()) {
						if (n.getOpponent() instanceof Player) {
							if (n.getOpponent().isUnregistering()) {
								n.setOpponent(null);
								count++;
							}
						}
					}
				}
			});
			player.message("cleaned " + count + " player references.");
		}
		if (command.equals("cancelshutdown")) {
			Server.getServer().saveAndShutdown();
		}
		if(command.equals("saveall")) {
			int count = 0;
			for(Player p : World.getWorld().getPlayers()) {
				p.save();
				count++;
			}
			player.message("Saved " + count + " players on server!");
		}
		if (command.equals("cleanregions")) {
			Server.getServer().submitTask(new Runnable() {
				@Override
				public void run() {
					final int HORIZONTAL_PLANES = (World.MAX_WIDTH / RegionManager.REGION_SIZE) + 1;
					final int VERTICAL_PLANES = (World.MAX_HEIGHT / RegionManager.REGION_SIZE) + 1;
					for (int x = 0; x < HORIZONTAL_PLANES; ++x)
						for (int y = 0; y < VERTICAL_PLANES; ++y) {
							Region r = RegionManager.getRegion(x * RegionManager.REGION_SIZE,
									y * RegionManager.REGION_SIZE);
							if (r != null)
								for (Iterator<Player> i = r.getPlayers().iterator(); i.hasNext();) {
									if (i.next().isRemoved())
										i.remove();
								}
						}
					System.gc();
				}
			});
			player.message("Done");
		}
		if (command.equals("globaldrop")) {
			if (args.length != 3) {
				player.message("globaldrop, id of item, amount to be dropped, show locations (yes/no)");
				return;
			}

			final int itemToDrop = Integer.parseInt(args[0]);
			final int amountToDrop = Integer.parseInt(args[1]);
			final boolean showLoc = args[2].equalsIgnoreCase("yes") ? true : false;

			if (globalDropEvent != null) {
				player.message("There is already a world drop running");
				return;
			}
			player.message("Starting global holiday drop...");
			final Player p = player;
			PluginHandler.getPluginHandler().getExecutor().submit(new Runnable() {

				@Override
				public void run() {
					while (count < amountToDrop) {
						Point location = getRandomLocation();
						if (showLoc)
							p.message("Dropped at: x: " + location.getX() + " y: " + location.getY());
						// World.getWorld().getTile(location).add(new
						// Item(itemToDrop, location));
						world.registerItem(new GroundItem(itemToDrop, location.getX(), location.getY(), 1, null));
						count++;
						globalDropEvent = null;
					}
					count = 0;
				}
			});
			world.sendWorldMessage("@gre@New global drop started! " + EntityHandler.getItemDef(itemToDrop).getName() + "'s dropped in Al-Kharid!");
			world.sendWorldMessage("@red@Telegrab has been disabled!");
			GameLogging.addQuery(new StaffLog(player, 21, "Started a globaldrop (id: " + itemToDrop + " amount: " + amountToDrop + ")"));
			World.WORLD_TELEGRAB_TOGGLE = true;
			Server.getServer().getEventHandler().add(new SingleEvent(null, 60000 * 3) {
				public void action() {
					world.sendWorldMessage("@yel@Global drop has ended! Happy Holiday!");
					world.sendWorldMessage("@gre@Telegrab has been enabled!");
					World.WORLD_TELEGRAB_TOGGLE = false;
				}
			});
		}
		if (command.equals("simulatedrop")) {
			int npcID = Integer.parseInt(args[0]);
			int maxAttempts = Integer.parseInt(args[1]);
			int dropID = -1;
			int dropWeight = 0;

			HashMap<String, Integer> hmap = new HashMap<String, Integer>();

			ItemDropDef[] drops = EntityHandler.getNpcDef(npcID).getDrops();
			for (ItemDropDef drop : drops) {
				dropID = drop.getID();
				if (dropID == -1) continue;
				if (dropID == 160) {
					int[] rares = { 160, 159, 158, 157, 526, 527, 1277 };
					String[] rareNames = {"uncut sapphire", "uncut emerald",
							"uncut ruby", "uncut diamond", "Half of a key", "Half of a key", "Half Dragon Square Shield"};
					for (int r = 0; r < rares.length; r++)
						hmap.put(rareNames[r]+" "+rares[r], 0);
				}
				else if (dropID == 165) {
					int[] herbs = { 165, 435, 436, 437, 438, 439, 440, 441, 442, 443 };
					for (int h : herbs)
						hmap.put("Herb "+h, 0);
				}
				else {
					ItemDefinition def = EntityHandler.getItemDef(dropID);
					hmap.put(def.getName()+" "+dropID, 0);
				}
			}
			int originalTotal = 0;
			for (ItemDropDef drop : drops) {
				originalTotal += drop.getWeight();
			}
			System.out.println("Total Weight: "+originalTotal);

			int total = 0;
			for (int i = 0; i < maxAttempts; i++) {
				int hit = DataConversions.random(0, originalTotal);
				total = 0;
				for (ItemDropDef drop : drops) {
					if (drop == null) {
						continue;
					}
					dropID = drop.getID();
					dropWeight = drop.getWeight();
					if (dropWeight == 0 && dropID != -1) {
						continue;
					}
					if (hit >= total && hit < (total + dropWeight)) {
						if (dropID != -1) {
							if (dropID == 160)
								dropID = Formulae.calculateRareDrop();
							else if (dropID == 165)
								dropID = Formulae.calculateHerbDrop();
							ItemDefinition def = EntityHandler.getItemDef(dropID);
							try {
								hmap.put(def.getName()+" "+dropID, hmap.get(def.getName()+" "+dropID) + 1);
							}
							catch (NullPointerException n) {
							}
							break;
						}
					}
					total += dropWeight;
				}
			}
			System.out.println(Arrays.toString(hmap.entrySet().toArray()));
		}
		if (command.equals("reloaddrops")) {
			try {
				PreparedStatement statement = DatabaseConnection.getDatabase().prepareStatement(
						"SELECT * FROM `" + Constants.GameServer.MYSQL_TABLE_PREFIX + "npcdrops` WHERE npcdef_id = ?");
				for (int i = 0; i < EntityHandler.npcs.size(); i++) {
					statement.setInt(1, i);
					ResultSet dropResult = statement.executeQuery();

					NPCDef def = EntityHandler.getNpcDef(i);
					def.drops = null;
					ArrayList<ItemDropDef> drops = new ArrayList<ItemDropDef>();
					while (dropResult.next()) {
						ItemDropDef drop;

						drop = new ItemDropDef(dropResult.getInt("id"), dropResult.getInt("amount"),
								dropResult.getInt("weight"));

						drops.add(drop);
					}
					dropResult.close();
					def.drops = drops.toArray(new ItemDropDef[] {});
				}
			} catch (SQLException e) {
				LOGGER.catching(e);
			}
			player.message("might have reloaded drops..idk.");
		}
		if (command.equals("gi")) {
			if (args.length != 3) {
				player.message("Invalid args. Syntax: gitem <id> <amount> <respawnTime>");
				return;
			}
			int id = Integer.parseInt(args[0]);
			int amount = Integer.parseInt(args[1]);
			int respawnTime = Integer.parseInt(args[2]);
			ItemLoc item = new ItemLoc(id, player.getX(), player.getY(), amount, respawnTime);
			if (id >= 0) {
				DatabaseConnection.getDatabase()
				.executeUpdate("INSERT INTO `" + Constants.GameServer.MYSQL_TABLE_PREFIX
						+ "grounditems`(`id`, `x`, `y`, `amount`, `respawn`) VALUES ('"
						+ item.getId() + "','" + item.getX() + "','" + item.getY() + "','" + item.getAmount()
						+ "','" + item.getRespawnTime() + "')");
				World.getWorld().registerItem(new GroundItem(item));
				player.message("Succesfully stored ground item into database");

			} else {
				GroundItem itemr = player.getViewArea().getGroundItem(player.getLocation());
				if (itemr == null) {
					player.message("There is no item here.");
					return;
				}
				DatabaseConnection.getDatabase()
				.executeUpdate("DELETE FROM `" + Constants.GameServer.MYSQL_TABLE_PREFIX
						+ "grounditems` WHERE `x` = '" + itemr.getX() + "' AND `y` =  '" + itemr.getY()
						+ "' AND `id` = '" + itemr.getID() + "'");
				World.getWorld().unregisterItem(itemr);
				player.message("Removed item from database.");
			}
		}
		if (command.equals("reloadland")) {
			World.getWorld().wl.loadWorld(World.getWorld());
		}
		if (command.equals("summonall")) {
			for (Player p : world.getPlayers()) {
				p.teleport(player.getX() + player.getRandom().nextInt(10),
						player.getY() + player.getRandom().nextInt(10), true);
			}
			return;
		}
		if (command.equals("tile")) {
			TileValue tv = World.getWorld().getTile(player.getLocation());
			player.message("travelsal: " + tv.traversalMask + ", vertVal:" + (tv.verticalWallVal & 0xff) + ", horiz: "
					+ (tv.horizontalWallVal & 0xff) + ", diagVal: " + (tv.diagWallVal & 0xff));
		}
		if (command.equals("debugregion")) {
			boolean debugPlayers = Integer.parseInt(args[0]) == 1;
			boolean debugNpcs = Integer.parseInt(args[1]) == 1;
			boolean debugItems = Integer.parseInt(args[2]) == 1;
			boolean debugObjects = Integer.parseInt(args[3]) == 1;

			ActionSender.sendBox(player, player.getRegion().toString(debugPlayers, debugNpcs, debugItems, debugObjects)
					.replaceAll("\n", "%"), true);
		}
		if (command.equals("storecache")) {
			final Player scrn = World.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
			if (scrn != null) {
				if (scrn.getCache().hasKey(args[1])) {
					player.message("That player already has that setting set.");
					return;
				}
				scrn.getCache().store(args[1], args[2]);
				player.message("Added to players settings: " + args[1] + ":" + args[2]);
			} else {
				player.message("User not found.");
			}
		}
		if (command.equals("deletecache")) {
			final Player scrn = World.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
			if (scrn != null) {
				if (!scrn.getCache().hasKey(args[1])) {
					player.message("That setting for this player doesn't exist.");
				}
				scrn.getCache().remove(args[1]);
				player.message("Removed from players settings: " + args[1]);
			} else {
				player.message("User not found.");
			}
		}
		if (command.equals("addcache")) {
			player.getCache().store(args[0], args[1]);
		}
		if (command.equals("questcom")) {
			int q = Integer.parseInt(args[0]);
			player.sendQuestComplete(q);
		}
                if (command.equals("shutdown")) {
                        String reason = "";
			int seconds = 0;
			if (Server.getServer().shutdownForUpdate(seconds)) {
				for (Player p : world.getPlayers()) {
					ActionSender.startShutdown(p, seconds);
				}
			}
                }
		if (command.equals("update")) {
			String reason = "";
			int seconds = 60;
			if (args.length > 0) {
				for (int i = 0; i < args.length; i++) {
					if (i == 0) {
						try {
							seconds = Integer.parseInt(args[i]);
						} catch (Exception e) {
							reason += (args[i] + " ");
						}
					} else {
						reason += (args[i] + " ");
					}
				}
				reason = reason.substring(0, reason.length() - 1);
			}
			int minutes = seconds / 60;
			int remainder = seconds % 60;

			if (Server.getServer().shutdownForUpdate(seconds)) {
				String message = "The server will be shutting down in "
						+ (minutes > 0 ? minutes + " minute" + (minutes > 1 ? "s" : "") + " " : "")
						+ (remainder > 0 ? remainder + " second" + (remainder > 1 ? "s" : "") : "")
						+ (reason == "" ? "" : ": % % " + reason);
				for (Player p : world.getPlayers()) {
					ActionSender.sendBox(p, message, false);
					ActionSender.startShutdown(p, seconds);
				}
			}
			// Services.lookup(DatabaseManager.class).addQuery(new
			// StaffLog(player, 7));
		}
		if (command.equals("appearance")) {
			player.setChangingAppearance(true);
			ActionSender.sendAppearanceScreen(player);
		}
		if (command.equals("dropall")) {
			player.getInventory().getItems().clear();
			ActionSender.sendInventory(player);
		}
		if (command.equals("sysmsg")) {
			StringBuilder sb = new StringBuilder("SYSTEM MESSAGE: @whi@");

			for (int i = 0; i < args.length; i++) {
				sb.append(args[i]).append(" ");
			}

			world.sendWorldMessage("@red@" + sb.toString());
			world.sendWorldMessage("@yel@" + sb.toString());
			world.sendWorldMessage("@gre@" + sb.toString());
			world.sendWorldMessage("@cya@" + sb.toString());
		}
		if (command.equals("system")) {
			StringBuilder sb = new StringBuilder("@yel@System message: @whi@");

			for (int i = 0; i < args.length; i++) {
				sb.append(args[i]).append(" ");
			}

			for (Player p : World.getWorld().getPlayers()) {
				ActionSender.sendBox(p, sb.toString(), false);
			}
		}
                if (command.equals("item")) {
                        if (args.length < 1 || args.length > 2) {
                                ActionSender.sendMessage(player, "Invalid args. Syntax: ITEM id [amount]");
                                return;
                        }
                        int id = Integer.parseInt(args[0]);
                        if (EntityHandler.getItemDef(id) != null) {
                                int amount = 1;
                                if (args.length == 2) {
                                        amount = Integer.parseInt(args[1]);
                                }
                                if (EntityHandler.getItemDef(id).isStackable())
                                        player.getInventory().add(new Item(id, amount));
                                else {
                                        for (int i = 0; i < amount; i++) {
                                                if (amount > 30) { // Prevents too many un-stackable items from being spawned and crashing clients in the local area.
                                                        ActionSender.sendMessage(player, "Invalid amount specified. Please spawn 30 or less of that item.");
                                                        return;
                                                }
                                                player.getInventory().add(new Item(id, 1));
                                        }
                                }
                        } 
                        else {
                                ActionSender.sendMessage(player, "Invalid id");
                        }
                        return;
                }
                if (command.equalsIgnoreCase("about")) {
                        Player p = args.length > 0 ? World.getWorld().getPlayer(DataConversions.usernameToHash(args[0])) : player;
                        p.updateTotalPlayed();
			long timePlayed = p.getCache().getLong("total_played");
                        long timeMoved = System.currentTimeMillis() - p.getLastMoved();
                        long timeOnline = System.currentTimeMillis() - p.getCurrentLogin();
                        if(p != null) {
                        
                        ActionSender.sendBox(player, 
                        "@lre@Player Information: %"
                        + " %"
			+ "@gre@Name:@whi@ " + p.getUsername() + "@lre@ %" 
                        + "@gre@Fatigue:@whi@ " + ((p.getFatigue() / 25) * 100 / 750) + " %"
                        + "@gre@Group ID:@whi@ " + p.getGroupID() + " %"                        
                        + "@gre@Busy:@whi@ " + (p.isBusy() ? "true" : "false") + " %"
                        + "@gre@IP:@whi@ " + p.getLastIP() + " %"
			+ "@gre@Last Login:@whi@ " + p.getDaysSinceLastLogin() + " days ago %"
                        + "@gre@Coordinates:@whi@ " + p.getStatus() + " at " + p.getLocation().toString() + " %"
                        + "@gre@Last Moved:@whi@ " + DataConversions.getDateFromMsec(timeMoved) + " %"
                        + "@gre@Time Logged In:@whi@ " + DataConversions.getDateFromMsec(timeOnline) + " %"
			+ "@gre@Total Time Played:@whi@ " + DataConversions.getDateFromMsec(timePlayed) + " %"
                        , true);
			return;
                        
                        }
                        else
                                ActionSender.sendMessage(player, "Invalid name");
                }
                if (command.equalsIgnoreCase("inventory")) {
                        Player p = args.length > 0 ? World.getWorld().getPlayer(DataConversions.usernameToHash(args[0])) : player;
                        if(p != null) {
                                ArrayList<Item> inventory = p.getInventory().getItems();
                                ArrayList<String> itemStrings = new ArrayList<String>();
                                for(Item invItem : inventory) itemStrings.add("@gre@" + invItem.getAmount() + " @whi@" + invItem.getDef().getName());
                                
                                ActionSender.sendBox(player, 
                                "@lre@Inventory of " + p.getUsername() + ":%" 
                                + "@whi@" + StringUtils.join(itemStrings, ", "), true);
                                return;
                        }
                        else
                                ActionSender.sendMessage(player, "Invalid name");
                }
                if (command.equalsIgnoreCase("bank")) {
                        Player p = args.length > 0 ? World.getWorld().getPlayer(DataConversions.usernameToHash(args[0])) : player;
                        if(p != null) {
                                // Show bank screen to yourself
                                if(p.getUsernameHash() == player.getUsernameHash()) {
                                        player.setAccessingBank(true);
                                        ActionSender.showBank(player);
                                }
                                else {
                                        ArrayList<Item> inventory = p.getBank().getItems();
                                        ArrayList<String> itemStrings = new ArrayList<String>();
                                        for(Item bankItem : inventory) itemStrings.add("@gre@" + bankItem.getAmount() + " @whi@" + bankItem.getDef().getName());
                                        ActionSender.sendBox(player, 
                                        "@lre@Bank of " + p.getUsername() + ":%" 
                                        + "@whi@" + StringUtils.join(itemStrings, ", "), true);
                                        return;
                                }
                        }
                        else
                                ActionSender.sendMessage(player, "Invalid name");
                }
		if (command.equals("set")) {
			if (args.length < 2) {
				player.message("INVALID USE - EXAMPLE setstat attack 99");
				return;
			}

			int stat = Formulae.getStatIndex(args[0]);
			int lvl = Integer.parseInt(args[1]);

			if (lvl < 0 || lvl > 99) {
				player.message("Invalid " + Formulae.statArray[stat] + " level.");
				return;
			}

			player.getSkills().setLevelTo(stat, lvl);
			ActionSender.sendStat(player, stat);
			player.message("Your " + Formulae.statArray[stat] + " has been set to level " + lvl);

			player.checkEquipment();
		}
	}

	private Point getRandomLocation() {
		Point location = Point.location(DataConversions.random(48, 91), DataConversions.random(575,717));

		if (!Formulae.isF2PLocation(location)) {
			return getRandomLocation();
		}

		/*
		 * TileValue tile = World.getWorld().getTile(location.getX(),
		 * location.getY()); if (tile.) { return getRandomLocation(); }
		 */

		TileValue value = World.getWorld().getTile(location.getX(), location.getY());

		if (value.diagWallVal != 0 || value.horizontalWallVal != 0 || value.verticalWallVal != 0
				|| value.overlay != 0) {
			return getRandomLocation();
		}
		return location;
	}
}