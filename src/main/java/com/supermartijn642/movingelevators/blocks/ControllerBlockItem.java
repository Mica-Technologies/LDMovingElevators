package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.item.BaseBlockItem;
import com.supermartijn642.core.item.ItemProperties;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/**
 * The elevator controller as an item, which can also remember a fire alarm panel.
 * <p>
 * Sneak and right-click a panel to note it down, then place the controller on the floor the car should
 * return to when that alarm sounds. Placing is what fixes the recall floor, so a builder chooses it by
 * standing where they want the lift to end up rather than by typing a number.
 * <p>
 * No check is made that the clicked block really is an alarm panel, and that is deliberate: asking
 * would mean naming a City Super Mod block class here, which is exactly what this mod must not do
 * outside its compat class. A position that turns out not to be a panel simply never reports an alarm,
 * and the controller's own readout will say it is paired to nothing that ever sounds.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class ControllerBlockItem extends BaseBlockItem {

    public static final String PANEL_KEY = "alarmPanelPos";

    public ControllerBlockItem(Block block, ItemProperties properties){
        super(block, properties);
    }

    @Override
    public InteractionFeedback interactWithBlock(ItemStack stack, EntityPlayer player, EnumHand hand, World level, BlockPos hitPos, EnumFacing hitSide, Vec3d hitLocation){
        // Only while sneaking: an ordinary right-click has to go on placing controllers, and a fire
        // alarm panel has its own screen that a plain click should still open.
        if(!player.isSneaking())
            return super.interactWithBlock(stack, player, hand, level, hitPos, hitSide, hitLocation);
        if(!level.isRemote){
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setLong(PANEL_KEY, hitPos.toLong());
            stack.setTagCompound(tag);
            player.sendStatusMessage(TextComponents.translation("movingelevators.elevator.alarm_noted",
                TextComponents.number(hitPos.getX()).color(TextFormatting.GOLD).get(),
                TextComponents.number(hitPos.getY()).color(TextFormatting.GOLD).get(),
                TextComponents.number(hitPos.getZ()).color(TextFormatting.GOLD).get()).get(), true);
        }
        return InteractionFeedback.SUCCESS;
    }

    /** @return the panel this item remembers, or null */
    public static BlockPos readPanel(ItemStack stack){
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(PANEL_KEY, Constants.NBT.TAG_LONG) ? null : BlockPos.fromLong(tag.getLong(PANEL_KEY));
    }
}
