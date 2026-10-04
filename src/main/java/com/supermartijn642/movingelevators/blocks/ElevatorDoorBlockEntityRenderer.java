package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.render.TextureAtlases;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/**
 * Draws a door leaf part-way through sliding.
 * <p>
 * The leaf is drawn here rather than baked into the chunk because a baked model can only ever be
 * fully open or fully shut -- the block state has no room for anything in between, and animating it
 * through states would mean a block update every tick for every door in the world. The block's own
 * model is therefore empty, and this is the only thing that draws it.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class ElevatorDoorBlockEntityRenderer extends TileEntitySpecialRenderer<ElevatorDoorBlockEntity> {

    /**
     * How bright a leaf is drawn, out of 255: what the leaves have always looked like.
     * <p>
     * Mica: they were drawn with the dispatcher's item lighting switched on and no normal of their
     * own, which left only the light's ambient term -- 0.4 -- on every face. Measured in the
     * regression screenshots, old to new, at exactly 0.40 on every face. Writing it into the vertex
     * colour keeps that look in the batch, which draws without item lighting, and makes it the same
     * on every frame rather than at the mercy of whatever drew before.
     */
    private static final int SHADE = 102;

    /**
     * Into Forge's shared batch, alongside every other door in view, drawn with a single call.
     * <p>
     * Mica: each door block used to be drawn on its own: the dispatcher set up item lighting and the
     * lightmap for it, and this bound the atlas and drew one box -- once per door block per frame,
     * which for a district in view is over a thousand draw calls for some thousand small boxes. The
     * box already went out in the batch's own vertex format, so nothing about it had to change but
     * where it is written.
     */
    @Override
    public void renderTileEntityFast(ElevatorDoorBlockEntity entity, double x, double y, double z, float partialTicks, int destroyStage, float partial, BufferBuilder buffer){
        this.emit(entity, partialTicks, buffer, x, y, z);
    }

    /** The unbatched path, for when the dispatcher is not collecting a batch -- inside a moving cabin. */
    @Override
    public void render(ElevatorDoorBlockEntity entity, double x, double y, double z, float partialTicks, int destroyStage, float alpha){
        ScreenUtils.bindTexture(TextureAtlases.getBlocks());
        // Unlit, like the batch, so the shade below is the whole story on both paths.
        GlStateManager.disableLighting();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        this.emit(entity, partialTicks, buffer, x, y, z);
        Tessellator.getInstance().draw();
        GlStateManager.enableLighting();
    }

    private void emit(ElevatorDoorBlockEntity entity, float partialTicks, BufferBuilder buffer, double x, double y, double z){
        IBlockState state = entity.getWorld().getBlockState(entity.getPos());
        if(!(state.getBlock() instanceof ElevatorDoorBlockBase))
            return;
        TextureAtlasSprite sprite = MovingElevatorsClient.METAL_SPRITE;
        if(sprite == null)
            return;

        // Same geometry the outline and collision use, so the leaf can never be drawn somewhere it
        // cannot be walked through.
        AxisAlignedBB leaf = ((ElevatorDoorBlockBase)state.getBlock())
            .shapeForProgress(state, entity.getAnimation(partialTicks));

        BlockPos pos = entity.getPos();
        int combinedLight = entity.getWorld().getCombinedLight(pos, 0);
        this.drawBox(buffer, sprite, combinedLight, leaf, x, y, z);
    }

    /** @param box the leaf within its own block; ox, oy, oz where that block is drawn */
    private void drawBox(BufferBuilder buffer, TextureAtlasSprite sprite, int combinedLight, AxisAlignedBB box, double ox, double oy, double oz){
        float x0 = (float)box.minX, y0 = (float)box.minY, z0 = (float)box.minZ;
        float x1 = (float)box.maxX, y1 = (float)box.maxY, z1 = (float)box.maxZ;
        int sky = combinedLight >> 16 & 0xFFFF, block = combinedLight & 0xFFFF;

        // UVs come from the block grid rather than the face size, so the brushed metal keeps one
        // scale whatever width the leaf currently is.
        this.quad(buffer, sprite, sky, block, ox, oy, oz, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, x1, y1); // north
        this.quad(buffer, sprite, sky, block, ox, oy, oz, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, x0, y0, x1, y1); // south
        this.quad(buffer, sprite, sky, block, ox, oy, oz, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, z0, y0, z1, y1); // west
        this.quad(buffer, sprite, sky, block, ox, oy, oz, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, z0, y0, z1, y1); // east
        this.quad(buffer, sprite, sky, block, ox, oy, oz, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, z0, x1, z1); // up
        this.quad(buffer, sprite, sky, block, ox, oy, oz, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, z0, x1, z1); // down
    }

    private void quad(BufferBuilder buffer, TextureAtlasSprite sprite, int sky, int block, double ox, double oy, double oz,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float u0, float v0, float u1, float v1){
        float minU = sprite.getInterpolatedU(u0 * 16), maxU = sprite.getInterpolatedU(u1 * 16);
        float minV = sprite.getInterpolatedV(v0 * 16), maxV = sprite.getInterpolatedV(v1 * 16);
        buffer.pos(ox + ax, oy + ay, oz + az).color(SHADE, SHADE, SHADE, 255).tex(minU, maxV).lightmap(sky, block).endVertex();
        buffer.pos(ox + bx, oy + by, oz + bz).color(SHADE, SHADE, SHADE, 255).tex(minU, minV).lightmap(sky, block).endVertex();
        buffer.pos(ox + cx, oy + cy, oz + cz).color(SHADE, SHADE, SHADE, 255).tex(maxU, minV).lightmap(sky, block).endVertex();
        buffer.pos(ox + dx, oy + dy, oz + dz).color(SHADE, SHADE, SHADE, 255).tex(maxU, maxV).lightmap(sky, block).endVertex();
    }
}
