package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BaseBlock;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.core.block.EntityHoldingBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
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
 * A slim wall-mounted floor indicator: the same "which floor is the cabin at" readout as
 * {@link RemoteDisplayBlock}, but as a thin metal plate that sits on the face of the block behind
 * it, the way a real elevator hall indicator sits above a door.
 * <p>
 * Deliberately not a {@link CamoBlock}. A two-pixel-deep plate has nothing meaningful to disguise,
 * and dropping camouflage lets the block be genuinely directional -- the plate exists on one side
 * only, so {@link #FACING} is a real block state rather than something stashed in the block entity.
 * The full-cube {@link RemoteDisplayBlock} keeps camouflage for people who want that style.
 */
public class RemoteIndicatorBlock extends BaseBlock implements EntityHoldingBlock {

    /** The direction the plate faces, i.e. away from the wall it is mounted on. */
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    /**
     * Plate geometry, in sixteenths.
     * <p>
     * The plate hugs the wall it is mounted on, which is the neighbour <em>opposite</em> the way it
     * faces: a north-facing plate is read from the north, so its wall is the south neighbour and the
     * metal sits at high Z. Getting this backwards leaves the plate floating a block clear of the
     * wall, which is exactly how the first version looked in game.
     */
    private static final double MIN_X = 1 / 16d, MAX_X = 15 / 16d;
    private static final double MIN_Y = 5.5 / 16d, MAX_Y = 10.5 / 16d;
    /** Thickness of the plate. Shared with the renderer, which puts the label just proud of it. */
    public static final double PLATE_DEPTH = 2 / 16d;

    private static final AxisAlignedBB SHAPE_NORTH = new AxisAlignedBB(MIN_X, MIN_Y, 1 - PLATE_DEPTH, MAX_X, MAX_Y, 1);
    private static final AxisAlignedBB SHAPE_SOUTH = new AxisAlignedBB(MIN_X, MIN_Y, 0, MAX_X, MAX_Y, PLATE_DEPTH);
    private static final AxisAlignedBB SHAPE_WEST = new AxisAlignedBB(1 - PLATE_DEPTH, MIN_Y, MIN_X, 1, MAX_Y, MAX_X);
    private static final AxisAlignedBB SHAPE_EAST = new AxisAlignedBB(0, MIN_Y, MIN_X, PLATE_DEPTH, MAX_Y, MAX_X);

    public RemoteIndicatorBlock(BlockProperties properties){
        super(false, properties);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
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
    public IBlockState getStateForPlacement(World level, BlockPos pos, EnumFacing hitSide, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand){
        // Mount on the wall that was clicked; clicking a floor or ceiling falls back to facing the
        // placer, which is what a player expects when they aim at the ground.
        EnumFacing facing = hitSide.getAxis().isHorizontal() ? hitSide : placer.getHorizontalFacing().getOpposite();
        return this.getDefaultState().withProperty(FACING, facing);
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

    // --- Shape and state -------------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        switch(state.getValue(FACING)){
            case SOUTH:
                return SHAPE_SOUTH;
            case WEST:
                return SHAPE_WEST;
            case EAST:
                return SHAPE_EAST;
            default:
                return SHAPE_NORTH;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        // No collision, like a sign -- a plate this thin should never nudge a player off a landing.
        return NULL_AABB;
    }

    @Override
    protected BlockStateContainer createBlockState(){
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta){
        return this.getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state){
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, net.minecraft.util.Rotation rotation){
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, net.minecraft.util.Mirror mirror){
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
    public net.minecraft.block.material.EnumPushReaction getMobilityFlag(IBlockState state){
        return net.minecraft.block.material.EnumPushReaction.BLOCK;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World level, BlockPos pos, Block block, BlockPos fromPos){
        // Pop off when the wall it is mounted on goes away, like a torch or a sign.
        EnumFacing facing = state.getValue(FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        if(!level.isSideSolid(supportPos, facing) && !level.getBlockState(supportPos).isFullCube()){
            this.dropBlockAsItem(level, pos, state, 0);
            level.setBlockToAir(pos);
        }
    }
}
