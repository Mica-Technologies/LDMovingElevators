package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.BaseBlockEntity;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.elevator.ElevatorBank;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A destination-dispatch panel: bound to several elevators at once, it takes a floor and picks which
 * of them will collect you.
 * <p>
 * Cannot extend {@link RemoteBoundBlockEntity}, which is built around exactly one controller. That is
 * the difference that matters here -- every other fixture speaks for one elevator, and this one exists
 * precisely because it speaks for a group of them.
 * <p>
 * The elevators do not know they are in a bank. Binding lives entirely on this side, so an elevator in
 * a bank is an ordinary elevator that happens to also receive calls from here, and pulling the panel
 * out leaves nothing to clean up.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankLobbyPanelBlockEntity extends BaseBlockEntity {

    /** One bound elevator: the controller that identifies it, and the side that controller faces. */
    public static final class Binding {

        public final BlockPos pos;
        public final EnumFacing facing;

        public Binding(BlockPos pos, EnumFacing facing){
            this.pos = pos;
            this.facing = facing;
        }

        public boolean matches(BlockPos pos, EnumFacing facing){
            return this.pos.equals(pos) && this.facing == facing;
        }
    }

    private final List<Binding> bindings = new ArrayList<>();

    public BankLobbyPanelBlockEntity(){
        super(MovingElevators.bank_lobby_panel_tile);
    }

    /**
     * @return whether the binding was new. Clicking a controller already in the bank removes it
     * instead, so the same gesture both adds and takes away.
     */
    public boolean toggleBinding(BlockPos controllerPos, EnumFacing controllerFacing){
        for(int i = 0; i < this.bindings.size(); i++){
            if(this.bindings.get(i).matches(controllerPos, controllerFacing)){
                this.bindings.remove(i);
                this.dataChanged();
                return false;
            }
        }
        this.bindings.add(new Binding(controllerPos, controllerFacing));
        this.dataChanged();
        return true;
    }

    public void setBindings(List<Binding> bindings){
        this.bindings.clear();
        this.bindings.addAll(bindings);
        this.dataChanged();
    }

    public List<Binding> getBindings(){
        return new ArrayList<>(this.bindings);
    }

    public int getBoundCount(){
        return this.bindings.size();
    }

    public boolean isBound(){
        return !this.bindings.isEmpty();
    }

    public EnumFacing getFacing(){
        if(this.world == null)
            return EnumFacing.NORTH;
        IBlockState state = this.world.getBlockState(this.pos);
        return state.getBlock() instanceof WallPanelBlock ? state.getValue(WallPanelBlock.FACING) : EnumFacing.NORTH;
    }

    /**
     * The elevators currently reachable. Bindings whose controller has been broken resolve to nothing
     * and are simply skipped, so a bank degrades to the cars that are still there rather than breaking.
     */
    public List<ElevatorGroup> getGroups(){
        List<ElevatorGroup> groups = new ArrayList<>();
        if(this.world == null)
            return groups;
        ElevatorGroupCapability capability = ElevatorGroupCapability.get(this.world);
        if(capability == null)
            return groups;
        for(Binding binding : this.bindings){
            if(binding.facing == null)
                continue;
            ElevatorGroup group = capability.get(binding.pos.getX(), binding.pos.getZ(), binding.facing);
            if(group != null && group.hasControllerAt(binding.pos.getY()) && !groups.contains(group))
                groups.add(group);
        }
        return groups;
    }

    /**
     * Every floor the bank can reach, as y levels, low to high.
     * <p>
     * The union rather than any one elevator's list: cars in a bank usually serve the same floors, but
     * nothing enforces that, and a floor only one of them reaches is still a floor you can get to.
     * Dispatch filters to the cars that actually serve the floor you picked.
     */
    public List<Integer> getBankFloors(){
        TreeSet<Integer> floors = new TreeSet<>();
        for(ElevatorGroup group : this.getGroups())
            for(int floor = 0; floor < group.getFloorCount(); floor++)
                floors.add(group.getFloorYLevel(floor));
        return new ArrayList<>(floors);
    }

    /**
     * The name for a floor, taken from the first bound elevator that serves it. Null when none names
     * it, which the screen renders as a plain number.
     */
    public String getFloorName(int yLevel){
        for(ElevatorGroup group : this.getGroups()){
            int floor = group.getFloorNumber(yLevel);
            if(floor != -1)
                return group.getFloorDisplayName(floor);
        }
        return null;
    }

    /**
     * Whether every elevator in this bank is out of service, so no car can answer at all.
     * <p>
     * All of them rather than any of them, deliberately. Dispatch already skips a halted car
     * silently, and a lobby flashing an emergency because one of four shafts has somebody in it
     * would tell a waiting passenger to give up when three cars are still running. The signal is
     * only worth showing when it is the whole answer.
     */
    public boolean isBankOutOfService(){
        List<ElevatorGroup> groups = this.getGroups();
        if(groups.isEmpty())
            return false;
        for(ElevatorGroup group : groups)
            if(!group.isEmergencyStopped())
                return false;
        return true;
    }

    /** Any bound elevator, purely so the readout can share the bank's flash beat. */
    public ElevatorGroup getAnyGroup(){
        List<ElevatorGroup> groups = this.getGroups();
        return groups.isEmpty() ? null : groups.get(0);
    }

    /** The landing this panel stands on. */
    public int getPanelY(){
        return this.pos.getY();
    }

    /**
     * Sends a car. Server side.
     *
     * @return the assignment made, or null when no bound elevator can make the trip
     */
    public ElevatorBank.Assignment dispatch(int destinationY, EntityPlayer requester){
        ElevatorBank.Assignment assignment = ElevatorBank.pick(this.getGroups(), this.getPanelY(), destinationY);
        if(assignment != null)
            assignment.group.onBankedCall(assignment.pickupY, destinationY, requester);
        return assignment;
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("bindings", writeBindings(this.bindings));
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        this.bindings.clear();
        this.bindings.addAll(readBindings(compound.getTagList("bindings", Constants.NBT.TAG_COMPOUND)));
    }

    /** Shared with the block item, which carries the same list around before the panel is placed. */
    public static NBTTagList writeBindings(List<Binding> bindings){
        NBTTagList list = new NBTTagList();
        for(Binding binding : bindings){
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", binding.pos.getX());
            tag.setInteger("y", binding.pos.getY());
            tag.setInteger("z", binding.pos.getZ());
            if(binding.facing != null)
                tag.setInteger("facing", binding.facing.getHorizontalIndex());
            list.appendTag(tag);
        }
        return list;
    }

    public static List<Binding> readBindings(NBTTagList list){
        List<Binding> bindings = new ArrayList<>();
        for(int i = 0; i < list.tagCount(); i++){
            NBTTagCompound tag = list.getCompoundTagAt(i);
            bindings.add(new Binding(
                new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")),
                tag.hasKey("facing", Constants.NBT.TAG_INT) ? EnumFacing.getHorizontal(tag.getInteger("facing")) : null));
        }
        return bindings;
    }
}
