package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.BankLobbyPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

/**
 * A destination chosen on a lobby panel.
 * <p>
 * Carries only where the player wants to go, never which car should take them there: that choice
 * belongs to the server, both because the panel is the thing that knows what every car in the bank is
 * doing and because letting a client name the car would let it name any car.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketBankDestination extends BlockEntityBasePacket<BankLobbyPanelBlockEntity> {

    private int destinationY;

    public PacketBankDestination(BlockPos pos, int destinationY){
        super(pos);
        this.destinationY = destinationY;
    }

    public PacketBankDestination(){
    }

    @Override
    public void write(PacketBuffer buffer){
        super.write(buffer);
        buffer.writeInt(this.destinationY);
    }

    @Override
    public void read(PacketBuffer buffer){
        super.read(buffer);
        this.destinationY = buffer.readInt();
    }

    @Override
    protected void handle(BankLobbyPanelBlockEntity blockEntity, PacketContext context){
        // The panel is a block in the world, so a message about it should come from somebody
        // standing near it.
        if(!PacketReach.isInReach(context.getSendingPlayer(), blockEntity.getPos()))
            return;
        EntityPlayer player = context.getSendingPlayer();
        BankLobbyPanelBlockEntity.Dispatch dispatch = blockEntity.dispatch(this.destinationY, player);
        if(player == null)
            return;

        if(dispatch == null){
            // Every bound elevator was broken out, or none of them serves the floor asked for. Either
            // way nothing is coming, and silence would read as a car on its way.
            player.sendStatusMessage(TextComponents.translation("movingelevators.bank_lobby.no_car").color(TextFormatting.RED).get(), true);
            return;
        }

        // Where it is going, not where it is collecting from -- the passenger is standing at the
        // collection point and does not need telling. Listing every destination booked from this
        // landing is also what makes a shared car legible: two people are told the same pair of
        // floors and can see they are riding together rather than waiting for separate lifts.
        ITextComponent floors = describeFloors(dispatch.group, dispatch.destinations);
        String car = dispatch.group.getName();
        String key = "movingelevators.bank_lobby."
            + (dispatch.readyToBoard ? "ready" : "dispatched") + (car == null ? "" : "_named");
        player.sendStatusMessage(car == null
            ? TextComponents.translation(key, floors).get()
            : TextComponents.translation(key, TextComponents.string(car).color(TextFormatting.GOLD).get(), floors).get(), true);
    }

    /**
     * "Floor 3", or "Floor 3 and Floor 7", or "Floor 3, Floor 7 and Floor 9".
     * <p>
     * Built from two translated joiners rather than by pasting commas and the word "and" together,
     * since neither the punctuation nor the word order survives translation.
     */
    private static ITextComponent describeFloors(ElevatorGroup group, java.util.List<Integer> destinations){
        ITextComponent joined = null;
        for(int index = 0; index < destinations.size(); index++){
            ITextComponent floor = describeFloor(group, destinations.get(index));
            if(joined == null){
                joined = floor;
                continue;
            }
            // The last one is joined differently from the rest: "a, b" but "b and c".
            joined = TextComponents.translation(index == destinations.size() - 1
                ? "movingelevators.bank_lobby.floors_last" : "movingelevators.bank_lobby.floors_more",
                joined, floor).get();
        }
        return joined;
    }

    private static ITextComponent describeFloor(ElevatorGroup group, int yLevel){
        int floor = group.getFloorNumber(yLevel);
        String name = floor == -1 ? null : group.getFloorDisplayName(floor);
        return name != null && !name.isEmpty()
            ? TextComponents.string(name).color(TextFormatting.GOLD).get()
            : TextComponents.translation("movingelevators.floor_name", TextComponents.number(floor + 1).get()).color(TextFormatting.GOLD).get();
    }
}
