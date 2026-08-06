package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.BaseBlockEntity;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.elevator.ElevatorCabinLevel;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

/**
 * Backing entity for the slim wall-mounted {@link RemoteIndicatorBlock}.
 * <p>
 * Same binding as {@link RemoteDisplayBlockEntity} -- bind to a controller, report its floor -- but
 * with no camouflage, so it extends {@link BaseBlockEntity} directly. The facing lives in the block
 * state rather than here, because unlike the full-cube displays this block is genuinely directional:
 * the plate is only on one side.
 */
public class RemoteIndicatorBlockEntity extends BaseBlockEntity {

    private BlockPos controllerPos = BlockPos.ORIGIN;
    private EnumFacing controllerFacing = null;

    public RemoteIndicatorBlockEntity(){
        super(MovingElevators.remote_indicator_tile);
    }

    public void setValues(BlockPos controllerPos, EnumFacing controllerFacing){
        this.controllerPos = controllerPos;
        this.controllerFacing = controllerFacing;
        this.dataChanged();
    }

    /**
     * @return the side the plate is mounted on, read from the block state
     */
    public EnumFacing getFacing(){
        if(this.world == null)
            return EnumFacing.NORTH;
        net.minecraft.block.state.IBlockState state = this.world.getBlockState(this.pos);
        return state.getBlock() instanceof RemoteIndicatorBlock
            ? state.getValue(RemoteIndicatorBlock.FACING)
            : EnumFacing.NORTH;
    }

    public BlockPos getControllerPos(){
        return this.controllerPos;
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
            // The indicator is riding inside the cabin, where the surrounding fake level knows its group.
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
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger("controllerX", this.controllerPos.getX());
        compound.setInteger("controllerY", this.controllerPos.getY());
        compound.setInteger("controllerZ", this.controllerPos.getZ());
        if(this.controllerFacing != null)
            compound.setInteger("controllerFacing", this.controllerFacing.getHorizontalIndex());
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        this.controllerPos = new BlockPos(compound.getInteger("controllerX"), compound.getInteger("controllerY"), compound.getInteger("controllerZ"));
        this.controllerFacing = compound.hasKey("controllerFacing", Constants.NBT.TAG_INT) ? EnumFacing.getHorizontal(compound.getInteger("controllerFacing")) : null;
    }
}
