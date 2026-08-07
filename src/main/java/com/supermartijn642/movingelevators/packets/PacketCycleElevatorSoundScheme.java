package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorSoundScheme;
import net.minecraft.entity.player.EntityPlayer;
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
public class PacketCycleElevatorSoundScheme extends ElevatorGroupPacket {

    public PacketCycleElevatorSoundScheme(BlockPos pos){
        super(pos);
    }

    public PacketCycleElevatorSoundScheme(){
    }

    @Override
    protected void handle(ElevatorGroup group, ControllerBlockEntity blockEntity, EntityPlayer player){
        // Off is the first value of one setting rather than a switch beside it: "what does this
        // elevator sound like" has three answers, and two controls for one question cost a row the
        // screen did not have.
        if(!group.areSoundsEnabled()){
            group.setSoundsEnabled(true);
            group.setSoundScheme(ElevatorSoundScheme.STANDARD);
            return;
        }
        ElevatorSoundScheme next = group.getSoundScheme().next();
        if(next == ElevatorSoundScheme.STANDARD)
            group.setSoundsEnabled(false);
        else
            group.setSoundScheme(next);
    }
}
