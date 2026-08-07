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
 * The car panel for an elevator that is part of a bank: a readout and the controls a passenger still
 * needs, with no floor buttons at all.
 * <p>
 * Floor buttons are exactly what destination dispatch takes away. You say where you are going at the
 * lobby, before you board, and the car already knows -- so a bank of floor buttons inside is at best
 * furniture and at worst an invitation to send the car somewhere the dispatcher did not plan for.
 * What is still wanted inside is everything that is not choosing a floor: which floor you are passing,
 * which way you are going, the doors, and the alarm.
 * <p>
 * Shares its block entity with {@link ElevatorCarPanelBlock}, because nothing about the two differs
 * except which controls are offered -- both are a plate bound to one elevator, riding with the cabin.
 * That also means the door and alarm messages work here without a single change.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankCarPanelBlock extends WallPanelBlock {

    /**
     * Half the height of the full car panel: with the floor buttons gone there is nothing to fill the
     * lower half with, and a plate two thirds empty looks unfinished rather than uncluttered.
     */
    private static final double MIN_X = 2 / 16d, MAX_X = 14 / 16d;
    private static final double MIN_Y = 7 / 16d, MAX_Y = 15 / 16d;

    public BankCarPanelBlock(BlockProperties properties){
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

        // Only the face the plate is on opens the controls; the sides are just metal.
        if(hitSide == state.getValue(FACING) && level.isRemote){
            if(((ElevatorCarPanelBlockEntity)entity).hasGroup())
                MovingElevatorsClient.openCarControlsScreen(pos);
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
            info.accept(TextComponents.translation("movingelevators.bank_car_panel.tooltip").color(TextFormatting.AQUA).get());
        else{
            ITextComponent x = TextComponents.number(tag.getInteger("controllerX")).color(TextFormatting.GOLD).get();
            ITextComponent y = TextComponents.number(tag.getInteger("controllerY")).color(TextFormatting.GOLD).get();
            ITextComponent z = TextComponents.number(tag.getInteger("controllerZ")).color(TextFormatting.GOLD).get();
            ITextComponent dimension = TextComponents.dimension(DimensionType.getById(tag.getInteger("controllerDim"))).color(TextFormatting.GOLD).get();
            info.accept(TextComponents.translation("movingelevators.remote_controller.tooltip.bound", x, y, z, dimension).get());
        }
    }
}
