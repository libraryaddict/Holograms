package me.libraryaddict.holograms;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;

public class HologramCentral implements Listener {
    private static ArrayList<Hologram> holograms = new ArrayList<Hologram>();
    /**
     * A list of holograms each player is currently seeing
     */
    private static HashMap<String, ArrayList<Hologram>> viewableHolograms = new HashMap<String, ArrayList<Hologram>>();

    static {
        Plugin plugin = Bukkit.getPluginManager().getPlugins()[0];
        Bukkit.getPluginManager().registerEvents(new HologramCentral(), plugin);
        BukkitRunnable runnable = new BukkitRunnable() {
            public void run() {
                for (Hologram hologram : new ArrayList<Hologram>(holograms)) {
                    if (hologram.getRelativeEntity() != null) {
                        Entity entity = hologram.getRelativeEntity();
                        if (!entity.isValid() && (!(entity instanceof Player) || !((Player) entity).isOnline())) {
                            if (hologram.isRemovedOnEntityDeath()) {
                                removeHologram(hologram);
                            } else {
                                hologram.setMoveRelative(null);
                            }
                        } else {
                            Location loc1 = hologram.entityLastLocation;
                            Location loc2 = entity.getLocation();
                            if (loc1.getX() != loc2.getX() || loc1.getY() != loc2.getY() || loc1.getZ() != loc2.getZ()) {
                                // Here I figure out where the hologram moved to.
                                hologram.entityLastLocation = loc2;
                                Location newLoc = loc2.clone().add(hologram.getRelativeToEntity());
                                // Note I'm not setting a new relative position. I'm just moving it relative to the entity
                                // So I don't give a fig for the holograms location. Just for the entity location and the new
                                // relative location.
                                hologram.moveHologram(newLoc, false);
                            }
                        }
                    }
                    if (hologram.getVector() != null) {
                        hologram.moveHologram(hologram.getLocation().add(hologram.getVector()));
                    }
                }
            }
        };
        runnable.runTaskTimer(plugin, 0, 0);
    }

    static void addHologram(Hologram hologram) {
        if (!holograms.contains(hologram)) {
            holograms.add(hologram);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (hologram.isVisible(p, p.getLocation())) {
                    ArrayList<Hologram> viewable = viewableHolograms.get(p.getName());
                    if (viewable == null) {
                        viewable = new ArrayList<Hologram>();
                        viewableHolograms.put(p.getName(), viewable);
                    }
                    viewable.add(hologram);
                    try {
                        for (PacketContainer packet : hologram.getDisplayPackets()) {
                            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                        }
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    static void addHologram(Player p, Hologram hologram) {
        if (holograms.contains(hologram)) {
            if (hologram.isVisible(p, p.getLocation())) {
                ArrayList<Hologram> viewable = viewableHolograms.get(p.getName());
                if (viewable == null) {
                    viewable = new ArrayList<Hologram>();
                    viewableHolograms.put(p.getName(), viewable);
                }
                viewable.add(hologram);
                try {
                    for (PacketContainer packet : hologram.getDisplayPackets()) {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                    }
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static boolean isInUse(Hologram hologram) {
        return holograms.contains(hologram);
    }

    static void removeHologram(Hologram hologram) {
        if (holograms.contains(hologram)) {
            holograms.remove(hologram);
            Iterator<Entry<String, ArrayList<Hologram>>> itel = viewableHolograms.entrySet().iterator();
            while (itel.hasNext()) {
                Entry<String, ArrayList<Hologram>> entry = itel.next();
                if (entry.getValue().remove(hologram)) {
                    Player player = Bukkit.getPlayerExact(entry.getKey());
                    if (player != null) {
                        try {
                            ProtocolLibrary.getProtocolManager().sendServerPacket(player, hologram.getDestroyPacket(), false);
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                    if (player == null || entry.getValue().isEmpty()) {
                        itel.remove();
                    }
                }
            }
        }
    }

    static void removeHologram(Player player, Hologram hologram) {
        if (holograms.contains(hologram)) {
            if (viewableHolograms.containsKey(player.getName())) {
                if (viewableHolograms.get(player.getName()).remove(hologram)) {
                    if (viewableHolograms.get(player.getName()).isEmpty()) {
                        viewableHolograms.remove(player.getName());
                        try {
                            ProtocolLibrary.getProtocolManager().sendServerPacket(player, hologram.getDestroyPacket(), false);
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void doCheck(Player p, Location loc) {
        ArrayList<Hologram> viewable = viewableHolograms.get(p.getName());
        if (viewable == null) {
            viewable = new ArrayList<Hologram>();
        }
        for (Hologram hologram : holograms) {
            boolean view = hologram.isVisible(p, loc);
            if (view != viewable.contains(hologram)) {
                if (view) {
                    viewable.add(hologram);
                    try {
                        for (PacketContainer packet : hologram.getDisplayPackets()) {
                            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                        }
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                } else {
                    viewable.remove(hologram);
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, hologram.getDestroyPacket(), false);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (viewable.isEmpty()) {
            viewableHolograms.remove(p.getName());
        } else if (!viewableHolograms.containsKey(p.getName())) {
            viewableHolograms.put(p.getName(), viewable);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        doCheck(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewableHolograms.remove(event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        doCheck(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(WorldUnloadEvent event) {
        for (Hologram hologram : new ArrayList<Hologram>(holograms)) {
            if (hologram.getLocation().getWorld() == event.getWorld()) {
                removeHologram(hologram);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldSwitch(PlayerChangedWorldEvent event) {
        viewableHolograms.remove(event.getPlayer().getName());
        doCheck(event.getPlayer(), event.getPlayer().getLocation());
    }
}
