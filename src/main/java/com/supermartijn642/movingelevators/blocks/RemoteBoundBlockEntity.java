package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.BaseBlockEntity;
import com.supermartijn642.core.block.BaseBlockEntityType;
import com.supermartijn642.movingelevators.elevator.ElevatorCabinLevel;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

/**
 * Shared plumbing for the wall panels: remember which elevator controller this fixture was bound to,
 * and resolve that back into a live {@link ElevatorGroup}.
 * <p>
 * Separate from {@link RemoteControllerBlockEntity}, which carries redstone and button handling that
 * a wall fixture does not want, and from {@link CamoBlockEntity}, because these panels are too thin
 * to camouflage.
 * <p>
 * Created for the Mica Technologies fork.
 */
public abstract class RemoteBoundBlockEntity extends BaseBlockEntity {

    protected BlockPos controllerPos = BlockPos.ORIGIN;
    private boolean registeredComparator;
    private EnumFacing controllerFacing = null;

    protected RemoteBoundBlockEntity(BaseBlockEntityType<?> blockEntityType){
        super(blockEntityType);
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
        IBlockState state = this.world.getBlockState(this.pos);
        return state.getBlock() instanceof WallPanelBlock ? state.getValue(WallPanelBlock.FACING) : EnumFacing.NORTH;
    }

    public BlockPos getControllerPos(){
        return this.controllerPos;
    }

    /**
     * @return the side the bound controller faces, which together with its column is what identifies
     * an elevator; null when unbound
     */
    public EnumFacing getControllerFacing(){
        return this.controllerFacing;
    }

    /**
     * The floor this fixture speaks for: the one its bound controller is on. A panel hung anywhere in
     * the shaft still calls and reports for that landing, which is how the existing remote panel
     * behaves.
     */
    public int getFloorLevel(){
        return this.controllerPos.getY();
    }

    /**
     * @return whether this fixture has been bound to a controller at all
     */
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
            // The panel is riding inside the cabin, where the surrounding fake level knows its group.
            group = ((ElevatorCabinLevel)this.world).getElevatorGroup();
        else{
            ElevatorGroupCapability capability = ElevatorGroupCapability.get(this.world);
            group = capability == null ? null : capability.get(this.controllerPos.getX(), this.controllerPos.getZ(), this.controllerFacing);
        }
        if(group == null || !group.hasControllerAt(this.controllerPos.getY()))
            return null;
        // Registered the first time anything asks, which is the first time a comparator reads this
        // block. These panels do not tick, so there is no other moment at which to do it, and by the
        // time something wants the value the elevator certainly exists -- which it may well not have
        // when the panel itself loaded.
        if(!this.registeredComparator && this.world != null && !this.world.isRemote){
            this.registeredComparator = true;
            group.addComparatorListener(this.getFloorLevel(), this.pos);
        }
        return group;
    }

    @Override
    public void invalidate(){
        super.invalidate();
        if(this.registeredComparator && this.world != null && !this.world.isRemote){
            ElevatorGroup group = this.getGroup();
            if(group != null)
                group.removeComparatorListener(this.pos);
        }
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
