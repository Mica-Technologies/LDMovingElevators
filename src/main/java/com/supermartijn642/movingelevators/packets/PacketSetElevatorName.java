package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.gui.ElevatorScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

/**
 * Names the elevator itself, as opposed to {@link PacketSetFloorName}, which names the single floor
 * the controller stands on.
 * <p>
 * An {@link ElevatorGroupPacket} rather than a plain {@link ControllerPacket}, because the name lives
 * on the group: it is the identity a bank lobby panel announces and the car panel displays, so every
 * controller in the shaft has to agree on it.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketSetElevatorName extends ElevatorGroupPacket {

    public String name;

    public PacketSetElevatorName(BlockPos pos, String name){
        super(pos);
        this.name = name;
    }

    public PacketSetElevatorName(){
    }

    @Override
    public void write(PacketBuffer buffer){
        super.write(buffer);
        buffer.writeBoolean(this.name == null);
        if(this.name != null)
            buffer.writeString(this.name);
    }

    @Override
    public void read(PacketBuffer buffer){
        super.read(buffer);
        this.name = buffer.readBoolean() ? null : buffer.readString(32767);
    }

    @Override
    public boolean verify(PacketContext context){
        return this.name == null || this.name.length() <= ElevatorScreen.MAX_NAME_LENGTH;
    }

    @Override
    protected void handle(ElevatorGroup group, ControllerBlockEntity blockEntity, EntityPlayer player){
        // Passed straight through rather than blanked here the way PacketSetFloorName does it:
        // ElevatorGroup#setName already trims and turns an empty name into null, so repeating that
        // would only give the rule a second place to drift.
        group.setName(this.name);
    }
}
