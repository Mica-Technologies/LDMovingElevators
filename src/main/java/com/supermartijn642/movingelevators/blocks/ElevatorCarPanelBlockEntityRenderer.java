package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.render.CustomBlockEntityRenderer;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.EnumDyeColor;
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

    /**
     * Squared, so this is 15 blocks -- half the landing panels' 30. This panel is read from arm's
     * length inside the cabin, never across a room like the landing fixtures, so the shorter reach
     * is deliberate rather than a value nobody got around to raising.
     */
    private static final double TEXT_RENDER_DISTANCE = 15 * 15;

    // Face layout, in sixteenths, stated as the player sees it. Each of these is checked against
    // the plate bounds, against a half-pixel edge margin, and against every other element before
    // being trusted -- the previous set had the arrows swapped, the bank off-centre and things
    // touching the plate edge.
    /** Readout window. Fixed size, so it looks like a display rather than shrink-wrapping the text. */
    private static final float SCREEN_X = 8 / 16f, SCREEN_Y = 12.4f / 16f;
    private static final float SCREEN_HALF_WIDTH = 2.8f / 16f, SCREEN_HALF_HEIGHT = 1.6f / 16f;
    private static final float SCREEN_PADDING = 0.35f / 16f;
    /** Direction arrows, flanking the readout like the fixtures these are modelled on. */
    private static final float ARROW_UP_X = 3.7f / 16f, ARROW_DOWN_X = 12.3f / 16f;
    private static final float ARROW_Y = 12.4f / 16f, ARROW_HALF = 0.6f / 16f;

    /**
     * The elevator's name, in the band between the readout row and the top of the button bank.
     * <p>
     * That band runs from 10.0 to 10.8 of sixteen and is the only full-width gap left on the face:
     * the plate stops at 15.0, one pixel above the readout, and at 1.0, less than a pixel below the
     * bank, and both of those are edge margin rather than free space. A tenth of a pixel is kept
     * clear at the top and bottom of the band, which is what makes the name as small as it is.
     */
    private static final float NAME_X = 8 / 16f, NAME_Y = 10.15f / 16f;
    /**
     * Scale here is per font unit rather than per line, so a nine-pixel line stands 9/300 of a block
     * -- 0.48 of a face pixel -- which with the padding either side of it fills the 0.6 pixel budget
     * above exactly. The width cap never binds at that scale, since an eleven character name comes
     * out about 3.5 pixels wide on a 12 pixel plate; it is there so that no name can overhang.
     */
    /**
     * Sized to nearly fill the gap between the readout and the top row of buttons rather than to be
     * safely small. At 1/300 the glyph came out under half a pixel of the sixteen-pixel face -- present,
     * unreadable, and worse than absent. The band runs from 9.5 to 10.8, and the inset this draws is
     * about 1.19 of that, so it sits inside with a little air either side.
     */
    private static final float NAME_SCALE = 1 / 120f, NAME_MAX_WIDTH = 8 / 16f, NAME_PADDING = 0.06f / 16f;
    /** The name is not a floor, so it has no dye colour of its own; white reads on the inset screen. */
    private static final EnumDyeColor NAME_COLOR = EnumDyeColor.WHITE;

    /** Button bank: two columns, three rows, filling upwards like a real car station. */
    private static final int BUTTON_COLUMNS = 2, BUTTON_ROWS = 3;
    private static final float BUTTON_HALF = 0.733f / 16f;
    private static final float BUTTON_LEFT_X = 5.4f / 16f, BUTTON_COLUMN_GAP = 5.2f / 16f;
    private static final float BUTTON_BOTTOM_Y = 3.033f / 16f, BUTTON_ROW_GAP = 2.867f / 16f;

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
                MovingElevatorsClient.formatDisplayLabel(group, cabinFloor));
            if(label != null && !label.isEmpty())
                FloorLabelRenderer.drawFittedLabel(label, group.getFloorDisplayColor(cabinFloor),
                    SCREEN_X, SCREEN_Y, SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, SCREEN_PADDING);
        }

        // Direction of travel, lit only while actually moving that way.
        int direction = group.getTravelDirection();
        FloorLabelRenderer.drawArrow(ARROW_UP_X, ARROW_Y, ARROW_HALF, ARROW_HALF, true, direction > 0);
        FloorLabelRenderer.drawArrow(ARROW_DOWN_X, ARROW_Y, ARROW_HALF, ARROW_HALF, false, direction < 0);

        // Which car you are riding in, for buildings with more than one. Nothing at all is drawn when
        // the elevator has no name, rather than an empty screen, so a shaft nobody has named looks
        // exactly as it did before names existed.
        String name = group.getName();
        if(name != null && !name.isEmpty())
            FloorLabelRenderer.drawCenteredLabel(name, NAME_COLOR, NAME_X, NAME_Y,
                NAME_SCALE, NAME_MAX_WIDTH, NAME_PADDING);

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
