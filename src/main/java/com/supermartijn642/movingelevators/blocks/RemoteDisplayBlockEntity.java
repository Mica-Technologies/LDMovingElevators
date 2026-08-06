package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.elevator.ElevatorCabinLevel;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

/**
 * A read-only counterpart to {@link RemoteControllerBlockEntity}: it binds to an elevator controller
 * the same way, but only reports which floor the cabin is at.
 * <p>
 * Deliberately extends {@link CamoBlockEntity} rather than {@link ElevatorInputBlockEntity}. That
 * base ticks redstone and forwards button presses to the group, neither of which an indicator should
 * do -- and extending it would make a redstone pulse next to a sign call the elevator. Camouflage
 * comes for free from {@link CamoBlockEntity}.
 * <p>
 * It also does not tick at all. Everything it shows is derived from the elevator group on demand, so
 * the renderer can pull the current floor when it draws instead of the block entity polling for it.
 *
 * @see RemoteControllerBlockEntity for the input-capable equivalent
 */
public class RemoteDisplayBlockEntity extends CamoBlockEntity {

    private EnumFacing facing = EnumFacing.NORTH;
    private BlockPos controllerPos = BlockPos.ORIGIN;
    private EnumFacing controllerFacing = null;

    public RemoteDisplayBlockEntity(){
        super(MovingElevators.remote_display_tile);
    }

    public void setValues(EnumFacing facing, BlockPos controllerPos, EnumFacing controllerFacing){
        this.facing = facing;
        this.controllerPos = controllerPos;
        this.controllerFacing = controllerFacing;
        this.dataChanged();
    }

    /**
     * @return the side this display faces, i.e. the one the floor label is drawn on
     */
    public EnumFacing getFacing(){
        return this.facing;
    }

    public BlockPos getControllerPos(){
        return this.controllerPos;
    }

    public boolean isBound(){
        return this.controllerFacing != null;
    }

    /**
     * Resolves the bound elevator group. Works on both sides: the client keeps its own copy of the
     * capability, synced by the group packets.
     *
     * @return the group, or {@code null} when unbound, out of dimension, or its controller is gone
     */
    public ElevatorGroup getGroup(){
        if(this.world == null || this.controllerPos == null || this.controllerFacing == null)
            return null;
        ElevatorGroup group;
        if(this.world instanceof ElevatorCabinLevel)
            // The display is riding inside the cabin, where the surrounding fake level knows its group.
            group = ((ElevatorCabinLevel)this.world).getElevatorGroup();
        else{
            ElevatorGroupCapability capability = ElevatorGroupCapability.get(this.world);
            group = capability == null ? null : capability.get(this.controllerPos.getX(), this.controllerPos.getZ(), this.controllerFacing);
        }
        return group != null && group.hasControllerAt(this.controllerPos.getY()) ? group : null;
    }

    public boolean hasGroup(){
        return this.getGroup() != null;
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = super.writeData();
        compound.setInteger("facing", this.facing.getIndex());
        compound.setInteger("controllerX", this.controllerPos.getX());
        compound.setInteger("controllerY", this.controllerPos.getY());
        compound.setInteger("controllerZ", this.controllerPos.getZ());
        if(this.controllerFacing != null)
            compound.setInteger("controllerFacing", this.controllerFacing.getHorizontalIndex());
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        super.readData(compound);
        this.facing = EnumFacing.getFront(compound.getInteger("facing"));
        this.controllerPos = new BlockPos(compound.getInteger("controllerX"), compound.getInteger("controllerY"), compound.getInteger("controllerZ"));
        this.controllerFacing = compound.hasKey("controllerFacing", Constants.NBT.TAG_INT) ? EnumFacing.getHorizontal(compound.getInteger("controllerFacing")) : null;
    }
}
