package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.util.math.BlockPos;

/**
 * The car panel's cabin music switch.
 * <p>
 * On the car panel rather than the controller's screen for the obvious reason: it is switched by
 * whoever is listening to it, from inside the thing playing it. It is also its own setting rather
 * than part of the sound scheme, because someone who wants the chimes and not the music has a
 * perfectly ordinary preference.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketToggleCabinMusic extends BlockEntityBasePacket<ElevatorCarPanelBlockEntity> {

    public PacketToggleCabinMusic(BlockPos pos){
        super(pos);
    }

    public PacketToggleCabinMusic(){
    }

    @Override
    protected void handle(ElevatorCarPanelBlockEntity blockEntity, PacketContext context){
        if(!PacketReach.isInReach(context.getSendingPlayer(), blockEntity.getPos()))
            return;
        ElevatorGroup group = blockEntity.getGroup();
        if(group != null)
            group.setCabinMusicEnabled(!group.isCabinMusicEnabled());
    }
}
