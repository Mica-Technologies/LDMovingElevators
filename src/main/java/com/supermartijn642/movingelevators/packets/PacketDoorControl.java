package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

/**
 * The car panel's "door open" / "door close" buttons.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketDoorControl extends BlockEntityBasePacket<ElevatorCarPanelBlockEntity> {

    private boolean open;

    public PacketDoorControl(BlockPos pos, boolean open){
        super(pos);
        this.open = open;
    }

    public PacketDoorControl(){
    }

    @Override
    public void write(PacketBuffer buffer){
        super.write(buffer);
        buffer.writeBoolean(this.open);
    }

    @Override
    public void read(PacketBuffer buffer){
        super.read(buffer);
        this.open = buffer.readBoolean();
    }

    @Override
    protected void handle(ElevatorCarPanelBlockEntity blockEntity, PacketContext context){
        // The panel is a block in the world, so a message about it should come from somebody
        // standing near it.
        if(!PacketReach.isInReach(context.getSendingPlayer(), blockEntity.getPos()))
            return;
        ElevatorGroup group = blockEntity.getGroup();
        if(group == null)
            return;
        // The panel rides with the cabin, so the doors it controls are the ones at whichever floor
        // the cabin is currently parked at -- not the floor the panel was bound to.
        int cabinFloor = group.getCabinFloorNumber();
        if(cabinFloor < 0 || cabinFloor >= group.getFloorCount())
            return;
        int floorY = group.getFloorYLevel(cabinFloor);
        if(this.open)
            group.requestDoorOpen(floorY);
        else
            group.requestDoorClose(floorY);
    }
}
