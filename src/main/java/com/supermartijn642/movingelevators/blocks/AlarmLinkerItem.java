package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.item.BaseItem;
import com.supermartijn642.core.item.ItemProperties;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Pairs an elevator to a fire alarm panel: click the panel, then click an elevator controller.
 * <p>
 * The order and the gesture are borrowed wholesale from City Super Mod's own linking tool, which is
 * how its sounders are attached to a panel. Somebody wiring a building has already learnt it there,
 * and an elevator is just another thing that ought to know the alarm has gone off.
 * <p>
 * The floor of the controller you click becomes the floor the car returns to, so the recall floor is
 * chosen by standing where you want the lift to end up.
 * <p>
 * Nothing here checks that the first block really was an alarm panel. Asking would mean naming a City
 * Super Mod class outside the compat class, which is what would turn an optional integration into a
 * required one. A position that is not a panel simply never reports an alarm.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class AlarmLinkerItem extends BaseItem {

    private static final String PANEL_KEY = "alarmPanelPos";

    public AlarmLinkerItem(ItemProperties properties){
        super(properties);
    }

    @Override
    public InteractionFeedback interactWithBlock(ItemStack stack, EntityPlayer player, EnumHand hand, World level, BlockPos hitPos, EnumFacing hitSide, Vec3d hitLocation){
        if(level.isRemote)
            return InteractionFeedback.SUCCESS;

        // A controller never reaches this method: the block is asked about a right-click before the
        // item is, so ControllerBlock calls pair() directly. Guarded anyway, or a click on a
        // controller face that the block declined would quietly record it as an alarm panel.
        if(level.getTileEntity(hitPos) instanceof ControllerBlockEntity)
            return InteractionFeedback.SUCCESS;

        // Anything else is taken to be the panel. It is remembered rather than acted on, so the second
        // click has something to attach.
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setLong(PANEL_KEY, hitPos.toLong());
        stack.setTagCompound(tag);
        player.sendStatusMessage(TextComponents.translation("movingelevators.alarm_linker.selected",
            position(hitPos)).get(), true);
        return InteractionFeedback.SUCCESS;
    }

    @Override
    public ItemUseResult interact(ItemStack stack, EntityPlayer player, EnumHand hand, World level){
        // Sneaking in the air forgets the panel, so a tool carried around between buildings does not
        // quietly attach the wrong alarm.
        if(player.isSneaking() && !level.isRemote && readPanel(stack) != null){
            stack.getTagCompound().removeTag(PANEL_KEY);
            player.sendStatusMessage(TextComponents.translation("movingelevators.alarm_linker.cleared").get(), true);
        }
        return ItemUseResult.success(stack);
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable net.minecraft.world.IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        BlockPos panel = readPanel(stack);
        info.accept(panel == null
            ? TextComponents.translation("movingelevators.alarm_linker.tooltip").color(TextFormatting.AQUA).get()
            : TextComponents.translation("movingelevators.alarm_linker.tooltip.holding", position(panel)).color(TextFormatting.GOLD).get());
    }

    /**
     * Attaches the remembered panel to an elevator. Called by {@link ControllerBlock}, which is handed
     * the right-click before this item is.
     */
    public static void pair(EntityPlayer player, ItemStack stack, ControllerBlockEntity controller){
        BlockPos panel = readPanel(stack);
        if(panel == null){
            player.sendStatusMessage(TextComponents.translation("movingelevators.alarm_linker.no_panel").color(TextFormatting.RED).get(), true);
            return;
        }
        ElevatorGroup group = controller.getGroup();
        if(group == null){
            player.sendStatusMessage(TextComponents.translation("movingelevators.alarm_linker.no_elevator").color(TextFormatting.RED).get(), true);
            return;
        }
        // The controller clicked decides where the car goes, so an elevator can be re-aimed at a
        // different floor by clicking a different controller of the same shaft.
        group.setAlarmPanel(panel, controller.getFloorLevel());
        player.sendStatusMessage(TextComponents.translation("movingelevators.alarm_linker.paired",
            position(panel)).color(TextFormatting.GREEN).get(), true);
    }

    private static ITextComponent position(BlockPos pos){
        return TextComponents.string(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).color(TextFormatting.GOLD).get();
    }

    private static BlockPos readPanel(ItemStack stack){
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(PANEL_KEY, Constants.NBT.TAG_LONG) ? null : BlockPos.fromLong(tag.getLong(PANEL_KEY));
    }
}
