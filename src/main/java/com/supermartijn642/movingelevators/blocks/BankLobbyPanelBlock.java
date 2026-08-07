package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * The bank lobby panel: a destination-dispatch station. You enter where you are going before you
 * board, and it decides which of several elevators collects you.
 * <p>
 * Entirely optional. Elevators know nothing about banks, so one that a panel happens to dispatch is
 * still an ordinary elevator with its own controllers, buttons and doors -- banking is something a
 * building does, not a mode an elevator is in.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankLobbyPanelBlock extends WallPanelBlock {

    /** Tall and narrow, like the touchscreen stations these are modelled on. */
    private static final double MIN_X = 4 / 16d, MAX_X = 12 / 16d;
    private static final double MIN_Y = 2 / 16d, MAX_Y = 15 / 16d;

    public BankLobbyPanelBlock(BlockProperties properties){
        super(properties, MIN_X, MAX_X, MIN_Y, MAX_Y);
    }

    @Override
    public TileEntity createNewBlockEntity(){
        return new BankLobbyPanelBlockEntity();
    }

    @Override
    protected InteractionFeedback interact(IBlockState state, World level, BlockPos pos, EntityPlayer player, EnumHand hand, EnumFacing hitSide, Vec3d hitLocation){
        TileEntity entity = level.getTileEntity(pos);
        if(!(entity instanceof BankLobbyPanelBlockEntity))
            return InteractionFeedback.SUCCESS;
        BankLobbyPanelBlockEntity panel = (BankLobbyPanelBlockEntity)entity;

        // Sneaking asks what it is linked to. Binding is the one thing about this block that cannot
        // be seen in the world, and a wrong bank looks exactly like a right one until a car fails to
        // arrive, so there has to be a way to ask rather than infer.
        if(player != null && player.isSneaking() && player.getHeldItem(hand).isEmpty()){
            if(!level.isRemote)
                panel.reportStatus(player);
            return InteractionFeedback.SUCCESS;
        }

        // Clicking a configured panel with another panel copies its bank, so the second station in a
        // lobby costs one click instead of a repeat of the whole binding walk.
        if(player != null && player.getHeldItem(hand).getItem() instanceof BankLobbyPanelBlockItem){
            if(!level.isRemote){
                if(panel.isBound())
                    BankLobbyPanelBlockItem.copyFrom(player, player.getHeldItem(hand), panel);
                else
                    player.sendStatusMessage(TextComponents.translation("movingelevators.bank_lobby.unbound").get(), true);
            }
            return InteractionFeedback.SUCCESS;
        }

        // Only the face opens the screen; the sides are just metal.
        if(hitSide == state.getValue(FACING) && level.isRemote){
            if(panel.isBound())
                MovingElevatorsClient.openBankLobbyScreen(pos);
            else
                player.sendStatusMessage(TextComponents.translation("movingelevators.bank_lobby.unbound").get(), true);
        }
        return InteractionFeedback.SUCCESS;
    }

    @Override
    public void onBlockPlacedBy(World level, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
        super.onBlockPlacedBy(level, pos, state, placer, stack);
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof BankLobbyPanelBlockEntity){
            ((BankLobbyPanelBlockEntity)entity).setBindings(BankLobbyPanelBlockItem.readBindings(stack));
            // The bank belongs to the panel now, not to what is left in your hand. Carrying it over
            // meant the next panel silently inherited the last one's elevators and then accumulated
            // more on top, so a bank built second was never the bank you thought you were building.
            // Copying a placed panel is the deliberate way to repeat one.
            if(!level.isRemote)
                BankLobbyPanelBlockItem.clearBindings(stack);
        }
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        List<BankLobbyPanelBlockEntity.Binding> bindings = BankLobbyPanelBlockItem.readBindings(stack);
        if(bindings.isEmpty())
            info.accept(TextComponents.translation("movingelevators.bank_lobby_panel.tooltip").color(TextFormatting.AQUA).get());
        else
            info.accept(TextComponents.translation("movingelevators.bank_lobby_panel.bound",
                TextComponents.number(bindings.size()).color(TextFormatting.GOLD).get()).get());
    }
}
