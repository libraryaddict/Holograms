package me.libraryaddict.holograms;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;

public class Hologram {
    public enum HologramTarget {
        EVERYONE_BUT_THESE_PLAYERS, ONLY_THESE_PLAYERS;
    }

    private static int getId() {
        try {
            Field field = Class.forName(
                    "net.minecraft.server." + Bukkit.getServer().getClass().getName().split("\\.")[3] + ".Entity")
                    .getDeclaredField("entityCount");
            field.setAccessible(true);
            int id = field.getInt(null);
            field.set(null, id + 1);
            return id;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    private PacketContainer destroyPacket;
    private PacketContainer[] displayPackets;
    private ArrayList<Entry<Integer, Integer>> entityIds = new ArrayList<Entry<Integer, Integer>>();
    private ArrayList<String> hologramPlayers = new ArrayList<String>();
    private HologramTarget hologramTarget = HologramTarget.EVERYONE_BUT_THESE_PLAYERS;
    private String[] lines;
    private double lineSpacing = 1;
    private Location location;
    private int viewDistance = 70;
    private Vector moveVector;
    private Entity relativeEntity;
    private Location relativeToEntity = new Location(null, 0, 0, 0);
    private Location lastMovement = new Location(null, 0, 0, 0);
    protected Location entityLastLocation;
    private boolean keepAliveAfterEntityDies;

    public Vector getVector() {
        return moveVector;
    }

    public Hologram setMoveVector(Vector vector) {
        this.moveVector = vector;
        return this;
    }

    public Entity getRelativeEntity() {
        return relativeEntity;
    }

    public Hologram setRelativeToEntityLocation(Location location) {
        this.relativeToEntity = location;
        return this;
    }

    public Location getRelativeToEntity() {
        return relativeToEntity;
    }

    public Hologram setMoveRelative(Entity entity) {
        setMoveRelative(entity, true);
        return this;
    }

    public boolean isRemovedOnEntityDeath() {
        return this.keepAliveAfterEntityDies;
    }

    public Hologram setMoveRelative(Entity entity, boolean isRemoveOnEntityDeath) {
        relativeEntity = entity;
        if (entity != null) {
            this.keepAliveAfterEntityDies = isRemoveOnEntityDeath;
            relativeToEntity = getLocation().subtract(entity.getLocation());
            entityLastLocation = entity.getLocation();
        }
        return this;
    }

    private ArrayList<Player> getPlayers() {
        ArrayList<Player> players = new ArrayList<Player>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isVisible(player, player.getLocation())) {
                players.add(player);
            }
        }
        return players;
    }

    public Hologram moveHologram(Location location) {
        moveHologram(location, true);
        return this;
    }

    public Hologram moveHologram(Location location, boolean setNewRelativeLocation) {
        ArrayList<Player> oldPlayers = getPlayers();
        Location loc = getLocation();
        this.location = location.clone().add(0, 54.6, 0);
        if (setNewRelativeLocation && getRelativeEntity() != null) {
            relativeToEntity = getLocation().subtract(getRelativeEntity().getLocation());
        }
        if (isInUse()) {
            makeDisplayPackets();
            ArrayList<Player> newPlayers = getPlayers();
            Iterator<Player> itel = oldPlayers.iterator();
            while (itel.hasNext()) {
                Player p = itel.next();
                if (!newPlayers.contains(p)) {
                    itel.remove();
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, getDestroyPacket(), false);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
            PacketContainer[] packets = null;
            if (!oldPlayers.isEmpty()) {
                lastMovement.add(location.getX() - loc.getX(), location.getY() - loc.getY(), location.getZ() - loc.getZ());
                int x = (int) Math.floor(32 * lastMovement.getX());
                int y = (int) Math.floor(32 * lastMovement.getY());
                int z = (int) Math.floor(32 * lastMovement.getZ());
                packets = new PacketContainer[lines.length];
                int i = 0;
                if (x >= -128 && x < 128 && y >= -128 && y < 128 && z >= -128 && z < 128) {
                    lastMovement.subtract(x / 32D, y / 32D, z / 32D);
                    for (Entry<Integer, Integer> entityId : this.entityIds) {
                        packets[i] = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE);
                        packets[i].getIntegers().write(0, entityId.getKey());
                        StructureModifier<Byte> bytes = packets[i].getBytes();
                        bytes.write(0, (byte) x);
                        bytes.write(1, (byte) y);
                        bytes.write(2, (byte) z);
                        i++;
                    }
                } else {
                    x = (int) Math.floor(32 * location.getX());
                    y = (int) Math.floor(32 * location.getY());
                    z = (int) Math.floor(32 * location.getZ());
                    lastMovement = new Location(null, location.getX() - (x / 32D), location.getY() - (y / 32D), location.getZ()
                            - (z / 32D));
                    for (Entry<Integer, Integer> entityId : this.entityIds) {
                        packets[i] = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
                        StructureModifier<Integer> ints = packets[i].getIntegers();
                        ints.write(0, entityId.getKey());
                        ints.write(1, x);
                        ints.write(2, y);
                        ints.write(3, z);
                        i++;
                    }
                }
            }
            for (Player p : newPlayers) {
                try {
                    for (PacketContainer packet : oldPlayers.contains(p) ? packets : getDisplayPackets()) {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                    }
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        return this;
    }

    public Hologram(Location location, String... lines) {
        assert lines.length != 0 : "You need more lines than nothing!";
        assert location.getWorld() != null : "You can't have a null world in the location!";
        this.lines = lines;
        this.location = location.clone().add(0, 54.6, 0);
    }

    public Hologram addPlayer(Player... players) {
        for (Player player : players) {
            if (!hologramPlayers.contains(player.getName())) {
                hologramPlayers.add(player.getName());
                if (isInUse()) {
                    HologramCentral.addHologram(player, this);
                }
            }
        }
        return this;
    }

    public PacketContainer getDestroyPacket() {
        return destroyPacket;
    }

    public PacketContainer[] getDisplayPackets() {
        return displayPackets;
    }

    public HologramTarget getHologramTarget() {
        return hologramTarget;
    }

    public String[] getLines() {
        return lines;
    }

    public double getLineSpacing() {
        return lineSpacing;
    }

    public Location getLocation() {
        return location.clone().subtract(0, 54.6, 0);
    }

    public boolean hasPlayer(Player player) {
        return hologramPlayers.contains(player.getName());
    }

    public boolean isInUse() {
        return HologramCentral.isInUse(this);
    }

    boolean isVisible(Player player, Location loc) {
        return (getHologramTarget() == HologramTarget.EVERYONE_BUT_THESE_PLAYERS != hasPlayer(player))
                && loc.getWorld() == getLocation().getWorld() && (loc.distance(getLocation()) <= viewDistance);
    }

    private void makeDestroyPacket() {
        int[] ids = new int[entityIds.size() * 2];
        int i = 0;
        for (Entry<Integer, Integer> entry : entityIds) {
            ids[i++] = entry.getKey();
            ids[i++] = entry.getValue();
        }
        destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntegerArrays().write(0, ids);
    }

    private void makeDisplayPackets() {
        Iterator<Entry<Integer, Integer>> itel = entityIds.iterator();
        displayPackets = new PacketContainer[lines.length * 3];
        for (int i = 0; i < displayPackets.length; i += 3) {
            Entry<Integer, Integer> entry = itel.next();
            PacketContainer[] packets = makeSpawnPackets(i / 3, entry.getKey(), entry.getValue(), lines[(lines.length - 1)
                    - (i / 3)]);
            for (int a = 0; a < 3; a++) {
                displayPackets[i + a] = packets[a];
            }
        }
    }

    private PacketContainer[] makeSpawnPackets(int height, int witherId, int horseId, String horseName) {
        PacketContainer[] displayPackets = new PacketContainer[3];
        // Spawn wither skull
        displayPackets[0] = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        StructureModifier<Integer> ints = displayPackets[0].getIntegers();
        ints.write(0, witherId);
        ints.write(1, (int) (getLocation().getX() * 32));
        ints.write(2, (int) ((location.getY() + ((double) height * (getLineSpacing() * 0.285))) * 32));
        ints.write(3, (int) (getLocation().getZ() * 32));
        ints.write(9, 66);
        // Spawn horse
        displayPackets[1] = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);
        ints = displayPackets[1].getIntegers();
        ints.write(0, horseId);
        ints.write(1, 100);
        ints.write(2, (int) (getLocation().getX() * 32));
        ints.write(3, (int) ((location.getY() + ((double) height * (getLineSpacing() * 0.285))) * 32));
        ints.write(4, (int) (getLocation().getZ() * 32));
        // Setup datawatcher
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        watcher.setObject(0, (byte) 1);
        watcher.setObject(1, (short) 300);
        watcher.setObject(10, horseName);
        watcher.setObject(11, (byte) 1);
        watcher.setObject(12, -1700000);
        displayPackets[1].getDataWatcherModifier().write(0, watcher);
        // Make horse ride wither
        displayPackets[2] = new PacketContainer(PacketType.Play.Server.ATTACH_ENTITY);
        ints = displayPackets[2].getIntegers();
        ints.write(1, horseId);
        ints.write(2, witherId);
        return displayPackets;
    }

    public Hologram removePlayer(Player... players) {
        for (Player player : players) {
            if (hologramPlayers.contains(player.getName())) {
                hologramPlayers.remove(player.getName());
                if (isInUse()) {
                    HologramCentral.removeHologram(player, this);
                }
            }
        }
        return this;
    }

    public Hologram setHologramTarget(HologramTarget target) {
        if (target != getHologramTarget()) {
            hologramTarget = target;
            if (isInUse()) {
                HologramCentral.removeHologram(this);
                HologramCentral.addHologram(this);
            }
        }
        return this;
    }

    public Hologram setLines(String... lines) {
        this.lines = lines;
        if (isInUse()) {
            int i = 0;
            ArrayList<Player> players = getPlayers();

            for (; i < Math.min(entityIds.size(), lines.length); i++) {
                Entry<Integer, Integer> entry = entityIds.get(i);
                PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
                packet.getIntegers().write(0, entry.getValue());
                ArrayList<WrappedWatchableObject> list = new ArrayList<WrappedWatchableObject>();
                list.add(new WrappedWatchableObject(0, (byte) 1));
                list.add(new WrappedWatchableObject(1, (short) 300));
                list.add(new WrappedWatchableObject(10, lines[i]));
                list.add(new WrappedWatchableObject(11, (byte) 1));
                list.add(new WrappedWatchableObject(12, -1700000));
                packet.getWatchableCollectionModifier().write(0, list);
                for (Player p : players) {
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (lines.length < entityIds.size()) {
                // Make delete packets
                int[] destroyIds = new int[(entityIds.size() - lines.length) * 2];
                int e = 0;
                while (entityIds.size() != lines.length) {
                    Entry<Integer, Integer> entry = entityIds.remove(entityIds.size() - 1);
                    destroyIds[e++] = entry.getKey();
                    destroyIds[e++] = entry.getValue();
                }
                PacketContainer destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
                destroyPacket.getIntegerArrays().write(0, destroyIds);
                for (Player p : players) {
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, destroyPacket, false);
                    } catch (InvocationTargetException b) {
                        b.printStackTrace();
                    }
                }
            } else {
                for (; i < lines.length; i++) {
                    Entry<Integer, Integer> entry = new HashMap.SimpleEntry(getId(), getId());
                    entityIds.add(entry);
                    // Make create packets
                    PacketContainer[] packets = this.makeSpawnPackets(i, entry.getKey(), entry.getValue(), lines[i]);
                    for (Player p : players) {
                        try {
                            for (PacketContainer packet : packets) {
                                ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                            }
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            makeDisplayPackets();
        }
        return this;
    }

    public Hologram setLineSpacing(double newLineSpacing) {
        this.lineSpacing = newLineSpacing;
        if (isInUse()) {
            makeDisplayPackets();
            HologramCentral.removeHologram(this);
            HologramCentral.addHologram(this);
        }
        return this;
    }

    public Hologram setViewDistance(int viewDistance) {
        assert viewDistance > 0 : "Why the hell are you setting the view distance to " + viewDistance + "?!?!";
        this.viewDistance = viewDistance;
        if (isInUse()) {
            HologramCentral.removeHologram(this);
            HologramCentral.addHologram(this);
        }
        return this;
    }

    public Hologram startHologram() {
        if (!isInUse()) {
            for (int i = 0; i < lines.length; i++) {
                int entityId = getId();
                this.entityIds.add(new HashMap.SimpleEntry(getId(), entityId));
            }
            makeDestroyPacket();
            makeDisplayPackets();
            HologramCentral.addHologram(this);
        }
        return this;
    }

    public Hologram stopHologram() {
        HologramCentral.removeHologram(this);
        return this;
    }

}
