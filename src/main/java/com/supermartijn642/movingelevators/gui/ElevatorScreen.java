package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.BlockEntityBaseWidget;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.packets.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Created 05/02/2022 by SuperMartijn642
 */
public class ElevatorScreen extends BlockEntityBaseWidget<ControllerBlockEntity> {

    public static final int MAX_NAME_LENGTH = 11;

    /**
     * The elevator name field, tucked into the void to the left of the two option checkboxes.
     * <p>
     * That is the only space left on this screen. The background texture draws a sunken slot behind
     * the floor name field and behind the "hide controls" checkbox, so neither of those can move off
     * y=31 and (42,60) without the panel showing an empty recess; the fork's own sounds checkbox and
     * sound scheme button then fill the flat area below them down to y=104, against a panel that ends
     * at y=110. What is left between rows is at most a six pixel gap -- too short for an eleven pixel
     * field. The checkboxes are only 11px wide and centred in the 84px column, though, which leaves a
     * 35x35 void down either side of them, and that is the largest free rectangle in the column.
     * <p>
     * Left-hand side, so the field lines up with the x=6 every other element in the column starts at,
     * and 34px wide, which ends it two pixels clear of the checkbox. Far too narrow for a label line
     * of its own, so the field's suggestion has to serve as the label.
     */
    /**
     * The elevator name field takes the band the "Controls" heading used to occupy. That heading only
     * repeated what the checkbox beneath it already says in full -- "Hide controls: True/False" --
     * and it was the one full-width gap in this column, every other free band being four or five
     * pixels tall. A short field squeezed beside a checkbox could not have shown its own placeholder,
     * which is the only label it gets.
     */
    private static final int ELEVATOR_NAME_X = 6, ELEVATOR_NAME_Y = 45, ELEVATOR_NAME_WIDTH = 84;

    private static final ResourceLocation BACKGROUND = new ResourceLocation("movingelevators", "textures/gui/gui_background.png");
    private static final ResourceLocation SIZE_ICONS = new ResourceLocation("movingelevators", "textures/gui/size_icons2.png");

    public ElevatorScreen(BlockPos entityPos){
        super(0, 0, 280, 118, ClientUtils.getWorld(), entityPos);
    }

    @Override
    protected ITextComponent getNarrationMessage(ControllerBlockEntity object){
        return null;
    }

    @Override
    protected void addWidgets(@Nonnull ControllerBlockEntity blockEntity){
        // Floor name
        this.addWidget(new SynchingTextFieldWidget(6, 31, 84, MAX_NAME_LENGTH, () -> {
                String name = blockEntity.getFloorName();
                return name == null ? "" : name;
            }, name -> MovingElevators.CHANNEL.sendToServer(new PacketSetFloorName(this.blockEntityPos, name))))
            .setSuggestion(MovingElevatorsClient.formatFloorDisplayName(null, blockEntity.getGroup().getFloorNumber(blockEntity.getFloorLevel())));
        // Elevator name. On the group rather than on this controller, because it names the car: it is
        // what a bank lobby panel announces and what the car panel displays, so it cannot differ from
        // one floor's controller to the next.
        //
        // Unlike the floor name above, this reads through the group, which a controller does not have
        // until its first tick and loses when the last floor goes -- and this supplier is polled every
        // tick, not just while the screen is being built, so it has to survive that.
        this.addWidget(new SynchingTextFieldWidget(ELEVATOR_NAME_X, ELEVATOR_NAME_Y, ELEVATOR_NAME_WIDTH, MAX_NAME_LENGTH, () -> {
                ElevatorGroup group = blockEntity.getGroup();
                String name = group == null ? null : group.getName();
                return name == null ? "" : name;
            }, name -> MovingElevators.CHANNEL.sendToServer(new PacketSetElevatorName(this.blockEntityPos, name))))
            .setSuggestion(TextComponents.translation("movingelevators.elevator_screen.elevator_name").format());
        // Render buttons option
        this.addWidget(new CheckBoxWidget(42, 60,
            checked -> TextComponents.translation("movingelevators.elevator_screen.hide_controls", checked ? TextComponents.translation("movingelevators.elevator_screen.hide_controls.on").color(TextFormatting.GREEN).get() : TextComponents.translation("movingelevators.elevator_screen.hide_controls.off").color(TextFormatting.RED).get()).get(),
            () -> this.object.areControlsHidden(),
            checked -> MovingElevators.CHANNEL.sendToServer(new PacketToggleShowControllerButtons(this.blockEntityPos))
        ));

        // Out of service. On the group, like everything else here that is about the elevator rather
        // than this one controller.
        this.addWidget(new CheckBoxWidget(42, 76,
            checked -> TextComponents.translation("movingelevators.elevator_screen.out_of_service", checked ? TextComponents.translation("movingelevators.elevator_screen.out_of_service.yes").color(TextFormatting.RED).get() : TextComponents.translation("movingelevators.elevator_screen.out_of_service.no").color(TextFormatting.GREEN).get()).get(),
            () -> this.object.hasGroup() && this.object.getGroup().isOutOfService(),
            checked -> MovingElevators.CHANNEL.sendToServer(new PacketToggleOutOfService(this.blockEntityPos))
        ));
        // One control for "what does this elevator sound like", off being its first answer. Two
        // controls for one question cost a row the screen has not got.
        this.addWidget(new CycleButtonWidget(6, 91, 84, 14,
            () -> TextComponents.translation("movingelevators.elevator_screen.sounds",
                TextComponents.translation(!this.object.hasGroup() || !this.object.getGroup().areSoundsEnabled()
                    ? "movingelevators.elevator_screen.sounds.off"
                    : this.object.getGroup().getSoundScheme().getNameTranslationKey()).get()).get(),
            TextComponents.translation("movingelevators.elevator_screen.sound_scheme").get(),
            () -> MovingElevators.CHANNEL.sendToServer(new PacketCycleElevatorSoundScheme(this.blockEntityPos))));
        // Width
        PlusMinusButtonWidget widthSizeIncrease = this.addWidget(new PlusMinusButtonWidget(207, 31, true, TextComponents.translation("movingelevators.elevator_screen.cabin_width.increase_size").get(), () -> blockEntity.getGroup().canIncreaseCageWidth(), () -> MovingElevators.CHANNEL.sendToServer(new PacketIncreaseCabinWidth(this.blockEntityPos))));
        PlusMinusButtonWidget widthSizeDecrease = this.addWidget(new PlusMinusButtonWidget(230, 31, false, TextComponents.translation("movingelevators.elevator_screen.cabin_width.decrease_size").get(), () -> blockEntity.getGroup().canDecreaseCageWidth(), () -> MovingElevators.CHANNEL.sendToServer(new PacketDecreaseCabinWidth(this.blockEntityPos))));
        LeftRightArrowWidget widthOffsetDecrease = this.addWidget(new LeftRightArrowWidget(247, 31, true, TextComponents.translation("movingelevators.elevator_screen.cabin_width.decrease_offset").get(), () -> blockEntity.getGroup().canDecreaseCageSideOffset(), () -> MovingElevators.CHANNEL.sendToServer(new PacketDecreaseCabinSideOffset(this.blockEntityPos))));
        LeftRightArrowWidget widthOffsetIncrease = this.addWidget(new LeftRightArrowWidget(267, 31, false, TextComponents.translation("movingelevators.elevator_screen.cabin_width.increase_offset").get(), () -> blockEntity.getGroup().canIncreaseCageSideOffset(), () -> MovingElevators.CHANNEL.sendToServer(new PacketIncreaseCabinSideOffset(this.blockEntityPos))));
        // Depth
        PlusMinusButtonWidget depthSizeIncrease = this.addWidget(new PlusMinusButtonWidget(207, 47, true, TextComponents.translation("movingelevators.elevator_screen.cabin_depth.increase_size").get(), () -> blockEntity.getGroup().canIncreaseCageDepth(), () -> MovingElevators.CHANNEL.sendToServer(new PacketIncreaseCabinDepth(this.blockEntityPos))));
        PlusMinusButtonWidget depthSizeDecrease = this.addWidget(new PlusMinusButtonWidget(230, 47, false, TextComponents.translation("movingelevators.elevator_screen.cabin_depth.decrease_size").get(), () -> blockEntity.getGroup().canDecreaseCageDepth(), () -> MovingElevators.CHANNEL.sendToServer(new PacketDecreaseCabinDepth(this.blockEntityPos))));
        LeftRightArrowWidget depthOffsetDecrease = this.addWidget(new LeftRightArrowWidget(247, 47, true, TextComponents.translation("movingelevators.elevator_screen.cabin_depth.decrease_offset").get(), () -> blockEntity.getGroup().canDecreaseCageDepthOffset(), () -> MovingElevators.CHANNEL.sendToServer(new PacketDecreaseCabinDepthOffset(this.blockEntityPos))));
        LeftRightArrowWidget depthOffsetIncrease = this.addWidget(new LeftRightArrowWidget(267, 47, false, TextComponents.translation("movingelevators.elevator_screen.cabin_depth.increase_offset").get(), () -> blockEntity.getGroup().canIncreaseCageDepthOffset(), () -> MovingElevators.CHANNEL.sendToServer(new PacketIncreaseCabinDepthOffset(this.blockEntityPos))));
        // Height
        PlusMinusButtonWidget heightSizeIncrease = this.addWidget(new PlusMinusButtonWidget(207, 63, true, TextComponents.translation("movingelevators.elevator_screen.cabin_height.increase_size").get(), () -> blockEntity.getGroup().canIncreaseCageHeight(), () -> MovingElevators.CHANNEL.sendToServer(new PacketIncreaseCabinHeight(this.blockEntityPos))));
        PlusMinusButtonWidget heightSizeDecrease = this.addWidget(new PlusMinusButtonWidget(230, 63, false, TextComponents.translation("movingelevators.elevator_screen.cabin_height.decrease_size").get(), () -> blockEntity.getGroup().canDecreaseCageHeight(), () -> MovingElevators.CHANNEL.sendToServer(new PacketDecreaseCabinHeight(this.blockEntityPos))));
        LeftRightArrowWidget heightOffsetDecrease = this.addWidget(new LeftRightArrowWidget(247, 63, true, TextComponents.translation("movingelevators.elevator_screen.cabin_height.decrease_offset").get(), () -> blockEntity.getGroup().canDecreaseCageHeightOffset(), () -> MovingElevators.CHANNEL.sendToServer(new PacketDecreaseCabinHeightOffset(this.blockEntityPos))));
        LeftRightArrowWidget heightOffsetIncrease = this.addWidget(new LeftRightArrowWidget(267, 63, false, TextComponents.translation("movingelevators.elevator_screen.cabin_height.increase_offset").get(), () -> blockEntity.getGroup().canIncreaseCageHeightOffset(), () -> MovingElevators.CHANNEL.sendToServer(new PacketIncreaseCabinHeightOffset(this.blockEntityPos))));
        // Speed
        this.addWidget(new SliderWidget(190, 92, 84, 1, MovingElevatorsConfig.maxCabinSpeed.get(), (int)Math.round(blockEntity.getGroup().getTargetSpeed() * 10), speed -> TextComponents.translation("movingelevators.elevator_screen.current_speed", TextComponents.number(speed / 10d, 1).get()).get(), speed -> MovingElevators.CHANNEL.sendToServer(new PacketElevatorSpeed(this.blockEntityPos, speed / 10d))));

        // Cabin preview
        Supplier<BlockPos> previewSizeIncrease = () -> new BlockPos(widthSizeIncrease.active && widthSizeIncrease.isFocused() ? 1 : widthSizeDecrease.active && widthSizeDecrease.isFocused() ? -1 : 0, heightSizeIncrease.active && heightSizeIncrease.isFocused() ? 1 : heightSizeDecrease.active && heightSizeDecrease.isFocused() ? -1 : 0, depthSizeIncrease.active && depthSizeIncrease.isFocused() ? 1 : depthSizeDecrease.active && depthSizeDecrease.isFocused() ? -1 : 0);
        Supplier<BlockPos> previewOffset = () -> new BlockPos(widthOffsetIncrease.active && widthOffsetIncrease.isFocused() ? 1 : widthOffsetDecrease.active && widthOffsetDecrease.isFocused() ? -1 : 0, heightOffsetIncrease.active && heightOffsetIncrease.isFocused() ? 1 : heightOffsetDecrease.active && heightOffsetDecrease.isFocused() ? -1 : 0, depthOffsetIncrease.active && depthOffsetIncrease.isFocused() ? 1 : depthOffsetDecrease.active && depthOffsetDecrease.isFocused() ? -1 : 0);
        this.addWidget(new ElevatorPreviewWidget(99, 13, 82, 99, () -> this.object, previewSizeIncrease, previewOffset));
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, ControllerBlockEntity object){
        // Background
        ScreenUtils.bindTexture(BACKGROUND);
        ScreenUtils.drawTexture(0, 0, this.width(), this.height());

        super.renderBackground(mouseX, mouseY, object);
    }

    @Override
    protected void render(int mouseX, int mouseY, ControllerBlockEntity blockEntity){
        // Size icons
        ScreenUtils.bindTexture(SIZE_ICONS);
        ScreenUtils.drawTexture(190, 31, 11, 11, 0, 0, 1, 1 / 3f);
        ScreenUtils.drawTexture(190, 47, 11, 11, 0, 1 / 3f, 1, 1 / 3f);
        ScreenUtils.drawTexture(190, 63, 11, 11, 0, 2 / 3f, 1, 1 / 3f);

        // Size values
        ScreenUtils.drawCenteredString(TextComponents.number(blockEntity.getGroup().getCageWidth()).get(), 224, 34);
        ScreenUtils.drawCenteredString(TextComponents.number(blockEntity.getGroup().getCageSideOffset()).get(), 261, 34);
        ScreenUtils.drawCenteredString(TextComponents.number(blockEntity.getGroup().getCageDepth()).get(), 224, 50);
        ScreenUtils.drawCenteredString(TextComponents.number(blockEntity.getGroup().getCageDepthOffset()).get(), 261, 50);
        ScreenUtils.drawCenteredString(TextComponents.number(blockEntity.getGroup().getCageHeight()).get(), 224, 66);
        ScreenUtils.drawCenteredString(TextComponents.number(blockEntity.getGroup().getCageHeightOffset()).get(), 261, 66);

        // Text
        ScreenUtils.drawCenteredString(TextComponents.translation("movingelevators.elevator_screen.current_floor").get(), 47, 3, ScreenUtils.ACTIVE_TEXT_COLOR);
        ScreenUtils.drawCenteredString(TextComponents.translation("movingelevators.elevator_screen.elevator").get(), 232, 3, ScreenUtils.ACTIVE_TEXT_COLOR);
        ScreenUtils.drawString(TextComponents.translation("movingelevators.elevator_screen.floor_name").get(), 6, 18);
        ScreenUtils.drawString(TextComponents.translation("movingelevators.elevator_screen.cabin_size").get(), 190, 18);
        ScreenUtils.drawString(TextComponents.translation("movingelevators.elevator_screen.elevator_speed").get(), 190, 79);

        super.render(mouseX, mouseY, blockEntity);
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY, @Nonnull ControllerBlockEntity blockEntity){
        if(mouseX >= 190 && mouseX <= 190 + 11 && mouseY >= 31 && mouseY <= 31 + 11)
            ScreenUtils.drawTooltip(TextComponents.translation("movingelevators.elevator_screen.cabin_width").get(), mouseX, mouseY);
        else if(mouseX >= 190 && mouseX <= 190 + 11 && mouseY >= 47 && mouseY <= 47 + 11)
            ScreenUtils.drawTooltip(TextComponents.translation("movingelevators.elevator_screen.cabin_depth").get(), mouseX, mouseY);
        else if(mouseX >= 190 && mouseX <= 190 + 11 && mouseY >= 63 && mouseY <= 63 + 11)
            ScreenUtils.drawTooltip(TextComponents.translation("movingelevators.elevator_screen.cabin_height").get(), mouseX, mouseY);

        super.renderTooltips(mouseX, mouseY, blockEntity);
    }
}
