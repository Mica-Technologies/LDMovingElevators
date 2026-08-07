package com.supermartijn642.movingelevators.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * Whether the player who sent a message is close enough to the block it acts on.
 * <p>
 * A helper rather than a base class because the panels that need it already extend
 * {@code BlockEntityBasePacket} for four different block entity types, and one shared answer written
 * once is worth more than a generic hierarchy built to hold it.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class PacketReach {

    /**
     * The same range vanilla containers use to decide an open screen is still usable. A screen stays
     * open while its owner walks, so this has to permit a step back from the block without permitting
     * action from across the map.
     */
    private static final double MAX_REACH_SQUARED = 64;

    private PacketReach(){
    }

    public static boolean isInReach(EntityPlayer player, BlockPos pos){
        return player != null
            && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_REACH_SQUARED;
    }
}
