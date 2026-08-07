package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.ClientUtils;
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
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;

/**
 * The bank car panel's controls: where the cabin is, the doors, and the alarm -- and no floor buttons.
 * <p>
 * This is {@link FloorSelectScreen} with the grid taken out, because that grid is exactly what
 * destination dispatch removes from a car. A passenger boards a banked elevator already routed, so the
 * only things left worth reaching for inside are the ones that are not a choice of floor.
 * <p>
 * The title carries the elevator's name when it has one. On an ordinary car panel there is only ever
 * one elevator to be in, but a bank is several, and "which car did the lobby put me in" is the whole
 * reason the elevators get named in the first place.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class CarControlsScreen extends BlockEntityBaseWidget<ElevatorCarPanelBlockEntity> {

    /**
     * Deliberately duplicated from {@link FloorSelectScreen}, where they are private. The two screens
     * are the same plate with a different set of controls on it and have to line up visually, but
     * sharing the numbers would mean neither could be nudged without moving the other -- and this one
     * has no grid to tune around, so it will drift on purpose sooner or later.
     */
    private static final int GAP = 3, PADDING = 7, HEADER = 22;
    /**
     * The door row, with the alarm in a row below it and the emergency stop below that, matching the
     * car panel's rows and spacing exactly -- a passenger moving between a banked car and an ordinary
     * one should find the same controls in the same places.
     */
    private static final int DOOR_ROW_HEIGHT = 20, DOOR_ROW_GAP = 5, DOOR_LABEL_PADDING = 6;

    public CarControlsScreen(BlockPos entityPos){
        super(0, 0, 0, 0, ClientUtils.getWorld(), entityPos);
    }

    /** Where the door row starts. Fixed, since nothing above it varies in height. */
    private static int doorRowY(){
        // DOOR_ROW_GAP still applies with the grid gone: it is the breathing room between the readout
        // and the controls, which the car panel happens to spend on the bottom of its grid.
        return PADDING + HEADER + DOOR_ROW_GAP;
    }

    @Override
    protected int width(ElevatorCarPanelBlockEntity blockEntity){
        return PADDING * 2 + headerWidth(blockEntity);
    }

    /**
     * The widest of the title, the door row, the emergency stop and the current-floor line -- the
     * last measured from every floor name rather than the one the cabin happens to be at, so the
     * panel keeps one width while the elevator moves instead of resizing under the cursor.
     */
    private static int headerWidth(ElevatorCarPanelBlockEntity blockEntity){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        int widest = fontRenderer.getStringWidth(title(blockEntity).format());
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
                    TextComponents.translation("movingelevators.floor_select.current", TextComponents.string(name).get()).format()));
            }
        }
        return widest;
    }

    @Override
    protected int height(ElevatorCarPanelBlockEntity blockEntity){
        return doorRowY() + DOOR_ROW_HEIGHT + GAP + DOOR_ROW_HEIGHT + GAP + DOOR_ROW_HEIGHT + PADDING;
    }

    /**
     * The elevator's name in the title when it has one, so a passenger can tell which of the bank's
     * cars they are standing in.
     */
    private static TextComponents.TextComponentBuilder title(ElevatorCarPanelBlockEntity blockEntity){
        ElevatorGroup group = blockEntity == null ? null : blockEntity.getGroup();
        String name = group == null ? null : group.getName();
        return name == null
            ? TextComponents.translation("movingelevators.car_controls.title")
            : TextComponents.translation("movingelevators.car_controls.title_named", TextComponents.string(name).get());
    }

    @Override
    protected ITextComponent getNarrationMessage(ElevatorCarPanelBlockEntity blockEntity){
        return title(blockEntity).get();
    }

    @Override
    protected void addWidgets(@Nonnull ElevatorCarPanelBlockEntity blockEntity){
        // No early return on a missing group, unlike the car panel: every widget it builds is a floor
        // and there are no floors without a group, whereas none of these controls need one to be laid
        // out, and both packets already refuse to do anything when there is no group behind them. A
        // panel that came up momentarily unsynced would otherwise be a blank plate for good, since
        // widgets are only built once.
        this.addDoorControls(blockEntity);
    }

    /**
     * "Open doors" and "Close doors", plus the alarm and the emergency stop. Same widgets, labels and
     * messages as the ordinary car panel -- a banked car takes away the floor buttons, not the
     * controls beside them.
     */
    private void addDoorControls(ElevatorCarPanelBlockEntity blockEntity){
        int y = doorRowY();
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

    @Override
    protected void renderBackground(int mouseX, int mouseY, ElevatorCarPanelBlockEntity blockEntity){
        ScreenUtils.drawScreenBackground(0, 0, this.width(), this.height());
    }

    @Override
    protected void renderForeground(int mouseX, int mouseY, ElevatorCarPanelBlockEntity blockEntity){
        ElevatorGroup group = blockEntity.getGroup();
        ScreenUtils.drawCenteredString(title(blockEntity).get(), this.width() / 2f, 6);

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
