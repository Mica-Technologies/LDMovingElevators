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
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
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
    /**
     * Boarding time at an ordinary stop, from config.
     * <p>
     * It was one second, which is not long enough to walk into a lift -- the cabin answered a call,
     * opened its doors and left again before anybody could reach it. Ten seconds by default, and
     * "close doors" cuts it short for anyone who is already aboard.
     */
    private static int dwellTicks(){
        return MovingElevatorsConfig.elevatorDwellTicks.get();
    }
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
    /**
     * What this elevator is called: "A", "Freight", or nothing.
     * <p>
     * Belongs to the elevator rather than to a bank, so a car keeps its name wherever it is referred
     * to -- the lobby that dispatched it, its own car panel, a landing indicator. A bank is only one
     * of the places a name is useful, and an elevator that is called "B" from the lobby and nothing
     * from inside would be worse than one with no name at all.
     */
    private String name;
    /** Half a second on each half of the flashing announcement. */
    private static final int ANNOUNCE_FLASH_TICKS = 10;
    /**
     * The landing this elevator is currently announcing itself at, or {@link Integer#MIN_VALUE}.
     * <p>
     * Set when a banked call is collected and cleared when the cabin departs, which covers the
     * stretch a passenger most needs it: the car has arrived and they have to pick it out from the
     * others before it goes without them.
     */
    private int announcingFloor = Integer.MIN_VALUE;
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

    /**
     * What the elevator is doing about somebody being in its shaft: nothing, crawling to the nearest
     * floor, or sitting there waiting for them to leave.
     * <p>
     * Per elevator, never per bank. A bank shares dispatch, not shafts -- halting every car in a
     * building because one shaft has a person in it would strand everybody else, which is its own
     * hazard rather than a cure for this one.
     */
    public enum EmergencyState {
        NONE, LEVELLING, HOLDING
    }

    /**
     * Ticks between shaft sweeps. Half a second: one entity query per moving elevator at that rate is
     * nothing, and the interval sets how far ahead the sweep has to see. It was five seconds, over
     * which a cabin at the default speed covers twenty blocks -- so it was finding people it had
     * already driven through rather than people it was about to.
     */
    private static final int SHAFT_SCAN_INTERVAL = 10;
    /**
     * How far below the top of the cabin something must stand to count as being inside it rather than
     * on its roof. Small: it only has to absorb floating-point drift, since a rider's feet sit exactly
     * on the roof's top face.
     */
    private static final double ROOF_CLEARANCE = 0.05;
    /** How far above and below the cabin counts as being in its way. */
    private static final int SHAFT_SCAN_REACH = 10;
    /**
     * Levelling speed: a crawl. The point of an emergency stop is to end the movement that put
     * somebody at risk, not to replace it with a sudden one.
     */
    private static final double EMERGENCY_SPEED = 0.02;
    /** Thirty seconds sitting at the floor before it will even consider going back into service. */
    private static final int EMERGENCY_HOLD_TICKS = 600;
    /** Half a second on each half of the flashing readout. */
    private static final int EMERGENCY_FLASH_TICKS = 10;

    /** Ticks between checks that the floor the cabin is heading for is still clear. */
    private static final int DESTINATION_CHECK_INTERVAL = 20;

    private EmergencyState emergencyState = EmergencyState.NONE;
    private int emergencyHold;
    /** Where the cabin was at the last shaft sweep, so the next one can cover the gap between. */
    private double lastShaftScanY = Double.NaN;

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
            // Swept while moving only: a parked cabin is not a hazard to stand next to, and this is
            // the one state where somebody in the shaft is about to be run into.
            if(!this.level.isRemote && this.emergencyState == EmergencyState.NONE
                && this.tickCounter % SHAFT_SCAN_INTERVAL == 0){
                double previous = Double.isNaN(this.lastShaftScanY) ? this.currentY : this.lastShaftScanY;
                this.lastShaftScanY = this.currentY;
                if(this.isShaftObstructedSince(previous))
                    this.triggerEmergencyStop();
            }
            // S2: the destination is checked before departure and could be built into afterwards.
            if(!this.level.isRemote && this.tickCounter % DESTINATION_CHECK_INTERVAL == 0)
                this.considerBlockedDestination();

            if(!this.level.isRemote)
                this.considerRetarget();

            if(this.currentY != this.targetY)
                this.lastY = this.currentY;
            if(Math.abs(this.targetY - this.currentY) / this.speed < (this.speed - 0.01) / ACCELERATION)
                this.speed = Math.max(0.01, this.speed - ACCELERATION);
            else if(this.speed < this.targetSpeed)
                this.speed = Math.min(this.targetSpeed, this.speed + ACCELERATION);
            // Applied after the ordinary acceleration so it cannot be accelerated back out of.
            if(this.emergencyState == EmergencyState.LEVELLING)
                this.speed = Math.min(this.speed, EMERGENCY_SPEED);
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
        }else if(!this.level.isRemote){
            this.updateEmergencyHold();
            // An elevator in emergency takes no calls. They keep their place in the queue and are
            // served once it is back in service, so nobody has to press anything again.
            if(this.emergencyState == EmergencyState.NONE)
                this.updateCallQueue();
        }
    }

    /**
     * Dispatches the cabin to the next outstanding call. Server-side only: the queue is authoritative
     * on the server and reaches clients through {@link #write()}.
     */
    private void updateCallQueue(){
        // Counted down before the queue is checked, not after. A dwell is a wait at a floor that has
        // to expire on its own; gating it on there being something queued meant an idle cabin kept
        // its unspent wait indefinitely and handed the whole of it to whoever called next -- fifteen
        // seconds of nothing after a bank pickup, thirty after an emergency.
        if(this.dwellCounter > 0){
            this.dwellCounter--;
            return;
        }
        if(this.callQueue.isEmpty())
            return;

        Integer target = this.pickNextCall();
        if(target == null)
            return;
        // Taken regardless of what happens below. If the cabin cannot reach the floor right now --
        // obstructed, or the controller is gone -- the call is dropped rather than retried forever;
        // pressing the button again re-queues it.
        this.callQueue.remove(target);

        int targetFloor = this.getFloorNumber(target);
        if(targetFloor == -1){
            // The floor has stopped existing, so its arrows have to go out with it.
            this.clearHallCalls(target);
            return;
        }
        // Cabin is already sitting there, so the call is already satisfied.
        if(this.isCageAvailableAt(targetFloor, true, null)){
            this.clearHallCalls(target);
            this.requestDoorOpen(target);
            return;
        }

        // Reuse the existing "bring the cabin here" path so queued calls behave exactly like a
        // button press: it picks the nearest floor holding a cabin and checks the destination is
        // clear. A null requester means no chat feedback, which is right for an automatic dispatch.
        this.onButtonPress(false, false, target, null);
        // Nothing came of it -- obstructed, or there was no cabin to fetch -- so the call has been
        // dropped. Every path out of this method that discards a call now puts its arrow out too:
        // a lit arrow with no call behind it is worse than no arrow, because pressing it again
        // looks like it did nothing.
        if(!this.isMoving)
            this.clearHallCalls(target);
    }

    /** Puts out both arrows at a landing, for the paths that discard the call entirely. */
    private void clearHallCalls(int yLevel){
        if(this.callDirections.remove(yLevel) != null)
            this.shouldBeSynced = true;
    }

    /**
     * Collects a call that has come in ahead of the cabin, mid-trip, rather than sailing past it.
     * <p>
     * {@link #pickNextCall} only runs while stopped, so a cabin travelling from the first floor to
     * the tenth would pass somebody calling from the fifth and only fetch them on the way back. Real
     * collective control stops on the way, and the whole point of a call queue is that it does.
     * <p>
     * Only calls the cabin can still stop for are taken, which is what makes this safe to do while
     * moving: the swapped-in floor is never one it would have to overshoot and come back to. The
     * displaced target goes back in the queue, so nothing is lost by taking the nearer one first.
     */
    private void considerRetarget(){
        // An emergency stop is on its way somewhere specific and must not be diverted.
        if(this.emergencyState != EmergencyState.NONE || this.callQueue.isEmpty())
            return;
        double direction = Math.signum(this.targetY - this.currentY);
        if(direction == 0)
            return;

        double remaining = Math.abs(this.targetY - this.currentY);
        Integer best = null;
        double bestDistance = remaining;
        for(int y : this.callQueue){
            // Ahead of the cabin, the way it is already going, and nearer than where it is headed.
            if(Math.signum(y - this.currentY) != direction)
                continue;
            double distance = Math.abs(y - this.currentY);
            if(distance >= bestDistance || !this.canStopBy(distance) || this.getFloorNumber(y) == -1)
                continue;
            best = y;
            bestDistance = distance;
        }
        if(best == null)
            return;

        this.callQueue.remove(best);
        this.callQueue.add(this.targetY);
        this.targetY = best;
        // Clients drive their own copy of the movement from targetY, so a diversion they are not
        // told about would leave the cabin visibly in the wrong place until the next resync.
        this.shouldBeSynced = true;
    }

    /**
     * Whether the cabin can still come to rest within {@code distance}, using the same braking
     * relation the movement code applies -- so a floor accepted here is one the mover agrees it can
     * stop at, rather than a second opinion that could disagree with it.
     */
    private boolean canStopBy(double distance){
        return distance / this.speed >= (this.speed - 0.01) / ACCELERATION;
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

        Integer previous = this.callDirections.get(yLevel);
        int merged = (previous == null ? 0 : previous) | (up ? CALL_UP : CALL_DOWN);
        if(previous == null || previous != merged){
            this.callDirections.put(yLevel, merged);
            // The lamps are drawn from this map on the client, so a change nobody is told about is a
            // button that visibly does nothing. Pressing the second arrow while the cabin is already
            // on its way changes nothing else -- queueCall returns early because the floor is already
            // the target -- so this was the only path by which that press could ever be seen.
            this.shouldBeSynced = true;
        }

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
        // Destinations are otherwise only picked up on arrival, and a hall call for the floor the
        // cabin is already standing on does not produce an arrival -- it just opens the doors. Without
        // this, the commonest request of all, calling a car that is already here, took the request,
        // said a car was coming, and then sat there forever.
        if(!this.isMoving && this.isCabinAt(pickupY))
            this.collectBankedDestinations(pickupY);
    }

    /**
     * Turns destinations booked from a floor into real calls, and holds the doors for boarding.
     *
     * @return whether there was anything to collect
     */
    private boolean collectBankedDestinations(int yLevel){
        Set<Integer> banked = this.bankedDestinations.remove(yLevel);
        if(banked == null || banked.isEmpty())
            return false;
        for(int destination : banked)
            if(destination != yLevel && this.getFloorNumber(destination) != -1)
                this.callQueue.add(destination);
        this.doorHoldTicks.put(yLevel, BANKED_DWELL_TICKS);
        this.dwellCounter = BANKED_DWELL_TICKS;
        // Keep identifying itself now it is standing here: en route the pending call did that, and it
        // has just been consumed.
        this.announcingFloor = yLevel;
        this.shouldBeSynced = true;
        return true;
    }

    /**
     * Destinations already booked by passengers waiting at a landing.
     * <p>
     * Worth asking before placing a call rather than after: the moment the cabin is standing at the
     * floor these are moved into the ordinary queue, where they are indistinguishable from anywhere
     * else the elevator has to be.
     */
    public Set<Integer> getBankedDestinationsFrom(int pickupY){
        Set<Integer> destinations = this.bankedDestinations.get(pickupY);
        return destinations == null ? Collections.emptySet() : Collections.unmodifiableSet(destinations);
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
        // Any dwell, not just a long one. The comparison used to be against the ordinary dwell, so
        // once that became a real boarding wait rather than a single second, the button that exists
        // to skip it would have stopped skipping it.
        if(this.dwellCounter > 0 && this.isCabinAt(yLevel))
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

    /**
     * Whether anybody is standing in the shaft outside the cabin, within reach above or below it.
     * <p>
     * The cabin's own footprint extended vertically, rather than a sphere or the whole chunk: the
     * shaft is exactly the column the cabin sweeps, and somebody on a landing beside it is in no
     * danger and must not stop the lift.
     */
    private boolean isShaftObstructed(){
        return this.isShaftObstructedSince(this.currentY);
    }

    /**
     * As {@link #isShaftObstructed()}, but covering everything the cabin has passed through
     * since it was last at {@code previousY} as well as where it is now.
     * <p>
     * A fixed window around the current position is only safe if the cabin cannot outrun it, and it
     * can: at the top speed it covers a hundred blocks between sweeps while looking ten either way,
     * so the great majority of the shaft went unexamined and somebody standing in it was simply
     * driven through. Sweeping the travelled volume cannot have a gap by construction, whatever the
     * speed and interval are set to.
     */
    private boolean isShaftObstructedSince(double previousY){
        Vec3d anchor = this.getCageAnchorPos(this.currentY);
        AxisAlignedBB cabin = new AxisAlignedBB(anchor.x, anchor.y, anchor.z,
            anchor.x + this.cageSizeX, anchor.y + this.cageSizeY, anchor.z + this.cageSizeZ);
        Vec3d previousAnchor = this.getCageAnchorPos(previousY);
        double sweptMinY = Math.min(cabin.minY, previousAnchor.y);
        double sweptMaxY = Math.max(cabin.maxY, previousAnchor.y + this.cageSizeY);
        // Reaching further the way the cabin is going, by everything it will cover before the next
        // sweep. Looking a fixed distance both ways is only a warning if the cabin cannot outrun it;
        // whatever it would have reached first went unseen, which is why somebody standing below a
        // descending cabin was run into rather than stopped for.
        double travel = Math.signum(this.targetY - this.currentY);
        double lookAhead = SHAFT_SCAN_REACH + Math.abs(this.speed) * SHAFT_SCAN_INTERVAL;
        double below = travel < 0 ? lookAhead : SHAFT_SCAN_REACH;
        double above = travel > 0 ? lookAhead : SHAFT_SCAN_REACH;
        AxisAlignedBB shaft = new AxisAlignedBB(cabin.minX, sweptMinY - below, cabin.minZ,
            cabin.maxX, sweptMaxY + above, cabin.maxZ);
        for(EntityLivingBase entity : this.level.getEntitiesWithinAABB(EntityLivingBase.class, shaft)){
            if(entity instanceof EntityPlayer && ((EntityPlayer)entity).isSpectator())
                continue;
            if(!this.isRidingInside(cabin, entity))
                return true;
        }
        return false;
    }

    /**
     * Whether something is riding inside the cabin rather than standing in the shaft with it.
     * <p>
     * Decided by where its feet are, not by whether its box touches the cabin. Touching was the wrong
     * question and quietly defeated the whole feature: anything standing on the roof touches the
     * cabin from above, and anything pressed against the outside of a wall touches it from the side,
     * so the two cases most worth catching were the two being excluded. Feet within the cabin's own
     * span is what actually separates a passenger from a hazard.
     */
    private boolean isRidingInside(AxisAlignedBB cabin, EntityLivingBase entity){
        return entity.posX >= cabin.minX && entity.posX <= cabin.maxX
            && entity.posZ >= cabin.minZ && entity.posZ <= cabin.maxZ
            && entity.posY >= cabin.minY && entity.posY < cabin.maxY - ROOF_CLEARANCE;
    }

    /**
     * Diverts the cabin if the floor it is heading for has stopped being clear.
     * <p>
     * The destination is checked once before departure and never again, so anything that arrives in
     * the meantime -- a player building, falling sand, flowing water -- is simply destroyed on
     * arrival. Worse, a block the cabin is not allowed to break makes the placement drop the cabin's
     * own block as an item instead, so part of the floor you are standing on turns into an item
     * entity with no explanation.
     */
    private void considerBlockedDestination(){
        if(this.emergencyState != EmergencyState.NONE || this.isDestinationClear(this.targetY))
            return;
        int best = Integer.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for(int floor = 0; floor < this.getFloorCount(); floor++){
            int y = this.getFloorYLevel(floor);
            if(y == this.targetY)
                continue;
            double distance = Math.abs(y - this.currentY);
            if(distance < bestDistance && this.isDestinationClear(y)){
                bestDistance = distance;
                best = y;
            }
        }
        // Nowhere better to go. Carrying on is no worse than the old behaviour, and stopping in the
        // shaft would strand whoever is aboard.
        if(best == Integer.MIN_VALUE)
            return;
        this.targetY = best;
        this.shouldBeSynced = true;
    }

    /** Whether the cabin's volume at a floor is empty. The cabin is out of the world while it travels,
     * so anything found here arrived after it set off. */
    private boolean isDestinationClear(int yLevel){
        BlockPos startPos = this.getCageAnchorBlockPos(yLevel);
        for(int x = 0; x < this.cageSizeX; x++)
            for(int y = 0; y < this.cageSizeY; y++)
                for(int z = 0; z < this.cageSizeZ; z++)
                    if(!this.level.isAirBlock(startPos.add(x, y, z)))
                        return false;
        return true;
    }

    /** Redirects the cabin to the nearest floor at a crawl. */
    private void triggerEmergencyStop(){
        this.emergencyState = EmergencyState.LEVELLING;
        this.targetY = this.nearestFloorTo(this.currentY);
        this.speed = Math.min(this.speed, EMERGENCY_SPEED);
        this.playAtCabin(ElevatorSoundScheme.Moment.OBSTRUCTED);
        this.shouldBeSynced = true;
    }

    private int nearestFloorTo(double y){
        int best = this.targetY;
        double bestDistance = Double.MAX_VALUE;
        for(int floor = 0; floor < this.getFloorCount(); floor++){
            int floorY = this.getFloorYLevel(floor);
            double distance = Math.abs(floorY - y);
            if(distance < bestDistance){
                bestDistance = distance;
                best = floorY;
            }
        }
        return best;
    }

    /**
     * Counts down the wait at the floor, and only returns to service once the shaft is actually
     * clear -- the timer is a minimum, not a licence. Somebody still standing there buys another
     * thirty seconds rather than getting run into by an elevator that decided its wait was up.
     */
    private void updateEmergencyHold(){
        if(this.emergencyState != EmergencyState.HOLDING)
            return;
        if(this.emergencyHold > 0){
            this.emergencyHold--;
            return;
        }
        if(this.isShaftObstructed()){
            this.emergencyHold = EMERGENCY_HOLD_TICKS;
            return;
        }
        this.emergencyState = EmergencyState.NONE;
        // Calls are reset rather than resumed. The queue was built before whatever happened in the
        // shaft, and after half a minute out of service the people who pressed those buttons have
        // had every chance to give up and walk off -- an elevator setting out on a round of errands
        // nobody is waiting for is worse than one that asks to be told again.
        this.callQueue.clear();
        this.callDirections.clear();
        this.bankedDestinations.clear();
        this.requestDoorClose(this.targetY);
        this.shouldBeSynced = true;
    }

    /**
     * Which half of the emergency flash the readouts should currently be showing.
     * <p>
     * A phase rather than the text itself. "E" and "ST" are an English abbreviation of "emergency
     * stop", and were the only user-visible words in the mod that could not be translated -- deciding
     * what they say is the client's business, and all this has to settle is when they change.
     * <p>
     * Driven off world time, so every readout in the building flashes on the same beat without
     * anything having to synchronise them.
     */
    public boolean isEmergencyFlashOn(){
        return this.level != null && (this.level.getTotalWorldTime() / EMERGENCY_FLASH_TICKS) % 2 == 0;
    }

    public boolean isEmergencyStopped(){
        return this.emergencyState != EmergencyState.NONE;
    }

    /**
     * Whether this elevator should be identifying itself at a landing -- because it has been sent
     * there by a bank and has not left again.
     */
    public boolean isAnnouncingAt(int yLevel){
        return this.bankedDestinations.containsKey(yLevel) || this.announcingFloor == yLevel;
    }

    /** Which half of the announcement flash, off world time so every readout agrees without syncing. */
    public boolean isAnnounceFlashOn(){
        return this.level != null && (this.level.getTotalWorldTime() / ANNOUNCE_FLASH_TICKS) % 2 == 0;
    }

    /** @return this elevator's name, or null if it has not been given one */
    public String getName(){
        return this.name;
    }

    public void setName(String name){
        String trimmed = name == null ? null : name.trim();
        this.name = trimmed == null || trimmed.isEmpty() ? null : trimmed;
        this.shouldBeSynced = true;
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
        if(!this.collectBankedDestinations(this.targetY)){
            this.doorHoldTicks.remove(this.targetY);
            this.dwellCounter = dwellTicks();
        }
        // Levelled after an emergency stop: sit here with the doors open. Open rather than shut
        // because whoever is in the shaft may well want to get out through the cabin, and whoever is
        // inside it should not be held in a box that has just stopped for an emergency.
        if(this.emergencyState == EmergencyState.LEVELLING){
            this.emergencyState = EmergencyState.HOLDING;
            this.emergencyHold = EMERGENCY_HOLD_TICKS;
            this.dwellCounter = EMERGENCY_HOLD_TICKS;
            this.doorHoldTicks.put(this.targetY, EMERGENCY_HOLD_TICKS);
        }
        // Carry on the way whoever called from this landing wanted to travel. With both arrows
        // pressed the current direction wins, which is what a real elevator does.
        Integer directions = this.callDirections.get(this.targetY);
        if(directions != null){
            int served = directions == CALL_UP ? 1
                : directions == CALL_DOWN ? -1
                // Both arrows are lit and only one journey is about to happen, so the car answers
                // the one it is already set up for.
                : this.lastDirection != 0 ? this.lastDirection : 1;
            this.lastDirection = served;
            // Only the direction being served. Clearing the pair meant arriving to collect somebody
            // going up also put out the down arrow and threw that call away with it, leaving whoever
            // pressed it waiting for a car that was no longer coming.
            this.clearHallCall(this.targetY, served > 0);
            if(this.callDirections.containsKey(this.targetY))
                this.queueCall(this.targetY);
        }
        // Arriving opens the doors, exactly as a real elevator does.
        this.requestDoorOpen(this.targetY);

        // The direction travelled, taken from the movement rather than from lastDirection -- that
        // has already been reassigned above to the way the cabin is going next, which is frequently
        // the opposite of the way it came in.
        this.cage.place(this.level, this.getCageAnchorBlockPos(this.targetY), this.currentY - this.lastY);
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
        // Out of service means out of service. The call queue was already skipped during an
        // emergency, but a floor pressed on the car panel reaches this directly and drove the cabin
        // off mid-emergency -- with somebody still in the shaft, which is the one thing the whole
        // feature exists to prevent.
        if(this.level == null || this.isMoving || this.emergencyState != EmergencyState.NONE)
            return;
        // Whatever it was announcing, it is leaving.
        this.announcingFloor = Integer.MIN_VALUE;
        // Anchored to where this trip begins, so the first sweep covers the ground already travelled
        // rather than only the window around wherever the cabin happens to be when the timer fires.
        this.lastShaftScanY = currentY;

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
        int removedY = this.floors.get(floor);
        this.floors.remove(floor);
        this.floorData.remove(floor);
        // The cabin was on its way here. stopElevator looks the floor up by y and indexes floorData
        // with the result, so leaving targetY pointing at a floor that no longer exists crashes the
        // world tick the moment it arrives. Sending it to the nearest surviving floor instead means
        // a controller broken mid-trip merely changes where the cabin ends up.
        if(this.isMoving && this.targetY == removedY && !this.floors.isEmpty()){
            this.targetY = this.nearestFloorTo(this.currentY);
            this.shouldBeSynced = true;
        }
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
            this.cageChecks++;
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
    // Synced as well as saved: the readouts that flash a car's name are drawn on the client, and a
    // pending pickup it has never been told about cannot be announced.
    int[] banked = new int[this.bankedDestinations.values().stream().mapToInt(Set::size).sum() * 2];
    int bankedIndex = 0;
    for(Map.Entry<Integer,Set<Integer>> entry : this.bankedDestinations.entrySet()){
        for(int destination : entry.getValue()){
            banked[bankedIndex++] = entry.getKey();
            banked[bankedIndex++] = destination;
        }
    }
    compound.setIntArray("bankedDestinations", banked);
    compound.setInteger("announcingFloor", this.announcingFloor);
    compound.setBoolean("hasElevatorName", this.name != null);
    if(this.name != null)
        compound.setString("elevatorName", this.name);
        compound.setString("emergencyState", this.emergencyState.name());
        compound.setInteger("emergencyHold", this.emergencyHold);
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
    this.bankedDestinations.clear();
    int[] banked = compound.getIntArray("bankedDestinations");
    for(int i = 0; i + 1 < banked.length; i += 2)
        this.bankedDestinations.computeIfAbsent(banked[i], y -> new LinkedHashSet<>()).add(banked[i + 1]);
    this.announcingFloor = compound.hasKey("announcingFloor") ? compound.getInteger("announcingFloor") : Integer.MIN_VALUE;
    this.name = compound.getBoolean("hasElevatorName") ? compound.getString("elevatorName") : null;
        this.emergencyState = EmergencyState.NONE;
        for(EmergencyState state : EmergencyState.values())
            if(state.name().equals(compound.getString("emergencyState")))
                this.emergencyState = state;
        this.emergencyHold = compound.getInteger("emergencyHold");
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
        // The two lists are indexed by the same floor number everywhere else in this class, and every
        // one of those lookups is a plain get(). A save written before floorData existed, or one whose
        // list is short for any other reason, would therefore throw on the first display draw rather
        // than degrade. Padding costs nothing and turns a crash into an unnamed floor.
        while(this.floorData.size() < this.floors.size())
            this.floorData.add(new FloorData(null, EnumDyeColor.GRAY));
        while(this.floorData.size() > this.floors.size())
            this.floorData.remove(this.floorData.size() - 1);
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
