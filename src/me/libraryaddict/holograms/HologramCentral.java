package me.libraryaddict.holograms;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.server.v1_7_R4.EntityPlayer;
import net.minecraft.server.v1_7_R4.EnumEntityUseAction;
import net.minecraft.server.v1_7_R4.EnumGamemode;
import net.minecraft.server.v1_7_R4.EnumMovingObjectType;
import net.minecraft.server.v1_7_R4.MathHelper;
import net.minecraft.server.v1_7_R4.MovingObjectPosition;
import net.minecraft.server.v1_7_R4.Vec3D;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_7_R4.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_7_R4.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction;

public class HologramCentral implements Listener {

    private static ArrayList<Hologram> holograms = new ArrayList<Hologram>();
    /**
     * A list of holograms each player is currently seeing
     */
    private static HashMap<String, ArrayList<Hologram>> viewableHolograms = new HashMap<String, ArrayList<Hologram>>();
    private static Plugin plugin;

    static {
        plugin = Bukkit.getPluginManager().getPlugins()[0];
        Bukkit.getPluginManager().registerEvents(new HologramCentral(), plugin);
        BukkitRunnable runnable = new BukkitRunnable() {
            public void run() {
                for (Hologram hologram : new ArrayList<Hologram>(holograms)) {
                    if (hologram.getEntityFollowed() != null) {
                        Entity entity = hologram.getEntityFollowed();
                        if (!entity.isValid() && (!(entity instanceof Player) || !((Player) entity).isOnline())) {
                            if (hologram.isRemovedOnEntityDeath()) {
                                removeHologram(hologram);
                            } else {
                                hologram.setFollowEntity(null);
                            }
                        } else {
                            Location loc1 = hologram.entityLastLocation;
                            Location loc2 = entity.getLocation();
                            if (!loc1.equals(loc2)) {
                                // Here I figure out where the hologram moved to.
                                hologram.entityLastLocation = loc2;
                                Location toAdd = hologram.getRelativeToEntity();
                                if (hologram.isRelativePitch() || hologram.isRelativeYaw()) {
                                    double r = Math.sqrt((toAdd.getX() * toAdd.getX()) + (toAdd.getY() * toAdd.getY())
                                            + (toAdd.getZ() * toAdd.getZ()));
                                    if (hologram.isRelativePitchControlMoreThanHeight()) {
                                        // TODO Calculate new height based on how high the entity is looking.
                                        // The max height difference can be 30 for now.
                                        // TODO Calculate new X Z as a circle around the player
                                    } else {
                                        // TODO Calculate new X Y Z as a sphere.
                                    }
                                    toAdd.setWorld(loc2.getWorld());
                                }
                                Location newLoc = loc2.clone().add(toAdd);
                                hologram.moveHologram(newLoc, false);
                            }
                        }
                    }
                    if (hologram.getMovement() != null) {
                        hologram.moveHologram(hologram.getLocation().add(hologram.getMovement()));
                    }
                }
            }
        };
        runnable.runTaskTimer(plugin, 0, 0);
        System.out.print("1");
        ProtocolLibrary.getProtocolManager().getAsynchronousManager()
                .registerAsyncHandler(new PacketAdapter(plugin, PacketType.Play.Client.USE_ENTITY) {
                    // Source:
                    // [url]http://www.gamedev.net/topic/338987-aabb---line-segment-intersection-test/[/url]
                    private boolean hasIntersection(Vector3D p1, Vector3D p2, Vector3D min, Vector3D max) {
                        final double epsilon = 0.0001f;

                        Vector3D d = p2.subtract(p1).multiply(0.5);
                        Vector3D e = max.subtract(min).multiply(0.5);
                        Vector3D c = p1.add(d).subtract(min.add(max).multiply(0.5));
                        Vector3D ad = d.abs();

                        if (Math.abs(c.x) > e.x + ad.x)
                            return false;
                        if (Math.abs(c.y) > e.y + ad.y)
                            return false;
                        if (Math.abs(c.z) > e.z + ad.z)
                            return false;

                        if (Math.abs(d.y * c.z - d.z * c.y) > e.y * ad.z + e.z * ad.y + epsilon)
                            return false;
                        if (Math.abs(d.z * c.x - d.x * c.z) > e.z * ad.x + e.x * ad.z + epsilon)
                            return false;
                        if (Math.abs(d.x * c.y - d.y * c.x) > e.x * ad.y + e.y * ad.x + epsilon)
                            return false;

                        return true;
                    }

                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        if (is1_8(event.getPlayer())) {
                            Player p = event.getPlayer();
                            if (viewableHolograms.containsKey(p.getName())) {
                                int entityId = event.getPacket().getIntegers().read(0);
                                for (Hologram hologram : viewableHolograms.get(p.getName())) {
                                    for (Entry<Integer, Integer> entry : hologram.getEntityIds()) {
                                        if (entityId == entry.getKey()) {
                                            EntityPlayer player = ((CraftPlayer) p).getHandle();
                                            {
                                                final int ATTACK_REACH = 4;

                                                Player observer = event.getPlayer();
                                                Location observerPos = observer.getEyeLocation();
                                                Vector3D observerDir = new Vector3D(observerPos.getDirection());

                                                Vector3D observerStart = new Vector3D(observerPos);
                                                Vector3D observerEnd = observerStart.add(observerDir.multiply(ATTACK_REACH));

                                                Entity hit = null;

                                                // Get nearby entities
                                                for (Entity entity : observer.getWorld().getLivingEntities()) {
                                                    // No need to simulate an attack if the player is already visible
                                                    if (entity != observer
                                                            && (!(entity instanceof Player) || observer.canSee(((Player) entity)))) {
                                                        // Bounding box of the given player
                                                        Vector3D targetPos = new Vector3D(entity.getLocation());
                                                        Vector3D minimum = targetPos.add(-0.5, 0, -0.5);
                                                        Vector3D maximum = targetPos.add(0.5, 1.67, 0.5);

                                                        if (hasIntersection(observerStart, observerEnd, minimum, maximum)) {
                                                            if (hit == null
                                                                    || hit.getLocation().distanceSquared(observerPos) > entity
                                                                            .getLocation().distanceSquared(observerPos)) {
                                                                hit = entity;
                                                            }
                                                        }
                                                    }
                                                }
                                                if (hit != null) {
                                                    event.getPacket().getIntegers().write(0, hit.getEntityId());
                                                    return;
                                                }
                                            }
                                            {
                                                float f = 1.0F;
                                                float f1 = player.lastPitch + (player.pitch - player.lastPitch) * f;
                                                float f2 = player.lastYaw + (player.yaw - player.lastYaw) * f;
                                                double d0 = player.lastX + (player.locX - player.lastX) * f;
                                                double d1 = player.lastY + (player.locY - player.lastY) * f + 1.62D
                                                        - player.height;
                                                double d2 = player.lastZ + (player.locZ - player.lastZ) * f;
                                                Vec3D vec3d = Vec3D.a(d0, d1, d2);

                                                float f3 = MathHelper.cos(-f2 * 0.01745329F - 3.141593F);
                                                float f4 = MathHelper.sin(-f2 * 0.01745329F - 3.141593F);
                                                float f5 = -MathHelper.cos(-f1 * 0.01745329F);
                                                float f6 = MathHelper.sin(-f1 * 0.01745329F);
                                                float f7 = f4 * f5;
                                                float f8 = f3 * f5;
                                                double d3 = player.playerInteractManager.getGameMode() == EnumGamemode.CREATIVE ? 5.0D
                                                        : 4.5D;
                                                Vec3D vec3d1 = vec3d.add(f7 * d3, f6 * d3, f8 * d3);
                                                Vector vec = new Vector(vec3d1.a - vec3d.a, vec3d1.b - vec3d.b, vec3d1.c
                                                        - vec3d.c).normalize();
                                                vec3d.a -= vec.getX() * 0.8;
                                                vec3d.b -= vec.getY();
                                                vec3d.c -= vec.getZ() * 0.8;
                                                MovingObjectPosition mov = player.world.rayTrace(vec3d, vec3d1, true);
                                                if (mov != null && mov.type == EnumMovingObjectType.BLOCK) {
                                                    Object obj = event.getPacket().getModifier().read(1);
                                                    if (obj != null && obj == EnumEntityUseAction.ATTACK) {
                                                        player.playerInteractManager.dig(mov.b, mov.c, mov.d, 0);
                                                    } else {
                                                        player.playerInteractManager.interact(player, player.world,
                                                                CraftItemStack.asNMSCopy(p.getItemInHand()), mov.b, mov.c, mov.d,
                                                                1, 0, 1, 0.06F);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }).syncStart();
    }

    static boolean is1_8(Player player) {
        if (!is1_8.containsKey(player.getName()))
            is1_8.put(player.getName(), ((CraftPlayer) player).getHandle().playerConnection.networkManager.getVersion() >= 28);
        return is1_8.get(player.getName());
    }

    private static HashMap<String, Boolean> is1_8 = new HashMap<String, Boolean>();

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
                        for (PacketContainer packet : hologram.getSpawnPackets(p)) {
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
                    for (PacketContainer packet : hologram.getSpawnPackets(p)) {
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
                            ProtocolLibrary.getProtocolManager().sendServerPacket(player, hologram.getDestroyPacket(player),
                                    false);
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
                            ProtocolLibrary.getProtocolManager().sendServerPacket(player, hologram.getDestroyPacket(player),
                                    false);
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static Location rotate(Location loc, double yaw, double pitch) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        yaw = Math.toRadians(yaw);
        pitch = Math.toRadians(pitch);
        double newX = x * Math.cos(yaw) - z * Math.sin(yaw);
        double newZ = x * Math.sin(yaw) + z * Math.cos(yaw);
        double newY = y * (yaw / 90);// ((y * cospitch) - (z * sinpitch));
        return new Location(null, newX, newY, newZ);
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
                        for (PacketContainer packet : hologram.getSpawnPackets(p)) {
                            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                        }
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                } else {
                    viewable.remove(hologram);
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, hologram.getDestroyPacket(p), false);
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
        is1_8.remove(event.getPlayer().getName());
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
