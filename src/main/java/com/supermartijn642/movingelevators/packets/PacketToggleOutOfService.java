package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * The controller screen's out-of-service switch.
 * <p>
 * Only ever moves between normal and out of service. Independent service is the third value of the
 * same setting but is set from inside the cabin, so this leaves it alone rather than cycling through
 * it -- a maintenance switch on the outside should not be able to quietly put a car on independent
 * service, which looks identical from a landing and behaves quite differently.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketToggleOutOfService extends ElevatorGroupPacket {

    public PacketToggleOutOfService(BlockPos pos){
        super(pos);
    }

    public PacketToggleOutOfService(){
    }

    @Override
    protected void handle(ElevatorGroup group, ControllerBlockEntity blockEntity, EntityPlayer player){
        group.setServiceMode(group.isOutOfService()
            ? ElevatorGroup.ServiceMode.NORMAL : ElevatorGroup.ServiceMode.OUT_OF_SERVICE);
    }
}
