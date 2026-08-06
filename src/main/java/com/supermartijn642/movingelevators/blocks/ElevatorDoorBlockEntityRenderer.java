package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.render.CustomBlockEntityRenderer;
import com.supermartijn642.core.render.TextureAtlases;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
public class ElevatorDoorBlockEntityRenderer implements CustomBlockEntityRenderer<ElevatorDoorBlockEntity> {

    @Override
    public void render(ElevatorDoorBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
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

        ScreenUtils.bindTexture(TextureAtlases.getBlocks());
        GlStateManager.pushMatrix();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        this.drawBox(buffer, sprite, combinedLight, leaf);
        Tessellator.getInstance().draw();

        GlStateManager.popMatrix();
    }

    private void drawBox(BufferBuilder buffer, TextureAtlasSprite sprite, int combinedLight, AxisAlignedBB box){
        float x0 = (float)box.minX, y0 = (float)box.minY, z0 = (float)box.minZ;
        float x1 = (float)box.maxX, y1 = (float)box.maxY, z1 = (float)box.maxZ;
        int sky = combinedLight >> 16 & 0xFFFF, block = combinedLight & 0xFFFF;

        // UVs come from the block grid rather than the face size, so the brushed metal keeps one
        // scale whatever width the leaf currently is.
        this.quad(buffer, sprite, sky, block, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, x1, y1); // north
        this.quad(buffer, sprite, sky, block, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, x0, y0, x1, y1); // south
        this.quad(buffer, sprite, sky, block, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, z0, y0, z1, y1); // west
        this.quad(buffer, sprite, sky, block, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, z0, y0, z1, y1); // east
        this.quad(buffer, sprite, sky, block, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, z0, x1, z1); // up
        this.quad(buffer, sprite, sky, block, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, z0, x1, z1); // down
    }

    private void quad(BufferBuilder buffer, TextureAtlasSprite sprite, int sky, int block,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float u0, float v0, float u1, float v1){
        float minU = sprite.getInterpolatedU(u0 * 16), maxU = sprite.getInterpolatedU(u1 * 16);
        float minV = sprite.getInterpolatedV(v0 * 16), maxV = sprite.getInterpolatedV(v1 * 16);
        buffer.pos(ax, ay, az).color(255, 255, 255, 255).tex(minU, maxV).lightmap(sky, block).endVertex();
        buffer.pos(bx, by, bz).color(255, 255, 255, 255).tex(minU, minV).lightmap(sky, block).endVertex();
        buffer.pos(cx, cy, cz).color(255, 255, 255, 255).tex(maxU, minV).lightmap(sky, block).endVertex();
        buffer.pos(dx, dy, dz).color(255, 255, 255, 255).tex(maxU, maxV).lightmap(sky, block).endVertex();
    }
}
