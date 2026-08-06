package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * A {@link ControllerPacket} that acts on the elevator rather than on the controller block, and so
 * needs the group to exist.
 * <p>
 * A controller has no group until its first tick, and loses it when the last floor goes, so this is
 * a real state and not merely a defensive nicety.
 * <p>
 * Created for the Mica Technologies fork.
 */
public abstract class ElevatorGroupPacket extends ControllerPacket {

    protected ElevatorGroupPacket(BlockPos pos){
        super(pos);
    }

    protected ElevatorGroupPacket(){
    }

    @Override
    protected final void handle(ControllerBlockEntity blockEntity, EntityPlayer player){
        ElevatorGroup group = blockEntity.getGroup();
        if(group == null)
            return;
        this.handle(group, blockEntity, player);
    }

    protected abstract void handle(ElevatorGroup group, ControllerBlockEntity blockEntity, EntityPlayer player);
}
