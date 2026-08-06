package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.item.BaseBlockItem;
import com.supermartijn642.core.item.ItemProperties;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.List;

/**
 * Carries a bank's worth of bindings before the panel is placed.
 * <p>
 * The other remote fixtures keep one controller in item NBT and overwrite it on each click. This one
 * accumulates instead, because a bank panel is defined by the set of elevators it serves -- and
 * clicking a controller that is already in the set removes it, so the one gesture both adds and takes
 * away and there is nothing to learn beyond "click the elevators you want".
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankLobbyPanelBlockItem extends BaseBlockItem {

    public static final String BINDINGS_KEY = "bankBindings";

    public BankLobbyPanelBlockItem(Block block, ItemProperties properties){
        super(block, properties);
    }

    @Override
    public ItemUseResult interact(ItemStack stack, EntityPlayer player, EnumHand hand, World level){
        if(level.isRemote)
            return ItemUseResult.success(stack);

        List<BankLobbyPanelBlockEntity.Binding> bindings = readBindings(stack);
        if(player.isSneaking()){
            // Sneaking in the air clears the lot, which is the only way back out of a half-built bank
            // short of throwing the item away.
            if(!bindings.isEmpty()){
                setBindings(stack, java.util.Collections.emptyList());
                player.sendStatusMessage(TextComponents.translation("movingelevators.remote_controller.clear").get(), true);
            }
            return ItemUseResult.success(stack);
        }
        player.sendStatusMessage(bindings.isEmpty()
            ? TextComponents.translation("movingelevators.bank_lobby_panel.tooltip").get()
            : TextComponents.translation("movingelevators.bank_lobby_panel.bound", TextComponents.number(bindings.size()).get()).get(), true);
        return ItemUseResult.success(stack);
    }

    /**
     * Adds a controller to this item's bank, or takes it out again if it is already in.
     * <p>
     * Called from {@link ControllerBlock} when a player right-clicks a controller holding one of these.
     */
    public static void toggleBinding(EntityPlayer player, ItemStack stack, BlockPos controllerPos, EnumFacing controllerFacing){
        List<BankLobbyPanelBlockEntity.Binding> bindings = readBindings(stack);
        boolean removed = bindings.removeIf(binding -> binding.matches(controllerPos, controllerFacing));
        if(!removed)
            bindings.add(new BankLobbyPanelBlockEntity.Binding(controllerPos, controllerFacing));
        setBindings(stack, bindings);
        player.sendStatusMessage(TextComponents.translation(
            removed ? "movingelevators.bank_lobby_panel.unbound_one" : "movingelevators.bank_lobby_panel.bound",
            TextComponents.number(bindings.size()).get()).get(), true);
    }

    /**
     * Copies a placed panel's whole configuration onto this item.
     * <p>
     * A lobby has several of these facing different directions, and clicking every controller again
     * for each one is tedious and easy to get wrong -- a bank where one panel knows about three cars
     * and its neighbour knows about two is a confusing thing to debug. Copying makes the second panel
     * exact by construction.
     */
    public static void copyFrom(EntityPlayer player, ItemStack stack, BankLobbyPanelBlockEntity panel){
        List<BankLobbyPanelBlockEntity.Binding> bindings = panel.getBindings();
        setBindings(stack, bindings);
        player.sendStatusMessage(TextComponents.translation("movingelevators.bank_lobby_panel.copied",
            TextComponents.number(bindings.size()).get()).get(), true);
    }

    public static List<BankLobbyPanelBlockEntity.Binding> readBindings(ItemStack stack){
        NBTTagCompound tag = stack.getTagCompound();
        if(tag == null || !tag.hasKey(BINDINGS_KEY, Constants.NBT.TAG_LIST))
            return new java.util.ArrayList<>();
        return BankLobbyPanelBlockEntity.readBindings(tag.getTagList(BINDINGS_KEY, Constants.NBT.TAG_COMPOUND));
    }

    private static void setBindings(ItemStack stack, List<BankLobbyPanelBlockEntity.Binding> bindings){
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagList list = BankLobbyPanelBlockEntity.writeBindings(bindings);
        if(list.tagCount() == 0)
            tag.removeTag(BINDINGS_KEY);
        else
            tag.setTag(BINDINGS_KEY, list);
        stack.setTagCompound(tag);
    }
}
