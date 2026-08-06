package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.premade.AbstractButtonWidget;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The car station's alarm: it rings for as long as it is held down, not for a fixed spell.
 * <p>
 * Hold-to-ring rather than press-for-three-seconds because that is what the real thing does, and
 * because a fixed burst is a sound a player cannot stop once started -- and can restart on every
 * click. Holding is self-limiting: let go and it stops.
 * <p>
 * The press is repeated rather than sent as a start and a stop, so the ringing lapses on its own if
 * this screen goes away mid-hold. See PacketRingAlarm.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class AlarmButtonWidget extends AbstractButtonWidget {

    /** Dark red on the pale button face, matching how the floor buttons keep their ink dark. */
    private static final int IDLE_COLOR = 0xFF7A1F1F;
    /** Held, the button inverts the way the current-floor button does, so it is obvious it is live. */
    private static final int RINGING_BACKGROUND = 0xFF3A0E0E;
    private static final int RINGING_BORDER = 0xFFFF6B6B;
    private static final int RINGING_COLOR = 0xFFFFD0D0;

    /** Milliseconds between repeats. Comfortably inside the elevator's hold window. */
    private static final long REPEAT_INTERVAL = 150;

    private final Supplier<String> label;
    private final ITextComponent tooltip;
    private final Runnable ring;
    private boolean held;
    private long lastSent;

    public AlarmButtonWidget(int x, int y, int width, int height, Supplier<String> label, ITextComponent tooltip, Runnable onPress){
        super(x, y, width, height, onPress);
        this.ring = onPress;
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
    public boolean mousePressed(int mouseX, int mouseY, int button, boolean hasBeenHandled){
        if(!hasBeenHandled && mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height)
            this.held = true;
        return super.mousePressed(mouseX, mouseY, button, hasBeenHandled);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button, boolean hasBeenHandled){
        this.held = false;
        return super.mouseReleased(mouseX, mouseY, button, hasBeenHandled);
    }

    @Override
    public void render(int mouseX, int mouseY){
        if(this.held){
            long now = System.currentTimeMillis();
            if(now - this.lastSent >= REPEAT_INTERVAL){
                this.lastSent = now;
                this.ring.run();
            }
        }

        if(this.held){
            ScreenUtils.fillRect(this.x, this.y, this.width, this.height, RINGING_BORDER);
            ScreenUtils.fillRect(this.x + 1, this.y + 1, this.width - 2, this.height - 2, RINGING_BACKGROUND);
        }else
            ScreenUtils.drawButtonBackground(this.x, this.y, this.width, this.height, this.isFocused() ? 1 : 0);

        ScreenUtils.drawCenteredString(TextComponents.string(this.label.get()).get(),
            this.x + this.width / 2f, this.y + (this.height - 8) / 2f, this.held ? RINGING_COLOR : IDLE_COLOR);
    }
}
