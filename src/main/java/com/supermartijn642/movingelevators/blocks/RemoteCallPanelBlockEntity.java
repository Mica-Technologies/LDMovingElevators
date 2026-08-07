package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Backing entity for {@link RemoteCallPanelBlock}. The floor readout and the landing this panel speaks
 * for come from the single binding in {@link RemoteBoundBlockEntity}; the call state itself lives on
 * the elevator group, so there is nothing of that to keep here.
 * <p>
 * A panel may serve several elevators. That is a set of extra bindings held beside the inherited one
 * rather than a replacement for it: the inherited binding stays the primary, so a panel bound to one
 * shaft is byte-for-byte and behaviour-for-behaviour what it was before this existed, and a panel
 * bound to a whole bank is the same thing with more entries.
 */
public class RemoteCallPanelBlockEntity extends RemoteBoundBlockEntity {

    /**
     * Written only when there are extras, so the saved and synced form of a single-binding panel is
     * unchanged and old panels load without a migration.
     */
    private static final String EXTRA_BINDINGS_KEY = "extraBindings";

    private final List<BankLobbyPanelBlockEntity.Binding> extraBindings = new ArrayList<>();

    public RemoteCallPanelBlockEntity(){
        super(MovingElevators.remote_call_panel_tile);
    }

    /**
     * Takes a whole set of bindings, the first of which becomes the primary. First rather than
     * nearest or lowest, because the primary decides the readout and the landing, and the order the
     * player clicked the controllers in is the only ordering they can predict.
     */
    public void setBindings(List<BankLobbyPanelBlockEntity.Binding> bindings){
        this.extraBindings.clear();
        if(bindings.isEmpty()){
            this.setValues(BlockPos.ORIGIN, null);
            return;
        }
        this.extraBindings.addAll(bindings.subList(1, bindings.size()));
        // setValues ends with dataChanged(), which also publishes the extras written above.
        this.setValues(bindings.get(0).pos, bindings.get(0).facing);
    }

    /** The primary binding first, then the extras, in the order they will be reported. */
    public List<BankLobbyPanelBlockEntity.Binding> getBindings(){
        List<BankLobbyPanelBlockEntity.Binding> bindings = new ArrayList<>();
        if(this.isBound())
            bindings.add(new BankLobbyPanelBlockEntity.Binding(this.getControllerPos(), this.getControllerFacing()));
        bindings.addAll(this.extraBindings);
        return bindings;
    }

    /**
     * Every elevator this panel can call, primary first.
     * <p>
     * With no extras this is the primary and nothing else, so a panel bound to one shaft takes the
     * same lookup it always did and pays nothing for the machinery around it.
     */
    public List<ElevatorGroup> getGroups(){
        List<ElevatorGroup> groups = new ArrayList<>();
        // Through getGroup() rather than the shared resolver, because the primary is the binding that
        // has to keep working while the panel rides inside a cabin, where the group comes from the
        // surrounding fake level instead of the capability.
        ElevatorGroup primary = this.getGroup();
        if(primary != null)
            groups.add(primary);
        if(this.extraBindings.isEmpty())
            return groups;
        for(ElevatorGroup group : BankLobbyPanelBlockEntity.resolveGroups(this.world, this.extraBindings))
            if(!groups.contains(group))
                groups.add(group);
        return groups;
    }

    /**
     * Which elevator is answering a call from this landing, or null when none is.
     * <p>
     * The call arrows report the landing, not one shaft. Once several elevators can answer here only
     * one of them takes each call, so lighting the arrow from the primary binding alone would leave a
     * pressed button dark whenever a sibling was the car dispatched.
     */
    public ElevatorGroup getRespondingGroup(boolean up){
        for(ElevatorGroup group : this.getGroups()){
            int landing = nearestFloorY(group, this.getFloorLevel());
            if(landing != Integer.MIN_VALUE && group.hasHallCall(landing, up))
                return group;
        }
        return null;
    }

    /**
     * Reports what this panel is linked to, in the same words the bank lobby panel uses -- a player
     * should only have to learn this readout once.
     */
    public void reportStatus(EntityPlayer player){
        BankLobbyPanelBlockEntity.reportStatus(player, this.world, "movingelevators.remote_call_panel.status.header",
            this.getLandingLabel(), this.getBindings());
    }

    /** The primary's name for this landing, falling back to its bare height when it has none. */
    private String getLandingLabel(){
        ElevatorGroup primary = this.getGroup();
        int floor = primary == null ? -1 : primary.getFloorNumber(this.getFloorLevel());
        String name = floor == -1 ? null : primary.getFloorDisplayName(floor);
        return name == null || name.isEmpty() ? Integer.toString(this.getFloorLevel()) : name;
    }

    /**
     * The floor of an elevator nearest this panel, so a shaft that does not stop at the primary
     * binding's exact height is still asked about the landing it does serve here.
     * <p>
     * Duplicated from ElevatorBank, whose copy is private and whose file is not ours to widen.
     */
    private static int nearestFloorY(ElevatorGroup group, int panelY){
        int best = Integer.MIN_VALUE, bestDistance = Integer.MAX_VALUE;
        for(int floor = 0; floor < group.getFloorCount(); floor++){
            int y = group.getFloorYLevel(floor);
            int distance = Math.abs(y - panelY);
            if(distance < bestDistance){
                bestDistance = distance;
                best = y;
            }
        }
        return best;
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = super.writeData();
        if(!this.extraBindings.isEmpty())
            compound.setTag(EXTRA_BINDINGS_KEY, BankLobbyPanelBlockEntity.writeBindings(this.extraBindings));
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        super.readData(compound);
        this.extraBindings.clear();
        if(compound.hasKey(EXTRA_BINDINGS_KEY, Constants.NBT.TAG_LIST))
            this.extraBindings.addAll(BankLobbyPanelBlockEntity.readBindings(compound.getTagList(EXTRA_BINDINGS_KEY, Constants.NBT.TAG_COMPOUND)));
    }
}
