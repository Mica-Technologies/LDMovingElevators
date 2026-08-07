package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.util.math.BlockPos;

/**
 * The car panel's emergency stop.
 * <p>
 * Deliberately not a toggle. An emergency stop that a passenger can also cancel is a switch, and the
 * elevator already decides when it is safe to go back into service -- it waits out its hold and then
 * checks the shaft is clear. Pressing this again while it is stopped does nothing, which is the
 * honest behaviour: the button's job is done.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketEmergencyStop extends BlockEntityBasePacket<ElevatorCarPanelBlockEntity> {

    public PacketEmergencyStop(BlockPos pos){
        super(pos);
    }

    public PacketEmergencyStop(){
    }

    @Override
    protected void handle(ElevatorCarPanelBlockEntity blockEntity, PacketContext context){
        // The panel is a block in the world, so a message about it should come from somebody
        // standing near it.
        if(!PacketReach.isInReach(context.getSendingPlayer(), blockEntity.getPos()))
            return;
        ElevatorGroup group = blockEntity.getGroup();
        if(group != null)
            group.requestEmergencyStop();
    }
}
