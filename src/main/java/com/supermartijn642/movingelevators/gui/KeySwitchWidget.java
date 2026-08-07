package com.supermartijn642.movingelevators.gui;

import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.gui.widget.premade.AbstractButtonWidget;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Supplier;

/**
 * The small round key switch a lift carries for staff -- the one a passenger can see, can read the
 * state of, and is not expected to be able to turn.
 * <p>
 * Drawn as a key switch rather than given a labelled row of its own because that is the honest shape
 * for it: a control that looks like the buttons beside it invites a press, whereas a keyhole says at
 * a glance who it is for. It is deliberately not gated on the viewer's permission -- the server
 * decides who may throw it and answers the ones who may not -- so the drawing has to carry the "this
 * is not really yours" signal that a hidden control would carry by being absent.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class KeySwitchWidget extends AbstractButtonWidget {

    /**
     * Odd, so there is a true centre column for the keyhole to sit on, and small enough to tuck into
     * a header beside a line of text without crowding it.
     */
    public static final int SIZE = 11;

    /**
     * Unlit: a muted steel bezel and a darker face, the palette of a fitting rather than a button.
     * Nothing here is inviting on purpose.
     * <p>
     * The keyhole is 2.5:1 against the face it is cut into. That is under the 3:1 a UI shape is
     * normally held to, and deliberately so -- a keyhole is a hole, it has to read as recessed, and
     * the mark's meaning does not depend on reading it precisely. What has to be unambiguous is lit
     * against unlit, and that pair is far apart (below).
     */
    private static final int BEZEL = 0xFF6B6E73;
    private static final int FACE = 0xFF54585E;
    private static final int KEYHOLE = 0xFF14161A;

    /**
     * Thrown: the amber of a live indicator, echoing the amber the floor buttons light with. The
     * keyhole goes to 8.6:1 against the lit face where it was 2.5:1 against the unlit one, so the two
     * states differ in brightness and hue at once and do not rely on either alone.
     */
    private static final int LIT_BEZEL = 0xFFD9A441;
    private static final int LIT_FACE = 0xFF3A2A05;
    private static final int LIT_KEYHOLE = 0xFFFFC24D;

    /** Same focus wash the floor buttons use, so keyboard focus reads the same across the panel. */
    private static final int FOCUS_OVERLAY = 0x30FFFFFF;

    private final Supplier<Boolean> isLit;
    private final Supplier<java.util.List<ITextComponent>> tooltip;

    public KeySwitchWidget(int x, int y, Supplier<Boolean> isLit, Supplier<java.util.List<ITextComponent>> tooltip, Runnable onPress){
        super(x, y, SIZE, SIZE, onPress);
        this.isLit = isLit;
        this.tooltip = tooltip;
    }

    @Override
    public ITextComponent getNarrationMessage(){
        java.util.List<ITextComponent> lines = this.tooltip.get();
        return lines.isEmpty() ? null : lines.get(0);
    }

    @Override
    public void render(int mouseX, int mouseY){
        boolean lit = this.isLit.get();

        fillRound(this.x, this.y, SIZE, lit ? LIT_BEZEL : BEZEL);
        fillRound(this.x + 1, this.y + 1, SIZE - 2, lit ? LIT_FACE : FACE);

        // Keyhole: the round part of the hole with the blade slot below it. Hard-coded offsets rather
        // than derived from SIZE -- at eleven pixels there is no scaling to be had, only a shape that
        // either reads as a keyhole or does not.
        int centre = this.x + SIZE / 2;
        int keyhole = lit ? LIT_KEYHOLE : KEYHOLE;
        ScreenUtils.fillRect(centre - 1, this.y + 3, 3, 2, keyhole);
        ScreenUtils.fillRect(centre, this.y + 5, 1, 3, keyhole);

        // Over the whole square, not the drawn circle: the widget's hit area is the square anyway, so
        // the focus wash marking the same region is the truthful thing to show.
        if(this.isFocused())
            ScreenUtils.fillRect(this.x, this.y, SIZE, SIZE, FOCUS_OVERLAY);
    }

    /**
     * A square with its four corner pixels left off, which is all it takes to read as round at this
     * size -- and cheaper and crisper than any actual circle rasterised into eleven pixels.
     */
    private static void fillRound(int x, int y, int size, int color){
        ScreenUtils.fillRect(x + 1, y, size - 2, size, color);
        ScreenUtils.fillRect(x, y + 1, size, size - 2, color);
    }
}
