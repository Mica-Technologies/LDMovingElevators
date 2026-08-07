package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.ClientUtils;
import net.minecraft.client.gui.FontRenderer;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.BlockEntityBaseWidget;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.packets.PacketDoorControl;
import com.supermartijn642.movingelevators.packets.PacketEmergencyStop;
import com.supermartijn642.movingelevators.packets.PacketRingAlarm;
import com.supermartijn642.movingelevators.packets.PacketRequestFloor;
import com.supermartijn642.movingelevators.packets.PacketToggleIndependentService;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;

/**
 * The car panel's floor list: one button per floor, laid out like a real car station -- lowest at the
 * bottom, filling upwards in two columns.
 * <p>
 * This exists because floors are not fixed. Players add and remove controllers whenever they like, so
 * a bank of buttons drawn on a block face can never match the shaft, and hit-testing a dozen of them
 * across sixteen pixels would be miserable besides. A screen sidesteps both problems and can label
 * each floor with its actual name.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class FloorSelectScreen extends BlockEntityBaseWidget<ElevatorCarPanelBlockEntity> {

    private static final int BUTTON_SIZE = 22, GAP = 3, PADDING = 7, HEADER = 22;
    /**
     * Door controls sit under the grid, in their own row, with the alarm in a row below that and the
     * emergency stop below the alarm. Every one of those rows is {@link #DOOR_ROW_HEIGHT} tall and
     * {@link #GAP} apart, so the three read as one block of controls.
     */
    private static final int DOOR_ROW_HEIGHT = 20, DOOR_ROW_GAP = 5, DOOR_LABEL_PADDING = 6;
    /** Rows before the grid grows sideways instead, so a tall shaft cannot run off the screen. */
    private static final int MAX_ROWS = 8;
    /**
     * Top of the key switch, chosen so it sits centred against the header's two lines of text -- they
     * run from y 6 to y 23, and the switch is {@link KeySwitchWidget#SIZE} tall.
     */
    private static final int KEY_SWITCH_Y = 9;

    public FloorSelectScreen(BlockPos entityPos){
        super(0, 0, 0, 0, ClientUtils.getWorld(), entityPos);
    }

    /**
     * Columns for a roughly square grid, widening further once it would otherwise get taller than
     * {@link #MAX_ROWS}.
     * <p>
     * Filling one column at a time produced a single tower of buttons -- eight floors gave an eight
     * high, one wide strip -- and a building with forty floors would have run off the screen
     * entirely. Square-ish keeps both the common case and the extreme readable.
     */
    private static int columnsFor(int floorCount){
        if(floorCount <= 1)
            return 1;
        int columns = (int)Math.ceil(Math.sqrt(floorCount));
        if((floorCount + columns - 1) / columns > MAX_ROWS)
            columns = (floorCount + MAX_ROWS - 1) / MAX_ROWS;
        return columns;
    }

    private static int rowsFor(int floorCount){
        int columns = columnsFor(floorCount);
        return Math.max(1, (floorCount + columns - 1) / columns);
    }

    /**
     * The key switch's tooltip: the elevator's current service mode, since the switch itself has no
     * room for a word of label and a lit bezel alone does not say what it is lit about.
     * <p>
     * Shared with {@link CarControlsScreen}, unlike the layout constants that screen deliberately
     * duplicates -- what a control means is not a knob to nudge, and the two panels describing the
     * same switch differently would be a bug rather than drift.
     */
    /**
     * Three lines rather than one: what the switch is set to, what that setting does, and that turning
     * it needs a key. A control drawn as a locked keyhole with no words at all is a puzzle, and the
     * state alone -- "Independent service: Normal service" -- says nothing about what would change.
     */
    /**
     * Drawn to the left of the switch rather than at the cursor.
     * <p>
     * The switch sits at the panel's right edge, and the tooltip helper places its box to the right of
     * whatever it is given, with no notion of the screen edge to clamp against -- so at the cursor it
     * ran off the display. Growing leftwards from the switch keeps it over the panel, which is on
     * screen by definition.
     */
    static void drawIndependentTooltip(ElevatorCarPanelBlockEntity blockEntity, int switchX, int mouseY){
        java.util.List<ITextComponent> lines = independentTooltip(blockEntity);
        int widest = 0;
        for(ITextComponent line : lines)
            widest = Math.max(widest, ClientUtils.getFontRenderer().getStringWidth(line.getFormattedText()));
        // The helper insets its box from the position it is handed; this puts the box's far edge back
        // beside the switch rather than a box-width past it.
        ScreenUtils.drawTooltip(lines, switchX - widest - TOOLTIP_BOX_INSET, mouseY);
    }

    /** What the tooltip helper adds between the position it is given and the text it draws. */
    private static final int TOOLTIP_BOX_INSET = 16;

    static java.util.List<ITextComponent> independentTooltip(ElevatorCarPanelBlockEntity blockEntity){
        ElevatorGroup group = blockEntity == null ? null : blockEntity.getGroup();
        ElevatorGroup.ServiceMode mode = group == null ? ElevatorGroup.ServiceMode.NORMAL : group.getServiceMode();
        return java.util.Arrays.asList(
            TextComponents.translation("movingelevators.floor_select.independent",
                TextComponents.translation(mode.getNameTranslationKey()).color(TextFormatting.GOLD).get()).get(),
            TextComponents.translation("movingelevators.floor_select.independent.what").color(TextFormatting.GRAY).get(),
            TextComponents.translation("movingelevators.floor_select.independent.key").color(TextFormatting.DARK_GRAY).get());
    }

    /**
     * Whether the key switch should read as thrown. Shared for the same reason as the tooltip above.
     */
    static boolean isOnIndependentService(ElevatorCarPanelBlockEntity blockEntity){
        ElevatorGroup group = blockEntity == null ? null : blockEntity.getGroup();
        return group != null && group.getServiceMode() == ElevatorGroup.ServiceMode.INDEPENDENT;
    }

    /**
     * Adds the independent service key switch to the top-right of the header, where both screens put
     * it.
     * <p>
     * Beside the title rather than in a row of its own: it is a staff control, not something a
     * passenger reaches for, and the rows below are the passenger's. The header is also the one place
     * on either screen with spare space, so tucking it there costs no height and leaves the car
     * panel's grid and the control rows identical on both screens.
     * <p>
     * No permission check here. The server decides who may throw it and tells the player when they
     * may not; checking here as well would duplicate a rule the server has to enforce anyway, and
     * would be wrong the moment the two disagreed.
     */
    private void addKeySwitch(ElevatorCarPanelBlockEntity blockEntity){
        this.addWidget(new KeySwitchWidget(keySwitchX(blockEntity), KEY_SWITCH_Y,
            () -> isOnIndependentService(blockEntity),
            () -> independentTooltip(blockEntity),
            () -> MovingElevators.CHANNEL.sendToServer(new PacketToggleIndependentService(this.blockEntityPos))));
    }

    @Override
    protected int width(ElevatorCarPanelBlockEntity blockEntity){
        int columns = columnsFor(floorCount(blockEntity));
        int grid = columns * BUTTON_SIZE + (columns - 1) * GAP;
        // The header has to fit too. With a narrow shaft the grid is only one or two buttons wide,
        // and the title alone was wider than the whole panel.
        return PADDING * 2 + Math.max(grid, headerWidth(blockEntity));
    }

    /**
     * Measured from the longest floor name rather than the floor the cabin happens to be at, so the
     * panel keeps one width while the elevator moves instead of resizing under the cursor.
     */
    private static int headerWidth(ElevatorCarPanelBlockEntity blockEntity){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        // The header's two lines are centred, and the key switch sits at the right-hand end of them,
        // so the room it takes has to be reserved on both sides or a centred line would slide under
        // it. Reserving symmetrically is what keeps the lines centred on the panel rather than on
        // what is left of it.
        int centredTextReserve = (KeySwitchWidget.SIZE + GAP) * 2;
        int widest = fontRenderer.getStringWidth(TextComponents.translation("movingelevators.floor_select.title").format()) + centredTextReserve;
        // The door row is two buttons side by side, so the panel has to be wide enough for both
        // labels plus a little padding inside each button, or the text spills over the edges.
        int doorOpen = fontRenderer.getStringWidth(TextComponents.translation("movingelevators.floor_select.door_open").format());
        int doorClose = fontRenderer.getStringWidth(TextComponents.translation("movingelevators.floor_select.door_close").format());
        widest = Math.max(widest, doorOpen + doorClose + DOOR_LABEL_PADDING * 2 + GAP);
        // The emergency stop spans the whole interior on its own, and its label is far longer than
        // any other button's, so it can set the panel's width where the short alarm label never does.
        int emergencyStop = fontRenderer.getStringWidth(TextComponents.translation("movingelevators.floor_select.emergency_stop").format());
        widest = Math.max(widest, emergencyStop + DOOR_LABEL_PADDING * 2);

        ElevatorGroup group = blockEntity == null ? null : blockEntity.getGroup();
        if(group != null){
            for(int floor = 0; floor < group.getFloorCount(); floor++){
                String name = MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor);
                widest = Math.max(widest, fontRenderer.getStringWidth(
                    TextComponents.translation("movingelevators.floor_select.current", TextComponents.string(name).get()).format())
                    + centredTextReserve);
            }
        }
        return widest;
    }

    @Override
    protected int height(ElevatorCarPanelBlockEntity blockEntity){
        int rows = rowsFor(floorCount(blockEntity));
        return PADDING + HEADER + rows * BUTTON_SIZE + (rows - 1) * GAP
            + DOOR_ROW_GAP + DOOR_ROW_HEIGHT + GAP + DOOR_ROW_HEIGHT + GAP + DOOR_ROW_HEIGHT + PADDING;
    }

    private static int floorCount(ElevatorCarPanelBlockEntity blockEntity){
        ElevatorGroup group = blockEntity == null ? null : blockEntity.getGroup();
        return group == null ? 0 : group.getFloorCount();
    }

    @Override
    protected ITextComponent getNarrationMessage(ElevatorCarPanelBlockEntity blockEntity){
        return TextComponents.translation("movingelevators.floor_select.title").get();
    }

    @Override
    protected void addWidgets(@Nonnull ElevatorCarPanelBlockEntity blockEntity){
        // Before the early return below, since the switch needs no floors to make sense and widgets
        // are only ever built once -- a panel that came up momentarily unsynced would otherwise be
        // missing its switch for as long as it stayed open.
        this.addKeySwitch(blockEntity);

        ElevatorGroup group = blockEntity.getGroup();
        if(group == null)
            return;

        int floors = group.getFloorCount();
        int rows = rowsFor(floors);

        int columns = columnsFor(floors);
        int grid = columns * BUTTON_SIZE + (columns - 1) * GAP;
        int gridLeft = (this.width(blockEntity) - grid) / 2;

        for(int floor = 0; floor < floors; floor++){
            // Lowest floor bottom-left, filling rightwards then upwards -- a car station reads
            // bottom-up, not top-down like a list.
            int column = floor % columns;
            int row = floor / columns;
            int x = gridLeft + column * (BUTTON_SIZE + GAP);
            int y = PADDING + HEADER + (rows - 1 - row) * (BUTTON_SIZE + GAP);

            int floorIndex = floor;
            int floorY = group.getFloorYLevel(floor);
            String name = MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floorIndex), floorIndex);
            this.addWidget(new FloorButtonWidget(x, y, BUTTON_SIZE, BUTTON_SIZE,
                () -> shortLabel(blockEntity, floorIndex),
                () -> {
                    ElevatorGroup current = blockEntity.getGroup();
                    return current != null && current.hasCallFor(floorY);
                },
                () -> {
                    ElevatorGroup current = blockEntity.getGroup();
                    return current != null && current.getCabinFloorNumber() == floorIndex;
                },
                TextComponents.string(name).get(),
                () -> MovingElevators.CHANNEL.sendToServer(new PacketRequestFloor(this.blockEntityPos, floorY))));
        }

        this.addDoorControls(blockEntity, rows);
    }

    /**
     * "Open doors" and "Close doors", the two controls a real car station has that are not floors,
     * plus the alarm and the emergency stop below them. The door buttons act on whichever floor the
     * cabin is parked at, so they do nothing while it is in motion.
     */
    private void addDoorControls(ElevatorCarPanelBlockEntity blockEntity, int rows){
        int y = PADDING + HEADER + rows * BUTTON_SIZE + (rows - 1) * GAP + DOOR_ROW_GAP;
        int available = this.width(blockEntity) - PADDING * 2 - GAP;
        int buttonWidth = available / 2;

        this.addWidget(new FloorButtonWidget(PADDING, y, buttonWidth, DOOR_ROW_HEIGHT,
            () -> TextComponents.translation("movingelevators.floor_select.door_open").format(),
            () -> false, () -> false,
            TextComponents.translation("movingelevators.floor_select.door_open").get(),
            () -> MovingElevators.CHANNEL.sendToServer(new PacketDoorControl(this.blockEntityPos, true))));
        this.addWidget(new FloorButtonWidget(PADDING + buttonWidth + GAP, y, available - buttonWidth, DOOR_ROW_HEIGHT,
            () -> TextComponents.translation("movingelevators.floor_select.door_close").format(),
            () -> false, () -> false,
            TextComponents.translation("movingelevators.floor_select.door_close").get(),
            () -> MovingElevators.CHANNEL.sendToServer(new PacketDoorControl(this.blockEntityPos, false))));

        // The alarm gets a row to itself rather than a third seat in the door row: it is not a door
        // control, and it is the one button here that should be hard to hit by accident.
        this.addWidget(new AlarmButtonWidget(PADDING, y + DOOR_ROW_HEIGHT + GAP, available + GAP, DOOR_ROW_HEIGHT,
            () -> TextComponents.translation("movingelevators.floor_select.alarm").format(),
            TextComponents.translation("movingelevators.floor_select.alarm.tooltip").get(),
            () -> MovingElevators.CHANNEL.sendToServer(new PacketRingAlarm(this.blockEntityPos))));

        // A plain FloorButtonWidget rather than the alarm's hold-to-ring widget: the stop is a single
        // press that latches on the elevator itself, so holding it would send the same request over
        // and over for no benefit. Full width and bottom-most, the way the physical control is the
        // odd one out at the end of the station.
        this.addWidget(new FloorButtonWidget(PADDING, y + (DOOR_ROW_HEIGHT + GAP) * 2, available + GAP, DOOR_ROW_HEIGHT,
            () -> TextComponents.translation("movingelevators.floor_select.emergency_stop").format(),
            () -> false, () -> false,
            TextComponents.translation("movingelevators.floor_select.emergency_stop").get(),
            () -> MovingElevators.CHANNEL.sendToServer(new PacketEmergencyStop(this.blockEntityPos))));
    }

    /**
     * The button face only has room for a couple of characters, so it shows the same identifier the
     * physical panels do -- "3" rather than "Floor 3". The full name is in the tooltip.
     */
    private static String shortLabel(ElevatorCarPanelBlockEntity blockEntity, int floor){
        ElevatorGroup group = blockEntity.getGroup();
        if(group == null || floor >= group.getFloorCount())
            return "";
        String name = MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor);
        String stripped = MovingElevatorsClient.stripFloorPrefix(name);
        return stripped.length() <= 3 ? stripped : stripped.substring(0, 3);
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, ElevatorCarPanelBlockEntity blockEntity){
        ScreenUtils.drawScreenBackground(0, 0, this.width(), this.height());
    }

    @Override
    protected void renderForeground(int mouseX, int mouseY, ElevatorCarPanelBlockEntity blockEntity){
        ElevatorGroup group = blockEntity.getGroup();
        ITextComponent title = TextComponents.translation("movingelevators.floor_select.title").get();
        ScreenUtils.drawCenteredString(title, this.width() / 2f, 6);

        if(group != null){
            int floor = group.getCabinFloorNumber();
            if(floor >= 0 && floor < group.getFloorCount()){
                String at = MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor);
                ScreenUtils.drawCenteredString(
                    TextComponents.translation("movingelevators.floor_select.current", TextComponents.string(at).get()).get(),
                    this.width() / 2f, 15);
            }
        }
    }

    private int keySwitchX(ElevatorCarPanelBlockEntity blockEntity){
        return this.width(blockEntity) - PADDING - KeySwitchWidget.SIZE;
    }

    /**
     * The key switch's tooltip is drawn here rather than by the widget.
     * <p>
     * A container only renders tooltips for whichever child it currently considers focused, and that
     * never produced one for this switch. It is the single control on the panel that cannot be
     * understood without words -- an unlabelled keyhole -- so it is worth taking the explicit route
     * rather than depending on focus bookkeeping to work out.
     */
    @Override
    protected void renderTooltips(int mouseX, int mouseY, ElevatorCarPanelBlockEntity blockEntity){
        super.renderTooltips(mouseX, mouseY, blockEntity);
        int x = this.keySwitchX(blockEntity);
        if(mouseX >= x && mouseX < x + KeySwitchWidget.SIZE
            && mouseY >= KEY_SWITCH_Y && mouseY < KEY_SWITCH_Y + KeySwitchWidget.SIZE)
            FloorSelectScreen.drawIndependentTooltip(blockEntity, x, mouseY);
    }
}
