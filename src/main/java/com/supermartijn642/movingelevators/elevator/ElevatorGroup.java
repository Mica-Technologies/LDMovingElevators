package com.supermartijn642.movingelevators.elevator;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockShape;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.packets.PacketSyncElevatorMovement;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created 4/7/2020 by SuperMartijn642
 */
public class ElevatorGroup {

    private static final int RE_SYNC_INTERVAL = 10;
    private static final double ACCELERATION = 0.05;
    private static final int CAGE_CHECK_INTERVAL = 20;
    private static final int MAX_CAGE_CHECKS_PER_TICK = 1;
    /**
     * How long the cabin waits at a floor before serving the next queued call, so passengers get a
     * chance to step in or out.
     */
    private static final int DWELL_TICKS = 20;
    /**
     * How long the cabin holds a landing it was dispatched to by a bank lobby panel: 15 seconds.
     * <p>
     * Far longer than the ordinary dwell because nobody is standing at the cabin when it is sent --
     * they are at a panel that may be across the lobby, and they pressed it before it arrived. The
     * "close doors" button cuts it short, so nobody has to wait out the full spell.
     */
    private static final int BANKED_DWELL_TICKS = 300;

    /**
     * Destinations to add to the queue once the cabin actually reaches a pickup floor, keyed by that
     * floor's y level.
     * <p>
     * Held back rather than queued when the call is placed, because a destination in the queue is
     * indistinguishable from a floor the cabin should already be going to. Someone at floor 1 asking
     * for floor 3 while the cabin sits at floor 5 would otherwise have it stop at 3 on the way down,
     * find nobody, and carry on to 1 -- having already served the floor they were going to.
     * <p>
     * A set per floor, so several people waiting at one landing for the same car all get their
     * destinations added when it arrives. That merging is the point of a bank.
     */
    private final Map<Integer,Set<Integer>> bankedDestinations = new HashMap<>();
    /** How long the doors at a floor should be held open, when longer than the configured default. */
    private final Map<Integer,Integer> doorHoldTicks = new HashMap<>();
    /** Hall call direction flags, stored as a mask per floor. */
    private static final int CALL_UP = 1, CALL_DOWN = 2;

    public final World level;
    public final int x, z;
    public final EnumFacing facing;

    private boolean isMoving = false;
    private int targetY;
    private double lastY;
    private double currentY;
    private double syncCurrentY = Integer.MAX_VALUE;
    private double targetSpeed = 0.2;
    private double speed = 0;
    private int cageSideOffset = 0, cageDepthOffset = 0, cageHeightOffset = -1;
    private int cageSizeX = 3, cageSizeY = 4, cageSizeZ = 3;
    private ElevatorCage cage = null;
    /**
     * The y coordinates of the controllers
     */
    private final ArrayList<Integer> floors = new ArrayList<>();
    private final ArrayList<FloorData> floorData = new ArrayList<>();
    private boolean shouldBeSynced = false;
    private final Map<Integer,Set<BlockPos>> comparatorListeners = new Int2ObjectArrayMap<>();

    private int syncCounter = 0;
    private int tickCounter = 0;
    private int cageChecks = 0;

    /**
     * y-levels of floors with an outstanding call, in the order they were requested. Insertion
     * ordered and duplicate-free, so pressing a button twice does not queue the floor twice.
     */
    private final LinkedHashSet<Integer> callQueue = new LinkedHashSet<>();
    /**
     * Direction of the last trip (+1 up, -1 down, 0 none yet). Calls continuing in this direction
     * are served first, so the cabin sweeps rather than ping-ponging.
     */
    private int lastDirection = 0;
    private int dwellCounter = 0;
    /**
     * Direction each hall call asked to travel, keyed by floor y-level, as a mask of
     * {@link #CALL_UP} / {@link #CALL_DOWN} -- a landing can have both pressed at once. Used to
     * light the landing panel's arrows, and to pick the sweep direction on arrival.
     */
    private final Map<Integer,Integer> callDirections = new HashMap<>();
    /**
     * When each floor last asked its doors to open or close, as world time.
     * <p>
     * A stamp rather than a flag that doors consume: a doorway is several blocks, and every one of
     * them has to see the same request. Each door remembers the last stamp it acted on. Not
     * persisted -- a request is a momentary thing, and doors close on their own anyway.
     */
    private final Map<Integer,Long> doorOpenRequests = new HashMap<>();
    private final Map<Integer,Long> doorCloseRequests = new HashMap<>();

    /**
     * The "Standard" sound scheme, and the only one so far.
     * <p>
     * Kept as named constants rather than scattered literals so that adding schemes later is a matter
     * of choosing a different set, not hunting down every playSound call.
     */

    private boolean soundsEnabled = true;
    private ElevatorSoundScheme soundScheme = ElevatorSoundScheme.STANDARD;
    /** Counts down to the second note of the arrival ding; 0 when there is none pending. */
    private int pendingDing;
    /** Which note the scheduled half of the arrival chime is. Decided on arrival, not when it plays,
     * so both notes belong to the same pair even if a call arrives in between. */
    private ElevatorSoundScheme.Moment pendingChime;

    /**
     * Ticks the alarm keeps ringing after the last "still held" from whoever is pressing it.
     * <p>
     * The button does not send "start" and "stop" -- it says "still held" every so often, and this
     * runs out on its own. A stop message can be lost to a disconnect, a closed screen, or a player
     * dying with the panel open, and an alarm nobody can switch off is far worse than one that stops
     * a fraction of a second late. It only has to outlast the gap between messages.
     */
    private static final int ALARM_HOLD_TICKS = 15;
    /** Ticks between strikes. An alarm bell is one bell hit over and over, so that is how it is made. */
    private static final int ALARM_STRIKE_INTERVAL = 5;
    private int alarmTicks;
    private int alarmStrikeCounter;

    public ElevatorGroup(World level, int x, int z, EnumFacing facing){
        this.level = level;
        this.x = x;
        this.z = z;
        this.facing = facing;
    }

    public void update(){
        this.tickCounter++;
        this.cageChecks = 0;
        if(!this.level.isRemote && this.pendingDing > 0 && --this.pendingDing == 0)
            this.playAtCabin(this.pendingChime);
        if(!this.level.isRemote)
            this.updateAlarm();
        if(!this.level.isRemote && this.shouldBeSynced){
            this.shouldBeSynced = false;
            this.updateGroup();
        }

        if(this.isMoving){
            if(this.currentY != this.targetY)
                this.lastY = this.currentY;
            if(Math.abs(this.targetY - this.currentY) / this.speed < (this.speed - 0.01) / ACCELERATION)
                this.speed = Math.max(0.01, this.speed - ACCELERATION);
            else if(this.speed < this.targetSpeed)
                this.speed = Math.min(this.targetSpeed, this.speed + ACCELERATION);
            if(this.currentY == this.targetY)
                this.stopElevator();
            else if(Math.abs(this.targetY - this.currentY) < this.speed){
                this.currentY = this.targetY;
                this.moveElevator(this.lastY, this.currentY);
            }else{
                if(this.syncCurrentY != Integer.MAX_VALUE){
                    this.currentY = this.syncCurrentY;
                    this.syncCurrentY = Integer.MAX_VALUE;
                }else
                    this.currentY += Math.signum(this.targetY - this.currentY) * this.speed;
                this.moveElevator(this.lastY, this.currentY);
            }

            this.playPassingFloorSounds();

            if(this.syncCounter >= RE_SYNC_INTERVAL){
                this.syncMovement();
                this.syncCounter = 0;
            }
            this.syncCounter++;
        }else if(!this.level.isRemote)
            this.updateCallQueue();
    }

    /**
     * Dispatches the cabin to the next outstanding call. Server-side only: the queue is authoritative
     * on the server and reaches clients through {@link #write()}.
     */
    private void updateCallQueue(){
        if(this.callQueue.isEmpty())
            return;
        if(this.dwellCounter > 0){
            this.dwellCounter--;
            return;
        }

        Integer target = this.pickNextCall();
        if(target == null)
            return;
        // Taken regardless of what happens below. If the cabin cannot reach the floor right now --
        // obstructed, or the controller is gone -- the call is dropped rather than retried forever;
        // pressing the button again re-queues it.
        this.callQueue.remove(target);

        int targetFloor = this.getFloorNumber(target);
        if(targetFloor == -1)
            return;
        // Cabin is already sitting there, so the call is already satisfied.
        if(this.isCageAvailableAt(targetFloor, true, null))
            return;

        // Reuse the existing "bring the cabin here" path so queued calls behave exactly like a
        // button press: it picks the nearest floor holding a cabin and checks the destination is
        // clear. A null requester means no chat feedback, which is right for an automatic dispatch.
        this.onButtonPress(false, false, target, null);
    }

    /**
     * Picks the call to serve next: the nearest one continuing the current direction of travel, or
     * failing that the nearest in any direction.
     */
    private Integer pickNextCall(){
        Integer best = null;
        int bestDistance = Integer.MAX_VALUE;

        if(this.lastDirection != 0){
            for(int y : this.callQueue){
                int delta = y - this.targetY;
                if(Integer.signum(delta) != this.lastDirection)
                    continue;
                int distance = Math.abs(delta);
                if(distance < bestDistance){
                    bestDistance = distance;
                    best = y;
                }
            }
            if(best != null)
                return best;
        }

        for(int y : this.callQueue){
            int distance = Math.abs(y - this.targetY);
            if(distance < bestDistance){
                bestDistance = distance;
                best = y;
            }
        }
        return best;
    }

    /**
     * A hall call: "bring the cabin to this floor, I want to go {@code up}".
     * <p>
     * Unlike {@link #onButtonPress}'s arrows, which mean "take the cabin from this floor to the next
     * one" and therefore only work while it is standing here, this always fetches the cabin --
     * that is what a landing button does. The requested direction is remembered so the sweep carries
     * on the way the caller wanted once it arrives.
     */
    public void onHallCall(int yLevel, boolean up, EntityPlayer requester){
        if(!this.floors.contains(yLevel))
            return;

        this.callDirections.merge(yLevel, up ? CALL_UP : CALL_DOWN, (a, b) -> a | b);

        if(this.isMoving){
            this.queueCall(yLevel);
            return;
        }

        int floor = this.getFloorNumber(yLevel);
        // Cabin is already standing here, so there is nothing to fetch -- but pressing the button
        // should still open the doors, which is what a waiting passenger expects.
        if(floor != -1 && this.isCageAvailableAt(floor, true, null)){
            this.clearHallCall(yLevel, up);
            this.requestDoorOpen(yLevel);
            return;
        }
        this.onButtonPress(false, false, yLevel, requester);
    }

    /**
     * A car call: "I am in the cabin, take me to this floor".
     * <p>
     * There is only ever one cabin, so fetching it to a floor and riding it to a floor are the same
     * movement -- the difference is only that a car call carries no direction, since the passenger is
     * already aboard and has said where they are going.
     */
    public void onCarCall(int yLevel, EntityPlayer requester){
        if(!this.floors.contains(yLevel))
            return;
        if(this.isMoving){
            this.queueCall(yLevel);
            return;
        }
        int floor = this.getFloorNumber(yLevel);
        // Already standing at the requested floor.
        if(floor != -1 && this.isCageAvailableAt(floor, true, null))
            return;
        this.onButtonPress(false, false, yLevel, requester);
    }

    /**
     * @return +1 while travelling up, -1 while travelling down, 0 when stopped
     */
    public int getTravelDirection(){
        return this.isMoving ? (int)Math.signum(this.targetY - this.currentY) : 0;
    }

    /**
     * Asks the doors at a floor to open. They only actually open if the cabin is standing there --
     * an open door onto an empty shaft would be both wrong and lethal.
     */
    public void requestDoorOpen(int yLevel){
        if(this.level != null)
            this.doorOpenRequests.put(yLevel, this.level.getTotalWorldTime());
    }

    /**
     * Asks the doors at a floor to close early, before their dwell runs out.
     */
    /**
     * A destination-dispatch call: "send a car to {@code pickupY}, and it is going to {@code destY}".
     * <p>
     * Unlike a hall call, the destination is known before boarding, which is what lets a bank put
     * two people going the same way into the same cabin.
     */
    public void onBankedCall(int pickupY, int destinationY, EntityPlayer requester){
        if(pickupY == destinationY)
            return;
        this.bankedDestinations.computeIfAbsent(pickupY, y -> new LinkedHashSet<>()).add(destinationY);
        this.onHallCall(pickupY, destinationY > pickupY, requester);
    }

    /**
     * Whether a car is already booked to collect from {@code pickupY} and carry on {@code up}.
     * Dispatch uses it to put riders going the same way together rather than sending a second car.
     */
    public boolean hasBankedCallFrom(int pickupY, boolean up){
        Set<Integer> destinations = this.bankedDestinations.get(pickupY);
        if(destinations == null)
            return false;
        for(int destination : destinations)
            if((destination > pickupY) == up)
                return true;
        return false;
    }

    /** How long the doors at a floor should stay open, in ticks. */
    public int getDoorHoldTicks(int yLevel){
        return this.doorHoldTicks.getOrDefault(yLevel, 0);
    }

    public void requestDoorClose(int yLevel){
        if(this.level != null)
            this.doorCloseRequests.put(yLevel, this.level.getTotalWorldTime());
        // Closing the doors ends the long hold a bank dispatch put on this landing. Whoever is
        // aboard has said they are ready, and not having to wait out the full fifteen seconds is
        // the entire reason the button exists.
        this.doorHoldTicks.remove(yLevel);
        if(this.dwellCounter > DWELL_TICKS && this.isCabinAt(yLevel))
            this.dwellCounter = 0;
    }

    /**
     * @return world time of the last open request for this floor, or 0 if there has never been one
     */
    public long getDoorOpenRequest(int yLevel){
        return this.doorOpenRequests.getOrDefault(yLevel, 0L);
    }

    /**
     * @return world time of the last close request for this floor, or 0 if there has never been one
     */
    public long getDoorCloseRequest(int yLevel){
        return this.doorCloseRequests.getOrDefault(yLevel, 0L);
    }

    /**
     * @return whether the cabin is parked at this floor, i.e. whether it is safe to open its doors
     */
    public boolean isCabinAt(int yLevel){
        if(this.isMoving)
            return false;
        int floor = this.getFloorNumber(yLevel);
        return floor != -1 && floor == this.getCabinFloorNumber();
    }

    /**
     * @return whether the floor at the given y-level has an outstanding hall call in this direction
     */
    public boolean hasHallCall(int yLevel, boolean up){
        return (this.callDirections.getOrDefault(yLevel, 0) & (up ? CALL_UP : CALL_DOWN)) != 0;
    }

    private void clearHallCall(int yLevel, boolean up){
        Integer directions = this.callDirections.get(yLevel);
        if(directions == null)
            return;
        int remaining = directions & ~(up ? CALL_UP : CALL_DOWN);
        if(remaining == 0)
            this.callDirections.remove(yLevel);
        else
            this.callDirections.put(yLevel, remaining);
        this.shouldBeSynced = true;
    }

    /**
     * Records a call for the floor at the given y-level, to be served once the cabin is free.
     */
    private void queueCall(int yLevel){
        if(!this.floors.contains(yLevel))
            return;
        // Already on its way there.
        if(this.isMoving && yLevel == this.targetY)
            return;
        if(this.callQueue.add(yLevel))
            this.shouldBeSynced = true;
    }

    /**
     * @return y-levels of floors with an outstanding call
     */
    public Set<Integer> getCallQueue(){
        return Collections.unmodifiableSet(this.callQueue);
    }

    /**
     * @return whether the floor at the given y-level has an outstanding call
     */
    public boolean hasCallFor(int yLevel){
        return this.callQueue.contains(yLevel);
    }

    public boolean areSoundsEnabled(){
        return this.soundsEnabled;
    }

    /**
     * Called repeatedly while a player holds the alarm button. Ringing lapses on its own once they
     * stop; see {@link #ALARM_HOLD_TICKS}.
     */
    public void ringAlarm(){
        // First press strikes immediately -- a button that waits before making a noise feels broken.
        if(this.alarmTicks <= 0)
            this.alarmStrikeCounter = 0;
        this.alarmTicks = ALARM_HOLD_TICKS;
    }

    public boolean isAlarmRinging(){
        return this.alarmTicks > 0;
    }

    private void updateAlarm(){
        if(this.alarmTicks <= 0)
            return;
        this.alarmTicks--;
        if(this.alarmStrikeCounter > 0){
            this.alarmStrikeCounter--;
            return;
        }
        this.alarmStrikeCounter = ALARM_STRIKE_INTERVAL;
        this.strikeAlarm();
    }

    /**
     * Rings once, in the cabin and at every landing.
     * <p>
     * At every landing because an alarm only the trapped passenger can hear is not an alarm -- the
     * point is to reach somebody who can help, and they are by definition not in the cabin.
     * <p>
     * Not gated on {@link #soundsEnabled}, unlike everything else here: that switch is for the
     * noises the elevator makes on its own, and a button a player is actively holding down that
     * produces no sound is indistinguishable from a broken one.
     */
    private void strikeAlarm(){
        Vec3d cabin = this.getCageAnchorPos(this.currentY).addVector(this.cageSizeX / 2d, this.cageSizeY / 2d, this.cageSizeZ / 2d);
        this.soundScheme.play(this.level, cabin, ElevatorSoundScheme.Moment.ALARM);
        for(int floor = 0; floor < this.getFloorCount(); floor++){
            BlockPos pos = this.getPos(this.getFloorYLevel(floor));
            if(this.level.isBlockLoaded(pos))
                this.soundScheme.play(this.level, new Vec3d(pos).addVector(0.5, 0.5, 0.5), ElevatorSoundScheme.Moment.ALARM);
        }
    }

    public ElevatorSoundScheme getSoundScheme(){
        return this.soundScheme;
    }

    public void setSoundScheme(ElevatorSoundScheme scheme){
        this.soundScheme = scheme == null ? ElevatorSoundScheme.STANDARD : scheme;
        this.shouldBeSynced = true;
    }

    public void setSoundsEnabled(boolean soundsEnabled){
        this.soundsEnabled = soundsEnabled;
        this.shouldBeSynced = true;
    }

    /**
     * A soft tick each time the cabin passes a landing, so a ride has some sense of progress rather
     * than being silent between departure and arrival.
     */
    private void playPassingFloorSounds(){
        if(this.level.isRemote || !this.soundsEnabled)
            return;
        double from = Math.min(this.lastY, this.currentY), to = Math.max(this.lastY, this.currentY);
        for(int floor = 0; floor < this.floors.size(); floor++){
            int y = this.floors.get(floor);
            // Strictly greater than 'from' so a floor is not announced twice when the cabin starts
            // moving from a standstill on top of it.
            if(y > from && y <= to)
                this.playAtCabin(ElevatorSoundScheme.Moment.PASSING_FLOOR);
        }
    }

    /**
     * Plays one of this elevator's sounds at the cabin, if sounds are on for this elevator and the
     * scheme in use has one for that moment.
     */
    public void playAtCabin(ElevatorSoundScheme.Moment moment){
        if(this.level == null || this.level.isRemote || !this.soundsEnabled)
            return;
        Vec3d pos = this.getCageAnchorPos(this.currentY).addVector(this.cageSizeX / 2d, this.cageSizeY / 2d, this.cageSizeZ / 2d);
        this.soundScheme.play(this.level, pos, moment);
    }

    /**
     * Plays one of this elevator's sounds somewhere other than the cabin -- at a landing's doors, say.
     */
    public void playAt(Vec3d pos, ElevatorSoundScheme.Moment moment){
        if(this.soundsEnabled)
            this.soundScheme.play(this.level, pos, moment);
    }

    private void moveElevator(double oldY, double newY){
        ElevatorCollisionHandler.handleEntityCollisions(this.level, this.cage.bounds, this.cage.collisionBoxes, this.getCageAnchorPos(oldY), new Vec3d(this.x, newY - oldY, 0));
    }

    private void stopElevator(){
        this.isMoving = false;

        // Arriving satisfies any call for this floor, and starts the dwell before the next one.
        this.callQueue.remove(this.targetY);
        // Anyone who asked for this car from a lobby panel is boarding now, so their destinations
        // become real calls -- all of them, which is how two people going the same way end up
        // sharing the trip.
        Set<Integer> banked = this.bankedDestinations.remove(this.targetY);
        boolean collecting = banked != null && !banked.isEmpty();
        if(collecting){
            for(int destination : banked)
                if(destination != this.targetY && this.getFloorNumber(destination) != -1)
                    this.callQueue.add(destination);
            this.doorHoldTicks.put(this.targetY, BANKED_DWELL_TICKS);
        }else
            this.doorHoldTicks.remove(this.targetY);
        this.dwellCounter = collecting ? BANKED_DWELL_TICKS : DWELL_TICKS;
        // Carry on the way whoever called from this landing wanted to travel. With both arrows
        // pressed the current direction wins, which is what a real elevator does.
        Integer directions = this.callDirections.remove(this.targetY);
        if(directions != null && directions != (CALL_UP | CALL_DOWN))
            this.lastDirection = directions == CALL_UP ? 1 : -1;
        // Arriving opens the doors, exactly as a real elevator does.
        this.requestDoorOpen(this.targetY);

        this.cage.place(this.level, this.getCageAnchorBlockPos(this.targetY));
        this.floorData.get(this.getFloorNumber(this.targetY)).isCageAvailable = true;

        this.moveElevator(this.lastY, this.currentY);

        if(!this.level.isRemote){
            this.level.updateComparatorOutputLevel(this.getPos(this.targetY), MovingElevators.elevator_block);
            for(BlockPos pos : this.comparatorListeners.getOrDefault(this.targetY, Collections.emptySet()))
                if(this.level.isBlockLoaded(pos))
                    this.level.updateComparatorOutputLevel(pos, this.level.getBlockState(pos).getBlock());
            this.shouldBeSynced = true;
            Vec3d soundPos = this.getCageAnchorPos(this.targetY).addVector(this.cageSizeX / 2d, this.cageSizeY / 2d, this.cageSizeZ / 2d);
            if(this.soundsEnabled){
                this.playAt(soundPos, ElevatorSoundScheme.Moment.ARRIVED);
                // Two notes rather than one: the second is scheduled, so arrival reads as a ding-dong
                // rather than a single blip lost under the arrival sound.
                // Which way the car goes next, announced as it lands. A hall call answered here
                // already says where its passenger is going; failing that, the next queued call does.
                int onward = this.onwardDirection(directions);
                this.playAtCabin(ElevatorSoundScheme.Moment.arrivalChime(onward, false));
                this.pendingChime = ElevatorSoundScheme.Moment.arrivalChime(onward, true);
                this.pendingDing = this.soundScheme.chimeGapTicks();
            }
            this.syncCounter = 0;
        }
    }

    /**
     * Where the cabin is headed after the stop it has just made: 1 up, -1 down, 0 nowhere.
     *
     * @param landingCallDirections the hall call consumed at this landing, if there was one. It is
     *                              the better answer when present -- someone stepping in has said
     *                              which way they want to go before any call for it exists.
     */
    private int onwardDirection(Integer landingCallDirections){
        if(landingCallDirections != null && landingCallDirections != (CALL_UP | CALL_DOWN))
            return landingCallDirections == CALL_UP ? 1 : -1;
        Integer next = this.pickNextCall();
        return next == null ? 0 : Integer.signum(next - this.targetY);
    }

    private void startElevator(int currentY, int targetY){
        if(this.level == null || this.isMoving)
            return;

        ElevatorCage cage = ElevatorCage.createCageAndClear(this.level, this.getCageAnchorBlockPos(currentY), this.cageSizeX, this.cageSizeY, this.cageSizeZ);
        if(cage == null)
            return;
        this.floorData.get(this.getFloorNumber(currentY)).isCageAvailable = false;

        this.cage = cage;
        this.isMoving = true;
        this.targetY = targetY;
        this.currentY = currentY;
        this.lastY = this.currentY;
        this.speed = 0;
        this.lastDirection = Integer.signum(targetY - currentY);
        // Whoever asked for this floor is being served now.
        this.callQueue.remove(targetY);

        if(!this.level.isRemote){
            this.level.updateComparatorOutputLevel(this.getPos(currentY), MovingElevators.elevator_block);
            for(BlockPos pos : this.comparatorListeners.getOrDefault(currentY, Collections.emptySet()))
                if(this.level.isBlockLoaded(pos))
                    this.level.updateComparatorOutputLevel(pos, this.level.getBlockState(pos).getBlock());
            this.updateGroup();
        }
    }

    public void onButtonPress(boolean isUp, boolean isDown, int yLevel, EntityPlayer requester){
        if(!this.floors.contains(yLevel))
            return;
        if(this.isMoving){
            // Queue "bring the cabin here" rather than dropping the press. The up/down arrows are
            // deliberately not queued: they mean "take the cabin from this floor to the next one",
            // which only has a meaning while the cabin is actually standing here.
            if(!isUp && !isDown)
                this.queueCall(yLevel);
            return;
        }

        ControllerBlockEntity entity = this.getEntity(yLevel);
        if(entity == null)
            return;
        int entityFloor = this.floors.indexOf(yLevel);

        if(isUp){
            if(this.isCageAvailableAt(entityFloor, true, requester)){
                for(int floor = entityFloor + 1; floor < this.floors.size(); floor++){
                    ControllerBlockEntity entity2 = this.getEntity(this.floors.get(floor));
                    if(entity2 != null){
                        if(this.canCageBePlacedAt(entity2, entity, requester))
                            this.startElevator(yLevel, this.floors.get(floor));
                        return;
                    }
                }
            }
        }else if(isDown){
            if(this.isCageAvailableAt(entityFloor, true, requester)){
                for(int floor = entityFloor - 1; floor >= 0; floor--){
                    ControllerBlockEntity entity2 = this.getEntity(this.floors.get(floor));
                    if(entity2 != null){
                        if(this.canCageBePlacedAt(entity2, entity, requester))
                            this.startElevator(yLevel, this.floors.get(floor));
                        return;
                    }
                }
            }
        }else{
            // The cabin is already standing here, so there is nothing to fetch -- but the press still
            // means "let me in". The search below skips this floor entirely, so without this a call
            // button did nothing at all once the cabin had arrived, and its doors stayed shut.
            if(this.isCageAvailableAt(entityFloor, true, null)){
                this.requestDoorOpen(yLevel);
                return;
            }
            List<Integer> floorIndices = IntStream.range(0, this.floors.size()).boxed().sorted(Comparator.comparingInt(i -> Math.abs(this.floors.get(i) - yLevel))).collect(Collectors.toList());
            for(int floor : floorIndices){
                if(floor == entityFloor)
                    continue;
                if(this.isCageAvailableAt(floor, true, null)){
                    if(this.canCageBePlacedAt(entity, this.getEntityForFloor(floor), requester))
                        this.startElevator(this.getFloorYLevel(floor), yLevel);
                    return;
                }
            }
            if(requester instanceof EntityPlayerMP && !this.isCageAvailableAt(entityFloor, true, null))
                requester.sendStatusMessage(TextComponents.translation("movingelevators.elevator.no_cabins").color(TextFormatting.GRAY).get(), false);
        }
    }

    public void onDisplayPress(int yLevel, int floorOffset, EntityPlayer requester){
        if(!this.floors.contains(yLevel))
            return;

        int floor = this.floors.indexOf(yLevel);
        if(this.isMoving){
            // Offset 0 is "call the cabin to this floor"; anything else selects a destination
            // relative to it. Either way the request becomes a call for a concrete floor.
            int toFloor = floor + floorOffset;
            if(floorOffset == 0)
                this.queueCall(yLevel);
            else if(toFloor >= 0 && toFloor < this.floors.size())
                this.queueCall(this.floors.get(toFloor));
            return;
        }
        if(floorOffset == 0){
            this.onButtonPress(false, false, yLevel, requester);
            return;
        }

        int toFloor = floor + floorOffset;
        if(toFloor < 0 || toFloor >= this.floors.size())
            return;

        ControllerBlockEntity entity = this.getEntity(yLevel);
        int toY = this.floors.get(toFloor);
        ControllerBlockEntity toEntity = this.getEntity(toY);
        if(entity != null && toEntity != null && this.isCageAvailableAt(floor, true, requester) && this.canCageBePlacedAt(toEntity, entity, requester))
            this.startElevator(yLevel, toY);
    }

    public void remove(ControllerBlockEntity entity){
        this.removeFloor(this.getFloorNumber(entity.getPos().getY()));
    }

    private void removeFloor(int floor){
        // remove() passes getFloorNumber(), which is indexOf() and so returns -1 when the y is not
        // in the list -- for instance when validateControllersExist already dropped this floor
        // while the controller block was still standing. ArrayList.remove(-1) would throw.
        if(floor < 0 || floor >= this.floors.size())
            return;
        // Drop any outstanding call for the floor before it stops existing, so the queue can never
        // dispatch to a y-level that is no longer a floor.
        this.callQueue.remove(this.floors.get(floor));
        this.callDirections.remove(this.floors.get(floor));
        this.floors.remove(floor);
        this.floorData.remove(floor);
        if(this.floors.isEmpty()){
            if(this.isMoving){
                Vec3d spawnPos = this.getCageAnchorPos(this.targetY).addVector(this.cageSizeX / 2d, this.cageSizeY / 2d, this.cageSizeZ / 2d);
                this.cage.getDrops().forEach(stack -> {
                    EntityItem itemEntity = new EntityItem(this.level, spawnPos.x, spawnPos.y, spawnPos.z, stack);
                    this.level.spawnEntity(itemEntity);
                });
            }
        }else
            this.shouldBeSynced = true;
    }

    public void add(ControllerBlockEntity entity){
        if(entity == null)
            return;
        int y = entity.getPos().getY();
        if(this.floors.contains(y))
            return;
        FloorData floorData = new FloorData(entity.getFloorName(), entity.getDisplayLabelColor());
        for(int i = 0; i < this.floors.size(); i++){
            if(y < this.floors.get(i)){
                this.floors.add(i, y);
                this.floorData.add(i, floorData);
                break;
            }
        }
        if(!this.floors.contains(y)){
            this.floors.add(y);
            this.floorData.add(floorData);
        }
        this.shouldBeSynced = true;
    }

    public void updateFloorData(ControllerBlockEntity entity, String name, EnumDyeColor color){
        int floor = this.getFloorNumber(entity.getPos().getY());
        if(floor == -1)
            return;
        FloorData data = this.floorData.get(floor);
        if(!Objects.equals(name, data.name) || color != data.color){
            data.name = name;
            data.color = color;
            this.shouldBeSynced = true;
        }
    }

    public boolean isMoving(){
        return this.isMoving;
    }

    public double getLastY(){
        return this.lastY;
    }

    public double getCurrentY(){
        return this.currentY;
    }

    public void updateCurrentY(double y, double speed){
        if(this.isMoving && (this.currentY < this.lastY ? y < this.currentY : y > this.currentY) && speed >= this.speed){
            this.syncCurrentY = y;
            this.speed = speed;
        }
    }

    public ElevatorCage getCage(){
        return this.cage;
    }

    public double getTargetSpeed(){
        return this.targetSpeed;
    }

    public void setTargetSpeed(double targetSpeed){
        this.targetSpeed = targetSpeed;
        this.shouldBeSynced = true;
    }

    public int getCageSideOffset(){
        return this.cageSideOffset;
    }

    public boolean canIncreaseCageSideOffset(){
        return !this.isMoving() && this.cageSideOffset < 2 + (this.facing == EnumFacing.NORTH || this.facing == EnumFacing.WEST ? (this.getCageWidth() - 1) / 2 : (int)Math.ceil((this.getCageWidth() - 1) / 2f));
    }

    public void increaseCageSideOffset(){
        if(this.canIncreaseCageSideOffset()){
            this.cageSideOffset++;
            this.shouldBeSynced = true;
        }
    }

    public boolean canDecreaseCageSideOffset(){
        return !this.isMoving() && this.cageSideOffset > -2 - (this.facing == EnumFacing.NORTH || this.facing == EnumFacing.WEST ? (int)Math.ceil((this.getCageWidth() - 1) / 2f) : (this.getCageWidth() - 1) / 2);
    }

    public void decreaseCageSideOffset(){
        if(this.canDecreaseCageSideOffset()){
            this.cageSideOffset--;
            this.shouldBeSynced = true;
        }
    }

    public int getCageDepthOffset(){
        return this.cageDepthOffset;
    }

    public boolean canIncreaseCageDepthOffset(){
        return !this.isMoving() && this.cageDepthOffset < 2;
    }

    public void increaseCageDepthOffset(){
        if(this.canIncreaseCageDepthOffset()){
            this.cageDepthOffset++;
            this.shouldBeSynced = true;
        }
    }

    public boolean canDecreaseCageDepthOffset(){
        return !this.isMoving() && this.cageDepthOffset > 0;
    }

    public void decreaseCageDepthOffset(){
        if(this.canDecreaseCageDepthOffset()){
            this.cageDepthOffset--;
            this.shouldBeSynced = true;
        }
    }

    public int getCageHeightOffset(){
        return this.cageHeightOffset;
    }

    public boolean canIncreaseCageHeightOffset(){
        return !this.isMoving() && this.cageHeightOffset < 3;
    }

    public void increaseCageHeightOffset(){
        if(this.canIncreaseCageHeightOffset()){
            this.cageHeightOffset++;
            this.shouldBeSynced = true;
        }
    }

    public boolean canDecreaseCageHeightOffset(){
        return !this.isMoving() && this.cageHeightOffset > -this.cageSizeY;
    }

    public void decreaseCageHeightOffset(){
        if(this.canDecreaseCageHeightOffset()){
            this.cageHeightOffset--;
            this.shouldBeSynced = true;
        }
    }

    public int getCageWidth(){
        return this.facing.getAxis() == EnumFacing.Axis.X ? this.cageSizeZ : this.cageSizeX;
    }

    public boolean canIncreaseCageWidth(){
        return !this.isMoving && this.getCageWidth() < MovingElevatorsConfig.maxCabinHorizontalSize.get();
    }

    public void increaseCageWidth(){
        if(!this.isMoving && this.canIncreaseCageWidth()){
            if(this.facing.getAxis() == EnumFacing.Axis.X)
                this.cageSizeZ++;
            else
                this.cageSizeX++;
            this.shouldBeSynced = true;
        }
    }

    public boolean canDecreaseCageWidth(){
        return !this.isMoving && this.getCageWidth() > 1;
    }

    public void decreaseCageWidth(){
        if(!this.isMoving && this.canDecreaseCageWidth()){
            if(this.facing.getAxis() == EnumFacing.Axis.X)
                this.cageSizeZ--;
            else
                this.cageSizeX--;
            if(this.cageSideOffset > 2 + (this.facing == EnumFacing.NORTH || this.facing == EnumFacing.WEST ? (this.getCageWidth() - 1) / 2 : (int)Math.ceil((this.getCageWidth() - 1) / 2f)))
                this.cageSideOffset = 2 + (this.facing == EnumFacing.NORTH || this.facing == EnumFacing.WEST ? (this.getCageWidth() - 1) / 2 : (int)Math.ceil((this.getCageWidth() - 1) / 2f));
            else if(this.cageSideOffset < -2 - (this.facing == EnumFacing.NORTH || this.facing == EnumFacing.WEST ? (int)Math.ceil((this.getCageWidth() - 1) / 2f) : (this.getCageWidth() - 1) / 2))
                this.cageSideOffset = -2 - (this.facing == EnumFacing.NORTH || this.facing == EnumFacing.WEST ? (int)Math.ceil((this.getCageWidth() - 1) / 2f) : (this.getCageWidth() - 1) / 2);
            this.shouldBeSynced = true;
        }
    }

    public int getCageDepth(){
        return this.facing.getAxis() == EnumFacing.Axis.X ? this.cageSizeX : this.cageSizeZ;
    }

    public boolean canIncreaseCageDepth(){
        return !this.isMoving && this.getCageDepth() < MovingElevatorsConfig.maxCabinHorizontalSize.get();
    }

    public void increaseCageDepth(){
        if(!this.isMoving && this.canIncreaseCageDepth()){
            if(this.facing.getAxis() == EnumFacing.Axis.X)
                this.cageSizeX++;
            else
                this.cageSizeZ++;
            this.shouldBeSynced = true;
        }
    }

    public boolean canDecreaseCageDepth(){
        return !this.isMoving && this.getCageDepth() > 1;
    }

    public void decreaseCageDepth(){
        if(!this.isMoving && this.canDecreaseCageDepth()){
            if(this.facing.getAxis() == EnumFacing.Axis.X)
                this.cageSizeX--;
            else
                this.cageSizeZ--;
            this.shouldBeSynced = true;
        }
    }

    public int getCageHeight(){
        return this.getCageSizeY();
    }

    public boolean canIncreaseCageHeight(){
        return !this.isMoving && this.cageSizeY < MovingElevatorsConfig.maxCabinVerticalSize.get();
    }

    public void increaseCageHeight(){
        if(!this.isMoving && this.canIncreaseCageHeight()){
            this.cageSizeY++;
            this.shouldBeSynced = true;
        }
    }

    public boolean canDecreaseCageHeight(){
        return !this.isMoving && this.cageSizeY > 1;
    }

    public void decreaseCageHeight(){
        if(!this.isMoving && this.canDecreaseCageHeight()){
            this.cageSizeY--;
            if(this.cageHeightOffset < -this.cageSizeY)
                this.cageHeightOffset = -this.cageSizeY;
            this.shouldBeSynced = true;
        }
    }

    public int getCageSizeX(){
        return this.cageSizeX;
    }

    public int getCageSizeY(){
        return this.cageSizeY;
    }

    public int getCageSizeZ(){
        return this.cageSizeZ;
    }

    public EnumDyeColor getFloorDisplayColor(int floor){
        return this.floorData.get(floor).color;
    }

    public String getFloorDisplayName(int floor){
        return this.floorData.get(floor).name;
    }

    /**
     * @param y y-level of the elevator controller
     */
    public BlockPos getCageAnchorBlockPos(int y){
        int x = 0, z = 0;
        if(this.facing == EnumFacing.NORTH){
            x = this.x - this.cageSizeX / 2 - this.cageSideOffset;
            z = this.z - this.cageSizeZ - this.cageDepthOffset;
        }else if(this.facing == EnumFacing.SOUTH){
            x = this.x - this.cageSizeX / 2 + this.cageSideOffset;
            z = this.z + 1 + this.cageDepthOffset;
        }else if(this.facing == EnumFacing.WEST){
            x = this.x - this.cageSizeX - this.cageDepthOffset;
            z = this.z - this.cageSizeZ / 2 + this.cageSideOffset;
        }else if(this.facing == EnumFacing.EAST){
            x = this.x + 1 + this.cageDepthOffset;
            z = this.z - this.cageSizeZ / 2 - this.cageSideOffset;
        }
        y += this.cageHeightOffset;
        return new BlockPos(x, y, z);
    }

    public Vec3d getCageAnchorPos(double y){
        BlockPos pos = this.getCageAnchorBlockPos(0);
        return new Vec3d(pos.getX(), y + this.cageHeightOffset, pos.getZ());
    }

    /**
     * @return whether the blocks at the given floor are suitable for a cage
     */
    public boolean isCageAvailableAt(int floor, boolean forceRefresh, EntityPlayer requester){
        FloorData floorData = this.floorData.get(floor);
        if(forceRefresh || (this.tickCounter - floorData.lastCageCheck > CAGE_CHECK_INTERVAL && this.cageChecks < MAX_CAGE_CHECKS_PER_TICK && this.level.isBlockLoaded(this.getPos(this.getFloorYLevel(floor))))){
            boolean isCageAvailable = ElevatorCage.canCreateCage(this.level, this.getCageAnchorBlockPos(this.getFloorYLevel(floor)), this.cageSizeX, this.cageSizeY, this.cageSizeZ, requester);
            if(isCageAvailable != floorData.isCageAvailable)
                this.shouldBeSynced = true;
            floorData.isCageAvailable = isCageAvailable;
            floorData.lastCageCheck = this.tickCounter;
        }
        return floorData.isCageAvailable;
    }

    /**
     * @return whether the blocks at the given floor are suitable for a cage
     */
    public boolean isCageAvailableAt(int floor){
        return this.isCageAvailableAt(floor, false, null);
    }

    /**
     * @param entity the entity that the cage would be placed in front of
     * @param from   the entity that the cage originates from, overlapping space in cage area will be ignored
     * @return whether there is enough space for the cage to be placed in front
     * of the given {@code entity}
     */
    public boolean canCageBePlacedAt(ControllerBlockEntity entity, ControllerBlockEntity from, EntityPlayer requester){
        BlockPos startPos = this.getCageAnchorBlockPos(entity.getPos().getY());
        int minY = 0, maxY = this.cageSizeY;
        if(from != null){
            int y = this.getCageAnchorBlockPos(from.getPos().getY()).getY();
            if(y > startPos.getY() && y < startPos.getY() + this.cageSizeY)
                maxY = y - startPos.getY();
            else if(y < startPos.getY() && y + this.cageSizeY > startPos.getY())
                minY = y + this.cageSizeY - startPos.getY();
        }
        for(int x = 0; x < this.cageSizeX; x++){
            for(int y = minY; y < maxY; y++){
                for(int z = 0; z < this.cageSizeZ; z++){
                    if(!this.level.isAirBlock(startPos.add(x, y, z))){
                        if(requester instanceof EntityPlayerMP){
                            ITextComponent block = TextComponents.block(this.level.getBlockState(startPos.add(x, y, z)).getBlock()).color(TextFormatting.GOLD).get();
                            ITextComponent position = TextComponents.string("(").color(TextFormatting.GRAY)
                                .append(TextComponents.number(startPos.getX() + x).color(TextFormatting.GOLD).get()).string(",").color(TextFormatting.GRAY)
                                .append(TextComponents.number(startPos.getY() + y).color(TextFormatting.GOLD).get()).string(",").color(TextFormatting.GRAY)
                                .append(TextComponents.number(startPos.getZ() + z).color(TextFormatting.GOLD).get()).string(")").color(TextFormatting.GRAY).get();
                            requester.sendStatusMessage(TextComponents.translation("movingelevators.elevator.obstructed", block, position).color(TextFormatting.GRAY).get(), false);
                        }
                        return false;
                }
            }
        }
    }
        return true;
}

public void addComparatorListener(int floorYLevel, BlockPos blockPos){
    this.comparatorListeners.putIfAbsent(floorYLevel, new HashSet<>());
    this.comparatorListeners.get(floorYLevel).add(blockPos);
}

public boolean removeComparatorListener(BlockPos blockPos){
    boolean removed = false;
    Iterator<Set<BlockPos>> iterator = this.comparatorListeners.values().iterator();
    while(iterator.hasNext()){
        Set<BlockPos> positions = iterator.next();
        if(positions.remove(blockPos))
            removed = true;
    }
    return removed;
}

public NBTTagCompound write(){
    NBTTagCompound compound = new NBTTagCompound();
    compound.setBoolean("isMoving", this.isMoving);
    // Written unconditionally: while stopped this is where the cabin came to rest, which is the
    // reference point pickNextCall() sweeps from. Upstream only stored it mid-move, so a reloaded
    // idle group would have dispatched queued calls relative to y=0.
    compound.setInteger("targetY", this.targetY);
    if(this.isMoving){
        compound.setDouble("lastY", this.lastY);
        compound.setDouble("currentY", this.currentY);
        compound.setTag("cage", this.cage.write());
    }
    compound.setDouble("targetSpeed", this.targetSpeed);
    compound.setDouble("speed", this.speed);
    compound.setInteger("cageSideOffset", this.cageSideOffset);
    compound.setInteger("cageDepthOffset", this.cageDepthOffset);
    compound.setInteger("cageHeightOffset", this.cageHeightOffset);
    compound.setInteger("cageSizeX", this.cageSizeX);
    compound.setInteger("cageSizeY", this.cageSizeY);
    compound.setInteger("cageSizeZ", this.cageSizeZ);
    int[] arr = new int[this.floors.size()];
    for(int i = 0; i < this.floors.size(); i++)
        arr[i] = this.floors.get(i);
    compound.setIntArray("floors", arr);
    int[] queue = new int[this.callQueue.size()];
    int queueIndex = 0;
    for(int y : this.callQueue)
        queue[queueIndex++] = y;
    compound.setIntArray("callQueue", queue);
    // Flattened y/mask pairs, so a landing's lit arrows survive a reload alongside its queued call.
    int[] directions = new int[this.callDirections.size() * 2];
    int directionIndex = 0;
    for(Map.Entry<Integer,Integer> entry : this.callDirections.entrySet()){
        directions[directionIndex++] = entry.getKey();
        directions[directionIndex++] = entry.getValue();
    }
    compound.setIntArray("callDirections", directions);
    compound.setBoolean("soundsEnabled", this.soundsEnabled);
    compound.setString("soundScheme", this.soundScheme.name());
    compound.setInteger("lastDirection", this.lastDirection);
    compound.setInteger("dwellCounter", this.dwellCounter);
    NBTTagList floorDataTag = new NBTTagList();
    for(FloorData floorDatum : this.floorData)
        floorDataTag.appendTag(floorDatum.write());
    compound.setTag("floorData", floorDataTag);
    return compound;
}

public void read(NBTTagCompound compound){
    if(compound.hasKey("moving")){ // old version stuff
        this.isMoving = compound.getBoolean("moving");
        int size = compound.getInteger("size");
        if(this.isMoving){
            this.targetY = compound.getInteger("targetY");
            this.lastY = compound.getDouble("lastY");
            this.currentY = compound.getDouble("currentY");
            IBlockState[][][] blockStates = new IBlockState[size][1][size];
            BlockShape shape = BlockShape.empty();
            for(int x = 0; x < size; x++){
                for(int z = 0; z < size; z++){
                    IBlockState state = Block.getStateById(compound.getInteger("platform" + x + "," + z));
                    if(state.getBlock() != Blocks.AIR){
                        blockStates[x][0][z] = state;
                        shape = BlockShape.or(shape, BlockShape.create(state.getCollisionBoundingBox(this.level, this.getPos((int)this.currentY))));
                    }
                }
            }
            // TODO reduce the number of collision boxes
//                shape.optimize();
            this.cage = this.level.isRemote ?
                new ClientElevatorCage(size, 1, size, blockStates, new NBTTagCompound[size][1][size], new NBTTagCompound[size][1][size], shape.toBoxes()) :
                new ElevatorCage(size, 1, size, blockStates, new NBTTagCompound[size][1][size], new NBTTagCompound[size][1][size], shape.toBoxes());
        }
        this.targetSpeed = compound.getDouble("speed");
        this.speed = this.targetSpeed;
        this.cageSizeX = this.cageSizeZ = size;
        this.cageSizeY = 1;
    }else{
        this.isMoving = compound.getBoolean("isMoving");
        // Absent from pre-queue saves that were stopped, where getInteger yields 0 -- the same
        // value the field would otherwise have held.
        this.targetY = compound.getInteger("targetY");
        if(this.isMoving){
            this.lastY = compound.getDouble("lastY");
            this.currentY = compound.getDouble("currentY");
            this.cage = ElevatorCage.read(compound.getCompoundTag("cage"), this.level.isRemote);
        }
        this.targetSpeed = compound.getDouble("targetSpeed");
        this.speed = compound.getDouble("speed");
        this.cageSideOffset = compound.getInteger("cageSideOffset");
        this.cageDepthOffset = compound.getInteger("cageDepthOffset");
        this.cageHeightOffset = compound.getInteger("cageHeightOffset");
        this.cageSizeX = compound.getInteger("cageSizeX");
        this.cageSizeY = compound.getInteger("cageSizeY");
        this.cageSizeZ = compound.getInteger("cageSizeZ");
    }
    this.floors.clear();
    for(int y : compound.getIntArray("floors"))
        this.floors.add(y);
    // Absent in saves written before the call queue existed, in which case getIntArray returns an
    // empty array and the elevator simply starts with nothing queued.
    this.callQueue.clear();
    for(int y : compound.getIntArray("callQueue"))
        this.callQueue.add(y);
    this.callDirections.clear();
    int[] directions = compound.getIntArray("callDirections");
    // Guard the length: a truncated or hand-edited array must not throw here.
    for(int i = 0; i + 1 < directions.length; i += 2)
        this.callDirections.put(directions[i], directions[i + 1]);
    // Absent in saves from before sounds existed, where the elevator should start out audible.
    this.soundsEnabled = !compound.hasKey("soundsEnabled") || compound.getBoolean("soundsEnabled");
    this.soundScheme = ElevatorSoundScheme.byName(compound.getString("soundScheme"));
    this.lastDirection = compound.getInteger("lastDirection");
    this.dwellCounter = compound.getInteger("dwellCounter");
    this.floorData.clear();
    if(compound.hasKey("floorData", Constants.NBT.TAG_LIST)){
        NBTBase base = compound.getTag("floorData");
        if(base instanceof NBTTagList){
            NBTTagList floorDataTag = (NBTTagList)base;
            for(NBTBase tag : floorDataTag)
                this.floorData.add(FloorData.read((NBTTagCompound)tag));
        }
    }
}

private BlockPos getPos(int y){
    return new BlockPos(this.x, y, this.z);
}

private ControllerBlockEntity getEntity(int y){
    if(this.level == null)
        return null;
    TileEntity entity = this.level.getTileEntity(this.getPos(y));
    return entity instanceof ControllerBlockEntity ? (ControllerBlockEntity)entity : null;
}

public int getFloorCount(){
    return this.floors.size();
}

public int getFloorNumber(int y){
    return this.floors.indexOf(y);
}

public int getClosestFloorNumber(int y){
    if(y < this.floors.get(0))
        return 0;
    for(int floor = 1; floor < this.floors.size(); floor++){
        if(y < (this.floors.get(floor - 1) + this.floors.get(floor)) / 2)
            return floor - 1;
    }
    return this.floors.size() - 1; // this should never be reached
}

public int getFloorYLevel(int floor){
    return this.floors.get(floor);
}

/**
 * The floor the cabin is at, for display purposes -- while moving, the floor it is nearest to.
 *
 * @return a floor index, or -1 if it cannot be determined
 */
public int getCabinFloorNumber(){
    if(this.floors.isEmpty())
        return -1;
    if(this.isMoving)
        return this.getClosestFloorNumber((int)Math.round(this.currentY));
    int floor = this.getFloorNumber(this.targetY);
    if(floor != -1)
        return floor;
    // The group has not moved yet, so targetY is not a floor. Fall back to whichever floor holds a
    // cabin. This reads the cached availability flags rather than forcing a block scan.
    for(int i = 0; i < this.floors.size(); i++)
        if(this.isCageAvailableAt(i))
            return i;
    return -1;
}

public ControllerBlockEntity getEntityForFloor(int floor){
    if(floor < 0 || floor >= this.floors.size())
        return null;
    return this.getEntity(this.floors.get(floor));
}

public boolean hasControllerAt(int yLevel){
    return this.floors.contains(yLevel);
}

private void updateGroup(){
    ElevatorGroupCapability.get(this.level).updateGroup(this);
}

private void syncMovement(){
    if(!this.level.isRemote)
        MovingElevators.CHANNEL.sendToDimension(this.level, new PacketSyncElevatorMovement(this.x, this.z, this.facing, this.currentY, this.speed));
}

public void validateControllersExist(Chunk chunk){
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.x, 0, this.z);
    // Iterate downwards: removeFloor() shifts every later element down by one, so counting up
    // skipped the floor after each removal and left controller-less floors in the group.
    for(int floor = this.floors.size() - 1; floor >= 0; floor--){
        pos.setY(this.floors.get(floor));
        if(!(chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) instanceof ControllerBlockEntity))
            this.removeFloor(floor);
    }
}

private static class FloorData {

    public String name;
    public EnumDyeColor color;
    public boolean isCageAvailable;
    public int lastCageCheck = -1;

    public FloorData(String name, EnumDyeColor color, boolean isCageAvailable){
        this.name = name;
        this.color = color;
        this.isCageAvailable = isCageAvailable;
    }

    public FloorData(String name, EnumDyeColor color){
        this(name, color, false);
    }

    public NBTTagCompound write(){
        NBTTagCompound tag = new NBTTagCompound();
        if(this.name != null)
            tag.setString("name", this.name);
        tag.setInteger("color", this.color.getDyeDamage());
        tag.setBoolean("isCageAvailable", this.isCageAvailable);
        return tag;
    }

    public static FloorData read(NBTTagCompound tag){
        return new FloorData(
            tag.hasKey("name") ? tag.getString("name") : null,
            EnumDyeColor.byDyeDamage(tag.getInteger("color")),
            tag.hasKey("isCageAvailable") && tag.getBoolean("isCageAvailable")
        );
    }
}
}
