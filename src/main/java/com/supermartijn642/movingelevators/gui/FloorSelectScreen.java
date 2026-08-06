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
import com.supermartijn642.movingelevators.packets.PacketRingAlarm;
import com.supermartijn642.movingelevators.packets.PacketRequestFloor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

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
    /** Door controls sit under the grid, in their own row, with the alarm in a row below that. */
    private static final int DOOR_ROW_HEIGHT = 20, DOOR_ROW_GAP = 5, DOOR_LABEL_PADDING = 6;
    /** Rows before the grid grows sideways instead, so a tall shaft cannot run off the screen. */
    private static final int MAX_ROWS = 8;

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
        int widest = fontRenderer.getStringWidth(TextComponents.translation("movingelevators.floor_select.title").format());
        // The door row is two buttons side by side, so the panel has to be wide enough for both
        // labels plus a little padding inside each button, or the text spills over the edges.
        int doorOpen = fontRenderer.getStringWidth(TextComponents.translation("movingelevators.floor_select.door_open").format());
        int doorClose = fontRenderer.getStringWidth(TextComponents.translation("movingelevators.floor_select.door_close").format());
        widest = Math.max(widest, doorOpen + doorClose + DOOR_LABEL_PADDING * 2 + GAP);

        ElevatorGroup group = blockEntity == null ? null : blockEntity.getGroup();
        if(group != null){
            for(int floor = 0; floor < group.getFloorCount(); floor++){
                String name = MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor);
                widest = Math.max(widest, fontRenderer.getStringWidth(
                    TextComponents.translation("movingelevators.floor_select.current", TextComponents.string(name).get()).format()));
            }
        }
        return widest;
    }

    @Override
    protected int height(ElevatorCarPanelBlockEntity blockEntity){
        int rows = rowsFor(floorCount(blockEntity));
        return PADDING + HEADER + rows * BUTTON_SIZE + (rows - 1) * GAP
            + DOOR_ROW_GAP + DOOR_ROW_HEIGHT + GAP + DOOR_ROW_HEIGHT + PADDING;
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
     * "Open doors" and "Close doors", the two controls a real car station has that are not floors.
     * They act on whichever floor the cabin is parked at, so they do nothing while it is in motion.
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
}
