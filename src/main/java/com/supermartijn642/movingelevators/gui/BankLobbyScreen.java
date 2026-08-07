package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.BlockEntityBaseWidget;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.blocks.BankLobbyPanelBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.packets.PacketBankDestination;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * The lobby panel's destination list: you pick where you are going before you board, and the panel
 * decides which of the bank's cars collects you.
 * <p>
 * There is no up/down call here on purpose. A bank knows the whole trip the moment the destination is
 * chosen, which is the only reason it can send a car that is already going that way -- asking for a
 * direction first and a floor later would throw that information away.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankLobbyScreen extends BlockEntityBaseWidget<BankLobbyPanelBlockEntity> {

    private static final int BUTTON_SIZE = 22, GAP = 3, PADDING = 7;
    /**
     * A shorter header than the car panel's: that one carries a second line for where the cabin
     * currently is, and a lobby panel never moves, so there is nothing to leave room for.
     */
    private static final int HEADER = 16;
    /** Rows before the grid grows sideways instead, so a tall building cannot run off the screen. */
    private static final int MAX_ROWS = 8;
    /** One line of text, for a panel with nothing to dispatch. */
    private static final int MESSAGE_HEIGHT = 12;

    public BankLobbyScreen(BlockPos entityPos){
        super(0, 0, 0, 0, ClientUtils.getWorld(), entityPos);
    }

    /**
     * Columns for a roughly square grid, widening further once it would otherwise get taller than
     * {@link #MAX_ROWS}.
     * <p>
     * Deliberately the same maths as {@link FloorSelectScreen}: the two panels sit in the same
     * building and a player who has learnt one should be able to read the other at a glance. Copied
     * rather than hoisted into a shared helper -- it is six lines of arithmetic, and tying the two
     * layouts together would mean the car panel could no longer be tuned without moving the lobby's
     * buttons around too.
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
    protected int width(BankLobbyPanelBlockEntity blockEntity){
        int floors = floorCount(blockEntity);
        if(floors == 0)
            return PADDING * 2 + Math.max(headerWidth(), messageWidth());

        int columns = columnsFor(floors);
        int grid = columns * BUTTON_SIZE + (columns - 1) * GAP;
        // The header has to fit too. A bank serving three floors is a one column grid, and the title
        // alone is wider than that.
        return PADDING * 2 + Math.max(grid, headerWidth());
    }

    /**
     * Only the title is measured here, unlike the car panel, which also has to fit its stretched door
     * buttons and its current-floor line. Every button on this screen is one fixed square, so the
     * grid's own width already accounts for the widest of them.
     */
    private static int headerWidth(){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        return fontRenderer.getStringWidth(TextComponents.translation("movingelevators.bank_lobby.title").format());
    }

    private static int messageWidth(){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        return fontRenderer.getStringWidth(TextComponents.translation("movingelevators.bank_lobby.unbound").format());
    }

    @Override
    protected int height(BankLobbyPanelBlockEntity blockEntity){
        int floors = floorCount(blockEntity);
        if(floors == 0)
            return PADDING + HEADER + MESSAGE_HEIGHT + PADDING;

        int rows = rowsFor(floors);
        return PADDING + HEADER + rows * BUTTON_SIZE + (rows - 1) * GAP + PADDING;
    }

    /**
     * A bound panel whose elevators have all been pulled out counts as having no floors, so it falls
     * back to the same notice an unbound one shows rather than drawing an empty grid.
     */
    private static int floorCount(BankLobbyPanelBlockEntity blockEntity){
        return blockEntity == null || !blockEntity.isBound() ? 0 : blockEntity.getBankFloors().size();
    }

    @Override
    protected ITextComponent getNarrationMessage(BankLobbyPanelBlockEntity blockEntity){
        return TextComponents.translation("movingelevators.bank_lobby.title").get();
    }

    @Override
    protected void addWidgets(@Nonnull BankLobbyPanelBlockEntity blockEntity){
        List<Integer> floors = blockEntity.isBound() ? blockEntity.getBankFloors() : Collections.emptyList();
        if(floors.isEmpty())
            return;

        int rows = rowsFor(floors.size());
        int columns = columnsFor(floors.size());
        int grid = columns * BUTTON_SIZE + (columns - 1) * GAP;
        int gridLeft = (this.width(blockEntity) - grid) / 2;
        int panelFloor = panelFloorIndex(floors, blockEntity.getPanelY());

        for(int index = 0; index < floors.size(); index++){
            // Lowest floor bottom-left, filling rightwards then upwards -- the same reading order as
            // the car panel, and the same order as the building.
            int column = index % columns;
            int row = index / columns;
            int x = gridLeft + column * (BUTTON_SIZE + GAP);
            int y = PADDING + HEADER + (rows - 1 - row) * (BUTTON_SIZE + GAP);

            int floorIndex = index;
            int floorY = floors.get(index);
            boolean isPanelFloor = index == panelFloor;
            String name = MovingElevatorsClient.formatFloorDisplayName(blockEntity.getFloorName(floorY), floorIndex);
            this.addWidget(new FloorButtonWidget(x, y, BUTTON_SIZE, BUTTON_SIZE,
                // A floor with a car coming alternates between its own number and the name of the
                // car coming for it. The press leaves the panel immediately and the screen stays open,
                // so without this the only evidence it registered is a line of chat -- and naming the
                // car answers the question a passenger asks next anyway. That a lobby panel is shared
                // makes this better rather than worse: everyone waiting can see what is already on
                // its way and for which floor.
                () -> {
                    ElevatorGroup car = blockEntity.getPendingCar(floorY);
                    return car != null && car.getName() != null && car.isAnnounceFlashOn()
                        ? car.getName() : shortLabel(blockEntity, floorY, floorIndex);
                },
                () -> blockEntity.getPendingCar(floorY) != null,
                () -> isPanelFloor,
                TextComponents.string(name).get(),
                // Asking for the floor you are already standing on is a trip to nowhere, so the button
                // is inert rather than dispatching a car that would arrive with nothing to do.
                isPanelFloor ? () -> {
                } : () -> MovingElevators.CHANNEL.sendToServer(new PacketBankDestination(this.blockEntityPos, floorY))));
        }
    }

    /**
     * Which entry of {@code floors} is the landing this panel stands on.
     * <p>
     * Nearest rather than exact, ties going to the lower floor, matching how the dispatcher picks the
     * pickup floor -- a panel hung a block above a landing has to agree with the car that answers it,
     * or it would mark one floor as current and collect you from another.
     *
     * @return the index, or -1 when there are no floors at all
     */
    private static int panelFloorIndex(List<Integer> floors, int panelY){
        int best = -1, bestDistance = Integer.MAX_VALUE;
        for(int index = 0; index < floors.size(); index++){
            int distance = Math.abs(floors.get(index) - panelY);
            if(distance < bestDistance){
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    /**
     * The button face only has room for a couple of characters, so it shows the same identifier the
     * physical panels do -- "3" rather than "Floor 3". The full name is in the tooltip.
     */
    private static String shortLabel(BankLobbyPanelBlockEntity blockEntity, int floorY, int index){
        String name = MovingElevatorsClient.formatFloorDisplayName(blockEntity.getFloorName(floorY), index);
        String stripped = MovingElevatorsClient.stripFloorPrefix(name);
        return stripped.length() <= 3 ? stripped : stripped.substring(0, 3);
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, BankLobbyPanelBlockEntity blockEntity){
        ScreenUtils.drawScreenBackground(0, 0, this.width(), this.height());
    }

    @Override
    protected void renderForeground(int mouseX, int mouseY, BankLobbyPanelBlockEntity blockEntity){
        ScreenUtils.drawCenteredString(TextComponents.translation("movingelevators.bank_lobby.title").get(), this.width() / 2f, 6);

        if(floorCount(blockEntity) == 0)
            ScreenUtils.drawCenteredString(TextComponents.translation("movingelevators.bank_lobby.unbound").get(), this.width() / 2f, PADDING + HEADER);
    }
}
