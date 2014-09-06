package me.libraryaddict.holograms;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.commons.lang.ArrayUtils;
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
        BLACKLIST, WHITELIST;
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

    private PacketContainer destroyPacket1_7;
    private PacketContainer destroyPacket1_8;
    private PacketContainer[] displayPackets1_7;
    private PacketContainer[] displayPackets1_8;
    private ArrayList<Entry<Integer, Integer>> entityIds = new ArrayList<Entry<Integer, Integer>>();
    protected Location entityLastLocation;
    private ArrayList<String> hologramPlayers = new ArrayList<String>();
    private HologramTarget hologramTarget = HologramTarget.BLACKLIST;
    private boolean keepAliveAfterEntityDies;
    private Location lastMovement = new Location(null, 0, 0, 0);
    private String[] lines;
    private double lineSpacing = 1;
    private Location location;
    private Vector moveVector;
    private boolean pitchControlsMoreThanY;
    private Entity relativeEntity;
    private Location relativeToEntity;
    private boolean setRelativePitch;
    private boolean setRelativeYaw;
    private int viewDistance = 70;

    protected ArrayList<Entry<Integer, Integer>> getEntityIds() {
        return entityIds;
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

    protected PacketContainer getDestroyPacket(Player player) {
        if (HologramCentral.is1_8(player)) {
            return destroyPacket1_8;
        }
        return destroyPacket1_7;
    }

    public Entity getEntityFollowed() {
        return relativeEntity;
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

    public Vector getMovement() {
        return moveVector;
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

    public Location getRelativeToEntity() {
        return relativeToEntity;
    }

    protected PacketContainer[] getSpawnPackets(Player player) {
        if (HologramCentral.is1_8(player)) {
            return displayPackets1_8;
        }
        return displayPackets1_7;
    }

    public HologramTarget getTarget() {
        return hologramTarget;
    }

    public boolean hasPlayer(Player player) {
        return hologramPlayers.contains(player.getName());
    }

    public boolean isInUse() {
        return HologramCentral.isInUse(this);
    }

    public boolean isRelativePitch() {
        return setRelativePitch;
    }

    public boolean isRelativePitchControlMoreThanHeight() {
        return pitchControlsMoreThanY;
    }

    public boolean isRelativeYaw() {
        return setRelativeYaw;
    }

    public boolean isRemovedOnEntityDeath() {
        return this.keepAliveAfterEntityDies;
    }

    boolean isVisible(Player player, Location loc) {
        return (getTarget() == HologramTarget.BLACKLIST != hasPlayer(player)) && loc.getWorld() == getLocation().getWorld()
                && (loc.distance(getLocation()) <= viewDistance);
    }

    private void makeDestroyPacket() {
        int[] ids = new int[entityIds.size() * 2];
        int[] ids2 = new int[ids.length / 2];
        int i = 0;
        for (Entry<Integer, Integer> entry : entityIds) {
            ids2[i / 2] = entry.getKey();
            ids[i++] = entry.getKey();
            ids[i++] = entry.getValue();
        }
        destroyPacket1_7 = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket1_7.getIntegerArrays().write(0, ids);
        destroyPacket1_8 = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket1_8.getIntegerArrays().write(0, ids2);
    }

    private void makeDisplayPackets() {
        Iterator<Entry<Integer, Integer>> itel = entityIds.iterator();
        displayPackets1_7 = new PacketContainer[lines.length * 3];
        displayPackets1_8 = new PacketContainer[lines.length];
        int b = 0;
        while (itel.hasNext()) {
            Entry<Integer, Integer> entry = itel.next();
            displayPackets1_8[b] = makeSpawnPacket1_8(b, entry.getKey(), lines[(lines.length - 1) - b]);
            PacketContainer[] packets = makeSpawnPackets1_7(b, entry.getKey(), entry.getValue(), lines[(lines.length - 1) - b]);
            for (int a = 0; a < 3; a++) {
                displayPackets1_7[(b * 3) + a] = packets[a];
            }
            b++;
        }
    }

    private PacketContainer makeSpawnPacket1_8(int height, int witherId, String horseName) {
        PacketContainer displayPacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);
        StructureModifier<Integer> ints = displayPacket.getIntegers();
        ints.write(0, witherId);
        ints.write(1, 30);
        ints.write(2, (int) (getLocation().getX() * 32));
        ints.write(3, (int) ((location.getY() + -56.7 + ((double) height * (getLineSpacing() * 0.285))) * 32));
        ints.write(4, (int) (getLocation().getZ() * 32));
        // Setup datawatcher for armor stand
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        watcher.setObject(0, (byte) 32);
        watcher.setObject(2, horseName);
        watcher.setObject(3, (byte) 1);
        displayPacket.getDataWatcherModifier().write(0, watcher);
        return displayPacket;
    }

    private PacketContainer[] makeSpawnPackets1_7(int height, int witherId, int horseId, String horseName) {
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
        ints.write(3, (int) ((location.getY() + ((double) height * (getLineSpacing() * 0.285)) + 0.23D) * 32));
        ints.write(4, (int) (getLocation().getZ() * 32));
        // Setup datawatcher
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        watcher.setObject(0, (byte) 0);
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

    public Hologram moveHologram(Location location) {
        moveHologram(location, true);
        return this;
    }

    public Hologram moveHologram(Location newLocation, boolean setNewRelativeLocation) {
        ArrayList<Player> oldPlayers = getPlayers();
        Location loc = getLocation();
        this.location = newLocation.clone().add(0, 54.6, 0);
        if (setNewRelativeLocation && getEntityFollowed() != null) {
            relativeToEntity = getLocation().subtract(getEntityFollowed().getLocation());
        }
        // If packet is in use
        if (isInUse()) {
            // Make the new display packets
            makeDisplayPackets();
            ArrayList<Player> newPlayers = getPlayers();
            Iterator<Player> itel = oldPlayers.iterator();
            // Loop over the old players and send those who can't see the hologram at the new location a destroy packet
            while (itel.hasNext()) {
                Player p = itel.next();
                if (!newPlayers.contains(p)) {
                    itel.remove();
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, getDestroyPacket(p), false);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
            PacketContainer[] packets1_7 = null;
            PacketContainer[] packets1_8 = null;
            if (!oldPlayers.isEmpty()) {
                lastMovement.add(location.getX() - loc.getX(), location.getY() - loc.getY(), location.getZ() - loc.getZ());
                int x = (int) Math.floor(32 * lastMovement.getX());
                int y = (int) Math.floor(32 * lastMovement.getY());
                int z = (int) Math.floor(32 * lastMovement.getZ());
                packets1_7 = new PacketContainer[lines.length];
                packets1_8 = new PacketContainer[lines.length];
                int i = 0;
                if (x >= -128 && x < 128 && y >= -128 && y < 128 && z >= -128 && z < 128) {
                    lastMovement.subtract(x / 32D, y / 32D, z / 32D);
                    for (Entry<Integer, Integer> entityId : this.entityIds) {
                        packets1_7[i] = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE);
                        packets1_7[i].getIntegers().write(0, entityId.getKey());
                        StructureModifier<Byte> bytes = packets1_7[i].getBytes();
                        bytes.write(0, (byte) x);
                        bytes.write(1, (byte) y);
                        bytes.write(2, (byte) z);
                        packets1_8[i] = packets1_7[i];
                        i++;
                    }
                } else {
                    x = (int) Math.floor(32 * location.getX());
                    z = (int) Math.floor(32 * location.getZ());
                    lastMovement = new Location(null, location.getX() - (x / 32D), 0, location.getZ() - (z / 32D));
                    for (Entry<Integer, Integer> entityId : this.entityIds) {
                        packets1_7[i] = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
                        StructureModifier<Integer> ints = packets1_7[i].getIntegers();
                        ints.write(0, entityId.getKey());
                        ints.write(1, x);
                        ints.write(2, (int) Math.floor((location.getY() + ((double) i * (getLineSpacing() * 0.285))) * 32));
                        ints.write(3, z);
                        packets1_8[i] = packets1_7[i].shallowClone();
                        packets1_8[i].getIntegers().write(2,
                                (int) Math.floor((location.getY() + -56.7 + ((double) i * (getLineSpacing() * 0.285))) * 32));
                        i++;
                    }
                }
            }
            for (Player p : newPlayers) {
                try {
                    for (PacketContainer packet : oldPlayers.contains(p) ? HologramCentral.is1_8(p) ? packets1_8 : packets1_7
                            : getSpawnPackets(p)) {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                    }
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        return this;
    }

    public Hologram remove() {
        HologramCentral.removeHologram(this);
        return this;
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

    public Hologram setFollowEntity(Entity entity) {
        setFollowEntity(entity, true);
        return this;
    }

    public Hologram setFollowEntity(Entity entity, boolean isRemoveOnEntityDeath) {
        setFollowEntity(entity, isRemoveOnEntityDeath, false, false, false);
        return this;
    }

    public Hologram setFollowEntity(Entity entity, boolean isRemoveOnEntityDeath, boolean setRelativeYaw,
            boolean setRelativePitch, boolean pitchControlsMoreThanY) {
        relativeEntity = entity;
        if (entity != null) {
            this.keepAliveAfterEntityDies = isRemoveOnEntityDeath;
            relativeToEntity = getLocation().subtract(entity.getLocation());
            entityLastLocation = entity.getLocation();
            Location l = entity.getLocation();
            if (setRelativeYaw || setRelativePitch) {
                // double pitch=-Math.atan2(l.getY(),Math.sqrt((Math.pow(l, b)))
                relativeToEntity.setPitch(l.getPitch());
                relativeToEntity.setYaw(l.getYaw());
                this.setRelativePitch = setRelativePitch;
                this.setRelativeYaw = setRelativeYaw;
                this.pitchControlsMoreThanY = pitchControlsMoreThanY;
            }
        }
        return this;
    }

    public Hologram setLines(String... lines) {
        if (!this.lines.equals(lines)) {
            String[] oldLines = this.lines.clone();
            this.lines = lines;
            lines = lines.clone();
            ArrayUtils.reverse(lines);
            ArrayUtils.reverse(oldLines);
            if (isInUse()) {
                int i = 0;
                ArrayList<Player> players = getPlayers();

                for (; i < Math.min(entityIds.size(), lines.length); i++) {
                    if (oldLines.length <= i || !oldLines[i].equals(lines[i])) {
                        Entry<Integer, Integer> entry = entityIds.get(i);
                        PacketContainer packet1_7 = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
                        packet1_7.getIntegers().write(0, entry.getValue());
                        ArrayList<WrappedWatchableObject> list = new ArrayList<WrappedWatchableObject>();
                        list.add(new WrappedWatchableObject(0, (byte) 0));
                        list.add(new WrappedWatchableObject(1, (short) 300));
                        list.add(new WrappedWatchableObject(10, lines[i]));
                        list.add(new WrappedWatchableObject(11, (byte) 1));
                        list.add(new WrappedWatchableObject(12, -1700000));
                        packet1_7.getWatchableCollectionModifier().write(0, list);
                        PacketContainer packet1_8 = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
                        packet1_8.getIntegers().write(0, entry.getKey());
                        list = new ArrayList<WrappedWatchableObject>();
                        list.add(new WrappedWatchableObject(0, (byte) 32));
                        list.add(new WrappedWatchableObject(2, lines[i]));
                        list.add(new WrappedWatchableObject(3, (byte) 1));
                        packet1_8.getWatchableCollectionModifier().write(0, list);
                        for (Player p : players) {
                            try {
                                ProtocolLibrary.getProtocolManager().sendServerPacket(p,
                                        HologramCentral.is1_8(p) ? packet1_8 : packet1_7, false);
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                if (lines.length != entityIds.size()) {
                    if (lines.length < entityIds.size()) {
                        // Make delete packets
                        int[] destroyIds = new int[(entityIds.size() - lines.length) * 2];
                        int e = 0;
                        while (entityIds.size() > lines.length) {
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
                    } else if (lines.length > entityIds.size()) {
                        for (; i < lines.length; i++) {
                            Entry<Integer, Integer> entry = new HashMap.SimpleEntry(getId(), getId());
                            entityIds.add(entry);
                            // Make create packets
                            PacketContainer[] packets1_7 = this
                                    .makeSpawnPackets1_7(i, entry.getKey(), entry.getValue(), lines[i]);
                            PacketContainer packet1_8 = this.makeSpawnPacket1_8(i, entry.getKey(), lines[i]);
                            for (Player p : players) {
                                try {
                                    if (HologramCentral.is1_8(p))
                                        ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet1_8, false);
                                    else
                                        for (PacketContainer packet : packets1_7) {
                                            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
                                        }
                                } catch (InvocationTargetException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    makeDestroyPacket();
                }
                makeDisplayPackets();
            } else {
                if (lines.length < entityIds.size()) {
                    while (entityIds.size() > lines.length) {
                        entityIds.remove(entityIds.size() - 1);
                    }
                } else {
                    for (int i = entityIds.size(); i < lines.length; i++) {
                        Entry<Integer, Integer> entry = new HashMap.SimpleEntry(getId(), getId());
                        entityIds.add(entry);
                    }
                }
            }
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

    public Hologram setMovement(Vector vector) {
        this.moveVector = vector;
        return this;
    }

    public Hologram setRadius(int viewDistance) {
        assert viewDistance > 0 : "Why the hell are you setting the view distance to " + viewDistance + "?!?!";
        this.viewDistance = viewDistance;
        if (isInUse()) {
            HologramCentral.removeHologram(this);
            HologramCentral.addHologram(this);
        }
        return this;
    }

    public Hologram setRelativeToEntity(Location location) {
        this.relativeToEntity = location;
        return this;
    }

    public Hologram setTarget(HologramTarget target) {
        if (target != getTarget()) {
            hologramTarget = target;
            if (isInUse()) {
                HologramCentral.removeHologram(this);
                HologramCentral.addHologram(this);
            }
        }
        return this;
    }

    public Hologram start() {
        if (!isInUse()) {
            for (int i = entityIds.size(); i < lines.length; i++) {
                int entityId = getId();
                this.entityIds.add(new HashMap.SimpleEntry(getId(), entityId));
            }
            makeDestroyPacket();
            makeDisplayPackets();
            HologramCentral.addHologram(this);
        }
        return this;
    }

    public Hologram stop() {
        return remove();
    }

}
