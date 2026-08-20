package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.TickableBlockEntity;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import com.supermartijn642.movingelevators.elevator.ElevatorSoundScheme;
import com.supermartijn642.core.TextComponents;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;

/**
 * Decides when a doorway should be open, and holds the dwell that closes it again.
 * <p>
 * The governing rule is that a landing door may only stand open when the cabin is actually parked at
 * its floor. An open door onto an empty shaft is both wrong to look at and a hole to fall down, so
 * the cabin's presence gates everything except the redstone override, which is deliberately absolute
 * -- it is the emergency release.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class ElevatorDoorBlockEntity extends RemoteBoundBlockEntity implements TickableBlockEntity {

    /**
     * Blocks of slack above and below a cabin within which a doorway still counts as standing in it.
     * <p>
     * The landing test used to be a flat two blocks either side of the controller. Searching only
     * downwards was wrong first -- the cabin floor sits below its controller, so a doorway you walk
     * through is not at controller height -- and a fixed window was wrong afterwards, because it was
     * still measured from the controller rather than from the cabin. Raise a cabin's height offset,
     * or make it taller, and the doorway standing in its mouth falls outside two blocks; at that
     * point the door cannot identify its own landing at all, so the cabin is never "here", it never
     * opens by itself, and the car panel's door buttons have nothing listening to them.
     */
    private static final int LANDING_SLACK = 1;

    /**
     * How far outside a cabin a doorway may stand and still adopt the elevator it belongs to, in
     * blocks. Measured from the cabin rather than from the controller behind it, so it means the same
     * thing whatever size the cabin has been set to.
     */
    private static int adoptRange(){
        return MovingElevatorsConfig.doorLinkRange.get();
    }
    /** Only retried periodically: an unbound door is looking for something that may not exist yet. */
    private static final int ADOPT_INTERVAL = 40;

    /** World time of the last open/close request this door acted on, so it reacts once per press. */
    private long lastOpenRequest, lastCloseRequest;
    /** Ticks left before the doors close on their own. */
    /**
     * How long a door blocked at closing time stays open before trying again. A second is long
     * enough to walk through and short enough that the door does not feel stuck once you are clear.
     */
    private static final int OBSTRUCTION_HOLD_TICKS = 20;

    private int openTicks;
    /**
     * Whether this block is the upper half of its doorway.
     * <p>
     * Kept here rather than in the block state on purpose. Inferring it from the block below --
     * "same block, same facing, so I must be the top" -- is ambiguous the moment two doorways are
     * stacked, and 1.12's four bits of metadata cannot hold facing, side, open and half at once. A
     * block entity has no such limit.
     */
    private boolean top;
    private int adoptCounter;

    public ElevatorDoorBlockEntity(){
        super(MovingElevators.elevator_door_tile);
    }

    public void setTop(boolean top){
        this.top = top;
        this.dataChanged();
    }

    public boolean isTop(){
        return this.top;
    }

    /**
     * The floor this door serves, taken from where the door itself stands rather than from the
     * controller it was bound to.
     * <p>
     * Binding says <em>which elevator</em>; position says <em>which landing</em>. Inheriting the
     * bound controller's floor -- as the panels do, since they are remotes and may be anywhere --
     * meant every door bound from the same item believed it was on that one floor, so a shaft's
     * worth of doorways all watched a single landing and only one of them ever opened.
     */
    @Override
    public int getFloorLevel(){
        ElevatorGroup group = this.getGroup();
        if(group != null){
            int best = Integer.MAX_VALUE, bestDistance = Integer.MAX_VALUE;
            for(int floor = 0; floor < group.getFloorCount(); floor++){
                int y = group.getFloorYLevel(floor);
                int distance = landingDistance(group, y, this.pos.getY());
                if(distance >= 0 && distance < bestDistance){
                    bestDistance = distance;
                    best = y;
                }
            }
            if(best != Integer.MAX_VALUE)
                return best;
        }
        return super.getFloorLevel();
    }

    /**
     * How far a block stands from the cabin floor at a landing, or -1 when it is not in that
     * landing's cabin at all.
     * <p>
     * Ranked by the cabin floor rather than merely accepted, so the two halves of a doorway can
     * never pick different landings. With a flat window they could: two landings a cabin's height
     * apart leave a doorway's lower block the same distance from each, and the tie went to whichever
     * floor came first in the list -- so the bottom leaf watched one floor while the top leaf watched
     * the other, and only ever half a door opened.
     *
     * @param floorY y-level of the landing's controller
     */
    private static int landingDistance(ElevatorGroup group, int floorY, int y){
        int distance = y - group.getCabinFloorY(floorY);
        return distance < -LANDING_SLACK || distance > group.getCageSizeY() - 1 + LANDING_SLACK
            ? -1 : Math.abs(distance);
    }

    /** Ticks a leaf takes to travel its full width. */
    private static final float ANIMATION_SPEED = 1 / 8f;

    /** Client-side only: how far this leaf has slid, and where it was last tick to interpolate from. */
    private float animation, previousAnimation;
    /**
     * Whether the leaf should be open. Kept here rather than in the block state deliberately.
     * <p>
     * Toggling a block state every time the doors move meant a block update, and with it the risk of
     * the block entity being rebuilt underneath the animation -- which is what made closing snap
     * while opening animated: a fresh entity starts at zero, so 0 to 1 looks like opening and 1 to 0
     * becomes 0 to 0. A rebuilt entity also loses the request stamps it uses to tell a new press from
     * one it has already acted on, which leaves a door that will not respond again.
     */
    private boolean open;

    public boolean isOpen(){
        return this.open;
    }

    public float getAnimation(float partialTicks){
        return this.previousAnimation + (this.animation - this.previousAnimation) * partialTicks;
    }

    @Override
    public void update(){
        if(this.world == null)
            return;
        if(this.world.isRemote){
            // The leaf slides towards whatever the block state says, so a door that is already open
            // when it comes into view is drawn open rather than sliding on first sight.
            float target = this.open ? 1 : 0;
            this.previousAnimation = this.animation;
            this.animation += Math.max(-ANIMATION_SPEED, Math.min(ANIMATION_SPEED, target - this.animation));
            return;
        }

        // Retried while a binding cannot be resolved as well as while there is none. A door whose
        // landing controller has been broken and rebuilt keeps a binding that no longer names any
        // elevator, and getGroup() then answers null for good -- so the door stood shut, deaf to its
        // own cabin and to the car panel's door buttons, looking exactly like one that works.
        if((!this.isBound() || this.getGroup() == null) && --this.adoptCounter <= 0){
            this.adoptCounter = ADOPT_INTERVAL;
            this.adoptNearestLanding();
        }

        ElevatorGroup group = this.getGroup();
        int floorLevel = this.getFloorLevel();
        boolean cabinHere = group != null && group.isCabinAt(floorLevel);

        if(group != null){
            long openRequest = group.getDoorOpenRequest(floorLevel);
            long closeRequest = group.getDoorCloseRequest(floorLevel);
            // Closing is read first, and cancels any open request it is not older than, so a request
            // still waiting for the cabin cannot re-open the doors on the tick after somebody inside
            // the cabin closed them.
            if(closeRequest > this.lastCloseRequest){
                this.lastCloseRequest = closeRequest;
                this.openTicks = 0;
                if(closeRequest >= openRequest)
                    this.lastOpenRequest = openRequest;
            }
            // Spent only when it is acted on. Spending it regardless -- on the reasoning that the
            // arrival would raise a fresh one -- meant any tick on which the door and the elevator
            // disagreed about the cabin being here swallowed the arrival's request silently, and
            // nothing ever raised another: the car stood out its whole wait with the doors shut and
            // then left. Left pending, that same request opens the doors as soon as the cabin is
            // agreed to be there.
            if(openRequest > this.lastOpenRequest && cabinHere){
                this.lastOpenRequest = openRequest;
                // A bank dispatch asks for a longer hold than the configured default, since whoever
                // called the car is walking to it rather than standing at the doors.
                this.openTicks = Math.max(MovingElevatorsConfig.doorAutoCloseTicks.get(), group.getDoorHoldTicks(floorLevel));
            }
        }

        // The cabin leaving shuts the doors immediately -- they must never be left open on a shaft.
        if(!cabinHere)
            this.openTicks = 0;
        else if(this.openTicks > 0)
            this.openTicks--;

        boolean shouldBeOpen = this.isDoorwayPowered() || this.openTicks > 0;
        // A safety edge. Checked only at the moment a door that is open would close, so the entity
        // scan costs nothing while it sits open or shut -- and holding rather than cancelling means
        // stepping clear lets it close a second later instead of leaving it open indefinitely.
        if(this.open && !shouldBeOpen && this.isDoorwayObstructed()){
            this.openTicks = OBSTRUCTION_HOLD_TICKS;
            shouldBeOpen = true;
        }
        this.setOpen(shouldBeOpen);
    }

    /**
     * Redstone applies to the doorway, not to the block that happens to touch the wire. Asking only
     * about this block's own position meant a lever opened whichever leaf it was next to and left the
     * rest shut.
     */
    private boolean isDoorwayObstructed(){
        IBlockState state = this.world.getBlockState(this.pos);
        return state.getBlock() instanceof ElevatorDoorBlockBase
            && ((ElevatorDoorBlockBase)state.getBlock()).isDoorwayObstructed(this.world, this.pos, state);
    }

    private boolean isDoorwayPowered(){
        IBlockState state = this.world.getBlockState(this.pos);
        return state.getBlock() instanceof ElevatorDoorBlockBase
            && ((ElevatorDoorBlockBase)state.getBlock()).isDoorwayPowered(this.world, this.pos, state);
    }

    /**
     * Finds the elevator landing this doorway stands at and binds to it.
     * <p>
     * Doors are not remotes. A landing panel can hang anywhere and genuinely needs to be told which
     * elevator it belongs to, but a doorway is physically at a landing -- it can simply look. That
     * removes the need to bind the item before placing it, and with it a whole failure mode: a door
     * whose binding did not survive placement looks identical to a working one and never moves.
     * <p>
     * Cheap despite appearances: it walks the world's elevator groups, of which there are a handful,
     * rather than scanning blocks.
     */
    private void adoptNearestLanding(){
        ElevatorGroupCapability capability = ElevatorGroupCapability.get(this.world);
        if(capability == null)
            return;

        ElevatorGroup best = null;
        int bestY = 0, bestDistance = Integer.MAX_VALUE, bestDrop = Integer.MAX_VALUE;
        for(ElevatorGroup group : capability.getGroups()){
            for(int floor = 0; floor < group.getFloorCount(); floor++){
                int y = group.getFloorYLevel(floor);
                int drop = landingDistance(group, y, this.pos.getY());
                if(drop < 0)
                    continue;
                // Measured from the cabin's mouth rather than from the controller behind it. A
                // doorway is attached to the cabin it opens onto, and the deeper that cabin is the
                // further its doors stand from their own controller -- far enough, in a bank, that
                // the car next door is the nearer of the two, and far enough on its own that a deep
                // cabin's doors fall outside the link range and never attach to anything. Adopting
                // the wrong car of a bank is the worse of the two, because everything else goes on
                // working: the elevator answers calls, the panels light, and only the doors never
                // move.
                int distance = group.horizontalDistanceToCabin(y, this.pos.getX(), this.pos.getZ());
                if(distance > adoptRange())
                    continue;
                if(best != null && compareCandidates(distance, drop, group, bestDistance, bestDrop, best) >= 0)
                    continue;
                best = group;
                bestY = y;
                bestDistance = distance;
                bestDrop = drop;
            }
        }

        if(best != null)
            this.setValues(new BlockPos(best.x, bestY, best.z), best.facing);
    }

    /**
     * Nearest cabin wins, then the landing whose floor the door stands closest to, and failing both
     * the elevator that comes first by position.
     * <p>
     * That last clause is the reason this is a comparison rather than a pair of ifs. The elevators
     * come out of a hash map, so a tie used to be settled by iteration order; a bank's shafts are
     * alike enough to tie regularly, and a door that adopts a different car depending on the order a
     * map happened to hand them over is worse than one that is consistently wrong, because it cannot
     * even be reproduced.
     */
    private static int compareCandidates(int distance, int drop, ElevatorGroup group,
                                         int bestDistance, int bestDrop, ElevatorGroup best){
        if(distance != bestDistance)
            return Integer.compare(distance, bestDistance);
        if(drop != bestDrop)
            return Integer.compare(drop, bestDrop);
        if(group.x != best.x)
            return Integer.compare(group.x, best.x);
        if(group.z != best.z)
            return Integer.compare(group.z, best.z);
        return Integer.compare(group.facing.ordinal(), best.facing.ordinal());
    }

    /**
     * Tells a player exactly what this door believes, in order of what has to be true for it to work.
     * Whichever line reads wrong is the failure.
     */
    public void reportStatus(net.minecraft.entity.player.EntityPlayer player){
        ElevatorGroup group = this.getGroup();
        player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.header").color(TextFormatting.AQUA).get());

        if(!this.isBound()){
            // The range is passed in rather than written into the message, which said "12 blocks"
            // regardless of what adoptRange() actually was.
            player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.searching",
                TextComponents.number(adoptRange()).get()).color(TextFormatting.YELLOW).get());
            return;
        }
        BlockPos controller = this.getControllerPos();
        player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.bound",
            TextComponents.number(controller.getX()).get(), TextComponents.number(controller.getY()).get(),
            TextComponents.number(controller.getZ()).get()).color(TextFormatting.GRAY).get());

        if(group == null){
            player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.no_group").color(TextFormatting.RED).get());
            return;
        }

        int floorLevel = this.getFloorLevel();
        boolean matched = group.hasControllerAt(floorLevel);
        player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.landing",
            TextComponents.number(this.pos.getY()).get(), TextComponents.number(floorLevel).get())
            .color(matched ? TextFormatting.GRAY : TextFormatting.RED).get());

        boolean cabinHere = group.isCabinAt(floorLevel);
        player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.cabin",
            TextComponents.string(Boolean.toString(cabinHere)).get(),
            TextComponents.number(group.getCabinFloorNumber()).get())
            .color(cabinHere ? TextFormatting.GREEN : TextFormatting.YELLOW).get());

        player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.half",
            TextComponents.string(Boolean.toString(this.top)).get(),
            TextComponents.number(this.openTicks).get()).color(TextFormatting.GRAY).get());
        player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.requests",
            TextComponents.string(Boolean.toString(this.open)).get(),
            TextComponents.number(group.getDoorOpenRequest(floorLevel) - this.lastOpenRequest).get(),
            TextComponents.number(group.getDoorCloseRequest(floorLevel) - this.lastCloseRequest).get())
            .color(TextFormatting.GRAY).get());
    }

    private void setOpen(boolean open){
        if(this.open == open)
            return;
        this.open = open;
        if(!this.world.isRemote && this.isDoorwayOrigin())
            this.playDoorSound(open);
        // Syncs to clients so their leaves start sliding, and marks the chunk so collision follows.
        this.dataChanged();
    }

    /**
     * A doorway is up to four blocks with an entity each, all of which open together. Only one of
     * them is allowed to make the noise, or a single door sounds like four.
     */
    private boolean isDoorwayOrigin(){
        if(this.top)
            return false;
        IBlockState state = this.world.getBlockState(this.pos);
        return !(state.getBlock() instanceof ElevatorDoorBlock) || !state.getValue(ElevatorDoorBlock.RIGHT);
    }

    private void playDoorSound(boolean open){
        ElevatorSoundScheme.Moment moment = open ? ElevatorSoundScheme.Moment.DOORS_OPENING : ElevatorSoundScheme.Moment.DOORS_CLOSING;
        Vec3d pos = new Vec3d(this.pos).addVector(0.5, 0.5, 0.5);
        ElevatorGroup group = this.getGroup();
        if(group != null)
            // Through the group, so the controller's sound toggle covers its landing doors too.
            group.playAt(pos, moment);
        else
            // An unbound door still moves under redstone, and should still be heard doing it.
            // There is no elevator to take a scheme from, so it gets the default one.
            ElevatorSoundScheme.STANDARD.play(this.world, pos, moment);
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = super.writeData();
        compound.setInteger("openTicks", this.openTicks);
        compound.setBoolean("top", this.top);
        compound.setBoolean("open", this.open);
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        super.readData(compound);
        this.openTicks = compound.getInteger("openTicks");
        this.top = compound.getBoolean("top");
        this.open = compound.getBoolean("open");
    }
}
