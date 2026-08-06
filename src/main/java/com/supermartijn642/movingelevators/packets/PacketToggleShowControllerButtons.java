package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * Created 4/3/2020 by SuperMartijn642
 */
public class PacketToggleShowControllerButtons extends ControllerPacket {

    public PacketToggleShowControllerButtons(BlockPos pos){
        super(pos);
    }

    public PacketToggleShowControllerButtons(){
    }

    @Override
    protected void handle(ControllerBlockEntity elevatorEntity, EntityPlayer player){
        elevatorEntity.toggleShowButtons();
    }
}
