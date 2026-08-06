package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.render.CustomBlockEntityRenderer;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the floor label for a {@link RemoteDisplayBlockEntity} on its facing side.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class RemoteDisplayBlockEntityRenderer implements CustomBlockEntityRenderer<RemoteDisplayBlockEntity> {

    /**
     * Font rendering is expensive, so skip it past this distance. Matches
     * {@link DisplayBlockEntityRenderer}.
     */
    private static final double TEXT_RENDER_DISTANCE = 15 * 15;
    /** Largest label height, as a fraction of the block face. */
    private static final float MAX_SCALE = 1 / 18f;
    /** Fraction of the block face the label is allowed to span horizontally. */
    private static final float MAX_WIDTH = 0.8f;

    @Override
    public void render(RemoteDisplayBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
        ElevatorGroup group = entity.getGroup();
        if(group == null)
            return;

        int floor = group.getCabinFloorNumber();
        if(floor < 0 || floor >= group.getFloorCount())
            return;

        BlockPos pos = entity.getPos();
        Vec3d cameraPos = RenderUtils.getCameraPosition();
        if(cameraPos.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TEXT_RENDER_DISTANCE)
            return;

        EnumFacing facing = entity.getFacing();
        String label = MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor);
        if(label == null || label.isEmpty())
            return;
        EnumDyeColor color = group.getFloorDisplayColor(floor);

        GlStateManager.pushMatrix();

        // Same framing as DisplayBlockEntityRenderer: move to the block centre, turn to face the
        // display's side, then step just proud of that face so the label is not z-fighting the block.
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, -0.51);

        this.drawFloorLabel(label, color.getColorValue());

        GlStateManager.popMatrix();
    }

    private void drawFloorLabel(String label, int color){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        int width = Math.max(fontRenderer.getStringWidth(label), 1);
        // Shrink long floor names so they stay on the block instead of spilling past its edges.
        float scale = Math.min(MAX_SCALE, MAX_WIDTH / width);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5, 0.5, -0.005);
        // Negative on both axes: the surrounding rotation leaves this space mirrored, the same way
        // DisplayBlockEntityRenderer's label drawing compensates for it.
        GlStateManager.scale(-scale, -scale, 1);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        fontRenderer.drawString(label, -width / 2, -fontRenderer.FONT_HEIGHT / 2, color);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
