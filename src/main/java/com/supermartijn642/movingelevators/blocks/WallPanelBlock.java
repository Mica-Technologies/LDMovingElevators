package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.BaseBlock;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.core.block.EntityHoldingBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A thin plate mounted flat on the face of the block behind it, the way real elevator fixtures sit
 * on a wall. Shared by the slim floor indicator and the landing call panel.
 * <p>
 * The one thing worth internalising: <b>the plate hugs the wall it is mounted on, which is the
 * neighbour opposite the way it faces.</b> A north-facing plate is read from the north, so its wall
 * is the south neighbour and the metal sits at high Z. Building it the other way round leaves the
 * plate floating a block clear of the wall -- which is exactly what the first version did, in the
 * model, the hitboxes and the label plane all at once. Keeping the geometry here means that only
 * has to be right in one place.
 * <p>
 * Created for the Mica Technologies fork.
 */
public abstract class WallPanelBlock extends BaseBlock implements EntityHoldingBlock {

    /** The direction the plate faces, i.e. away from the wall it is mounted on. */
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    /** Thickness of every panel. Renderers use it to place their contents just proud of the metal. */
    public static final double PLATE_DEPTH = 2 / 16d;

    private final AxisAlignedBB shapeNorth, shapeSouth, shapeWest, shapeEast;

    /**
     * @param minHorizontal edge of the plate across its face, in block units
     * @param minVertical   bottom of the plate, in block units
     */
    protected WallPanelBlock(BlockProperties properties, double minHorizontal, double maxHorizontal, double minVertical, double maxVertical){
        super(false, properties);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        this.shapeNorth = new AxisAlignedBB(minHorizontal, minVertical, 1 - PLATE_DEPTH, maxHorizontal, maxVertical, 1);
        this.shapeSouth = new AxisAlignedBB(minHorizontal, minVertical, 0, maxHorizontal, maxVertical, PLATE_DEPTH);
        this.shapeWest = new AxisAlignedBB(1 - PLATE_DEPTH, minVertical, minHorizontal, 1, maxVertical, maxHorizontal);
        this.shapeEast = new AxisAlignedBB(0, minVertical, minHorizontal, PLATE_DEPTH, maxVertical, maxHorizontal);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        switch(state.getValue(FACING)){
            case SOUTH:
                return this.shapeSouth;
            case WEST:
                return this.shapeWest;
            case EAST:
                return this.shapeEast;
            default:
                return this.shapeNorth;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        // No collision, like a sign -- a plate this thin should never nudge a player off a landing.
        return NULL_AABB;
    }

    @Override
    public IBlockState getStateForPlacement(World level, BlockPos pos, EnumFacing hitSide, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand){
        // Mount on the wall that was clicked; clicking a floor or ceiling falls back to facing the
        // placer, which is what a player expects when they aim at the ground.
        EnumFacing facing = hitSide.getAxis().isHorizontal() ? hitSide : placer.getHorizontalFacing().getOpposite();
        return this.getDefaultState().withProperty(FACING, facing);
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
