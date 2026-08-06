package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.util.math.BlockPos;

/**
 * "The alarm button is still held down."
 * <p>
 * Deliberately not a start/stop pair. The client repeats this while the button is down and the
 * elevator stops ringing once they run out, so a lost stop message -- a disconnect, a closed screen,
 * a player dying with the panel open -- cannot leave an alarm ringing that nobody can switch off.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketRingAlarm extends BlockEntityBasePacket<ElevatorCarPanelBlockEntity> {

    public PacketRingAlarm(BlockPos pos){
        super(pos);
    }

    public PacketRingAlarm(){
    }

    @Override
    protected void handle(ElevatorCarPanelBlockEntity blockEntity, PacketContext context){
        ElevatorGroup group = blockEntity.getGroup();
        if(group != null)
            group.ringAlarm();
    }
}
