package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
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
 * The car operating panel: the fixture you ride with. A tall wall plate showing the current floor and
 * direction of travel at the top, with a bank of floor buttons below.
 * <p>
 * Clicking anywhere on it opens a screen listing the elevator's actual floors, rather than mapping
 * hits onto the buttons drawn on the face. That is deliberate: floors are added and removed at will,
 * so a fixed grid can never match them, and a block face is far too small to hit-test a dozen
 * buttons reliably. The drawn bank is a readout -- it lights the floors that are actually selected --
 * while the screen is where you choose.
 */
public class ElevatorCarPanelBlock extends WallPanelBlock {

    /**
     * Tall, and wider than the landing panels: a readout, two direction arrows and a bank of floor
     * buttons all have to sit on this face without colliding, which 8 pixels could not manage.
     */
    private static final double MIN_X = 2 / 16d, MAX_X = 14 / 16d;
    private static final double MIN_Y = 1 / 16d, MAX_Y = 15 / 16d;

    public ElevatorCarPanelBlock(BlockProperties properties){
        super(properties, MIN_X, MAX_X, MIN_Y, MAX_Y);
    }

    @Override
    public TileEntity createNewBlockEntity(){
        return new ElevatorCarPanelBlockEntity();
    }

    @Override
    protected InteractionFeedback interact(IBlockState state, World level, BlockPos pos, EntityPlayer player, EnumHand hand, EnumFacing hitSide, Vec3d hitLocation){
        TileEntity entity = level.getTileEntity(pos);
        if(!(entity instanceof ElevatorCarPanelBlockEntity))
            return InteractionFeedback.SUCCESS;

        // Only the face the plate is on opens the panel; the sides are just metal.
        if(hitSide == state.getValue(FACING) && level.isRemote){
            if(((ElevatorCarPanelBlockEntity)entity).hasGroup())
                MovingElevatorsClient.openFloorSelectScreen(pos);
            else{
                BlockPos controllerPos = ((ElevatorCarPanelBlockEntity)entity).getControllerPos();
                ITextComponent x = TextComponents.number(controllerPos.getX()).color(TextFormatting.GOLD).get();
                ITextComponent y = TextComponents.number(controllerPos.getY()).color(TextFormatting.GOLD).get();
                ITextComponent z = TextComponents.number(controllerPos.getZ()).color(TextFormatting.GOLD).get();
                player.sendStatusMessage(TextComponents.translation("movingelevators.remote_controller.controller_location", x, y, z).get(), true);
            }
        }
        return InteractionFeedback.SUCCESS;
    }

    @Override
    public void onBlockPlacedBy(World level, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
        super.onBlockPlacedBy(level, pos, state, placer, stack);
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof ElevatorCarPanelBlockEntity){
            NBTTagCompound compound = stack.getTagCompound();
            if(compound == null || !compound.hasKey("controllerDim"))
                return;
            ((ElevatorCarPanelBlockEntity)entity).setValues(
                new BlockPos(compound.getInteger("controllerX"), compound.getInteger("controllerY"), compound.getInteger("controllerZ")),
                compound.hasKey("controllerFacing", Constants.NBT.TAG_INT) ? EnumFacing.getHorizontal(compound.getInteger("controllerFacing")) : null
            );
        }
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        NBTTagCompound tag = stack.getTagCompound();
        if(tag == null || !tag.hasKey("controllerDim"))
            info.accept(TextComponents.translation("movingelevators.elevator_car_panel.tooltip").color(TextFormatting.AQUA).get());
        else{
            ITextComponent x = TextComponents.number(tag.getInteger("controllerX")).color(TextFormatting.GOLD).get();
            ITextComponent y = TextComponents.number(tag.getInteger("controllerY")).color(TextFormatting.GOLD).get();
            ITextComponent z = TextComponents.number(tag.getInteger("controllerZ")).color(TextFormatting.GOLD).get();
            ITextComponent dimension = TextComponents.dimension(DimensionType.getById(tag.getInteger("controllerDim"))).color(TextFormatting.GOLD).get();
            info.accept(TextComponents.translation("movingelevators.remote_controller.tooltip.bound", x, y, z, dimension).get());
        }
    }
}
