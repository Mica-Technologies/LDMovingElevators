package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.premade.AbstractButtonWidget;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One floor on the car panel's screen. Lights while that floor is a selected destination, the way a
 * pressed car button stays lit until the elevator gets there.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class FloorButtonWidget extends AbstractButtonWidget {

    /**
     * Text colours for the button face, which is a light grey.
     * <p>
     * These echo the amber and green the physical panels light up with, but much darker: those
     * on-screen colours are meant for a near-black inset, and against a pale button they scored 1.11
     * and 1.35 contrast -- effectively invisible. White fared little better at 1.71. Dark keeps the
     * same meaning and actually reads.
     */
    private static final int SELECTED_COLOR = 0xFF663F00;
    private static final int CURRENT_COLOR = 0xFF0F5218;
    private static final int DEFAULT_COLOR = 0xFF404040;

    private final Supplier<String> label;
    private final Supplier<Boolean> isSelected;
    private final Supplier<Boolean> isCurrentFloor;
    private final ITextComponent tooltip;

    public FloorButtonWidget(int x, int y, int width, int height, Supplier<String> label, Supplier<Boolean> isSelected, Supplier<Boolean> isCurrentFloor, ITextComponent tooltip, Runnable onPress){
        super(x, y, width, height, onPress);
        this.label = label;
        this.isSelected = isSelected;
        this.isCurrentFloor = isCurrentFloor;
        this.tooltip = tooltip;
    }

    @Override
    public ITextComponent getNarrationMessage(){
        return this.tooltip;
    }

    @Override
    protected void getTooltips(Consumer<ITextComponent> tooltips){
        tooltips.accept(this.tooltip);
    }

    @Override
    public void render(int mouseX, int mouseY){
        ScreenUtils.drawButtonBackground(this.x, this.y, this.width, this.height, this.isFocused() ? 1 : 0);
        int color = this.isCurrentFloor.get() ? CURRENT_COLOR : this.isSelected.get() ? SELECTED_COLOR : DEFAULT_COLOR;
        ScreenUtils.drawCenteredString(com.supermartijn642.core.TextComponents.string(this.label.get()).get(),
            this.x + this.width / 2f, this.y + (this.height - 8) / 2f, color);
    }
}
