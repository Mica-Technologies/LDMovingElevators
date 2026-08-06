package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.BlockEntityBaseWidget;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
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
    /** Floors per column before a second column is started. Keeps tall shafts from running off-screen. */
    private static final int MAX_ROWS = 8;

    public FloorSelectScreen(BlockPos entityPos){
        super(0, 0, 0, 0, ClientUtils.getWorld(), entityPos);
    }

    private static int columnsFor(int floorCount){
        return Math.max(1, (floorCount + MAX_ROWS - 1) / MAX_ROWS);
    }

    private static int rowsFor(int floorCount){
        return Math.min(Math.max(floorCount, 1), MAX_ROWS);
    }

    @Override
    protected int width(ElevatorCarPanelBlockEntity blockEntity){
        int columns = columnsFor(floorCount(blockEntity));
        return PADDING * 2 + columns * BUTTON_SIZE + (columns - 1) * GAP;
    }

    @Override
    protected int height(ElevatorCarPanelBlockEntity blockEntity){
        int rows = rowsFor(floorCount(blockEntity));
        return PADDING + HEADER + rows * BUTTON_SIZE + (rows - 1) * GAP + PADDING;
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

        for(int floor = 0; floor < floors; floor++){
            // Lowest floor at the bottom of the first column, filling upwards -- a car station reads
            // bottom-up, not top-down like a list.
            int column = floor / MAX_ROWS;
            int row = floor % MAX_ROWS;
            int x = PADDING + column * (BUTTON_SIZE + GAP);
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
