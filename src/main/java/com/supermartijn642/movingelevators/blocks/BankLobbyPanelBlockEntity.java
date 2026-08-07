package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.BaseBlockEntity;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.elevator.ElevatorBank;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
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

        /**
         * Whether this binding is to the same elevator as the given controller, regardless of which
         * floor that controller is on.
         * <p>
         * An elevator is identified by its column and the way its controllers face -- the floor is
         * not part of it. Two controllers in one shaft are therefore the same elevator, and binding
         * both produced a panel that claimed two elevators and dispatched to one.
         */
        public boolean sameShaft(BlockPos pos, EnumFacing facing){
            return this.pos.getX() == pos.getX() && this.pos.getZ() == pos.getZ() && this.facing == facing;
        }
    }

    private final List<Binding> bindings = new ArrayList<>();

    public BankLobbyPanelBlockEntity(){
        super(MovingElevators.bank_lobby_panel_tile);
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

    /**
     * How two elevators of a bank disagree about a floor, or null when they are consistent.
     * <p>
     * Shafts in a bank are allowed to serve different floors -- an express car skipping the lower
     * half of a building is a real arrangement, not a mistake. What they may not do is disagree about
     * a floor they both have. Two rules catch that: a height they both stop at must have the same
     * name, and a name they both use must be at the same height.
     * <p>
     * Only floors somebody has actually named are compared. Unnamed ones fall back to their position
     * in their own shaft's list, so a car that skips floors numbers everything below differently
     * through no fault of the builder -- comparing those would report a mismatch on every express
     * elevator ever built.
     */
    public static String describeMisalignment(ElevatorGroup a, ElevatorGroup b){
        Map<Integer,String> named = namedFloors(a), other = namedFloors(b);
        for(Map.Entry<Integer,String> entry : named.entrySet()){
            String rival = other.get(entry.getKey());
            if(rival != null && !rival.equals(entry.getValue()))
                return "y " + entry.getKey() + " is \"" + entry.getValue() + "\" on one and \"" + rival + "\" on the other";
        }
        for(Map.Entry<Integer,String> entry : named.entrySet())
            for(Map.Entry<Integer,String> rival : other.entrySet())
                if(entry.getValue().equals(rival.getValue()) && !entry.getKey().equals(rival.getKey()))
                    return "\"" + entry.getValue() + "\" is at y " + entry.getKey() + " on one and y " + rival.getKey() + " on the other";
        return null;
    }

    private static Map<Integer,String> namedFloors(ElevatorGroup group){
        Map<Integer,String> floors = new HashMap<>();
        for(int floor = 0; floor < group.getFloorCount(); floor++){
            String name = group.getFloorDisplayName(floor);
            if(name != null && !name.isEmpty())
                floors.put(group.getFloorYLevel(floor), name);
        }
        return floors;
    }

    /**
     * Prints what this panel is actually linked to.
     * <p>
     * Binding is the one part of this block a player cannot see. Everything else about an elevator is
     * visible in the world; a bank is a list held in a block, and getting it wrong looks exactly like
     * getting it right until a car fails to turn up. Rather than infer from behaviour, ask.
     */
    public void reportStatus(EntityPlayer player){
        String landing = this.getFloorName(this.getLandingY());
        player.sendMessage(TextComponents.translation("movingelevators.bank_lobby_panel.status.header",
            TextComponents.string(landing == null || landing.isEmpty() ? Integer.toString(this.getLandingY()) : landing)
                .color(TextFormatting.GOLD).get()).color(TextFormatting.YELLOW).get());

        List<Binding> bindings = this.getBindings();
        if(bindings.isEmpty()){
            player.sendMessage(TextComponents.translation("movingelevators.bank_lobby_panel.status.none").color(TextFormatting.GRAY).get());
            return;
        }

        ElevatorGroupCapability capability = ElevatorGroupCapability.get(this.world);
        ElevatorGroup first = null;
        boolean aligned = true, anyResolved = false;
        int index = 0;
        for(Binding binding : bindings){
            index++;
            ElevatorGroup group = binding.facing == null || capability == null ? null
                : capability.get(binding.pos.getX(), binding.pos.getZ(), binding.facing);
            String where = binding.pos.getX() + ", " + binding.pos.getZ()
                + (binding.facing == null ? "" : " facing " + binding.facing.getName());
            if(group == null){
                player.sendMessage(TextComponents.translation("movingelevators.bank_lobby_panel.status.missing",
                    TextComponents.number(index).get(), TextComponents.string(where).get()).color(TextFormatting.RED).get());
                continue;
            }
            anyResolved = true;
            Set<Integer> floors = new TreeSet<>();
            for(int floor = 0; floor < group.getFloorCount(); floor++)
                floors.add(group.getFloorYLevel(floor));
            if(first == null)
                first = group;
            else if(aligned && describeMisalignment(first, group) != null)
                aligned = false;
            player.sendMessage(TextComponents.translation("movingelevators.bank_lobby_panel.status.elevator",
                TextComponents.number(index).get(), TextComponents.string(where).color(TextFormatting.GOLD).get(),
                TextComponents.number(floors.size()).color(TextFormatting.GOLD).get()).color(TextFormatting.GRAY).get());
        }

        if(!anyResolved)
            return;
        player.sendMessage(TextComponents.translation(aligned
            ? "movingelevators.bank_lobby_panel.status.aligned"
            : "movingelevators.bank_lobby_panel.status.mismatch")
            .color(aligned ? TextFormatting.GREEN : TextFormatting.RED).get());
    }

    /**
     * The bank floor this panel speaks for: the one nearest its own height.
     * <p>
     * Nearest rather than exact, because a panel is hung at eye level and a controller sits at floor
     * level, so demanding they share a y meant a panel mounted where anybody would actually mount one
     * matched no floor at all. Dispatch already worked this way; the face did not, so the screen went
     * dark on a panel that was dispatching perfectly well.
     *
     * @return the y level of the nearest bank floor, or the panel's own y when nothing is linked
     */
    public int getLandingY(){
        int best = this.getPanelY(), bestDistance = Integer.MAX_VALUE;
        for(int y : this.getBankFloors()){
            int distance = Math.abs(y - this.getPanelY());
            if(distance < bestDistance){
                bestDistance = distance;
                best = y;
            }
        }
        return best;
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
