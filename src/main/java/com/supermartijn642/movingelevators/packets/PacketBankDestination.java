package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.BankLobbyPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorBank;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
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
        ElevatorBank.Assignment assignment = blockEntity.dispatch(this.destinationY, player);
        if(player == null)
            return;

        if(assignment == null){
            // Every bound elevator was broken out, or none of them serves the floor asked for. Either
            // way nothing is coming, and silence would read as a car on its way.
            player.sendStatusMessage(TextComponents.translation("movingelevators.bank_lobby.no_car").color(TextFormatting.RED).get(), true);
            return;
        }

        // The pickup floor as a plain y level: floor names are resolved for display on the client, and
        // this runs on the server, where that formatting is not available.
        player.sendStatusMessage(TextComponents.translation("movingelevators.bank_lobby.dispatched",
            TextComponents.number(assignment.pickupY).get()).get(), true);
    }
}
