package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockProperties;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
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
import net.minecraft.world.DimensionType;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * A slim wall-mounted floor indicator: the same "which floor is the cabin at" readout as
 * {@link RemoteDisplayBlock}, but as a thin metal plate on the face of the block behind it, the way
 * a real elevator hall indicator sits above a door.
 * <p>
 * Deliberately not a {@link CamoBlock}. A two-pixel-deep plate has nothing meaningful to disguise,
 * and dropping camouflage lets the block be genuinely directional. The full-cube
 * {@link RemoteDisplayBlock} keeps camouflage for people who want that style.
 */
public class RemoteIndicatorBlock extends WallPanelBlock {

    /** Wide and short, like a hall indicator above a door. */
    private static final double MIN_X = 1 / 16d, MAX_X = 15 / 16d;
    private static final double MIN_Y = 5.5 / 16d, MAX_Y = 10.5 / 16d;

    public RemoteIndicatorBlock(BlockProperties properties){
        super(properties, MIN_X, MAX_X, MIN_Y, MAX_Y);
    }

    @Override
    public TileEntity createNewBlockEntity(){
        return new RemoteIndicatorBlockEntity();
    }

    @Override
    protected InteractionFeedback interact(IBlockState state, World level, BlockPos pos, EntityPlayer player, EnumHand hand, EnumFacing hitSide, Vec3d hitLocation){
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof RemoteIndicatorBlockEntity && level.isRemote){
            BlockPos controllerPos = ((RemoteIndicatorBlockEntity)entity).getControllerPos();
            ITextComponent x = TextComponents.number(controllerPos.getX()).color(TextFormatting.GOLD).get();
            ITextComponent y = TextComponents.number(controllerPos.getY()).color(TextFormatting.GOLD).get();
            ITextComponent z = TextComponents.number(controllerPos.getZ()).color(TextFormatting.GOLD).get();
            player.sendStatusMessage(TextComponents.translation("movingelevators.remote_controller.controller_location", x, y, z).get(), true);
        }
        // Always success, so right-clicking never places the held block into the wall behind.
        return InteractionFeedback.SUCCESS;
    }

    @Override
    public void onBlockPlacedBy(World level, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
        super.onBlockPlacedBy(level, pos, state, placer, stack);
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof RemoteIndicatorBlockEntity){
            NBTTagCompound compound = stack.getTagCompound();
            if(compound == null || !compound.hasKey("controllerDim"))
                return;
            ((RemoteIndicatorBlockEntity)entity).setValues(
                new BlockPos(compound.getInteger("controllerX"), compound.getInteger("controllerY"), compound.getInteger("controllerZ")),
                compound.hasKey("controllerFacing", Constants.NBT.TAG_INT) ? EnumFacing.getHorizontal(compound.getInteger("controllerFacing")) : null
            );
        }
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        NBTTagCompound tag = stack.getTagCompound();
        if(tag == null || !tag.hasKey("controllerDim"))
            info.accept(TextComponents.translation("movingelevators.remote_indicator.tooltip").color(TextFormatting.AQUA).get());
        else{
            ITextComponent x = TextComponents.number(tag.getInteger("controllerX")).color(TextFormatting.GOLD).get();
            ITextComponent y = TextComponents.number(tag.getInteger("controllerY")).color(TextFormatting.GOLD).get();
            ITextComponent z = TextComponents.number(tag.getInteger("controllerZ")).color(TextFormatting.GOLD).get();
            ITextComponent dimension = TextComponents.dimension(DimensionType.getById(tag.getInteger("controllerDim"))).color(TextFormatting.GOLD).get();
            info.accept(TextComponents.translation("movingelevators.remote_controller.tooltip.bound", x, y, z, dimension).get());
        }
    }
}
