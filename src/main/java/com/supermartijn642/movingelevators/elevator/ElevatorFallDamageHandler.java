package com.supermartijn642.movingelevators.elevator;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;

/**
 * Created 4/30/2020 by SuperMartijn642
 */
@Mod.EventBusSubscriber
public class ElevatorFallDamageHandler {

    private static final Field floatingTickCount = ObfuscationReflectionHelper.findField(NetHandlerPlayServer.class, "field_147365_f");

    @SubscribeEvent
    public static void onFallDamage(LivingFallEvent e){
        if(shouldCancelFallDamage(e.getEntityLiving()))
            e.setCanceled(true);
    }

    /**
     * How long after leaving an elevator fall damage stays cancelled.
     */
    private static final long GRACE_TICKS = 20 * 5;

    public static boolean shouldCancelFallDamage(EntityLivingBase entity){
        NBTTagCompound compound = entity.getEntityData();
        if(compound.hasKey("elevatorTime")){
            // 'elevatorTime' is a snapshot of ticksExisted, and it lives in the entity's Forge data,
            // which Forge persists to NBT as 'ForgeData'. ticksExisted, however, restarts at 0 when
            // the entity is reconstructed -- on relog, or when a mob's chunk reloads. The delta is
            // then large and negative, which upstream's bare '< GRACE_TICKS' test treats as "still
            // in the grace period", and because that branch returns early the tag is never cleared.
            // The result was fall damage staying cancelled for roughly as long as the entity had
            // existed when it last used an elevator. Treat a negative delta as expired instead.
            long delta = entity.ticksExisted - compound.getLong("elevatorTime");
            if(delta >= 0 && delta < GRACE_TICKS)
                return true;
            compound.removeTag("elevatorTime");
        }
        return false;
    }

    public static void resetElevatorTime(EntityLivingBase entity){
        entity.getEntityData().setLong("elevatorTime", entity.ticksExisted);
        if(entity instanceof EntityPlayerMP)
            resetFloatingTicks((EntityPlayerMP)entity);
    }

    public static void resetFloatingTicks(EntityPlayerMP player){
        try{
            floatingTickCount.setInt(player.connection, 0);
        }catch(IllegalAccessException e){
            e.printStackTrace();
        }
    }
}
