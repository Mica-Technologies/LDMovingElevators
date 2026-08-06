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
 * Draws an {@link ElevatorCarPanelBlockEntity}: the floor readout and direction arrows at the top,
 * with a bank of floor buttons below that light for whichever floors are currently selected.
 * <p>
 * The bank is a readout, not a control -- clicking anywhere opens the floor list. It shows a window
 * of floors around the cabin rather than all of them, because a shaft can have far more floors than
 * a block face has room for.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class ElevatorCarPanelBlockEntityRenderer implements CustomBlockEntityRenderer<ElevatorCarPanelBlockEntity> {

    private static final double TEXT_RENDER_DISTANCE = 15 * 15;

    // Face layout, in sixteenths. Every one of these was checked against the plate bounds and
    // against each other rather than eyeballed -- the first guess had the arrow hanging off the
    // plate, the two arrows on top of each other, and the button rows overlapping.
    /** Readout, below the direction arrows. */
    private static final float SCREEN_CENTER_Y = 10.3f / 16f;
    private static final float MAX_SCALE = 1 / 46f, MAX_WIDTH = 0.30f, PADDING = 0.02f;
    /** Direction arrows, side by side above the readout. */
    private static final float ARROW_UP_X = 5.5f / 16f, ARROW_DOWN_X = 8.5f / 16f;
    private static final float ARROW_Y = 13.6f / 16f, ARROW_HALF = 0.7f / 16f;

    /** Button bank: two columns, three rows, filling upwards like a real car station. */
    private static final int BUTTON_COLUMNS = 2, BUTTON_ROWS = 3;
    private static final float BUTTON_HALF = 0.65f / 16f;
    private static final float BUTTON_LEFT_X = 4.5f / 16f, BUTTON_COLUMN_GAP = 5f / 16f;
    private static final float BUTTON_BOTTOM_Y = 2.4f / 16f, BUTTON_ROW_GAP = 2.4f / 16f;

    private static final double LABEL_DEPTH = 0.5 - WallPanelBlock.PLATE_DEPTH - 0.01;

    @Override
    public void render(ElevatorCarPanelBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
        ElevatorGroup group = entity.getGroup();
        if(group == null)
            return;

        BlockPos pos = entity.getPos();
        Vec3d cameraPos = RenderUtils.getCameraPosition();
        if(cameraPos.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TEXT_RENDER_DISTANCE)
            return;

        EnumFacing facing = entity.getFacing();

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, LABEL_DEPTH);

        int cabinFloor = group.getCabinFloorNumber();

        // Readout
        if(cabinFloor >= 0 && cabinFloor < group.getFloorCount()){
            String label = MovingElevatorsClient.stripFloorPrefix(
                MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(cabinFloor), cabinFloor));
            if(label != null && !label.isEmpty())
                FloorLabelRenderer.drawCenteredLabel(label, group.getFloorDisplayColor(cabinFloor),
                    0.5f, SCREEN_CENTER_Y, MAX_SCALE, MAX_WIDTH, PADDING);
        }

        // Direction of travel, lit only while actually moving that way.
        int direction = group.getTravelDirection();
        FloorLabelRenderer.drawArrow(ARROW_UP_X, ARROW_Y, ARROW_HALF, ARROW_HALF, true, direction > 0);
        FloorLabelRenderer.drawArrow(ARROW_DOWN_X, ARROW_Y, ARROW_HALF, ARROW_HALF, false, direction < 0);

        this.drawButtonBank(group, cabinFloor);

        GlStateManager.popMatrix();
    }

    /**
     * Lights the floors that are selected, over a window of floors around the cabin. Slots beyond the
     * ends of the shaft are drawn unlit so the bank keeps its shape.
     */
    private void drawButtonBank(ElevatorGroup group, int cabinFloor){
        int slots = BUTTON_COLUMNS * BUTTON_ROWS;
        int floors = group.getFloorCount();
        // Centre the window on the cabin, then pull it back inside the shaft at either end.
        int start = Math.max(0, Math.min(cabinFloor < 0 ? 0 : cabinFloor - slots / 2, floors - slots));

        for(int slot = 0; slot < slots; slot++){
            int column = slot % BUTTON_COLUMNS;
            int row = slot / BUTTON_COLUMNS;
            float x = BUTTON_LEFT_X + column * BUTTON_COLUMN_GAP;
            float y = BUTTON_BOTTOM_Y + row * BUTTON_ROW_GAP;

            int floor = start + slot;
            boolean lit = floor >= 0 && floor < floors && group.hasCallFor(group.getFloorYLevel(floor));
            FloorLabelRenderer.drawButton(x, y, BUTTON_HALF, lit);
        }
    }
}
