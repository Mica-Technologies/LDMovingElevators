package com.supermartijn642.movingelevators.elevator;

import com.google.common.collect.Streams;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockShape;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.extensions.MovingElevatorsLevelChunk;
import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockPressurePlate;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.IFluidBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created 8/6/2021 by SuperMartijn642
 */
public class ElevatorCage {

    public static ElevatorCage createCageAndClear(World level, BlockPos startPos, int xSize, int ySize, int zSize){
        if(!canCreateCage(level, startPos, xSize, ySize, zSize, null))
            return null;

        IBlockState[][][] states = new IBlockState[xSize][ySize][zSize];
        NBTTagCompound[][][] entities = new NBTTagCompound[xSize][ySize][zSize];
        NBTTagCompound[][][] entityItemStacks = new NBTTagCompound[xSize][ySize][zSize];
        BlockShape shape = BlockShape.empty();

        for(int x = 0; x < xSize; x++){
            for(int y = 0; y < ySize; y++){
                for(int z = 0; z < zSize; z++){
                    BlockPos pos = startPos.add(x, y, z);
                    if(canBlockBeIgnored(level, pos))
                        continue;
                    states[x][y][z] = level.getBlockState(pos);
                    AxisAlignedBB boundingBox = states[x][y][z].getCollisionBoundingBox(level, pos);
                    BlockShape blockShape = boundingBox == null ? BlockShape.empty() : BlockShape.create(boundingBox);
                    blockShape = blockShape.offset(x, y, z);
                    shape = BlockShape.or(shape, blockShape);
                    TileEntity entity = level.getTileEntity(pos);
                    if(entity != null){
                        NBTTagCompound tag = entity.serializeNBT();
                        tag.setInteger("x", x);
                        tag.setInteger("y", y);
                        tag.setInteger("z", z);
                        entities[x][y][z] = tag;
                        // Create an item to drop in case the block can't be placed back
                        ItemStack stack = new ItemStack(states[x][y][z].getBlock());
                        tag = tag.copy();
                        tag.removeTag("x");
                        tag.removeTag("y");
                        tag.removeTag("z");
                        stack.setTagInfo("BlockEntityTag", tag);
                        NBTTagCompound displayTag = new NBTTagCompound();
                        NBTTagList loreTag = new NBTTagList();
                        loreTag.appendTag(new NBTTagString("(+NBT)"));
                        displayTag.setTag("Lore", loreTag);
                        stack.setTagInfo("display", displayTag);
                        entityItemStacks[x][y][z] = stack.serializeNBT();
                    }
                }
            }
        }

        for(int x = 0; x < xSize; x++){
            for(int y = 0; y < ySize; y++){
                for(int z = 0; z < zSize; z++){
                    BlockPos pos = startPos.add(x, y, z);
                    if(states[x][y][z] == null)
                        continue;
                    TileEntity entity = level.getTileEntity(pos);
                    if(entity != null)
                        level.removeTileEntity(pos);
                    // Suppress any block updates
                    Chunk chunk = level.getChunkFromBlockCoords(pos);
                    //noinspection ConstantValue
                    if(chunk != null){
                        ((MovingElevatorsLevelChunk)chunk).movingElevatorsSuppressBlockUpdates(true);
                        try{
                            level.setBlockState(pos, Blocks.AIR.getDefaultState(), 4 | 16);
                        }finally{
                            ((MovingElevatorsLevelChunk)chunk).movingElevatorsSuppressBlockUpdates(false);
                        }
                    }else
                        level.setBlockState(pos, Blocks.AIR.getDefaultState(), 4 | 16);
                }
            }
        }

        for(int x = 0; x < xSize; x++){
            for(int y = 0; y < ySize; y++){
                for(int z = 0; z < zSize; z++){
                    BlockPos pos = startPos.add(x, y, z);
                    IBlockState state = states[x][y][z];
                    if(state == null)
                        continue;
                    state.getBlock().breakBlock(level, pos, state);
                    level.markAndNotifyBlock(pos, level.getChunkFromBlockCoords(pos), states[x][y][z], level.getBlockState(pos), 1 | 2);
                }
            }
        }

        // TODO reduce the number of bounding boxes
//        shape.optimize();

        return level.isRemote ?
            new ClientElevatorCage(xSize, ySize, zSize, states, entities, entityItemStacks, shape.toBoxes()) :
            new ElevatorCage(xSize, ySize, zSize, states, entities, entityItemStacks, shape.toBoxes());
    }

    /** Lazily worked out from the cage's own blocks; -1 until asked for. */
    private int cachedLightLevel = -1;

    /**
     * The brightest light any block in this cabin gives off.
     * <p>
     * A cabin's blocks are taken out of the world while it moves, so anything luminous inside it
     * stops lighting the world -- a glowstone floor goes dark the moment the doors shut. This is what
     * the cabin's own lighting is floored to instead.
     */
    public int getLightLevel(){
        if(this.cachedLightLevel < 0){
            int brightest = 0;
            for(IBlockState[][] column : this.blockStates)
                for(IBlockState[] row : column)
                    for(IBlockState state : row)
                        if(state != null)
                            brightest = Math.max(brightest, state.getLightValue());
            this.cachedLightLevel = brightest;
        }
        return this.cachedLightLevel;
    }

    /** Lazily worked out from the cage's own blocks; null until asked for. */
    private Boolean cachedHasInterior;

    /**
     * Whether the cage has anywhere inside it to stand -- a cell of its own that something could be
     * in, holding it up.
     * <p>
     * This is the difference between a cabin and a platform, and it decides where this cage's
     * passengers are. A cabin is ridden from inside, so anything on its top face is on its roof; a
     * platform has no inside, so its top face is where its passengers stand and being there is what
     * riding it means. Any rule about the top of a cage has to know which of the two it has.
     * <p>
     * A cage needs only one block to exist, so this cannot be asked as "is it solid": a platform is
     * routinely a few blocks in a larger footprint, all of it empty cells. What makes an inside is an
     * empty cell <i>resting on</i> a filled one -- somewhere the cage itself would hold a passenger
     * up. The empty cells beside a platform are not that; you would fall straight through them.
     */
    public boolean hasInterior(){
        if(this.cachedHasInterior == null){
            boolean interior = false;
            for(int x = 0; !interior && x < this.xSize; x++)
                for(int y = 1; !interior && y < this.ySize; y++)
                    for(int z = 0; !interior && z < this.zSize; z++)
                        interior = !this.isCellFilled(x, y, z) && this.isCellOccupied(x, y - 1, z);
            this.cachedHasInterior = interior;
        }
        return this.cachedHasInterior;
    }

    /**
     * Whether a whole cell of the cage is taken up by collision -- so there is no standing in it.
     * Partial coverage is not enough: a cell holding a slab or a carpet is still one a passenger
     * occupies, standing a little higher.
     */
    private boolean isCellFilled(int x, int y, int z){
        for(AxisAlignedBB box : this.collisionBoxes)
            if(box.minX <= x && box.maxX >= x + 1
                && box.minY <= y && box.maxY >= y + 1
                && box.minZ <= z && box.maxZ >= z + 1)
                return true;
        return false;
    }

    /** Whether a cell of the cage has any collision in it at all -- enough to stand on. */
    private boolean isCellOccupied(int x, int y, int z){
        for(AxisAlignedBB box : this.collisionBoxes)
            if(box.minX < x + 1 && box.maxX > x
                && box.minY < y + 1 && box.maxY > y
                && box.minZ < z + 1 && box.maxZ > z)
                return true;
        return false;
    }

    public static boolean canCreateCage(World level, BlockPos startPos, int xSize, int ySize, int zSize, EntityPlayer requester){
        boolean hasBlocks = false;
        for(int x = 0; x < xSize; x++){
            for(int y = 0; y < ySize; y++){
                for(int z = 0; z < zSize; z++){
                    if(canBlockBeIgnored(level, startPos.add(x, y, z)))
                        continue;
                    if(!canBlockBeInCage(level, startPos.add(x, y, z))){
                        if(requester instanceof EntityPlayerMP){
                            ITextComponent block = TextComponents.block(level.getBlockState(startPos.add(x, y, z)).getBlock()).color(TextFormatting.GOLD).get();
                            ITextComponent position = TextComponents.string("(").color(TextFormatting.GRAY)
                                .append(TextComponents.number(startPos.getX() + x).color(TextFormatting.GOLD).get()).string(",").color(TextFormatting.GRAY)
                                .append(TextComponents.number(startPos.getY() + y).color(TextFormatting.GOLD).get()).string(",").color(TextFormatting.GRAY)
                                .append(TextComponents.number(startPos.getZ() + z).color(TextFormatting.GOLD).get()).string(")").color(TextFormatting.GRAY).get();
                            requester.sendStatusMessage(TextComponents.translation("movingelevators.elevator.invalid_block", block, position).color(TextFormatting.GRAY).get(), false);
                        }
                        return false;
                    }
                    hasBlocks = true;
                }
            }
        }
        if(!hasBlocks && requester instanceof EntityPlayerMP)
            requester.sendStatusMessage(TextComponents.translation("movingelevators.elevator.empty").color(TextFormatting.GRAY).get(), false);
        return hasBlocks;
    }

    public static boolean canBlockBeIgnored(World level, BlockPos pos){
        return level.isAirBlock(pos);
    }

    public static boolean canBlockBeInCage(World level, BlockPos pos){
        IBlockState state = level.getBlockState(pos);
        return !(state.getBlock() instanceof IFluidBlock) && (state.getBlockHardness(level, pos) >= 0 || MovingElevatorsConfig.allowUnbreakableBlocks.get());
    }

    public final int xSize, ySize, zSize;
    public final IBlockState[][][] blockStates;
    public final NBTTagCompound[][][] blockEntityData;
    public final NBTTagCompound[][][] blockEntityStacks;
    public final BlockShape shape;
    public final List<AxisAlignedBB> collisionBoxes;
    public final AxisAlignedBB bounds;

    public ElevatorCage(int xSize, int ySize, int zSize, IBlockState[][][] states, NBTTagCompound[][][] blockEntityData, NBTTagCompound[][][] blockEntityStacks, List<AxisAlignedBB> collisionBoxes){
        this.blockEntityData = blockEntityData;
        this.blockEntityStacks = blockEntityStacks;
        if(states.length != xSize || states[0].length != ySize || states[0][0].length != zSize)
            throw new IllegalArgumentException("Given size and block state array do not match!");
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;
        this.blockStates = states;
        this.collisionBoxes = Collections.unmodifiableList(collisionBoxes);
        BlockShape shape = BlockShape.empty();
        double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for(AxisAlignedBB box : collisionBoxes){
            shape = BlockShape.or(shape, BlockShape.create(box));
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }
        // TODO reduce the number of bounding boxes
//        this.shape = shape.optimize();
        this.shape = shape;
        this.bounds = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * @param travelDirection which way the cabin was moving when it came to rest: positive up, negative
     *                        down, zero unknown. Anything caught in the blocks about to be placed is
     *                        shoved that way -- see {@link #pushEntitiesClear}.
     */
    public void place(World level, BlockPos startPos, double travelDirection){
        // Before a single block is set, because a cell filled early would trap an entity the sweep has
        // not looked at yet.
        this.pushEntitiesClear(level, startPos, travelDirection);

        IBlockState[][][] oldStates = new IBlockState[this.xSize][this.ySize][this.zSize];
        for(int x = 0; x < this.xSize; x++){
            for(int y = 0; y < this.ySize; y++){
                for(int z = 0; z < this.zSize; z++){
                    IBlockState state = this.blockStates[x][y][z];
                    if(state == null)
                        continue;
                    BlockPos pos = startPos.add(x, y, z);
                    IBlockState currentState = level.getBlockState(pos);
                    if(canBlockBeIgnored(level, pos) || currentState.getBlockHardness(level, pos) >= 0){
                        oldStates[x][y][z] = currentState;
                        if(!level.isAirBlock(pos))
                            level.destroyBlock(pos, true);
                        // Suppress any block updates
                        Chunk chunk = level.getChunkFromBlockCoords(pos);
                        //noinspection ConstantValue
                        if(chunk != null){
                            ((MovingElevatorsLevelChunk)chunk).movingElevatorsSuppressBlockUpdates(true);
                            try{
                                level.setBlockState(pos, state, 4 | 16);
                            }finally{
                                ((MovingElevatorsLevelChunk)chunk).movingElevatorsSuppressBlockUpdates(false);
                            }
                        }else
                            level.setBlockState(pos, state, 4 | 16);
                        if(this.blockEntityData[x][y][z] != null){
                            TileEntity entity = TileEntity.create(level, this.blockEntityData[x][y][z]);
                            if(entity != null)
                                level.setTileEntity(pos, entity);
                        }
                    }else{
                        NBTTagCompound itemTag = this.blockEntityStacks[x][y][z];
                        ItemStack stack = itemTag == null ? new ItemStack(state.getBlock()) : new ItemStack(itemTag);
                        InventoryHelper.spawnItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    }
                }
            }
        }
        // Now update all block on the edges of the cage
        for(int x = 0; x < this.xSize; x++){
            for(int y = 0; y < this.ySize; y++){
                for(int z = 0; z < this.zSize; z++){
                    IBlockState previousState = oldStates[x][y][z];
                    if(previousState == null)
                        continue;
                    BlockPos pos = startPos.add(x, y, z);
                    IBlockState state = level.getBlockState(pos);

                    // Update the block itself
                    state.getBlock().onBlockAdded(level, pos, state);

                    // Update neighboring blocks that are not part of the elevator cage
                    boolean isOnTheBoundary = x == 0 || x == this.xSize - 1
                        || y == 0 || y == this.ySize - 1
                        || z == 0 || z == this.zSize - 1;
                    int flags = isOnTheBoundary ? 3 : 2 | 4 | 16;
                    level.markAndNotifyBlock(pos, level.getChunkFromBlockCoords(pos), previousState, state, flags);

                    // Special case for buttons and pressure plates to prevent them getting stuck
                    if(!level.isRemote
                        && state.getBlock() instanceof BlockButton
                        && state.getValue(BlockButton.POWERED))
                        state.getBlock().updateTick(level, pos, state, level.rand);
                    if(!level.isRemote
                        && state.getBlock() instanceof BlockPressurePlate
                        && state.getValue(BlockPressurePlate.POWERED))
                        state.getBlock().updateTick(level, pos, state, level.rand);

                    // Redstone wire only updates indirect neighbors when its power changes, not when it gets placed with a certain power
                    // Hence, special case for redstone wire
                    if(state.getBlock() == Blocks.REDSTONE_WIRE){
                        boolean[] updateDirections = new boolean[6];
                        updateDirections[4] = x == 0;
                        updateDirections[5] = x == this.xSize - 1;
                        updateDirections[0] = y == 0;
                        updateDirections[1] = y == this.ySize - 1;
                        updateDirections[2] = z == 0;
                        updateDirections[3] = z == this.zSize - 1;
                        for(int i = 0; i < updateDirections.length; i++){
                            if(updateDirections[i]){
                                EnumFacing direction = EnumFacing.values()[i];
                                level.notifyNeighborsOfStateChange(pos.offset(direction), Blocks.REDSTONE_WIRE, false);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * How deep an overlap has to be, in every axis, before it counts as being inside a block rather
     * than standing against one. Comfortably more than an entity settles into a platform carrying it,
     * and far less than the half-block or more of a body genuinely caught in masonry.
     */
    private static final double PENETRATION_TOLERANCE = 0.08;
    /**
     * How far past the cabin's face a shoved entity is set down. Flush would do geometrically, but a
     * position exactly on the boundary resolves into the blocks about as readily as out of them, and
     * placement is a one-shot -- nothing runs afterwards to catch an entity that landed wrong.
     */
    private static final double PUSH_CLEARANCE = 0.01;
    /**
     * How far something may have sunk into a surface and still count as standing on it rather than
     * being inside it.
     * <p>
     * A block: that is "its feet are somewhere in the cell it is standing on". Anything deeper is not
     * resting on the cabin, it is in the middle of it, and belongs to the eviction below.
     */
    private static final double SETTLE_DEPTH = 1;

    /**
     * Shoves entities out of the cells the cabin is about to fill with blocks.
     * <p>
     * The cabin materialises wherever it stops, and the emergency stop exists precisely to bring it to
     * a floor <i>because</i> somebody is standing in the shaft -- so without this the safety feature
     * entombs the very person it fired for. A player who logged out inside the cabin and rejoins after
     * it has moved, and mobs that wandered in, arrive in the same predicament.
     * <p>
     * Entities go the way the cabin was travelling, like a piston, and clear of the whole cage rather
     * than of the one cell they were caught in -- clearing the cell alone would just hand them to the
     * next cell along. The hollow interior is left alone on purpose: those cells receive no block, so
     * passengers riding the cabin are intersecting nothing and must not be thrown out of their own lift.
     */
    private void pushEntitiesClear(World level, BlockPos startPos, double travelDirection){
        AxisAlignedBB cage = new AxisAlignedBB(startPos, startPos.add(this.xSize, this.ySize, this.zSize));
        // An unknown direction goes up, because that leaves whoever was in the way standing on the roof
        // rather than dropped down the shaft they were just rescued from.
        boolean pushUp = travelDirection >= 0;
        double clearAbove = startPos.getY() + this.ySize + PUSH_CLEARANCE;
        double clearBelow = startPos.getY() - PUSH_CLEARANCE;

        // One query over the whole cage rather than one per cell: a cabin runs to hundreds of cells and
        // the shaft is empty for almost all of them.
        for(Entity entity : level.getEntitiesInAABBexcluding(null, cage, ElevatorCage::canBePushedAside)){
            if(!this.intersectsSolidBlock(startPos, entity.getEntityBoundingBox()))
                continue;
            // Standing on it, not buried in it: set it down on the surface it had sunk into and leave
            // it where it was. Evicting these was the roof-teleport -- a passenger's position on the
            // server is a network round trip stale, so on arrival their feet sit that much of a tick's
            // travel inside the cabin floor they are riding on, and the eviction below then threw them
            // out of their own lift onto its roof.
            double surface = this.restingSurface(startPos, entity);
            if(!Double.isNaN(surface)){
                // setPosition, not setPositionAndUpdate: a passenger's client already has them standing
                // on the floor and it is only this side's copy of them that is out of date, so a
                // teleport here would yank somebody who is in exactly the right place.
                entity.setPosition(entity.posX, surface, entity.posZ);
                entity.motionY = 0;
                entity.fallDistance = 0;
                continue;
            }
            double y = pushUp ? clearAbove : clearBelow - entity.height;
            // setPositionAndUpdate rather than setPosition, because on a player the former goes out over
            // the connection and actually tells the client it has been moved; a bare reposition leaves
            // the client walking around at the old spot until the server rubber-bands it back.
            entity.setPositionAndUpdate(entity.posX, y, entity.posZ);
            // Its momentum was worked out for a place it is no longer in, and the distance it just
            // covered was the cabin's doing, so it must not read as a fall it can be hurt by.
            entity.motionY = 0;
            entity.fallDistance = 0;
        }
    }

    /**
     * The height something caught in the cabin's blocks should be set down at to be standing on top of
     * them, or {@link Double#NaN} if there is no such height and it has to be evicted instead.
     * <p>
     * The top of the highest cell it is in -- clearing the highest clears the rest -- but only if it
     * had sunk no further than {@link #SETTLE_DEPTH} into it and only if it would actually be free
     * standing there. Both matter: something the cabin came down on top of is buried by much more than
     * a settle and is not standing on anything, and a cabin whose interior is too low to hold the thing
     * upright has nowhere to put it, so both fall through to the eviction.
     */
    private double restingSurface(BlockPos startPos, Entity entity){
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double surface = Double.NaN;
        for(AxisAlignedBB solid : this.collisionBoxes){
            AxisAlignedBB other = solid.offset(startPos.getX(), startPos.getY(), startPos.getZ());
            if(box.maxX <= other.minX || box.minX >= other.maxX
                || box.maxZ <= other.minZ || box.minZ >= other.maxZ
                || box.maxY <= other.minY || box.minY >= other.maxY)
                continue;
            if(Double.isNaN(surface) || other.maxY > surface)
                surface = other.maxY;
        }
        if(Double.isNaN(surface) || surface - box.minY > SETTLE_DEPTH)
            return Double.NaN;
        return this.intersectsSolidBlock(startPos, box.offset(0, surface - box.minY, 0)) ? Double.NaN : surface;
    }

    /**
     * Whether the entity is genuinely buried in one of the cabin's blocks, as opposed to resting
     * against one.
     * <p>
     * Overlap has to be real in all three axes, not merely present. Standing on the cabin floor is an
     * overlap of nothing at all in Y and the full width in X and Z; so is leaning on a wall, in its
     * own axis. Treating contact as entombment evicted a cabin's own passengers onto its roof on
     * every arrival, since standing on the floor is what passengers do.
     * <p>
     * The tolerance also absorbs the fraction of a block an entity carried by a moving platform can
     * settle into it, which no exact test survives.
     */
    private boolean intersectsSolidBlock(BlockPos startPos, AxisAlignedBB box){
        for(AxisAlignedBB solid : this.collisionBoxes){
            AxisAlignedBB other = solid.offset(startPos.getX(), startPos.getY(), startPos.getZ());
            double overlapX = Math.min(box.maxX, other.maxX) - Math.max(box.minX, other.minX);
            double overlapY = Math.min(box.maxY, other.maxY) - Math.max(box.minY, other.minY);
            double overlapZ = Math.min(box.maxZ, other.maxZ) - Math.max(box.minZ, other.minZ);
            if(overlapX > PENETRATION_TOLERANCE && overlapY > PENETRATION_TOLERANCE && overlapZ > PENETRATION_TOLERANCE)
                return true;
        }
        return false;
    }

    /**
     * Whether the cabin closing around this entity is worth moving it for.
     * <p>
     * Spectators walk through blocks, a passenger goes wherever whatever carries it goes -- pulling it
     * out from under its own mount would only strand it -- and anything a piston refuses to move is not
     * ours to move either. Fake players have no connection to tell about it and nothing to suffocate.
     */
    private static boolean canBePushedAside(Entity entity){
        if(entity instanceof EntityPlayer && ((EntityPlayer)entity).isSpectator())
            return false;
        if(entity instanceof EntityPlayerMP && ((EntityPlayerMP)entity).connection == null)
            return false;
        return !entity.isRiding() && entity.getPushReaction() == EnumPushReaction.NORMAL;
    }

    public List<ItemStack> getDrops(){
        List<ItemStack> drops = new ArrayList<>();
        for(int x = 0; x < this.xSize; x++){
            for(int y = 0; y < this.ySize; y++){
                for(int z = 0; z < this.zSize; z++){
                    if(this.blockStates[x][y][z] == null)
                        continue;
                    if(this.blockEntityStacks[x][y][z] != null)
                        drops.add(new ItemStack(this.blockEntityStacks[x][y][z]));
                    else
                        drops.add(new ItemStack(this.blockStates[x][y][z].getBlock()));
                }
            }
        }
        return drops;
    }

    public NBTTagCompound write(){
        return this.write(false);
    }

    /**
     * @param forClient leave out what only the server uses. Mica: the cabin is sent to every player
     *                  in the dimension each time it departs, and two things in it never meant
     *                  anything to a client: each block entity's item-stack copy, which is only for
     *                  dropping the cabin as items and repeated that block entity's whole data, and
     *                  a compound with six named doubles for every collision box. Saves are
     *                  unaffected, and {@link #read} takes either form.
     */
    public NBTTagCompound write(boolean forClient){
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger("xSize", this.xSize);
        compound.setInteger("ySize", this.ySize);
        compound.setInteger("zSize", this.zSize);
        int[] stateIds = new int[this.xSize * this.ySize * this.zSize];
        NBTTagList entityData = new NBTTagList();
        for(int x = 0; x < this.xSize; x++){
            for(int y = 0; y < this.ySize; y++){
                for(int z = 0; z < this.zSize; z++){
                    int index = x * this.ySize * this.zSize + y * this.zSize + z;
                    IBlockState state = this.blockStates[x][y][z];
                    stateIds[index] = Block.getStateId(state == null ? Blocks.AIR.getDefaultState() : state);
                    if(this.blockEntityData[x][y][z] != null){
                        NBTTagCompound tag = new NBTTagCompound();
                        tag.setInteger("x", x);
                        tag.setInteger("y", y);
                        tag.setInteger("z", z);
                        tag.setTag("data", this.blockEntityData[x][y][z]);
                        if(!forClient && this.blockEntityStacks[x][y][z] != null)
                            tag.setTag("stack", this.blockEntityStacks[x][y][z]);
                        entityData.appendTag(tag);
                    }
                }
            }
        }
        compound.setIntArray("blockStates", stateIds);
        compound.setTag("entityData", entityData);
        if(forClient){
            // Each double's exact bits, as two ints: the client collides with these boxes too, so they
            // must not be rounded. (1.12's long array tag has no way to read its contents back.)
            int[] bits = new int[this.collisionBoxes.size() * 12];
            int i = 0;
            for(AxisAlignedBB box : this.collisionBoxes){
                for(double value : new double[]{box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ}){
                    long raw = Double.doubleToRawLongBits(value);
                    bits[i++] = (int)(raw >>> 32);
                    bits[i++] = (int)raw;
                }
            }
            compound.setIntArray("boxBits", bits);
        }else{
            NBTTagList collisionBoxList = new NBTTagList();
            this.collisionBoxes.forEach(box -> collisionBoxList.appendTag(writeBox(box)));
            compound.setTag("collisionBoxes", collisionBoxList);
        }
        return compound;
    }

    public static ElevatorCage read(NBTTagCompound compound, boolean isClientSide){
        int xSize = compound.getInteger("xSize");
        int ySize = compound.getInteger("ySize");
        int zSize = compound.getInteger("zSize");
        int[] stateIds = compound.getIntArray("blockStates");
        IBlockState[][][] blockStates = new IBlockState[xSize][ySize][zSize];
        for(int x = 0; x < xSize; x++){
            for(int y = 0; y < ySize; y++){
                for(int z = 0; z < zSize; z++){
                    int index = x * ySize * zSize + y * zSize + z;
                    IBlockState state = Block.getStateById(stateIds[index]);
                    blockStates[x][y][z] = state.getBlock() == Blocks.AIR ? null : state;
                }
            }
        }
        NBTTagCompound[][][] entityTags = new NBTTagCompound[xSize][ySize][zSize];
        NBTTagCompound[][][] stackTags = new NBTTagCompound[xSize][ySize][zSize];
        if(compound.hasKey("entityData", Constants.NBT.TAG_LIST)){
            NBTTagList entityData = compound.getTagList("entityData", Constants.NBT.TAG_COMPOUND);
            for(NBTBase tag : entityData){
                int x = ((NBTTagCompound)tag).getInteger("x");
                int y = ((NBTTagCompound)tag).getInteger("y");
                int z = ((NBTTagCompound)tag).getInteger("z");
                entityTags[x][y][z] = ((NBTTagCompound)tag).getCompoundTag("data");
                if(((NBTTagCompound)tag).hasKey("stack", Constants.NBT.TAG_COMPOUND))
                    stackTags[x][y][z] = ((NBTTagCompound)tag).getCompoundTag("stack");
            }
        }
        List<AxisAlignedBB> collisionBoxes;
        if(compound.hasKey("boxBits", Constants.NBT.TAG_INT_ARRAY)){
            int[] bits = compound.getIntArray("boxBits");
            double[] values = new double[bits.length / 2];
            for(int i = 0; i < values.length; i++)
                values[i] = Double.longBitsToDouble(((long)bits[2 * i] << 32) | (bits[2 * i + 1] & 0xFFFFFFFFL));
            collisionBoxes = new ArrayList<>(values.length / 6);
            for(int i = 0; i + 5 < values.length; i += 6)
                collisionBoxes.add(new AxisAlignedBB(values[i], values[i + 1], values[i + 2], values[i + 3], values[i + 4], values[i + 5]));
        }else{
            NBTTagList collisionBoxList = compound.getTagList("collisionBoxes", 10);
            collisionBoxes = Streams.stream(collisionBoxList)
                .map(NBTTagCompound.class::cast)
                .map(ElevatorCage::readBox)
                .collect(Collectors.toList());
        }
        return isClientSide ?
            new ClientElevatorCage(xSize, ySize, zSize, blockStates, entityTags, stackTags, collisionBoxes) :
            new ElevatorCage(xSize, ySize, zSize, blockStates, entityTags, stackTags, collisionBoxes);
    }

    private static NBTTagCompound writeBox(AxisAlignedBB box){
        NBTTagCompound compound = new NBTTagCompound();
        compound.setDouble("x1", box.minX);
        compound.setDouble("y1", box.minY);
        compound.setDouble("z1", box.minZ);
        compound.setDouble("x2", box.maxX);
        compound.setDouble("y2", box.maxY);
        compound.setDouble("z2", box.maxZ);
        return compound;
    }

    private static AxisAlignedBB readBox(NBTTagCompound compound){
        return new AxisAlignedBB(
            compound.getDouble("x1"),
            compound.getDouble("y1"),
            compound.getDouble("z1"),
            compound.getDouble("x2"),
            compound.getDouble("y2"),
            compound.getDouble("z2")
        );
    }
}
