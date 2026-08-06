package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * Created 4/3/2020 by SuperMartijn642
 */
public class PacketDecreaseCabinHeight extends ElevatorGroupPacket {

    public PacketDecreaseCabinHeight(BlockPos pos){
        super(pos);
    }

    public PacketDecreaseCabinHeight(){
    }

    @Override
    protected void handle(ElevatorGroup group, ControllerBlockEntity blockEntity, EntityPlayer player){
        group.decreaseCageHeight();
    }
}
