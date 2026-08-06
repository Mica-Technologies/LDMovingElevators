package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.TickableBlockEntity;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Drives a pair of {@link ElevatorDoorBlock} panels: decides when they should be open, and holds the
 * dwell that closes them again.
 * <p>
 * The governing rule is that a landing door may only stand open when the cabin is actually parked at
 * its floor. An open door onto an empty shaft is both wrong to look at and a hole to fall down, so
 * the cabin's presence gates everything except the redstone override, which is deliberately absolute
 * -- it is the emergency release.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class ElevatorDoorBlockEntity extends RemoteBoundBlockEntity implements TickableBlockEntity {

    /** World time of the last open/close request this door acted on, so it reacts once per press. */
    private long lastOpenRequest, lastCloseRequest;
    /** Ticks left before the doors close on their own. */
    private int openTicks;

    public ElevatorDoorBlockEntity(){
        super(MovingElevators.elevator_door_tile);
    }

    @Override
    public void update(){
        if(this.world == null || this.world.isRemote)
            return;

        ElevatorGroup group = this.getGroup();
        boolean cabinHere = group != null && group.isCabinAt(this.getFloorLevel());

        if(group != null){
            long openRequest = group.getDoorOpenRequest(this.getFloorLevel());
            if(openRequest > this.lastOpenRequest){
                this.lastOpenRequest = openRequest;
                // Only honour it if the cabin is here; otherwise the request is simply spent, and the
                // arrival will raise a fresh one.
                if(cabinHere)
                    this.openTicks = MovingElevatorsConfig.doorAutoCloseTicks.get();
            }
            long closeRequest = group.getDoorCloseRequest(this.getFloorLevel());
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

        boolean powered = this.world.isBlockPowered(this.pos);
        this.setOpen(powered || this.openTicks > 0);
    }

    private void setOpen(boolean open){
        IBlockState state = this.world.getBlockState(this.pos);
        if(state.getBlock() instanceof ElevatorDoorBlock && state.getValue(ElevatorDoorBlock.OPEN) != open)
            this.world.setBlockState(this.pos, state.withProperty(ElevatorDoorBlock.OPEN, open), 3);
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = super.writeData();
        compound.setInteger("openTicks", this.openTicks);
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        super.readData(compound);
        this.openTicks = compound.getInteger("openTicks");
    }
}
