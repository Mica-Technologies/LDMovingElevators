package com.supermartijn642.movingelevators.elevator;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockShape;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.packets.PacketSyncElevatorMovement;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import com.supermartijn642.movingelevators.compat.CsmCompat;
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
    private static int bankedDwellTicks(){
        return MovingElevatorsConfig.bankedDwellTicks.get();
    }

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
    // Mica: primitive-keyed, because every door block reads these every tick, and a boxed key above
    // y=127 is a fresh Integer each time.
    private final Int2IntOpenHashMap doorHoldTicks = new Int2IntOpenHashMap();
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
    /**
     * Set when the cabin's contents change, as opposed to the elevator's state.
     * <p>
     * The cabin is nearly all of a sync: every block in it, with the NBT of any block entity among
     * them. The rest -- the queue, which arrows are lit, what it sounds like -- is a few dozen bytes
     * and changes constantly. Sending the cabin along with a button press meant broadcasting a cubic
     * cabin's worth of block data to every player in the dimension because somebody had lit a lamp.
     */
    private boolean cageChanged = true;
    private boolean shouldBeSynced = false;
    private final Map<Integer,Set<BlockPos>> comparatorListeners = new Int2ObjectArrayMap<>();

    private int syncCounter = 0;
    private int tickCounter = 0;
    /**
     * Bumped whenever something a landing door's answer depends on changes: the floors, or the
     * cabin's height and its offset from the controllers. Doors cache which landing they stand at
     * against this rather than re-deriving it every tick. See {@link #getLayoutVersion()}.
     */
    private int layoutVersion;
    private int cageChecks = 0;
    /**
     * Who the last shaft sweep accepted as a passenger, by entity id. Server-side, per trip.
     * <p>
     * A passenger's reported position can be ahead of the cabin as well as behind it. The client
     * simulates the cabin on its own clock and holds its rider on the floor, and the position it
     * sends is the one against that cabin -- so whenever this side runs slow, a stall or a tick
     * rate under twenty, the rider's position is further along the trip than this side's cabin has
     * got. Going down, {@link #carriedVolume} reaches no further ahead than the cabin's own floor,
     * so after a floor or so of drift the rider read as somebody standing in the shaft below and
     * the cabin stopped for them. The stop then told the rider's client the cabin was a floor above
     * where they were standing on it, and they dropped the length of the shaft: "fell through the
     * floor, and the elevator above me did an emergency stop". Somebody this sweep has already
     * accepted as a passenger, and who is still in the cabin's column just ahead of it, is a
     * passenger whose client has run ahead, not a hazard. Only ever players -- everything else is
     * moved by this side and cannot get ahead of it.
     */
    private final Set<UUID> riders = new HashSet<>();

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
    private final Int2LongOpenHashMap doorOpenRequests = new Int2LongOpenHashMap();
    private final Int2LongOpenHashMap doorCloseRequests = new Int2LongOpenHashMap();

    /**
     * The "Standard" sound scheme, and the only one so far.
     * <p>
     * Kept as named constants rather than scattered literals so that adding schemes later is a matter
     * of choosing a different set, not hunting down every playSound call.
     */

    private boolean soundsEnabled = true;
    /**
     * Whether this cabin plays music. Separate from the sound toggle, because wanting the chimes and
     * not the muzak is an entirely ordinary preference.
     */
    private boolean cabinMusicEnabled = true;
    /** Ticks left of the track currently playing, counted down in steps of the check interval. */
    private int cabinMusicRemaining;
    /** How often the cabin is checked for listeners. A second is prompt enough to start a record. */
    private static final int MUSIC_CHECK_INTERVAL = 20;
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

    /** Ticks between occupancy counts. An entity sweep of the cabin is cheap; doing it every tick is not. */
    private static final int OVERLOAD_CHECK_INTERVAL = 10;
    /** Ticks between buzzes while overloaded. Often enough to nag, not so often as to become a tone. */
    private static final int OVERLOAD_BUZZ_INTERVAL = 12;
    /** How often the held doors are told again to stay open, comfortably inside their own timer. */
    private static final int OVERLOAD_DOOR_REFRESH = 20;
    private boolean overloaded;

    /**
     * Whether this elevator is answering the building, only its own passengers, or nobody.
     * <p>
     * Independent service is the useful middle: the car still goes where the people inside it ask,
     * but stops being offered to the building. It is how you keep a lift to yourself to shift
     * furniture without taking it off the network altogether, which out of service does.
     */
    public enum ServiceMode {
        NORMAL, INDEPENDENT, OUT_OF_SERVICE,
        /**
         * A fire alarm has recalled the car. Not an emergency stop: the elevator is working, and is
         * working on getting everybody to one floor and staying there.
         */
        FIRE_RECALL;

        public ServiceMode next(){
            ServiceMode[] modes = values();
            return modes[(this.ordinal() + 1) % modes.length];
        }

        public String getNameTranslationKey(){
            return "movingelevators.service_mode." + this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private ServiceMode serviceMode = ServiceMode.NORMAL;

    /**
     * Ticks between asking the paired alarm panel whether it is sounding. Two seconds is prompt on the
     * timescale of a building emptying, and the query costs nothing while nothing is alarming.
     */
    private static final int ALARM_POLL_INTERVAL = 40;
    /** The fire alarm panel this elevator listens to, or null. Its position is its whole identity. */
    private BlockPos alarmPanelPos;
    /** The floor the alarm sends the car to. Set when the elevator is paired, from where it was paired. */
    private int recallFloorY;
    /** Ticks between attempts to send a recalled car that could not set off. Server only, never saved. */
    private static final int RECALL_RETRY_INTERVAL = 20;
    /** Counts down to the next of those attempts; 0 or less means try now. */
    private int recallRetryTicks;
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
     * on its roof -- or, on a platform, how far above the top face still counts as standing on it.
     * Small either way: it only has to absorb floating-point drift, since feet sit exactly on the face
     * they are on.
     */
    private static final double ROOF_CLEARANCE = 0.05;
    /**
     * How many ticks of cabin travel a passenger's reported position may trail the cabin by and still
     * be taken for a passenger, on top of the ground the cabin has swept since the last check.
     * <p>
     * Half a second, which is a bad connection's round trip. A passenger's position on the server is
     * the one their client last sent, worked out against the client's own copy of the cabin -- itself
     * a little behind the server's, since {@code syncCurrentY} snaps it back a latency late. Both
     * delays put a passenger where the cabin has been, never where it is going.
     */
    private static final int RIDER_LAG_TICKS = 10;
    /**
     * How many ticks of cabin travel a passenger already known to be riding may be <i>ahead</i> of
     * the cabin by and still be taken for a passenger. See {@link #riders}.
     * <p>
     * Three seconds of travel. A stall is transient -- the server catches its ticks up afterwards
     * and the lead shrinks again -- but a tick rate that stays low lets the lead grow for the whole
     * trip, by a fifth of the distance travelled at fifteen ticks a second, so this has to be a
     * good deal more than a round trip. Beyond it the old rule applies and the cabin stops, which is
     * the conservative outcome; this only widens what is forgiven, never what is seen.
     */
    private static final int RIDER_LEAD_TICKS = 60;
    /** How far above and below the cabin counts as being in its way. */
    private static int shaftScanReach(){
        return MovingElevatorsConfig.shaftScanReach.get();
    }
    /**
     * Levelling speed: a crawl. The point of an emergency stop is to end the movement that put
     * somebody at risk, not to replace it with a sudden one.
     */
    private static final double EMERGENCY_SPEED = 0.02;
    /** Thirty seconds sitting at the floor before it will even consider going back into service. */
    private static int emergencyHoldTicks(){
        return MovingElevatorsConfig.emergencyHoldTicks.get();
    }
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
        // A wait at a floor expires on its own, whatever else the elevator is doing. Everything that
        // reads it is asking "has the wait finished", and that must not depend on which state happens
        // to be driving this tick.
        if(!this.level.isRemote && this.dwellCounter > 0)
            this.dwellCounter--;
        if(!this.level.isRemote)
            this.updateAlarm();
        if(!this.level.isRemote)
            this.updateOverload();
        if(!this.level.isRemote)
            this.updateFireRecall();
        if(!this.level.isRemote)
            this.updateComparatorFloor();
        if(!this.level.isRemote)
            this.updateCabinMusic();
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
            // An elevator in any of these states takes no calls from the queue. Note that returning
            // from an emergency clears the queue outright rather than resuming it -- see
            // updateEmergencyHold, which explains why -- so this gate is about not dispatching, not
            // about preserving anything.
            // An overloaded cabin takes no calls either. Letting the queue run would have it try to
            // depart, fail, and drop the call as undeliverable -- so the calls would quietly vanish
            // while it sat there buzzing.
            // A recalled car drives itself to one floor and stays; the queue would only argue with it.
            if(this.emergencyState == EmergencyState.NONE && !this.overloaded
                && !this.isOutOfService() && !this.isFireRecalled())
                this.updateCallQueue();
        }
    }

    /**
     * Dispatches the cabin to the next outstanding call. Server-side only: the queue is authoritative
     * on the server and reaches clients through {@link #write()}.
     */
    private void updateCallQueue(){
        // Counted down in update(), not here. It lived here once, which was wrong twice over: it did
        // not run while the queue was empty, and it did not run at all in the states that skip this
        // method -- so a fire recall arriving within ten seconds of a stop waited on a dwell that had
        // stopped ticking, and the car sat through the whole alarm taking no input.
        if(this.dwellCounter > 0)
            return;
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
        if(!this.floors.contains(yLevel) || !this.acceptsHallCalls())
            return;

        Integer previous = this.callDirections.get(yLevel);
        int merged = (previous == null ? 0 : previous) | (up ? CALL_UP : CALL_DOWN);
        if(previous == null || previous != merged){
            this.callDirections.put(yLevel, merged);
            // Acknowledged where it was pressed. A landing button whose lift is eight floors away
            // gives no other sign it registered until the lamp is noticed.
            this.playAt(new Vec3d(this.getPos(yLevel)).addVector(0.5, 0.5, 0.5),
                ElevatorSoundScheme.Moment.CALL_ACCEPTED);
            // The lamps are drawn from this map on the client, so a change nobody is told about is a
            // button that visibly does nothing. Pressing the second arrow while the cabin is already
            // on its way changes nothing else -- queueCall returns early because the floor is already
            // the target -- so this was the only path by which that press could ever be seen.
            this.shouldBeSynced = true;
        }

        // Queued rather than dispatched whenever the cabin cannot set off now. Attempting anyway meant
        // startElevator refused, nothing was queued, and the arrow this method had just lit stayed lit
        // for good -- with a bank then reading that phantom call as a car already on its way and
        // pinning the whole landing to a lift that was never coming.
        if(this.isMoving || this.overloaded || this.emergencyState != EmergencyState.NONE){
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
        if(!this.floors.contains(yLevel) || !this.acceptsCarCalls())
            return;
        this.playAtCabin(ElevatorSoundScheme.Moment.CALL_ACCEPTED);
        if(this.isMoving || this.overloaded || this.emergencyState != EmergencyState.NONE){
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
        if(pickupY == destinationY || !this.acceptsHallCalls())
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
        this.doorHoldTicks.put(yLevel, bankedDwellTicks());
        this.dwellCounter = bankedDwellTicks();
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
        return this.doorHoldTicks.get(yLevel);
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
        return this.doorOpenRequests.get(yLevel);
    }

    /**
     * @return world time of the last close request for this floor, or 0 if there has never been one
     */
    public long getDoorCloseRequest(int yLevel){
        return this.doorCloseRequests.get(yLevel);
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
        double lookAhead = shaftScanReach() + Math.abs(this.speed) * SHAFT_SCAN_INTERVAL;
        double below = travel < 0 ? lookAhead : shaftScanReach();
        double above = travel > 0 ? lookAhead : shaftScanReach();
        AxisAlignedBB shaft = new AxisAlignedBB(cabin.minX, sweptMinY - below, cabin.minZ,
            cabin.maxX, sweptMaxY + above, cabin.maxZ);
        AxisAlignedBB carried = this.carriedVolume(cabin, sweptMinY, sweptMaxY, travel);
        // Walked to the end rather than returning at the first obstruction, so the riders seen this
        // time are known at the next sweep whatever else was in the shaft.
        Set<UUID> stillRiding = new HashSet<>();
        boolean obstructed = false;
        for(EntityLivingBase entity : this.level.getEntitiesWithinAABB(EntityLivingBase.class, shaft)){
            if(entity instanceof EntityPlayer && ((EntityPlayer)entity).isSpectator())
                continue;
            if(this.isRidingInside(carried, entity) || this.isRidingAhead(cabin, travel, entity))
                stillRiding.add(entity.getUniqueID());
            else
                obstructed = true;
        }
        this.riders.clear();
        this.riders.addAll(stillRiding);
        return obstructed;
    }

    /**
     * Records who is standing in the cabin as it departs, so the first sweep already knows its
     * passengers. Needed because the first sweep can already find them ahead: a stall that lands on
     * the departure itself leaves the client a cabin's worth of travel in front before this side
     * has swept once.
     */
    private void seedRiders(double y){
        this.riders.clear();
        Vec3d anchor = this.getCageAnchorPos(y);
        // The trailing block is the same allowance the sweep gives a stale position.
        AxisAlignedBB carried = new AxisAlignedBB(anchor.x, anchor.y - 1, anchor.z,
            anchor.x + this.cageSizeX, anchor.y + this.cageSizeY, anchor.z + this.cageSizeZ);
        for(EntityPlayer player : this.level.getEntitiesWithinAABB(EntityPlayer.class, carried))
            if(!player.isSpectator() && this.isRidingInside(carried, player))
                this.riders.add(player.getUniqueID());
    }

    /**
     * Whether a player the previous sweep accepted as a passenger has merely run ahead of the cabin
     * -- their client is further into the trip than this side is -- rather than left it. Ahead means
     * in the direction of travel, within the cabin's own column, and by no more than
     * {@link #RIDER_LEAD_TICKS} of travel; anything else is judged as before. See {@link #riders}.
     */
    private boolean isRidingAhead(AxisAlignedBB cabin, double travel, EntityLivingBase entity){
        if(!(entity instanceof EntityPlayer) || !this.riders.contains(entity.getUniqueID()))
            return false;
        if(entity.posX < cabin.minX || entity.posX > cabin.maxX || entity.posZ < cabin.minZ || entity.posZ > cabin.maxZ)
            return false;
        // Measured from the cabin floor. Going up a passenger inside the cabin is already ahead of
        // the floor by their height above it, so the cabin's own height is part of the allowance.
        double lead = travel < 0 ? cabin.minY - entity.posY : entity.posY - cabin.minY;
        return lead >= 0 && lead <= Math.abs(this.speed) * RIDER_LEAD_TICKS + this.cageSizeY;
    }

    /**
     * The volume something has to be in to count as being carried by the cabin rather than standing in
     * its way.
     * <p>
     * Not the cabin's live box. A passenger's position on the server is stale by a network round trip
     * (see {@link #RIDER_LAG_TICKS}), so measuring them against where the cabin is <i>now</i> finds
     * their feet below the floor they are standing on and calls a passenger an obstruction -- which is
     * exactly what stopped lifts in multiplayer with nothing whatever in the shaft. Singleplayer never
     * showed it because there is no round trip to be late by.
     * <p>
     * The allowance reaches back the way the cabin came and no further forward than the cabin itself:
     * over everything swept since the last check, which the cabin has demonstrably already passed
     * through, plus the round trip. Reaching forward as well would count somebody the cabin is about to
     * arrive at as a passenger and drive into them, which is the one thing this must never do.
     * <p>
     * Going down, a stale position sits <i>above</i> the true one instead, and extending the top is
     * only safe where nothing stands on top to be confused with a passenger -- so a platform gets the
     * same allowance the other way up, while a cabin makes do with its own headroom, which is a
     * cabin's worth of slack already there. Widening a cabin's top would excuse anybody on its roof,
     * who must keep being noticed.
     */
    private AxisAlignedBB carriedVolume(AxisAlignedBB cabin, double sweptMinY, double sweptMaxY, double travel){
        double trailing = Math.abs(this.speed) * RIDER_LAG_TICKS;
        double minY = travel < 0 ? sweptMinY : sweptMinY - trailing;
        double maxY = travel < 0 && this.isRiddenFromOnTop() ? sweptMaxY + trailing : cabin.maxY;
        return new AxisAlignedBB(cabin.minX, minY, cabin.minZ, cabin.maxX, maxY, cabin.maxZ);
    }

    /**
     * Whether the cabin is a platform, ridden by standing on its top face, rather than a cabin ridden
     * from inside.
     * <p>
     * The roof rule below assumes there is an inside to be in. A platform has none: its passengers
     * stand on the very face the rule calls a roof, so a cabin one block tall -- which is the whole of
     * upstream's original elevator -- read every passenger it had as somebody standing on top of it,
     * and stopped for them on every single trip.
     * <p>
     * Answered from the cage's own blocks while it is moving, which is when the shaft sweep runs and
     * the answer matters. Parked, and before the first trip of all, there may be no cage object to ask
     * and only the height is known -- enough for the case that actually needs catching, since a cage
     * one cell tall cannot have an inside whatever it is built of.
     */
    private boolean isRiddenFromOnTop(){
        return this.cage == null ? this.cageSizeY <= 1 : !this.cage.hasInterior();
    }

    /**
     * Whether something is riding inside the given volume -- the cabin itself when it is standing
     * still, {@link #carriedVolume} while it is moving -- rather than standing in the shaft with it.
     * <p>
     * Decided by where its feet are, not by whether its box touches the cabin. Touching was the wrong
     * question and quietly defeated the whole feature: anything standing on the roof touches the
     * cabin from above, and anything pressed against the outside of a wall touches it from the side,
     * so the two cases most worth catching were the two being excluded. Feet within the cabin's own
     * span is what actually separates a passenger from a hazard.
     * <p>
     * Except on a platform, where the top face is the floor and standing on it is the only way to ride
     * at all -- see {@link #isRiddenFromOnTop}.
     */
    private boolean isRidingInside(AxisAlignedBB cabin, EntityLivingBase entity){
        double ceiling = this.isRiddenFromOnTop() ? cabin.maxY + ROOF_CLEARANCE : cabin.maxY - ROOF_CLEARANCE;
        return entity.posX >= cabin.minX && entity.posX <= cabin.maxX
            && entity.posZ >= cabin.minZ && entity.posZ <= cabin.maxZ
            && entity.posY >= cabin.minY && entity.posY < ceiling;
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
            this.emergencyHold = emergencyHoldTicks();
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
     * How many can ride: one per block of cabin floor.
     * <p>
     * Floor area rather than volume, because it is standing room that runs out -- a taller cabin does
     * not hold more people, and a wider one plainly does.
     */
    public ServiceMode getServiceMode(){
        return this.serviceMode;
    }

    public void setServiceMode(ServiceMode mode){
        this.serviceMode = mode == null ? ServiceMode.NORMAL : mode;
        this.shouldBeSynced = true;
    }

    public boolean isOutOfService(){
        return this.serviceMode == ServiceMode.OUT_OF_SERVICE;
    }

    public boolean isFireRecalled(){
        return this.serviceMode == ServiceMode.FIRE_RECALL;
    }

    /**
     * Whether the people inside may still choose a floor. Independent service exists precisely so they
     * can; a recall exists precisely so they cannot.
     */
    public boolean acceptsCarCalls(){
        return this.serviceMode == ServiceMode.NORMAL || this.serviceMode == ServiceMode.INDEPENDENT;
    }

    public BlockPos getAlarmPanelPos(){
        return this.alarmPanelPos;
    }

    /**
     * Pairs this elevator to a fire alarm panel, and fixes where an alarm will send it.
     *
     * @param recallFloorY the floor to recall to -- the floor of the controller the pairing was made
     *                     from, so a builder chooses it by standing where they want the car to end up
     */
    public void setAlarmPanel(BlockPos panelPos, int recallFloorY){
        this.alarmPanelPos = panelPos;
        this.recallFloorY = recallFloorY;
        this.shouldBeSynced = true;
    }

    /**
     * Asks the paired panel whether it is sounding, and puts the elevator into or out of recall.
     * <p>
     * Reconciles rather than edge-triggers: the mode is recomputed from the answer every time, so a
     * transition missed for any reason corrects itself on the next poll. That matters because CSM
     * drops a panel from its registry when the chunk unloads without announcing it, so anything that
     * only listened for changes would eventually believe a stale answer.
     */
    private void updateFireRecall(){
        if(!MovingElevators.CSM_LOADED || this.alarmPanelPos == null){
            // A recalled car saved and then reopened without City Super Mod installed, or with its
            // panel forgotten, would otherwise never be released: the poll below is the only thing
            // that clears the mode, and it is exactly what this branch is skipping. The car refuses
            // every button, forever, for a fire in a world that no longer knows about fires.
            if(this.serviceMode == ServiceMode.FIRE_RECALL){
                this.recalling = false;
                this.setServiceMode(ServiceMode.NORMAL);
            }
            return;
        }
        if(this.floors.isEmpty())
            return;
        if(this.tickCounter % ALARM_POLL_INTERVAL == 0){
            // Fire only. A storm warning tells the people in a building to go and shelter, which they
            // need working lifts to do -- taking the lifts away is the opposite of helping. Recall is
            // a fire measure specifically, and the alarm sounding for anything else is not its cue.
            this.recalling = CsmCompat.isFireAlarmActiveAt(this.level, this.alarmPanelPos);
            // The alarm ending restores whatever the mode was before it, rather than assuming normal.
            // An elevator somebody deliberately took out of service must not quietly come back just
            // because a fire alarm ran and stopped.
            if(this.recalling && this.serviceMode != ServiceMode.FIRE_RECALL)
                this.preRecallMode = this.serviceMode;
            ServiceMode wanted = this.recalling ? ServiceMode.FIRE_RECALL
                : this.serviceMode == ServiceMode.FIRE_RECALL ? this.preRecallMode : this.serviceMode;
            if(wanted != this.serviceMode){
                this.serviceMode = wanted;
                // Whatever the building was asking for stopped mattering the moment the alarm sounded.
                if(this.recalling){
                    this.callQueue.clear();
                    this.callDirections.clear();
                    this.bankedDestinations.clear();
                }
                this.shouldBeSynced = true;
            }
        }
        if(!this.recalling || this.isMoving || this.emergencyState != EmergencyState.NONE)
            return;
        if(this.getFloorNumber(this.recallFloorY) == -1)
            return;
        if(this.isCabinAt(this.recallFloorY)){
            // Arrived: sit with the doors open. A recalled car is a way out, and a shut door is not.
            if(this.tickCounter % ALARM_POLL_INTERVAL == 0)
                this.requestDoorOpen(this.recallFloorY);
        }else if(this.dwellCounter <= 0 && --this.recallRetryTicks <= 0){
            this.onButtonPress(false, false, this.recallFloorY, null);
            // Mica: a dispatch that could not leave -- no cabin found, the recall floor obstructed,
            // the car overloaded -- used to be retried on the very next tick, for as long as the
            // alarm sounded. Each attempt scans the cabin volume at every floor, and a building-wide
            // alarm recalls every car at once. The first attempt is still immediate.
            if(!this.isMoving)
                this.recallRetryTicks = RECALL_RETRY_INTERVAL;
        }
    }

    /** Ticks between checking whether the cabin has reached a different floor, for comparators. */
    private static final int COMPARATOR_FLOOR_INTERVAL = 5;
    private int lastComparatorFloor = Integer.MIN_VALUE;
    private boolean recalling;
    /** What the building had asked for before the alarm, so the alarm ending gives it back. */
    private ServiceMode preRecallMode = ServiceMode.NORMAL;

    /** Whether the building may call this elevator, as opposed to the people already inside it. */
    public boolean acceptsHallCalls(){
        return this.serviceMode == ServiceMode.NORMAL;
    }

    /**
     * The cabin's floor as a comparator signal: 1 for the lowest floor, 0 when it cannot be said.
     * <p>
     * One-based so that zero can mean "no answer" rather than "the ground floor", which a comparator
     * has no other way to distinguish. Saturates at fifteen, because that is as high as a comparator
     * counts; a taller building simply reads fifteen for everything above it, which is honest and
     * still leaves the lower floors useful.
     */
    public int getComparatorFloor(){
        int floor = this.getCabinFloorNumber();
        return floor < 0 || floor >= this.getFloorCount() ? 0 : Math.min(15, floor + 1);
    }

    /**
     * Tells every registered comparator to look again.
     * <p>
     * All of them rather than one floor's, unlike an arrival: a readout showing which floor the cabin
     * is on is wrong the moment it moves, wherever it happens to be hanging.
     */
    private void notifyAllComparators(){
        for(Set<BlockPos> positions : this.comparatorListeners.values())
            for(BlockPos pos : positions)
                if(this.level.isBlockLoaded(pos))
                    this.level.updateComparatorOutputLevel(pos, this.level.getBlockState(pos).getBlock());
    }

    private void updateComparatorFloor(){
        if(this.tickCounter % COMPARATOR_FLOOR_INTERVAL != 0)
            return;
        int floor = this.getComparatorFloor();
        if(floor != this.lastComparatorFloor){
            this.lastComparatorFloor = floor;
            this.notifyAllComparators();
        }
    }

    public int getCabinCapacity(){
        return this.cageSizeX * this.cageSizeZ;
    }

    /** How many are aboard. Counted the same way the shaft sweep tells a passenger from a hazard. */
    public int getCabinOccupancy(){
        if(this.level == null)
            return 0;
        Vec3d anchor = this.getCageAnchorPos(this.currentY);
        AxisAlignedBB cabin = new AxisAlignedBB(anchor.x, anchor.y, anchor.z,
            anchor.x + this.cageSizeX, anchor.y + this.cageSizeY, anchor.z + this.cageSizeZ);
        int aboard = 0;
        // Queried a shade taller than the cabin, because a platform's passengers stand exactly on its
        // top face and a box sharing a plane with another does not intersect it -- so asking for the
        // cabin's own volume would return nobody at all on a platform. isRidingInside still decides
        // who counts, so this only widens the question, not the answer.
        for(EntityLivingBase entity : this.level.getEntitiesWithinAABB(EntityLivingBase.class, cabin.expand(0, ROOF_CLEARANCE, 0))){
            if(entity instanceof EntityPlayer && ((EntityPlayer)entity).isSpectator())
                continue;
            if(this.isRidingInside(cabin, entity))
                aboard++;
        }
        return aboard;
    }

    public boolean isOverloaded(){
        return this.overloaded;
    }

    /**
     * Which frame of a scrolling readout to show, off world time so every panel in the cabin scrolls
     * together without anything synchronising them.
     */
    public int marqueeStep(int steps){
        return this.level == null || steps <= 0 ? 0 : (int)((this.level.getTotalWorldTime() / 4) % steps);
    }

    /**
     * Counts who is aboard and, when there are too many, refuses to go anywhere: doors held open at
     * the floor it is standing at, and a buzz inside until somebody gets off.
     * <p>
     * Held open rather than merely stopped, because the way out of an overload is for a passenger to
     * leave, and a closed door makes that the one thing nobody can do.
     */
    private void updateOverload(){
        if(this.tickCounter % OVERLOAD_CHECK_INTERVAL == 0){
            boolean over = this.getCabinOccupancy() > this.getCabinCapacity();
            if(over != this.overloaded){
                this.overloaded = over;
                this.shouldBeSynced = true;
            }
        }
        if(!this.overloaded || this.isMoving)
            return;
        if(this.tickCounter % OVERLOAD_DOOR_REFRESH == 0)
            this.requestDoorOpen(this.targetY);
        if(this.tickCounter % OVERLOAD_BUZZ_INTERVAL == 0)
            this.playAtCabin(ElevatorSoundScheme.Moment.OVERLOAD);
    }

    /**
     * The emergency stop as a passenger asks for it, from inside the cabin.
     * <p>
     * A cabin already standing still cannot be brought to a halt, but it can be taken out of service,
     * which is the half of the behaviour that still means something when the button is pressed at a
     * floor.
     */
    public void requestEmergencyStop(){
        if(this.emergencyState != EmergencyState.NONE)
            return;
        if(this.isMoving){
            this.triggerEmergencyStop();
            return;
        }
        this.emergencyState = EmergencyState.HOLDING;
        this.emergencyHold = emergencyHoldTicks();
        this.dwellCounter = emergencyHoldTicks();
        this.doorHoldTicks.put(this.targetY, emergencyHoldTicks());
        this.requestDoorOpen(this.targetY);
        this.playAtCabin(ElevatorSoundScheme.Moment.OBSTRUCTED);
        this.shouldBeSynced = true;
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

    public boolean isCabinMusicEnabled(){
        return this.cabinMusicEnabled;
    }

    public void setCabinMusicEnabled(boolean enabled){
        this.cabinMusicEnabled = enabled;
        this.shouldBeSynced = true;
    }

    /**
     * Plays the cabin's music, and starts it again when it runs out.
     * <p>
     * Only while somebody is aboard. An empty lift playing to itself would be every lift in a
     * building playing at once, heard from every landing, forever -- and nothing about a track
     * started for nobody can be switched off by the person it eventually annoys.
     * <p>
     * The elevator cannot stop a track once handed to a client, so switching the music off takes
     * effect at the end of the one playing rather than immediately.
     */
    private void updateCabinMusic(){
        if(this.tickCounter % MUSIC_CHECK_INTERVAL != 0)
            return;
        if(this.cabinMusicRemaining > 0){
            this.cabinMusicRemaining -= MUSIC_CHECK_INTERVAL;
            return;
        }
        if(!this.cabinMusicEnabled || !this.soundsEnabled || this.getCabinOccupancy() <= 0)
            return;
        this.playAtCabin(ElevatorSoundScheme.Moment.CABIN_MUSIC);
        this.cabinMusicRemaining = this.soundScheme.cabinMusicLengthTicks();
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
        // A tick of travel spans a fraction of a block, so almost every tick crosses no floor at all.
        // Checking the span before touching the floor list turns the common case into one comparison
        // instead of a walk of the whole building, on every moving elevator, every tick.
        if(from == to)
            return;
        for(int floor = 0; floor < this.floors.size(); floor++){
            int y = this.floors.get(floor);
            if(y > to)
                // floors is kept in ascending order, so nothing after this can be in range either.
                break;
            // Strictly greater than 'from' so a floor is not announced twice when the cabin starts
            // moving from a standstill on top of it.
            if(y > from)
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
        this.riders.clear();

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
            this.emergencyHold = emergencyHoldTicks();
            this.dwellCounter = emergencyHoldTicks();
            this.doorHoldTicks.put(this.targetY, emergencyHoldTicks());
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
        if(this.level == null || this.isMoving || this.emergencyState != EmergencyState.NONE
            || this.overloaded || this.isOutOfService())
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
        if(!this.level.isRemote)
            this.seedRiders(currentY);
        this.cageChanged = true;
        this.playAtCabin(ElevatorSoundScheme.Moment.DEPARTING);
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
                    if(this.canCageBePlacedAt(entity, this.getEntityForFloor(floor), requester)){
                        this.startElevator(this.getFloorYLevel(floor), yLevel);
                        // Departure empties the floor it left, so anything still standing in the
                        // shaft's cabin volume elsewhere is a second cabin -- and there is only ever
                        // one. Checked here because this is the branch that has just had to guess
                        // which floor the cabin was on.
                        if(this.isMoving)
                            this.warnAboutStrayCabins(requester);
                    }
                    return;
                }
            }
            if(requester instanceof EntityPlayerMP && !this.isCageAvailableAt(entityFloor, true, null))
                requester.sendStatusMessage(TextComponents.translation("movingelevators.elevator.no_cabins").color(TextFormatting.GRAY).get(), false);
        }
    }

    /**
     * Points out any floor that still looks like it holds a cabin once this one has set off.
     * <p>
     * A shaft holds one cabin, and {@link ElevatorCage#canCreateCage} has no way to know that: it
     * answers "are these blocks movable", so a block left loose in the cabin volume at a landing reads
     * as a cabin like any other. The search above then takes whichever is nearest the floor asked for
     * -- which can be the stray rather than the cab. Nothing about that is visible in game. The cab
     * sits still while the readouts move, because the elevator really is carrying something, just not
     * the thing the player is standing in.
     * <p>
     * Only ever addressed to a player who pressed something. Recall, the call queue and bank dispatch
     * all pass a null requester, which is exactly why this failure had nobody to tell.
     */
    private void warnAboutStrayCabins(EntityPlayer requester){
        if(!(requester instanceof EntityPlayerMP))
            return;
        ITextComponent floors = null;
        for(int floor = 0; floor < this.floors.size(); floor++){
            if(!this.isCageAvailableAt(floor, true, null))
                continue;
            ITextComponent name = this.describeFloor(floor);
            floors = floors == null ? name
                : TextComponents.translation("movingelevators.elevator.stray_cabin.more", floors, name).get();
        }
        if(floors != null)
            requester.sendStatusMessage(TextComponents.translation("movingelevators.elevator.stray_cabin", floors)
                .color(TextFormatting.GOLD).get(), false);
    }

    /** A floor as a player would name it: its own name where it has one, otherwise "Floor 3". */
    private ITextComponent describeFloor(int floor){
        String name = this.getFloorDisplayName(floor);
        return name != null && !name.isEmpty()
            ? TextComponents.string(name).color(TextFormatting.GOLD).get()
            : TextComponents.translation("movingelevators.floor_name",
                TextComponents.number(floor + 1).get()).color(TextFormatting.GOLD).get();
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
        this.layoutVersion++;
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
        this.layoutVersion++;
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
            this.layoutVersion++;
            this.shouldBeSynced = true;
        }
    }

    public boolean canDecreaseCageHeightOffset(){
        return !this.isMoving() && this.cageHeightOffset > -this.cageSizeY;
    }

    public void decreaseCageHeightOffset(){
        if(this.canDecreaseCageHeightOffset()){
            this.cageHeightOffset--;
            this.layoutVersion++;
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
            this.layoutVersion++;
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
            this.layoutVersion++;
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

    /**
     * The y level the cabin's floor sits at when the cabin is standing at a landing -- the block a
     * passenger steps onto, and therefore the block that landing's doorway stands in.
     *
     * @param floorY y-level of the landing's controller
     */
    public int getCabinFloorY(int floorY){
        return floorY + this.cageHeightOffset;
    }

    /**
     * How far a column is from this elevator's cabin when the cabin is standing at a landing: zero
     * inside the cabin's own footprint, one for the ring of blocks around it, and so on.
     * <p>
     * Doorways are measured against this rather than against the controller column. A controller
     * sits behind its cabin, so the deeper the cabin the further its doors are from it -- past a
     * certain depth, further than the link range allows at all, and in a bank far enough that the
     * shaft next door can be the nearer of the two. The mouth of the cabin is what a doorway is
     * actually attached to, so that is what it should be measured from.
     *
     * @param floorY y-level of the landing's controller
     */
    public int horizontalDistanceToCabin(int floorY, int x, int z){
        BlockPos anchor = this.getCageAnchorBlockPos(floorY);
        int dx = Math.max(Math.max(anchor.getX() - x, x - (anchor.getX() + this.cageSizeX - 1)), 0);
        int dz = Math.max(Math.max(anchor.getZ() - z, z - (anchor.getZ() + this.cageSizeZ - 1)), 0);
        return Math.max(dx, dz);
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

    /**
     * @return whether the next sync has to carry the cabin's contents, clearing the flag
     */
    /**
     * Takes the cabin from the group this one is replacing, for updates that left it out.
     * <p>
     * Reading a group builds a new one and swaps it in rather than updating the old in place, so
     * "keep the cabin you already have" is not something the reader can do by itself -- there is no
     * "already" on a fresh object. Without this a state-only update mid-trip produced a group that
     * believed it was moving and had no cabin to move, which the renderer met once a frame.
     */
    public void inheritCage(ElevatorGroup previous){
        if(this.cage == null && previous != null)
            this.cage = previous.cage;
        // Belt and braces, and only ever on the client. If the cabin is genuinely unknown -- a message
        // arriving before any that carried one -- then not drawing a moving cabin is a disappointment,
        // and drawing a null one is a client that stops responding. The next full sync puts it right.
        // Never server-side: the cabin's blocks exist nowhere but that field while in flight, so
        // quietly deciding the lift has stopped would destroy them with no way back.
        if(this.level.isRemote && this.isMoving && this.cage == null)
            this.isMoving = false;
    }

    /**
     * Client only. Carries whatever is riding the cabin from where the group this one replaced had
     * it to where this one has it.
     * <p>
     * A state-only update mid-trip -- an emergency stop, a call queued by somebody on another floor,
     * a retarget -- replaces the client's group with one built from the server's numbers, and the
     * server's cabin is never quite where the client's was: a latency behind in the ordinary case,
     * and a long way behind when the server has stalled and the client has kept simulating. The new
     * group then moved on from the server's position and the rider was left standing in the air
     * where the old cabin had been, to fall the length of the shaft. Moving the cabin from the old
     * position to the new one, exactly as a tick of travel would, takes the rider along with it.
     */
    public void carryRidersFrom(ElevatorGroup previous){
        if(!this.level.isRemote || previous == null || !previous.isMoving || !this.isMoving || this.cage == null)
            return;
        if(previous.currentY != this.currentY)
            this.moveElevator(previous.currentY, this.currentY);
    }

    public boolean takeCageChanged(){
        boolean changed = this.cageChanged;
        this.cageChanged = false;
        return changed;
    }

    public NBTTagCompound write(){
        return this.write(true);
    }

    /**
     * @param includeCage whether to write the cabin's contents, which is nearly all of the size of
     *                    this and changes far less often than the rest of it
     */
    public NBTTagCompound write(boolean includeCage){
        NBTTagCompound compound = new NBTTagCompound();
        compound.setBoolean("isMoving", this.isMoving);
        // Written unconditionally: while stopped this is where the cabin came to rest, which is the
        // reference point pickNextCall() sweeps from. Upstream only stored it mid-move, so a reloaded
        // idle group would have dispatched queued calls relative to y=0.
        compound.setInteger("targetY", this.targetY);
        if(this.isMoving){
            compound.setDouble("lastY", this.lastY);
            compound.setDouble("currentY", this.currentY);
            if(includeCage)
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
    compound.setBoolean("cabinMusicEnabled", this.cabinMusicEnabled);
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
    compound.setBoolean("overloaded", this.overloaded);
    compound.setString("serviceMode", this.serviceMode.name());
    compound.setString("preRecallMode", this.preRecallMode.name());
    compound.setBoolean("hasAlarmPanel", this.alarmPanelPos != null);
    if(this.alarmPanelPos != null)
        compound.setLong("alarmPanelPos", this.alarmPanelPos.toLong());
    compound.setInteger("recallFloorY", this.recallFloorY);
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
                // Absent when this is a state-only update. The cabin it describes has not changed, so
                // the one the group being replaced was carrying is still right -- see inheritCage,
                // which the reader calls straight afterwards to hand it over.
                if(compound.hasKey("cage", Constants.NBT.TAG_COMPOUND))
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
        this.layoutVersion++;
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
    this.cabinMusicEnabled = !compound.hasKey("cabinMusicEnabled") || compound.getBoolean("cabinMusicEnabled");
        this.soundScheme = ElevatorSoundScheme.byName(compound.getString("soundScheme"));
    this.bankedDestinations.clear();
    int[] banked = compound.getIntArray("bankedDestinations");
    for(int i = 0; i + 1 < banked.length; i += 2)
        this.bankedDestinations.computeIfAbsent(banked[i], y -> new LinkedHashSet<>()).add(banked[i + 1]);
    this.announcingFloor = compound.hasKey("announcingFloor") ? compound.getInteger("announcingFloor") : Integer.MIN_VALUE;
    this.overloaded = compound.getBoolean("overloaded");
    this.alarmPanelPos = compound.getBoolean("hasAlarmPanel") ? BlockPos.fromLong(compound.getLong("alarmPanelPos")) : null;
    this.recallFloorY = compound.getInteger("recallFloorY");
    this.serviceMode = ServiceMode.NORMAL;
    this.preRecallMode = ServiceMode.NORMAL;
    for(ServiceMode mode : ServiceMode.values()){
        if(mode.name().equals(compound.getString("serviceMode")))
            this.serviceMode = mode;
        if(mode.name().equals(compound.getString("preRecallMode")))
            this.preRecallMode = mode;
    }
    // A recall is never what the building wanted back afterwards, whatever an older save says.
    if(this.preRecallMode == ServiceMode.FIRE_RECALL)
        this.preRecallMode = ServiceMode.NORMAL;
    this.name = compound.getBoolean("hasElevatorName") ? compound.getString("elevatorName") : null;
        this.emergencyState = EmergencyState.NONE;
        for(EmergencyState state : EmergencyState.values())
            if(state.name().equals(compound.getString("emergencyState")))
                this.emergencyState = state;
        this.emergencyHold = compound.getInteger("emergencyHold");
        this.lastDirection = compound.getInteger("lastDirection");
        this.dwellCounter = compound.getInteger("dwellCounter");
    // currentY is only written while moving, so a reloaded idle group had zero -- and everything
    // anchored to it followed: where sounds play, where the occupancy box sits, where the shaft sweep
    // looks, how a bank scores this car's distance. A stopped cabin is at the floor it stopped at.
    if(!this.isMoving)
        this.currentY = this.targetY;
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
        // Mica: compared unboxed. indexOf(Object) boxes y, which above y=127 allocates, and every door
        // block asks this every tick.
        for(int i = 0; i < this.floors.size(); i++)
            if(this.floors.get(i) == y)
                return i;
        return -1;
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
    /** @see #layoutVersion */
    public int getLayoutVersion(){
        return this.layoutVersion;
    }

    public int getCabinFloorNumber(){
        // Cached for the tick it was computed in. Every door block entity asks once a tick, a doorway
        // has up to four of them, and every landing renderer asks once a frame -- and the answer can
        // reach a full scan of the cabin's volume. It cannot change within a tick, so computing it
        // more than once a tick is pure waste.
        if(this.cabinFloorTick == this.tickCounter)
            return this.cachedCabinFloor;
        this.cabinFloorTick = this.tickCounter;
        this.cachedCabinFloor = this.computeCabinFloorNumber();
        return this.cachedCabinFloor;
    }

    private int cabinFloorTick = -1;
    private int cachedCabinFloor = -1;

    private int computeCabinFloorNumber(){
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
        return this.getFloorNumber(yLevel) != -1;
    }

    private void updateGroup(){
        ElevatorGroupCapability.get(this.level).updateGroup(this);
    }

    private void syncMovement(){
        if(!this.level.isRemote)
            MovingElevators.CHANNEL.sendToDimension(this.level, new PacketSyncElevatorMovement(this.x, this.z, this.facing, this.currentY, this.speed));
    }

    public void validateControllersExist(Chunk chunk){
        // Never while the cabin is in flight. A controller's block entity is not always present the
        // instant its chunk loads, so a floor can look missing when it is merely not ready yet -- and
        // removing the last floor of a moving elevator spawns the whole cabin as item drops. The check
        // runs again on the next chunk load, by which point the answer can be trusted; a floor that is
        // genuinely gone is caught then, and an elevator disintegrating mid-ride never is.
        if(this.isMoving)
            return;
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
