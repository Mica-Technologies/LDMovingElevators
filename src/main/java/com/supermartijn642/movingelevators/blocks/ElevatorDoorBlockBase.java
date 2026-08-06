package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BaseBlock;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.core.block.EntityHoldingBlock;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared behaviour for elevator doorways, whichever size they are: two blocks tall, placed as one
 * item, removed as one unit, and bound to an elevator like the other fixtures.
 * <p>
 * Every block of a doorway carries its own block entity and reaches its own conclusion about being
 * open. They always agree, since they share an elevator and a landing, and that is markedly simpler
 * than nominating a master and forwarding state to followers.
 * <p>
 * Created for the Mica Technologies fork.
 */
public abstract class ElevatorDoorBlockBase extends BaseBlock implements EntityHoldingBlock {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    /** Depth of the leaves within the block, so they sit in the middle of the frame. */
    protected static final double MIN_Z = 7 / 16d, MAX_Z = 9 / 16d;

    /** How much of a leaf is still showing once it has retracted into the frame, in sixteenths. */
    protected static final double LEAF_REMAINDER = 3 / 16d;

    /** Authored facing north, then rotated to match the block state's model rotation. */
    private static final AxisAlignedBB CLOSED = new AxisAlignedBB(0, 0, MIN_Z, 1, 1, MAX_Z);

    /**
     * Guards the cascade when one part of a doorway is broken and takes the rest with it -- each of
     * those removals fires this block's break handling again.
     */
    private static boolean removingDoorway = false;

    /** A doorway is at most four blocks, so a walk that finds more has strayed into a neighbour. */
    private static final int MAX_DOORWAY_CELLS = 4;

    protected ElevatorDoorBlockBase(BlockProperties properties){
        super(false, properties);
    }

    @Override
    public TileEntity createNewBlockEntity(){
        return new ElevatorDoorBlockEntity();
    }

    /** The direction along the doorway's width, from its origin block. */
    protected static EnumFacing sideOf(IBlockState state){
        return state.getValue(FACING).rotateY();
    }

    /**
     * @return every block of the doorway that {@code origin} is the bottom, near corner of
     */
    protected abstract BlockPos[] cellsFromOrigin(BlockPos origin, IBlockState state);

    /**
     * @return the state the given cell should take when the doorway is placed
     */
    protected abstract IBlockState stateForCell(IBlockState placedState, BlockPos origin, BlockPos cell);

    /**
     * Walks back to the doorway's bottom, near corner. The upper half is read from the block entity
     * rather than the block state -- see {@link ElevatorDoorBlockEntity#isTop()} for why.
     */
    protected BlockPos originOf(IBlockAccess level, BlockPos pos, IBlockState state){
        TileEntity entity = level.getTileEntity(pos);
        boolean top = entity instanceof ElevatorDoorBlockEntity && ((ElevatorDoorBlockEntity)entity).isTop();
        return top ? pos.down() : pos;
    }

    /**
     * Every door block reachable from this one, found by walking neighbours rather than by computing
     * the doorway from an origin.
     * <p>
     * Deliberately independent of which half a block believes it is. The origin calculation depends
     * on that flag, and when it was wrong the two halves disagreed about their own doorway -- power
     * at the bottom opened only the bottom, while power at the top opened everything, because each
     * block was looking at a different set of neighbours. A walk cannot disagree with itself.
     */
    private Set<BlockPos> connectedCells(World level, BlockPos pos, IBlockState state){
        EnumFacing facing = state.getValue(FACING);
        EnumFacing side = sideOf(state);
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        found.add(pos);
        pending.add(pos);
        while(!pending.isEmpty() && found.size() < MAX_DOORWAY_CELLS){
            BlockPos current = pending.removeFirst();
            for(BlockPos next : new BlockPos[]{current.up(), current.down(), current.offset(side), current.offset(side.getOpposite())}){
                if(found.contains(next))
                    continue;
                IBlockState neighbour = level.getBlockState(next);
                if(neighbour.getBlock() == this && neighbour.getValue(FACING) == facing){
                    found.add(next);
                    pending.add(next);
                }
            }
        }
        return found;
    }

    /**
     * @return whether any block of this doorway is receiving redstone power
     */
    public boolean isDoorwayPowered(World level, BlockPos pos, IBlockState state){
        for(BlockPos cell : this.connectedCells(level, pos, state))
            if(level.isBlockPowered(cell))
                return true;
        return false;
    }

    @Override
    public void onBlockPlacedBy(World level, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
        super.onBlockPlacedBy(level, pos, state, placer, stack);
        BlockPos[] cells = this.cellsFromOrigin(pos, state);

        // A doorway needs all of its blocks or it is nonsense, so refuse rather than place a fragment.
        for(BlockPos cell : cells){
            if(cell.equals(pos))
                continue;
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

        for(BlockPos cell : cells)
            if(!cell.equals(pos))
                level.setBlockState(cell, this.stateForCell(state, pos, cell), 3);

        // Every part carries the binding and knows which half it is, so each resolves the elevator
        // and its landing on its own.
        NBTTagCompound compound = stack.getTagCompound();
        BlockPos controllerPos = compound != null && compound.hasKey("controllerDim")
            ? new BlockPos(compound.getInteger("controllerX"), compound.getInteger("controllerY"), compound.getInteger("controllerZ")) : null;
        EnumFacing controllerFacing = controllerPos != null && compound.hasKey("controllerFacing", Constants.NBT.TAG_INT)
            ? EnumFacing.getHorizontal(compound.getInteger("controllerFacing")) : null;
        for(BlockPos cell : cells){
            TileEntity entity = level.getTileEntity(cell);
            if(entity instanceof ElevatorDoorBlockEntity){
                ((ElevatorDoorBlockEntity)entity).setTop(cell.getY() > pos.getY());
                if(controllerPos != null)
                    ((ElevatorDoorBlockEntity)entity).setValues(controllerPos, controllerFacing);
            }
        }
    }

    @Override
    public void breakBlock(World level, BlockPos pos, IBlockState state){
        if(!removingDoorway){
            removingDoorway = true;
            try{
                for(BlockPos cell : this.cellsFromOrigin(this.originOf(level, pos, state), state)){
                    if(!cell.equals(pos) && level.getBlockState(cell).getBlock() == this)
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
        // Reported from the server, because the server's answers are the ones that decide whether the
        // doors move. A door that silently does nothing gives a player -- and anyone debugging it --
        // no way to tell which of binding, landing or cabin position is the missing piece.
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof ElevatorDoorBlockEntity && !level.isRemote)
            ((ElevatorDoorBlockEntity)entity).reportStatus(player);
        return InteractionFeedback.SUCCESS;
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        NBTTagCompound tag = stack.getTagCompound();
        if(tag == null || !tag.hasKey("controllerDim"))
            info.accept(TextComponents.translation(this.getTooltipKey()).color(TextFormatting.AQUA).get());
        else{
            ITextComponent x = TextComponents.number(tag.getInteger("controllerX")).color(TextFormatting.GOLD).get();
            ITextComponent y = TextComponents.number(tag.getInteger("controllerY")).color(TextFormatting.GOLD).get();
            ITextComponent z = TextComponents.number(tag.getInteger("controllerZ")).color(TextFormatting.GOLD).get();
            ITextComponent dimension = TextComponents.dimension(DimensionType.getById(tag.getInteger("controllerDim"))).color(TextFormatting.GOLD).get();
            info.accept(TextComponents.translation("movingelevators.remote_controller.tooltip.bound", x, y, z, dimension).get());
        }
    }

    protected abstract String getTooltipKey();

    // --- Shape and state -------------------------------------------------------------------------

    /**
     * The shape a leaf takes once open, authored facing north. Which side it retracts to depends on
     * the door, so subclasses decide.
     */
    protected abstract AxisAlignedBB openShapeFacingNorth(IBlockState state);

    /**
     * Rotates a box authored facing north to the state's facing, matching the {@code y} rotation the
     * block state applies to the model. Ninety degrees clockwise from above maps (x, z) to (1 - z, x).
     */
    private static AxisAlignedBB rotate(AxisAlignedBB box, EnumFacing facing){
        int quarters = facing == EnumFacing.EAST ? 1 : facing == EnumFacing.SOUTH ? 2 : facing == EnumFacing.WEST ? 3 : 0;
        for(int i = 0; i < quarters; i++)
            box = new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
        return box;
    }

    /**
     * The leaf's shape part-way through opening, in world orientation.
     * <p>
     * The outline, the collision and the drawn leaf all come through here, so an animated door cannot
     * end up looking like one thing and behaving like another. Only the inner edge moves: the outer
     * one stays put against the frame, which is what makes it read as sliding into a pocket rather
     * than shrinking.
     *
     * @param progress 0 fully closed, 1 fully open
     */
    public AxisAlignedBB shapeForProgress(IBlockState state, float progress){
        AxisAlignedBB open = this.openShapeFacingNorth(state);
        AxisAlignedBB box = progress <= 0 ? CLOSED : progress >= 1 ? open : new AxisAlignedBB(
            CLOSED.minX + (open.minX - CLOSED.minX) * progress, CLOSED.minY, CLOSED.minZ,
            CLOSED.maxX + (open.maxX - CLOSED.maxX) * progress, CLOSED.maxY, CLOSED.maxZ);
        return rotate(box, state.getValue(FACING));
    }

    /**
     * @return whether the door at this position is open, according to its block entity
     */
    public static boolean isOpen(IBlockAccess level, BlockPos pos){
        TileEntity entity = level.getTileEntity(pos);
        return entity instanceof ElevatorDoorBlockEntity && ((ElevatorDoorBlockEntity)entity).isOpen();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        // The outline has to follow the leaf. Returning the closed shape while open drew a box around
        // the empty doorway you had just walked through.
        return this.shapeForProgress(state, isOpen(level, pos) ? 1 : 0);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess level, BlockPos pos){
        // Open doors are a doorway, not a wall.
        return isOpen(level, pos) ? NULL_AABB : this.getBoundingBox(state, level, pos);
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
