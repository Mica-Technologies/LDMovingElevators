package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.TickableBlockEntity;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import net.minecraft.block.state.IBlockState;
import com.supermartijn642.core.TextComponents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
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
     * How far a landing's controller may sit from a door block, in either direction.
     * <p>
     * Searching only downwards was wrong: the cabin floor sits one block <em>below</em> its
     * controller by default (see cageHeightOffset), so a doorway you actually walk through stands at
     * controller y minus one, and looking down from there never reaches the controller above it.
     * That is why the doors never opened by themselves -- every one of them failed to identify its
     * own landing, so the cabin was never "here".
     */
    private static final int FLOOR_SEARCH_RANGE = 2;

    /**
     * How far from a landing's controller column a doorway may stand and still adopt it. A cabin can
     * be up to fifteen blocks across, so its doors sit well off the controller's own column.
     */
    private static final int ADOPT_RANGE = 12;
    /** Only retried periodically: an unbound door is looking for something that may not exist yet. */
    private static final int ADOPT_INTERVAL = 40;

    /** World time of the last open/close request this door acted on, so it reacts once per press. */
    private long lastOpenRequest, lastCloseRequest;
    /** Ticks left before the doors close on their own. */
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
                int distance = Math.abs(y - this.pos.getY());
                if(distance <= FLOOR_SEARCH_RANGE && distance < bestDistance){
                    bestDistance = distance;
                    best = y;
                }
            }
            if(best != Integer.MAX_VALUE)
                return best;
        }
        return super.getFloorLevel();
    }

    /** Ticks a leaf takes to travel its full width. */
    private static final float ANIMATION_SPEED = 1 / 8f;

    /** Client-side only: how far this leaf has slid, and where it was last tick to interpolate from. */
    private float animation, previousAnimation;

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
            IBlockState state = this.world.getBlockState(this.pos);
            float target = state.getBlock() instanceof ElevatorDoorBlockBase && state.getValue(ElevatorDoorBlockBase.OPEN) ? 1 : 0;
            this.previousAnimation = this.animation;
            this.animation += Math.max(-ANIMATION_SPEED, Math.min(ANIMATION_SPEED, target - this.animation));
            return;
        }

        if(!this.isBound() && --this.adoptCounter <= 0){
            this.adoptCounter = ADOPT_INTERVAL;
            this.adoptNearestLanding();
        }

        ElevatorGroup group = this.getGroup();
        int floorLevel = this.getFloorLevel();
        boolean cabinHere = group != null && group.isCabinAt(floorLevel);

        if(group != null){
            long openRequest = group.getDoorOpenRequest(floorLevel);
            if(openRequest > this.lastOpenRequest){
                this.lastOpenRequest = openRequest;
                // Only honour it if the cabin is here; otherwise the request is simply spent, and the
                // arrival will raise a fresh one.
                if(cabinHere)
                    this.openTicks = MovingElevatorsConfig.doorAutoCloseTicks.get();
            }
            long closeRequest = group.getDoorCloseRequest(floorLevel);
            if(closeRequest > this.lastCloseRequest){
                this.lastCloseRequest = closeRequest;
                this.openTicks = 0;
            }
        }

        // The cabin leaving shuts the doors immediately -- they must never be left open on a shaft.
        if(!cabinHere)
            this.openTicks = 0;
        else if(this.openTicks > 0)
            this.openTicks--;

        this.setOpen(this.isDoorwayPowered() || this.openTicks > 0);
    }

    /**
     * Redstone applies to the doorway, not to the block that happens to touch the wire. Asking only
     * about this block's own position meant a lever opened whichever leaf it was next to and left the
     * rest shut.
     */
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
        int bestY = 0;
        double bestDistance = Double.MAX_VALUE;
        for(ElevatorGroup group : capability.getGroups()){
            double dx = group.x - this.pos.getX(), dz = group.z - this.pos.getZ();
            double distance = dx * dx + dz * dz;
            if(distance > ADOPT_RANGE * ADOPT_RANGE || distance >= bestDistance)
                continue;
            for(int floor = 0; floor < group.getFloorCount(); floor++){
                int y = group.getFloorYLevel(floor);
                if(Math.abs(y - this.pos.getY()) <= FLOOR_SEARCH_RANGE){
                    best = group;
                    bestY = y;
                    bestDistance = distance;
                    break;
                }
            }
        }

        if(best != null)
            this.setValues(new BlockPos(best.x, bestY, best.z), best.facing);
    }

    /**
     * Tells a player exactly what this door believes, in order of what has to be true for it to work.
     * Whichever line reads wrong is the failure.
     */
    public void reportStatus(net.minecraft.entity.player.EntityPlayer player){
        ElevatorGroup group = this.getGroup();
        player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.header").color(TextFormatting.AQUA).get());

        if(!this.isBound()){
            player.sendMessage(TextComponents.translation("movingelevators.elevator_door.status.searching").color(TextFormatting.YELLOW).get());
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
    }

    private void setOpen(boolean open){
        IBlockState state = this.world.getBlockState(this.pos);
        if(state.getBlock() instanceof ElevatorDoorBlockBase && state.getValue(ElevatorDoorBlockBase.OPEN) != open)
            this.world.setBlockState(this.pos, state.withProperty(ElevatorDoorBlockBase.OPEN, open), 3);
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = super.writeData();
        compound.setInteger("openTicks", this.openTicks);
        compound.setBoolean("top", this.top);
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        super.readData(compound);
        this.openTicks = compound.getInteger("openTicks");
        this.top = compound.getBoolean("top");
    }
}
