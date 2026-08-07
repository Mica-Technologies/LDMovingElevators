package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * The car panel's independent service switch: the car keeps answering the people inside it and stops
 * answering the building.
 * <p>
 * Permission-gated, because on a shared server this is a way to take a lift out of public service
 * from inside it, and a bank that quietly loses a car to whoever rode it last is worse than one with
 * no such switch. The check is the ordinary command-permission one, so a permissions mod can grant
 * {@link #PERMISSION} to somebody who is not an operator.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketToggleIndependentService extends BlockEntityBasePacket<ElevatorCarPanelBlockEntity> {

    /** Permission node, and the level an operator needs without a permissions mod present. */
    public static final String PERMISSION = "movingelevators.independent_service";
    private static final int PERMISSION_LEVEL = 2;

    public PacketToggleIndependentService(BlockPos pos){
        super(pos);
    }

    public PacketToggleIndependentService(){
    }

    public static boolean isAllowed(EntityPlayer player){
        return player != null && player.canUseCommand(PERMISSION_LEVEL, PERMISSION);
    }

    @Override
    protected void handle(ElevatorCarPanelBlockEntity blockEntity, PacketContext context){
        EntityPlayer player = context.getSendingPlayer();
        if(!PacketReach.isInReach(player, blockEntity.getPos()))
            return;
        ElevatorGroup group = blockEntity.getGroup();
        if(group == null)
            return;
        if(!isAllowed(player)){
            player.sendStatusMessage(TextComponents.translation("movingelevators.service_mode.not_allowed").color(TextFormatting.RED).get(), true);
            return;
        }
        // Out of service is set elsewhere and outranks this; toggling from inside must not undo it.
        if(group.isOutOfService())
            return;
        group.setServiceMode(group.getServiceMode() == ElevatorGroup.ServiceMode.INDEPENDENT
            ? ElevatorGroup.ServiceMode.NORMAL : ElevatorGroup.ServiceMode.INDEPENDENT);
    }
}
