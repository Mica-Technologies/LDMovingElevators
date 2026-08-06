package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.render.CustomBlockEntityRenderer;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.client.renderer.GlStateManager;
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
    /** Full-block face, so the label can be large. */
    private static final float MAX_SCALE = 1 / 12f, MAX_WIDTH = 0.7f, PADDING = 0.06f;

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

        String label = MovingElevatorsClient.stripFloorPrefix(
            MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor));
        if(label == null || label.isEmpty())
            return;

        EnumFacing facing = entity.getFacing();

        GlStateManager.pushMatrix();

        // Same framing as DisplayBlockEntityRenderer: move to the block centre, turn to face the
        // display's side, then step just proud of that face so the label is not z-fighting the block.
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, -0.51);

        FloorLabelRenderer.drawCenteredLabel(label, group.getFloorDisplayColor(floor),
            0.5f, 0.5f, MAX_SCALE, MAX_WIDTH, PADDING);

        GlStateManager.popMatrix();
    }
}
