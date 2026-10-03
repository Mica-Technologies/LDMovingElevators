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
 * The same block entity backs {@link BankCarPanelBlock}, so this renderer draws that too. Both now
 * sit on the identical plate, so the readout and arrows land in the same place on either and the
 * only difference is which controls fill the space below: floor buttons on the ordinary panel, and
 * on the bank panel none, because a car in a destination dispatch bank is not told where to go from
 * inside. Which of the two this is comes from the block, since that is all that differs.
 * <p>
 * Both panels also carry the controls that are not about choosing a floor -- the doors and the
 * alarm. Those are drawn because the panel opens a screen offering them: a plate showing only a
 * readout gives a passenger no reason to suspect there is an alarm behind it at all.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class ElevatorCarPanelBlockEntityRenderer implements CustomBlockEntityRenderer<ElevatorCarPanelBlockEntity> {

    /**
     * Squared, so this is 15 blocks -- half the landing panels' 30. This panel is read from arm's
     * length inside the cabin, never across a room like the landing fixtures, so the shorter reach
     * is deliberate rather than a value nobody got around to raising.
     */
    private static final double TEXT_RENDER_DISTANCE = TextRenderCutoff.squared(TextRenderCutoff.CAR_PANEL);

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
     * The band is 9.0 to 10.8 of sixteen, and the name is centred in it. It used to be stated as 9.5
     * to 10.8 and the name hung at 10.15, which was wrong on both counts: the top button socket
     * reached 10.0 rather than 9.5, so the band was 0.8 tall and the 1.32 tall inset drawn in it
     * overlapped the sockets by about half a pixel. The band was widened rather than the name shrunk
     * -- see NAME_SCALE, which is already as small as a legible name gets -- by moving the button
     * bank and the alarm down into the pixel and a quarter of unused plate below them.
     */
    private static final float NAME_X = 8 / 16f, NAME_Y = 9.9f / 16f;
    /**
     * Sized to nearly fill the gap between the readout and the top row of buttons rather than to be
     * safely small. At 1/300 the glyph came out under half a pixel of the sixteen-pixel face --
     * present, unreadable, and worse than absent.
     * <p>
     * Scale is per font unit, so a nine-unit line stands 9/120 of a block, i.e. 1.2 face pixels; with
     * the padding either side the inset is 1.32, which leaves 0.24 of clear metal above and below it
     * in the 1.8 pixel band. The width cap never binds at that scale, since an eleven character name
     * comes out about 3.5 pixels wide on a 12 pixel plate; it is there so that no name can overhang.
     */
    private static final float NAME_SCALE = 1 / 120f, NAME_MAX_WIDTH = 8 / 16f, NAME_PADDING = 0.06f / 16f;
    /** The name is not a floor, so it has no dye colour of its own; white reads on the inset screen. */
    private static final EnumDyeColor NAME_COLOR = EnumDyeColor.WHITE;

    /**
     * Button bank: two columns, three rows, filling upwards like a real car station.
     * <p>
     * Sockets are the button plus FloorLabelRenderer's half-pixel bezel, so each is 1.233 either side
     * of its centre and the bank spans 0.8 to 9.0. It sits a pixel lower than it first did, which is
     * what opens the band the elevator's name is drawn in; the pixel came from the unused plate below
     * the alarm rather than from anything else on the face.
     */
    private static final int BUTTON_COLUMNS = 2, BUTTON_ROWS = 3;
    private static final float BUTTON_HALF = 0.733f / 16f;
    private static final float BUTTON_LEFT_X = 5.4f / 16f, BUTTON_COLUMN_GAP = 5.2f / 16f;
    private static final float BUTTON_BOTTOM_Y = 2.033f / 16f, BUTTON_ROW_GAP = 2.867f / 16f;

    // Bank car panel layout. The plate is now the ordinary panel's, 2.0 to 14.0 by 1.0 to 15.0, so
    // the readout window and the arrows carry over verbatim and have no constants of their own --
    // a passenger stepping between an ordinary car and a bank car sees the same fixtures in the
    // same places. Only the name and the controls below it are stated separately.
    /**
     * The name hangs from the readout rather than floating in the middle of the plate: everything
     * below it is controls now, so the empty space it used to be centred in is gone. At the scale
     * below its inset is 8.04 to 9.76, which leaves 1.04 of clear metal between it and the readout
     * bottom at 10.8 -- about the pixel of surround every other element on the plate gets.
     */
    private static final float BANK_NAME_Y = 8.9f / 16f;
    /**
     * Bigger than the ordinary panel's name, which is squeezed into the 1.3 pixel band between the
     * readout and the top of the button bank. There is no button bank here, so the constraint is
     * gone. Per font unit, so a nine pixel line stands 0.1 of a block, i.e. 1.6 face pixels. The
     * width cap is widened to match: 10 pixels centred on a 12 pixel plate, still a pixel of metal
     * either side, so fewer names get scaled down to fit.
     */
    private static final float BANK_NAME_SCALE = 1 / 90f, BANK_NAME_MAX_WIDTH = 10 / 16f;

    /**
     * Door open and door close, side by side in the band the floor buttons would occupy on the
     * ordinary panel. Wide rather than square because there are only two of them across a twelve
     * pixel plate, and two small squares in that much space read as a grid missing its other rows.
     * <p>
     * Widths come from the plate outwards: 2.15 to 13.85 is the full run with the same 0.15 of
     * metal left at each edge that the alarm below takes, and the pair splits it with 0.5 of metal
     * between them. That gives each socket (11.7 - 0.5) / 2 = 5.6 wide, so the lamps are 5.6 - 2 x
     * 0.5 of bezel = 4.6 wide, i.e. a half width of 2.3, centred at 4.95 and 11.05.
     */
    private static final float DOOR_HALF_WIDTH = 2.3f / 16f, DOOR_HALF_HEIGHT = 1 / 16f;
    private static final float DOOR_LEFT_X = 4.95f / 16f, DOOR_RIGHT_X = 11.05f / 16f;
    /**
     * Centred in what is left between the name inset above (bottom 8.04) and the alarm's socket
     * below (top 0.65): the row's own socket is 2 x (1.0 + 0.5) = 3.0 tall, and (8.04 + 0.65) / 2 =
     * 4.345 puts its centre there, rounded to 4.35. Socket 2.85 to 5.85, so 2.2 of clear metal below
     * it and 2.19 above -- close enough to even that the row does not look hung from either.
     * <p>
     * Followed the alarm down when that moved: the row is centred on the gap rather than pinned to a
     * height, so leaving it where it was would have hung it from the name with a hole beneath.
     */
    private static final float DOOR_Y = 4.35f / 16f;

    /** Red, because it is the one thing this screen ever says that is not simply where you are. */
    private static final EnumDyeColor OVERLOAD_COLOR = EnumDyeColor.RED;

    /**
     * The alarm bar sits in the three pixels the plate hangs below its block, in the same place on
     * both panels -- one plate, one fixture, one muscle memory for where the alarm is whichever car
     * you are in. Under the button bank it had a pixel and a third to live in, bezel included, and
     * came out overflowing the metal; below the block it has room to be a bar rather than a smear.
     * <p>
     * Half a pixel lower than it first sat. That is not for its own sake: it is the room the button
     * bank moved down into, which in turn is the room the elevator's name needed. Its socket runs
     * -1.25 to 0.65, keeping the same 0.15 of clearance under the lowest button socket at 0.8 that
     * every other element on this plate leaves, and 0.75 of plate below it.
     */
    private static final float ALARM_X = 8 / 16f, ALARM_Y = -0.3f / 16f;
    private static final float ALARM_HALF_WIDTH = 5f / 16f, ALARM_HALF_HEIGHT = 0.45f / 16f;

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

        // Which of the two panels this is. Read once: it cannot change part way through a frame, and
        // a block lookup per element would be a lookup per button as well.
        boolean bank = entity.getWorld().getBlockState(pos).getBlock() instanceof BankCarPanelBlock;
        float nameY = bank ? BANK_NAME_Y : NAME_Y;
        float nameScale = bank ? BANK_NAME_SCALE : NAME_SCALE;
        float nameMaxWidth = bank ? BANK_NAME_MAX_WIDTH : NAME_MAX_WIDTH;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, LABEL_DEPTH);

        int cabinFloor = group.getCabinFloorNumber();

        // Readout. An overload takes the screen over entirely: which floor the cabin is at stops being
        // the useful thing to say the moment it is not going anywhere until somebody steps off.
        String overload = MovingElevatorsClient.overloadMarquee(group);
        if(overload != null)
            FloorLabelRenderer.drawFittedLabel(overload, OVERLOAD_COLOR,
                SCREEN_X, SCREEN_Y, SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, SCREEN_PADDING);
        else if(cabinFloor >= 0 && cabinFloor < group.getFloorCount()){
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
            FloorLabelRenderer.drawCenteredLabel(name, NAME_COLOR, NAME_X, nameY,
                nameScale, nameMaxWidth, NAME_PADDING);

        // No floor buttons in a bank car: the destination was given at the lobby, so a bank of
        // lit floors inside would show calls nobody in here can place or cancel. The doors get that
        // space instead. The ordinary panel has no room to show them -- the bank is sitting on it --
        // but it does not need to: a face covered in floor buttons already reads as a car station,
        // so a player opens it. The bare bank plate does not, which is why this one says so.
        if(bank)
            this.drawDoorButtons();
        else
            this.drawButtonBank(group, cabinFloor);

        // Both panels, in the same place: the alarm is the one control a passenger may need to find
        // without having gone looking for it first.
        FloorLabelRenderer.drawButton(ALARM_X, ALARM_Y, ALARM_HALF_WIDTH, ALARM_HALF_HEIGHT, false);

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

    /**
     * Door open and door close, both unlit.
     * <p>
     * Unlit like the bank lobby panel's keypad and for the same reason: clicking anywhere on the
     * plate opens the controls screen, so neither of these is a control with a state of its own, and
     * a lit one would be claiming something the block entity does not track. Which is which is left
     * to their order -- open on the left, close on the right, as on the screen they open.
     */
    private void drawDoorButtons(){
        FloorLabelRenderer.drawButton(DOOR_LEFT_X, DOOR_Y, DOOR_HALF_WIDTH, DOOR_HALF_HEIGHT, false);
        FloorLabelRenderer.drawButton(DOOR_RIGHT_X, DOOR_Y, DOOR_HALF_WIDTH, DOOR_HALF_HEIGHT, false);
    }
}
