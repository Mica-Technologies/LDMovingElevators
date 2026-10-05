package com.supermartijn642.movingelevators.elevator;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.core.render.RenderWorldEvent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * Created 11/8/2020 by SuperMartijn642
 */
@Mod.EventBusSubscriber(Side.CLIENT)
public class ElevatorGroupRenderer {

    public static final double RENDER_DISTANCE = 255 * 255 * 4;

    private static boolean isWithinRenderDistance(ElevatorGroup group){
        float renderDistance = ClientUtils.getMinecraft().gameSettings.renderDistanceChunks * 16 + 8 + group.getCageSizeX() / 2f + group.getCageSizeZ() / 2f;
        BlockPos playerPos = ClientUtils.getPlayer().getPosition();
        float distance = (group.x - playerPos.getX()) * (group.x - playerPos.getX()) + (group.z - playerPos.getZ()) * (group.z - playerPos.getZ());
        return distance < renderDistance * renderDistance;
    }

    @SubscribeEvent
    public static void onRender(RenderWorldEvent e){
        if(!ClientUtils.getMinecraft().getRenderManager().isDebugBoundingBox())
            return;
        ElevatorGroupCapability groups = ElevatorGroupCapability.get(ClientUtils.getWorld());

        GlStateManager.pushMatrix();
        Vec3d camera = RenderUtils.getCameraPosition();
        GlStateManager.translate(-camera.x, -camera.y, -camera.z);
        for(ElevatorGroup group : groups.getGroups()){
            if(isWithinRenderDistance(group))
                renderGroupCageOutlines(group);
        }
        GlStateManager.popMatrix();
    }

    public static void renderBlocks(BlockRenderLayer renderType){
        ElevatorGroupCapability groups = ElevatorGroupCapability.get(ClientUtils.getWorld());

        GlStateManager.pushMatrix();
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
        ForgeHooksClient.setRenderLayer(renderType);
        float partialTicks = ClientUtils.getPartialTicks();
        Entity renderViewEntity = ClientUtils.getMinecraft().getRenderViewEntity();
        double d3 = renderViewEntity.lastTickPosX + (renderViewEntity.posX - renderViewEntity.lastTickPosX) * partialTicks;
        double d4 = renderViewEntity.lastTickPosY + (renderViewEntity.posY - renderViewEntity.lastTickPosY) * partialTicks;
        double d5 = renderViewEntity.lastTickPosZ + (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * partialTicks;
        GlStateManager.translate(-d3, -d4, -d5);
        ICamera frustum = camera;
        BufferBuilder buffer = null;
        for(ElevatorGroup group : groups.getGroups()){
            if(group.isMoving() && isWithinRenderDistance(group) && isInView(group, frustum, partialTicks)){
                if(buffer == null){
                    buffer = Tessellator.getInstance().getBuffer();
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                }
                renderGroupBlocks(group, renderType, buffer, partialTicks);
            }
        }
        if(buffer != null)
            Tessellator.getInstance().draw();
        GlStateManager.popMatrix();
        ForgeHooksClient.setRenderLayer(oldLayer);
    }

    public static void renderBlockEntities(float partialTicks){
        ElevatorGroupCapability groups = ElevatorGroupCapability.get(ClientUtils.getWorld());

        ICamera frustum = camera;
        for(ElevatorGroup group : groups.getGroups()){
            if(group.isMoving() && isWithinRenderDistance(group) && isInView(group, frustum, partialTicks))
                renderGroupBlockEntities(group, partialTicks);
        }
    }

    /**
     * The camera vanilla culls with this frame, already positioned at the view entity, handed over
     * by {@link com.supermartijn642.movingelevators.mixin.LevelRendererMixin} from setupTerrain and
     * renderEntities. Null until the first frame.
     * <p>
     * Mica: moving cabins were re-tessellated block by block for every render layer of every frame
     * whether they were in view or not -- a cabin behind the camera cost exactly as much as one in
     * front of it. They are culled against vanilla's own camera rather than a new Frustum, because
     * {@code new Frustum()} re-initialises the ClippingHelper every vanilla camera shares from the
     * GL matrices current at that moment. Built at the head of a block layer, under the camera
     * translation, that moved vanilla's frustum away from the view, and renderEntities then culled
     * players and every other entity against it: they showed only from odd angles.
     */
    private static ICamera camera;

    public static void setCamera(ICamera camera){
        ElevatorGroupRenderer.camera = camera;
    }

    /**
     * Whether any part of the cabin, where it is this frame, is inside the frustum. Without a
     * camera yet, every cabin counts as in view.
     */
    private static boolean isInView(ElevatorGroup group, ICamera frustum, float partialTicks){
        if(frustum == null)
            return true;
        double renderY = group.getLastY() + (group.getCurrentY() - group.getLastY()) * partialTicks;
        Vec3d start = group.getCageAnchorPos(renderY);
        return frustum.isBoundingBoxInFrustum(new AxisAlignedBB(start.x, start.y, start.z,
            start.x + group.getCageSizeX(), start.y + group.getCageSizeY(), start.z + group.getCageSizeZ()));
    }

    public static void renderGroupBlocks(ElevatorGroup group, BlockRenderLayer renderType, BufferBuilder buffer, float partialTicks){
        ClientElevatorCage cage = (ClientElevatorCage)group.getCage();
        double lastY = group.getLastY(), currentY = group.getCurrentY();
        double renderY = lastY + (currentY - lastY) * partialTicks;
        Vec3d startPos = group.getCageAnchorPos(renderY);
        BlockPos anchorPos = new BlockPos((int)startPos.x, (int)startPos.y, (int)startPos.z);
        cage.loadRenderInfo(anchorPos, group);
        World level = ClientElevatorCage.getFakeLevel();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for(int x = 0; x < group.getCageSizeX(); x++){
            for(int y = 0; y < group.getCageSizeY(); y++){
                for(int z = 0; z < group.getCageSizeZ(); z++){
                    if(cage.blockStates[x][y][z] == null)
                        continue;

                    buffer.setTranslation(0, startPos.y - anchorPos.getY(), 0);

                    IBlockState state = cage.blockStates[x][y][z];
                    if(state.getRenderType() == EnumBlockRenderType.MODEL && state.getBlock().canRenderInLayer(state, renderType)){
                        pos.setPos(anchorPos.getX() + x, anchorPos.getY() + y, anchorPos.getZ() + z);
                        state = state.getActualState(level, pos);
                        ClientUtils.getBlockRenderer().renderBlock(state, pos, level, buffer);
                    }
                    buffer.setTranslation(0, 0, 0);
                }
            }
        }
    }

    public static void renderGroupBlockEntities(ElevatorGroup group, float partialTicks){
        ClientElevatorCage cage = (ClientElevatorCage)group.getCage();
        double lastY = group.getLastY(), currentY = group.getCurrentY();
        double renderY = lastY + (currentY - lastY) * partialTicks;
        Vec3d startPos = group.getCageAnchorPos(renderY);
        BlockPos anchorPos = new BlockPos((int)startPos.x, (int)startPos.y, (int)startPos.z);
        cage.loadRenderInfo(anchorPos, group);

        for(int x = 0; x < group.getCageSizeX(); x++){
            for(int y = 0; y < group.getCageSizeY(); y++){
                for(int z = 0; z < group.getCageSizeZ(); z++){
                    if(cage.blockEntities[x][y][z] == null)
                        continue;

                    GlStateManager.pushMatrix();
                    GlStateManager.translate(0, startPos.y - anchorPos.getY(), 0);

                    TileEntity entity = cage.blockEntities[x][y][z];
                    TileEntityRendererDispatcher.instance.render(entity, partialTicks, -1);

                    GlStateManager.popMatrix();
                }
            }
        }
    }

    public static void renderGroupCageOutlines(ElevatorGroup group){
        for(int floor = 0; floor < group.getFloorCount(); floor++){
            BlockPos anchorPos = group.getCageAnchorBlockPos(group.getFloorYLevel(floor));
            AxisAlignedBB cageArea = new AxisAlignedBB(anchorPos, anchorPos.add(group.getCageSizeX(), group.getCageSizeY(), group.getCageSizeZ()));
            cageArea.grow(0.01);
            RenderUtils.renderBox(cageArea, 1, 1, 1, true);
        }
        if(group.isMoving()){
            ElevatorCage cage = group.getCage();
            double lastY = group.getLastY(), currentY = group.getCurrentY();
            double renderY = lastY + (currentY - lastY) * ClientUtils.getPartialTicks();
            Vec3d startPos = group.getCageAnchorPos(renderY);
            RenderUtils.renderBox(new AxisAlignedBB(startPos, startPos.addVector(group.getCageSizeX(), group.getCageSizeY(), group.getCageSizeZ())), 1, 0, 0, true);
            RenderUtils.renderShape(cage.shape.offset(startPos.x, startPos.y, startPos.z), 49 / 255f, 224 / 255f, 219 / 255f, true);
        }
    }
}
