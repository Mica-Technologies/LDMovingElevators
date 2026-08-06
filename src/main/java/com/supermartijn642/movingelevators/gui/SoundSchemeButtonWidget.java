package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.premade.AbstractButtonWidget;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A button whose face is its own current value -- press it and it steps to the next one.
 * <p>
 * A cycling button rather than a list: there are two schemes, and a dropdown for two entries costs
 * more room than the elevator screen has and more clicks than reading the answer takes.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class SoundSchemeButtonWidget extends AbstractButtonWidget {

    private final Supplier<ITextComponent> label;
    private final ITextComponent tooltip;

    public SoundSchemeButtonWidget(int x, int y, int width, int height, Supplier<ITextComponent> label, ITextComponent tooltip, Runnable onPress){
        super(x, y, width, height, onPress);
        this.label = label;
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
        ScreenUtils.drawCenteredString(this.label.get(),
            this.x + this.width / 2f, this.y + (this.height - 8) / 2f, 0xFF404040);
    }
}
