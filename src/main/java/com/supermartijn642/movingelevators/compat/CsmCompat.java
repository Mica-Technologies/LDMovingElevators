package com.supermartijn642.movingelevators.compat;

import com.micatechnologies.minecraft.csm.api.firealarm.CsmFireAlarmQuery;
import com.micatechnologies.minecraft.csm.lifesafety.ItemFireAlarmLinker;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The only place in this mod that names a City Super Mod type.
 * <p>
 * <b>Never reference this class except behind a {@code Loader.isModLoaded("csm")} check.</b> CSM is a
 * compile-only dependency: it is absent from the runtime classpath, so resolving this class without
 * CSM installed throws {@link NoClassDefFoundError}. Keeping every CSM name inside one class body is
 * what makes that safe -- the JVM does not resolve a class until something actually calls into it --
 * and it is the whole of the guarantee. An import anywhere else compiles perfectly and breaks only at
 * runtime, on the configuration that is never tested. There is a CI check for this.
 * <p>
 * Queried rather than subscribed to, deliberately. Subscribing would mean an {@code @SubscribeEvent}
 * method whose parameter is a CSM class, and Forge resolves handler parameter types when the handler
 * is registered -- so merely registering one would fail without CSM. Polling also survives CSM
 * unregistering a panel on chunk unload without announcing it, which its event stream does.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class CsmCompat {

    private CsmCompat(){
    }

    /**
     * The panel a City Super Mod fire alarm linker currently has selected, or null if the item is not
     * one or has selected nothing.
     * <p>
     * Lets a builder use the tool they are already holding. CSM's linker only knows how to attach
     * CSM's own devices -- an elevator controller falls straight through it -- so this reads the
     * selection and lets this mod do its own half of the job.
     */
    public static BlockPos getLinkerSelection(ItemStack stack){
        if(!(stack.getItem() instanceof ItemFireAlarmLinker))
            return null;
        // Called reflectively only because the accessor is newer than the City Super Mod release this
        // mod compiles against. Once a release ships with it, this becomes a direct call and the
        // pinned version moves up. Until then reflection also buys graceful behaviour on an older CSM:
        // the accessor is simply missing, this returns null, and this mod's own linker still works.
        try{
            Object selected = stack.getItem().getClass().getMethod("getSelectedPanel").invoke(stack.getItem());
            return selected instanceof BlockPos ? (BlockPos)selected : null;
        }catch(ReflectiveOperationException e){
            return null;
        }
    }

    /**
     * Whether the fire alarm panel at this position is sounding.
     * <p>
     * Spelled as a set membership test because that is the shape CSM offers; there is no
     * predicate taking a panel position. The set is empty whenever nothing in the dimension is
     * alarming, which is almost always, so this costs very little. The dimension comes from the world
     * passed in, so a panel position from another dimension cannot match.
     */
    public static boolean isFireAlarmActiveAt(World level, BlockPos panelPos){
        return CsmFireAlarmQuery.getActiveFireAlarmPanels(level).contains(panelPos);
    }

}
