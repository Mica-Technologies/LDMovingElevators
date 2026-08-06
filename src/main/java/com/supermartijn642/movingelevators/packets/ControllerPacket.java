package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.BlockEntityBasePacket;
import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * A message from the elevator screen, checked before it is acted on.
 * <p>
 * The screen's packets carried no validation at all: each one took a position from the client and
 * did as it was told. Nothing established that the sender was anywhere near the elevator, so a
 * modified client could resize, rename or re-target any elevator in the world from any distance, and
 * nothing checked the elevator still existed either -- a controller broken while its screen was open
 * produced a server-side crash rather than a no-op.
 * <p>
 * Both checks belong here rather than in each message, because the answer is the same for all of
 * them and a check that has to be remembered twenty times is a check that will be forgotten once.
 * <p>
 * Created for the Mica Technologies fork.
 */
public abstract class ControllerPacket extends BlockEntityBasePacket<ControllerBlockEntity> {

    /**
     * The same range vanilla containers use to decide an open screen is still usable, and for the
     * same reason -- the screen stays open while you walk, so this has to allow a step back from the
     * block without allowing action from across the map.
     */
    private static final double MAX_REACH_SQUARED = 64;

    protected ControllerPacket(BlockPos pos){
        super(pos);
    }

    protected ControllerPacket(){
    }

    @Override
    protected void handle(ControllerBlockEntity blockEntity, PacketContext context){
        EntityPlayer player = context.getSendingPlayer();
        if(player == null)
            return;
        BlockPos pos = blockEntity.getPos();
        if(player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_REACH_SQUARED)
            return;
        this.handle(blockEntity, player);
    }

    protected abstract void handle(ControllerBlockEntity blockEntity, EntityPlayer player);
}
