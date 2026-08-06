package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.util.math.BlockPos;

/**
 * The elevator screen's sound scheme button, which steps to the next scheme.
 * <p>
 * Sends no scheme of its own: the server steps from whatever it currently has, so two players with
 * the screen open cannot fight over it, and a client that is a scheme behind cannot set one that no
 * longer exists.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketCycleElevatorSoundScheme extends BlockEntityBasePacket<ControllerBlockEntity> {

    public PacketCycleElevatorSoundScheme(BlockPos pos){
        super(pos);
    }

    public PacketCycleElevatorSoundScheme(){
    }

    @Override
    protected void handle(ControllerBlockEntity blockEntity, PacketContext context){
        ElevatorGroup group = blockEntity.getGroup();
        if(group != null)
            group.setSoundScheme(group.getSoundScheme().next());
    }
}
