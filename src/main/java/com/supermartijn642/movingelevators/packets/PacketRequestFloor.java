package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

/**
 * A destination picked from the car panel's floor list.
 * <p>
 * Carries the floor's y-level rather than its index: indices shift whenever a controller is added or
 * removed, and the screen the player clicked may be a tick or two out of date. The y-level either
 * still names a floor on arrival or it does not, and {@link ElevatorGroup#onCarCall} ignores it if
 * not.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketRequestFloor extends BlockEntityBasePacket<ElevatorCarPanelBlockEntity> {

    private int floorY;

    public PacketRequestFloor(BlockPos pos, int floorY){
        super(pos);
        this.floorY = floorY;
    }

    public PacketRequestFloor(){
    }

    @Override
    public void write(PacketBuffer buffer){
        super.write(buffer);
        buffer.writeInt(this.floorY);
    }

    @Override
    public void read(PacketBuffer buffer){
        super.read(buffer);
        this.floorY = buffer.readInt();
    }

    @Override
    protected void handle(ElevatorCarPanelBlockEntity blockEntity, PacketContext context){
        ElevatorGroup group = blockEntity.getGroup();
        if(group != null)
            group.onCarCall(this.floorY, context.getSendingPlayer());
    }
}
