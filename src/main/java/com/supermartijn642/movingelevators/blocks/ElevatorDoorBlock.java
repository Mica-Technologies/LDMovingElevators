package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BaseBlock;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.core.block.EntityHoldingBlock;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
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
 * A sliding double door for an elevator landing: a doorway two blocks wide and two blocks tall,
 * placed as one item. Two leaves meet in the middle when closed and retract into the frame when open.
 * <p>
 * Each of the four blocks carries its own block entity and reaches its own conclusion about whether
 * to be open. They always agree, because they share an elevator and a landing, and that is far
 * simpler than nominating one of them as the master and forwarding state to the other three.
 * <p>
 * Note what the four blocks do <em>not</em> encode: which of them is the upper half. The leaves are
 * uniform top to bottom, so only {@link #RIGHT} changes the model, and leaving the vertical half out
 * of the state keeps facing, side and open inside the four bits of metadata 1.12 gives us.
 */
public class ElevatorDoorBlock extends BaseBlock implements EntityHoldingBlock {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    /** Which leaf this is. The two retract in opposite directions. */
    public static final PropertyBool RIGHT = PropertyBool.create("right");
    public static final PropertyBool OPEN = PropertyBool.create("open");

    /** Depth of the leaves within the block, so they sit in the middle of the frame. */
    private static final double MIN_Z = 7 / 16d, MAX_Z = 9 / 16d;

    private static final AxisAlignedBB CLOSED_NORTH_SOUTH = new AxisAlignedBB(0, 0, MIN_Z, 1, 1, MAX_Z);
    private static final AxisAlignedBB CLOSED_EAST_WEST = new AxisAlignedBB(MIN_Z, 0, 0, MAX_Z, 1, 1);

    /**
     * Guards the cascade when one part of a doorway is broken and takes the other three with it --
     * each of those removals fires this block's break handling again.
     */
    private static boolean removingDoorway = false;

    public ElevatorDoorBlock(BlockProperties properties){
        super(false, properties);
        this.setDefaultState(this.blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH).withProperty(RIGHT, false).withProperty(OPEN, false));
    }

    @Override
    public TileEntity createNewBlockEntity(){
        return new ElevatorDoorBlockEntity();
    }

    /** The direction from the left leaf towards the right one. */
    private static EnumFacing sideOf(IBlockState state){
        return state.getValue(FACING).rotateY();
    }

    /**
     * @return the bottom-left block of the doorway this block belongs to
     */
    private static BlockPos originOf(IBlockAccess level, BlockPos pos, IBlockState state){
        BlockPos origin = state.getValue(RIGHT) ? pos.offset(sideOf(state).getOpposite()) : pos;
        IBlockState below = level.getBlockState(origin.down());
        // No upper/lower flag in the state, so ask the block below whether it is the other half of
        // this same doorway.
        if(below.getBlock() == state.getBlock()
            && below.getValue(FACING) == state.getValue(FACING)
            && below.getValue(RIGHT) == Boolean.FALSE)
            origin = origin.down();
        return origin;
    }

    private static BlockPos[] cellsOf(IBlockAccess level, BlockPos pos, IBlockState state){
        BlockPos origin = originOf(level, pos, state);
        EnumFacing side = sideOf(state);
        return new BlockPos[]{origin, origin.offset(side), origin.up(), origin.up().offset(side)};
    }

    @Override
    public void onBlockPlacedBy(World level, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
        super.onBlockPlacedBy(level, pos, state, placer, stack);
        EnumFacing side = sideOf(state);
        BlockPos[] cells = {pos.offset(side), pos.up(), pos.up().offset(side)};

        // A doorway needs all four blocks or it is nonsense, so refuse rather than place a fragment.
        for(BlockPos cell : cells){
            if(!level.getBlockState(cell).getBlock().isReplaceable(level, cell)){
                if(!level.isRemote){
                    level.setBlockToAir(pos);
                    spawnAsEntity(level, pos, new ItemStack(this));
                    if(placer instanceof EntityPlayer)
                        ((EntityPlayer)placer).sendStatusMessage(
                            TextComponents.translation("movingelevators.elevator_door.no_room").color(TextFormatting.RED).get(), true);
                }
                return;
            }
        }

        level.setBlockState(pos.offset(side), state.withProperty(RIGHT, true), 3);
        level.setBlockState(pos.up(), state, 3);
        level.setBlockState(pos.up().offset(side), state.withProperty(RIGHT, true), 3);

        // Every part carries the binding, so each can resolve the elevator on its own.
        NBTTagCompound compound = stack.getTagCompound();
        if(compound == null || !compound.hasKey("controllerDim"))
            return;
        BlockPos controllerPos = new BlockPos(compound.getInteger("controllerX"), compound.getInteger("controllerY"), compound.getInteger("controllerZ"));
        EnumFacing controllerFacing = compound.hasKey("controllerFacing", Constants.NBT.TAG_INT)
            ? EnumFacing.getHorizontal(compound.getInteger("controllerFacing")) : null;
        for(BlockPos cell : new BlockPos[]{pos, pos.offset(side), pos.up(), pos.up().offset(side)}){
            TileEntity entity = level.getTileEntity(cell);
            if(entity instanceof ElevatorDoorBlockEntity)
                ((ElevatorDoorBlockEntity)entity).setValues(controllerPos, controllerFacing);
        }
    }

    @Override
    public void breakBlock(World level, BlockPos pos, IBlockState state){
        if(!removingDoorway){
            removingDoorway = true;
            try{
                for(BlockPos cell : cellsOf(level, pos, state)){
                    if(cell.equals(pos))
                        continue;
                    if(level.getBlockState(cell).getBlock() == this)
                        level.setBlockToAir(cell);
                }
            }finally{
                removingDoorway = false;
            }
        }
        super.breakBlock(level, pos, state);
    }

    @Override
    protected InteractionFeedback interact(IBlockState state, World level, BlockPos pos, EntityPlayer player, EnumHand hand, EnumFacing hitSide, Vec3d hitLocation){
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof ElevatorDoorBlockEntity && level.isRemote){
            ElevatorDoorBlockEntity door = (ElevatorDoorBlockEntity)entity;
            if(!door.isBound())
                // Silent doors were the single most confusing thing about the first version: an
                // unbound doorway looks identical to a working one and simply never moves.
                player.sendStatusMessage(TextComponents.translation("movingelevators.elevator_door.not_bound").color(TextFormatting.RED).get(), true);
            else{
                BlockPos controllerPos = door.getControllerPos();
                ITextComponent x = TextComponents.number(controllerPos.getX()).color(TextFormatting.GOLD).get();
                ITextComponent y = TextComponents.number(controllerPos.getY()).color(TextFormatting.GOLD).get();
                ITextComponent z = TextComponents.number(controllerPos.getZ()).color(TextFormatting.GOLD).get();
                player.sendStatusMessage(TextComponents.translation("movingelevators.remote_controller.controller_location", x, y, z).get(), true);
            }
        }
        return InteractionFeedback.SUCCESS;
    }

    @Override
    public IBlockState getStateForPlacement(World level, BlockPos pos, EnumFacing hitSide, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand){
        return this.getDefaultState()
            .withProperty(FACING, placer.getHorizontalFacing().getOpposite())
            .withProperty(RIGHT, false)
            .withProperty(OPEN, false);
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        NBTTagCompound tag = stack.getTagCompound();
        if(tag == null || !tag.hasKey("controllerDim"))
            info.accept(TextComponents.translation("movingelevators.elevator_door.tooltip").color(TextFormatting.AQUA).get());
        else{
            ITextComponent x = TextComponents.number(tag.getInteger("controllerX")).color(TextFormatting.GOLD).get();
            ITextComponent y = TextComponents.number(tag.getInteger("controllerY")).color(TextFormatting.GOLD).get();
            ITextComponent z = TextComponents.number(tag.getInteger("controllerZ")).color(TextFormatting.GOLD).get();
            ITextComponent dimension = TextComponents.dimension(DimensionType.getById(tag.getInteger("controllerDim"))).color(TextFormatting.GOLD).get();
            info.accept(TextComponents.translation("movingelevators.remote_controller.tooltip.bound", x, y, z, dimension).get());
        }
    }

    // --- Shape and state -------------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        return state.getValue(FACING).getAxis() == EnumFacing.Axis.Z ? CLOSED_NORTH_SOUTH : CLOSED_EAST_WEST;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        // Open doors are a doorway, not a wall.
        return state.getValue(OPEN) ? NULL_AABB : this.getBoundingBox(state, level, pos);
    }

    @Override
    protected BlockStateContainer createBlockState(){
        return new BlockStateContainer(this, FACING, RIGHT, OPEN);
    }

    @Override
    public IBlockState getStateFromMeta(int meta){
        return this.getDefaultState()
            .withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
            .withProperty(RIGHT, (meta & 4) != 0)
            .withProperty(OPEN, (meta & 8) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state){
        return state.getValue(FACING).getHorizontalIndex()
            | (state.getValue(RIGHT) ? 4 : 0)
            | (state.getValue(OPEN) ? 8 : 0);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation){
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror){
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    public boolean isFullCube(IBlockState state){
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state){
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess level, IBlockState state, BlockPos pos, EnumFacing face){
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public boolean canCreatureSpawn(IBlockState state, IBlockAccess level, BlockPos pos, EntityLiving.SpawnPlacementType type){
        return false;
    }

    @Override
    public EnumPushReaction getMobilityFlag(IBlockState state){
        return EnumPushReaction.BLOCK;
    }
}
