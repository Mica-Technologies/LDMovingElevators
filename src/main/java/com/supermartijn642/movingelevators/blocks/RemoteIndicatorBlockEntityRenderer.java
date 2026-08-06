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
 * Draws the floor label on the slim plate of a {@link RemoteIndicatorBlockEntity}.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class RemoteIndicatorBlockEntityRenderer implements CustomBlockEntityRenderer<RemoteIndicatorBlockEntity> {

    /** Font rendering is expensive, so skip it past this distance. */
    private static final double TEXT_RENDER_DISTANCE = 15 * 15;
    /**
     * The plate is only 5 pixels tall, so the label has to be far smaller than the full-cube
     * display's -- these keep the screen inside the metal rather than overhanging it.
     */
    private static final float MAX_SCALE = 1 / 44f, MAX_WIDTH = 0.5f, PADDING = 0.025f;
    /** Vertical centre of the plate, matching RemoteIndicatorBlock's geometry. */
    private static final float PLATE_CENTER_Y = 8 / 16f;

    @Override
    public void render(RemoteIndicatorBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
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

        String label = FloorLabelRenderer.stripFloorPrefix(
            MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor));
        if(label == null || label.isEmpty())
            return;

        EnumFacing facing = entity.getFacing();

        GlStateManager.pushMatrix();

        // Same framing as the other displays, except the plate's front face sits flush with the
        // block face, so the label plane is unchanged.
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, -0.51);

        FloorLabelRenderer.drawCenteredLabel(label, group.getFloorDisplayColor(floor),
            0.5f, PLATE_CENTER_Y, MAX_SCALE, MAX_WIDTH, PADDING);

        GlStateManager.popMatrix();
    }
}
