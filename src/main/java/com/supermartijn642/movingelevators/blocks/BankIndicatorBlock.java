package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockProperties;
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
 * The bank indicator: one plate showing where every lift in a bank is.
 * <p>
 * Wider and shorter than the single indicator, because it is a row of readouts rather than one, and
 * it is read from across a lobby.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankIndicatorBlock extends WallPanelBlock {

    private static final double MIN_X = 0.5 / 16d, MAX_X = 15.5 / 16d;
    private static final double MIN_Y = 4 / 16d, MAX_Y = 12 / 16d;

    public BankIndicatorBlock(BlockProperties properties){
        super(properties, MIN_X, MAX_X, MIN_Y, MAX_Y);
    }

    @Override
    public TileEntity createNewBlockEntity(){
        return new BankIndicatorBlockEntity();
    }

    @Override
    protected InteractionFeedback interact(IBlockState state, World level, BlockPos pos, EntityPlayer player, EnumHand hand, EnumFacing hitSide, Vec3d hitLocation){
        TileEntity entity = level.getTileEntity(pos);
        if(!(entity instanceof BankIndicatorBlockEntity))
            return InteractionFeedback.SUCCESS;

        // A readout has nothing to press, so every click is a question about what it is showing.
        if(!level.isRemote && player != null)
            ((BankIndicatorBlockEntity)entity).reportStatus(player);
        return InteractionFeedback.SUCCESS;
    }

    @Override
    public void onBlockPlacedBy(World level, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
        super.onBlockPlacedBy(level, pos, state, placer, stack);
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof BankIndicatorBlockEntity)
            ((BankIndicatorBlockEntity)entity).setBindings(MultiControllerBlockItem.readBindings(stack));
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        List<BankLobbyPanelBlockEntity.Binding> bindings = MultiControllerBlockItem.readBindings(stack);
        if(bindings.isEmpty())
            info.accept(TextComponents.translation("movingelevators.bank_indicator.tooltip").color(TextFormatting.AQUA).get());
        else
            info.accept(TextComponents.translation("movingelevators.bank_lobby_panel.bound",
                TextComponents.number(bindings.size()).color(TextFormatting.GOLD).get()).get());
    }
}
