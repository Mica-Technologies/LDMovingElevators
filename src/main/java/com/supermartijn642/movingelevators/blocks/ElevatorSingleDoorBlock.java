package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.block.BlockProperties;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A 1x2 sliding doorway: a single leaf that retracts into one side of the frame, two blocks tall.
 * <p>
 * The narrow counterpart to {@link ElevatorDoorBlock}, for service lifts and tight landings. Same
 * behaviour in every other respect -- it opens when the cabin arrives at its floor and closes on its
 * own -- so all of that lives in {@link ElevatorDoorBlockBase}.
 */
public class ElevatorSingleDoorBlock extends ElevatorDoorBlockBase {

    public ElevatorSingleDoorBlock(BlockProperties properties){
        super(properties);
        this.setDefaultState(this.blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockPos[] cellsFromOrigin(BlockPos origin, IBlockState state){
        return new BlockPos[]{origin, origin.up()};
    }

    @Override
    protected IBlockState stateForCell(IBlockState placedState, BlockPos origin, BlockPos cell){
        return placedState;
    }

    @Override
    protected AxisAlignedBB openShapeFacingNorth(IBlockState state){
        return new AxisAlignedBB(0, 0, MIN_Z, LEAF_REMAINDER, 1, MAX_Z);
    }

    @Override
    protected String getTooltipKey(){
        return "movingelevators.elevator_single_door.tooltip";
    }

    @Override
    public IBlockState getStateForPlacement(World level, BlockPos pos, EnumFacing hitSide, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand){
        return this.getDefaultState()
            .withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    protected BlockStateContainer createBlockState(){
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta){
        return this.getDefaultState()
            .withProperty(FACING, EnumFacing.getHorizontal(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state){
        return state.getValue(FACING).getHorizontalIndex();
    }
}
