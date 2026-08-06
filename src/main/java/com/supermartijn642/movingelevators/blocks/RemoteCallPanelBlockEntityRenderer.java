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
 * Draws a {@link RemoteCallPanelBlockEntity}: floor readout at the top of the plate, up and down call
 * arrows below it, lit when there is an outstanding call in that direction.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class RemoteCallPanelBlockEntityRenderer implements CustomBlockEntityRenderer<RemoteCallPanelBlockEntity> {

    /** Font rendering is expensive, so skip it past this distance. */
    /**
     * Squared, so this is 30 blocks. Landing fixtures are read from across a lobby or down a
     * corridor, unlike the panel inside the cabin, which is read from arm's length.
     * <p>
     * Text is expensive to draw, which is why there is a cutoff at all. Past 64 blocks raising this
     * alone would do nothing anyway: block entities stop being rendered at that range unless they
     * ask for more.
     */
    private static final double TEXT_RENDER_DISTANCE = 30 * 30;
    /** The plate is narrow, so the readout has to be small. */
    private static final float MAX_SCALE = 1 / 46f, MAX_WIDTH = 0.28f, PADDING = 0.02f;
    /** Centres of the three zones, matching RemoteCallPanelBlock's hit regions. */
    private static final float SCREEN_CENTER_Y = 12 / 16f;
    private static final float UP_CENTER_Y = 7.5f / 16f, DOWN_CENTER_Y = 3.5f / 16f;
    private static final float ARROW_HALF_WIDTH = 1.8f / 16f, ARROW_HALF_HEIGHT = 1.4f / 16f;
    /** Matches the plate's own depth, so the contents sit just proud of the metal. */
    private static final double LABEL_DEPTH = 0.5 - WallPanelBlock.PLATE_DEPTH - 0.01;

    @Override
    public void render(RemoteCallPanelBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
        ElevatorGroup group = entity.getGroup();
        if(group == null)
            return;

        BlockPos pos = entity.getPos();
        Vec3d cameraPos = RenderUtils.getCameraPosition();
        if(cameraPos.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TEXT_RENDER_DISTANCE)
            return;

        EnumFacing facing = entity.getFacing();
        int floorLevel = entity.getFloorLevel();

        GlStateManager.pushMatrix();

        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, LABEL_DEPTH);

        int floor = group.getCabinFloorNumber();
        if(floor >= 0 && floor < group.getFloorCount()){
            String label = MovingElevatorsClient.stripFloorPrefix(
                MovingElevatorsClient.formatDisplayLabel(group, floor));
            if(label != null && !label.isEmpty())
                FloorLabelRenderer.drawCenteredLabel(label, group.getFloorDisplayColor(floor),
                    0.5f, SCREEN_CENTER_Y, MAX_SCALE, MAX_WIDTH, PADDING);
        }

        FloorLabelRenderer.drawArrow(0.5f, UP_CENTER_Y, ARROW_HALF_WIDTH, ARROW_HALF_HEIGHT,
            true, group.hasHallCall(floorLevel, true));
        FloorLabelRenderer.drawArrow(0.5f, DOWN_CENTER_Y, ARROW_HALF_WIDTH, ARROW_HALF_HEIGHT,
            false, group.hasHallCall(floorLevel, false));

        GlStateManager.popMatrix();
    }
}
