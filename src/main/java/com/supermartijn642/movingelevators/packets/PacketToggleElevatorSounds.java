package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * The elevator screen's sound toggle.
 * <p>
 * Sounds belong to the elevator rather than to one controller -- an elevator that beeps at some
 * floors and not others would be odd -- so this lands on the group, like speed and cabin size.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketToggleElevatorSounds extends ElevatorGroupPacket {

    public PacketToggleElevatorSounds(BlockPos pos){
        super(pos);
    }

    public PacketToggleElevatorSounds(){
    }

    @Override
    protected void handle(ElevatorGroup group, ControllerBlockEntity blockEntity, EntityPlayer player){
        group.setSoundsEnabled(!group.areSoundsEnabled());
    }
}
