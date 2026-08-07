package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

/**
 * Created 3/29/2020 by SuperMartijn642
 */
public class ControllerBlockEntity extends ElevatorInputBlockEntity {

    private boolean initialized = false;
    private EnumFacing facing;
    private String name;
    private EnumDyeColor color = EnumDyeColor.GRAY;
    private boolean showButtons = true;
    /**
     * A fire alarm panel carried over from the item this controller was placed from, held until the
     * elevator exists to be told about it. Controllers join their group on their first tick, so there
     * is nothing to pair to at the moment of placement.
     */
    private BlockPos pendingAlarmPanel;

    public ControllerBlockEntity(){
        super(MovingElevators.elevator_tile);
    }

    @Override
    public void update(){
        super.update();
        if(!this.initialized){
            ElevatorGroupCapability.get(this.world).add(this);
            this.getGroup().updateFloorData(this, this.name, this.color);
            if(this.pendingAlarmPanel != null){
                // This controller's own floor is the recall floor: it is the one the builder was
                // standing at when they placed it.
                this.getGroup().setAlarmPanel(this.pendingAlarmPanel, this.getFloorLevel());
                this.pendingAlarmPanel = null;
                this.dataChanged();
            }
            this.initialized = true;
        }
    }

    @Override
    public EnumFacing getFacing(){
        if(this.facing == null)
            this.facing = this.world.getBlockState(this.pos).getValue(ControllerBlock.FACING);
        return this.facing;
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = super.writeData();
        compound.setBoolean("hasName", this.name != null);
        if(this.name != null)
            compound.setString("name", this.name);
        compound.setInteger("color", this.color.getMetadata());
        compound.setBoolean("showButtons", this.showButtons);
        compound.setBoolean("hasPendingAlarmPanel", this.pendingAlarmPanel != null);
        if(this.pendingAlarmPanel != null)
            compound.setLong("pendingAlarmPanel", this.pendingAlarmPanel.toLong());
        if(this.facing != null)
            compound.setInteger("facing", this.facing.getHorizontalIndex());
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        super.readData(compound);
        if(compound.hasKey("hasName", Constants.NBT.TAG_BYTE))
            this.name = compound.getBoolean("hasName") ? compound.getString("name") : null;
        else if(compound.hasKey("name", Constants.NBT.TAG_STRING)){ // For older versions
            this.name = compound.getString("name");
        }else
            this.name = null;
        this.color = EnumDyeColor.byMetadata(compound.getInteger("color"));
        this.showButtons = !compound.hasKey("showButtons", Constants.NBT.TAG_BYTE) || compound.getBoolean("showButtons");
        this.pendingAlarmPanel = compound.getBoolean("hasPendingAlarmPanel") ? BlockPos.fromLong(compound.getLong("pendingAlarmPanel")) : null;
        this.facing = compound.hasKey("facing", Constants.NBT.TAG_INT) ? EnumFacing.getHorizontal(compound.getInteger("facing")) : null;
    }

    public void setPendingAlarmPanel(BlockPos panelPos){
        this.pendingAlarmPanel = panelPos;
        this.dataChanged();
    }

    public void onRemove(){
        if(!this.world.isRemote)
            ElevatorGroupCapability.get(this.world).remove(this);
    }

    @Override
    public String getFloorName(){
        return this.name;
    }

    public void setFloorName(String name){
        this.name = name;
        this.dataChanged();
        if(this.hasGroup())
            this.getGroup().updateFloorData(this, this.name, this.color);
    }

    public void setDisplayLabelColor(EnumDyeColor color){
        this.color = color;
        this.dataChanged();
        if(this.hasGroup())
            this.getGroup().updateFloorData(this, this.name, this.color);
    }

    @Override
    public EnumDyeColor getDisplayLabelColor(){
        return this.color;
    }

    public boolean shouldShowButtons(){
        return this.showButtons;
    }

    /**
     * Kept as the inverse of the existing "showButtons" flag rather than a new one, so elevators
     * built before this option existed keep their setting instead of silently changing.
     */
    @Override
    public boolean areControlsHidden(){
        return !this.showButtons;
    }

    public void toggleShowButtons(){
        this.showButtons = !this.showButtons;
        this.dataChanged();
    }

    @Override
    public ElevatorGroup getGroup(){
        return ElevatorGroupCapability.get(this.world).getGroup(this);
    }

    @Override
    public boolean hasGroup(){
        return this.initialized && ElevatorGroupCapability.get(this.world).getGroup(this) != null;
    }

    @Override
    public int getFloorLevel(){
        return this.pos.getY();
    }
}
