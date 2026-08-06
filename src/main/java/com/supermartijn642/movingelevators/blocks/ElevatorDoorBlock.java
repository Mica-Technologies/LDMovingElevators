package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.BlockProperties;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A 2x2 sliding doorway: two leaves that meet in the middle and retract into either side of the
 * frame, two blocks tall to walk through.
 */
public class ElevatorDoorBlock extends ElevatorDoorBlockBase {

    /** Which leaf this is. The two retract in opposite directions. */
    public static final PropertyBool RIGHT = PropertyBool.create("right");

    public ElevatorDoorBlock(BlockProperties properties){
        super(properties);
        this.setDefaultState(this.blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH).withProperty(RIGHT, false).withProperty(OPEN, false));
    }

    @Override
    protected BlockPos[] cellsFromOrigin(BlockPos origin, IBlockState state){
        EnumFacing side = sideOf(state);
        return new BlockPos[]{origin, origin.offset(side), origin.up(), origin.up().offset(side)};
    }

    @Override
    protected IBlockState stateForCell(IBlockState placedState, BlockPos origin, BlockPos cell){
        // The far column is the right leaf; both columns exist at both heights.
        boolean right = !cell.equals(origin) && !cell.equals(origin.up());
        return placedState.withProperty(RIGHT, right);
    }

    @Override
    protected BlockPos originOf(net.minecraft.world.IBlockAccess level, BlockPos pos, IBlockState state){
        BlockPos origin = super.originOf(level, pos, state);
        return state.getValue(RIGHT) ? origin.offset(sideOf(state).getOpposite()) : origin;
    }

    @Override
    protected AxisAlignedBB openShapeFacingNorth(IBlockState state){
        // The two leaves retract to opposite sides, so their outlines part the same way the models do.
        return state.getValue(RIGHT)
            ? new AxisAlignedBB(1 - LEAF_REMAINDER, 0, MIN_Z, 1, 1, MAX_Z)
            : new AxisAlignedBB(0, 0, MIN_Z, LEAF_REMAINDER, 1, MAX_Z);
    }

    @Override
    protected String getTooltipKey(){
        return "movingelevators.elevator_door.tooltip";
    }

    @Override
    public IBlockState getStateForPlacement(World level, BlockPos pos, EnumFacing hitSide, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand){
        return this.getDefaultState()
            .withProperty(FACING, placer.getHorizontalFacing().getOpposite())
            .withProperty(RIGHT, false)
            .withProperty(OPEN, false);
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
}
