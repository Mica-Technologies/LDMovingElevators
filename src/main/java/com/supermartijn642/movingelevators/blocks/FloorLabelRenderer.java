package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.ClientUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.EnumDyeColor;
import org.lwjgl.opengl.GL11;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared drawing for the "which floor is the cabin at" label, used by every remote display style.
 * <p>
 * Callers set up their own transform first: the label is drawn in the same space
 * {@link DisplayBlockEntityRenderer} uses, i.e. block-face coordinates running 0..1 with the face
 * itself at z=0 and smaller z towards the viewer.
 * <p>
 * Created for the Mica Technologies fork.
 */
final class FloorLabelRenderer {

    /**
     * Leading "Floor" wording, in any capitalisation, plus whatever separates it from the actual
     * floor identifier.
     * <p>
     * Two details that are easy to get wrong, and did get wrong first time round:
     * <ul>
     * <li>The lookahead is {@code (?![a-z])} rather than {@code \b}. There is no word boundary
     *     between the "r" of "Floor" and the "3" of "Floor3" -- both are word characters -- so
     *     {@code \b} silently failed to strip that form. The lookahead still protects "Flooring".
     * <li>A hyphen only counts as a separator when it is tight against the word. "Floor-5" is floor
     *     5, but "Floor -3" is floor <em>minus</em> 3, and treating that hyphen as a separator would
     *     display a different floor from the one named.
     * </ul>
     */
    private static final Pattern FLOOR_PREFIX = Pattern.compile("^\\s*floor(?![a-z])[-:._#]?[\\s:._#]*", Pattern.CASE_INSENSITIVE);

    /** Inset screen the label sits on, so it stays readable whatever is behind it. */
    private static final int SCREEN_R = 16, SCREEN_G = 16, SCREEN_B = 18, SCREEN_A = 240;

    /**
     * WCAG's AA threshold for large text. The label is a big glyph rather than body copy, and the
     * stricter 4.5 body-text bar also rejected colours that read perfectly well here -- magenta
     * scores 4.45 against the screen. At 3.0 the six genuinely dark dyes are replaced (grey, brown,
     * blue, red, purple, black) and the rest keep their colour.
     */
    private static final double MIN_CONTRAST = 3;

    private FloorLabelRenderer(){
    }

    /**
     * Drops a leading "Floor" from a label, so a panel reads "3" rather than "Floor 3". The default
     * floor names are "Floor 0", "Floor 1" and so on, and a panel has no room to repeat the word on
     * every floor.
     *
     * @return the identifier alone, or the original label if stripping would leave nothing
     */
    static String stripFloorPrefix(String label){
        if(label == null)
            return null;
        Matcher matcher = FLOOR_PREFIX.matcher(label);
        if(!matcher.find())
            return label;
        String remainder = label.substring(matcher.end()).trim();
        // A floor genuinely named just "Floor" keeps its name rather than rendering blank.
        return remainder.isEmpty() ? label.trim() : remainder;
    }

    /**
     * Draws the label centred on the given point, on an inset screen sized to fit it.
     *
     * @param maxScale height of one line of text as a fraction of a block
     * @param maxWidth how wide the label may get before it is scaled down to fit
     * @param padding  gap between the text and the edge of the screen, in block units
     */
    static void drawCenteredLabel(String label, EnumDyeColor color, float centerX, float centerY,
                                  float maxScale, float maxWidth, float padding){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        int width = Math.max(fontRenderer.getStringWidth(label), 1);
        // Shrink long floor names so they stay on the panel instead of spilling past its edges.
        float scale = Math.min(maxScale, maxWidth / width);

        float halfWidth = width * scale / 2;
        float halfHeight = fontRenderer.FONT_HEIGHT * scale / 2;
        drawScreen(centerX - halfWidth - padding, centerY - halfHeight - padding,
            centerX + halfWidth + padding, centerY + halfHeight + padding);

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, -0.005);
        // Negative on both axes: the surrounding rotation leaves this space mirrored, the same way
        // DisplayBlockEntityRenderer's label drawing compensates for it.
        GlStateManager.scale(-scale, -scale, 1);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        fontRenderer.drawString(label, -width / 2, -fontRenderer.FONT_HEIGHT / 2, readableColor(color));
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * A call arrow, drawn as a filled triangle on its own dark inset -- the illuminated look of a
     * real landing button. Lit arrows use the call colour, unlit ones a dim grey, so a press reads
     * as registered at a glance.
     *
     * @param up whether the triangle points up
     * @param lit whether there is an outstanding call in this direction
     */
    static void drawArrow(float centerX, float centerY, float halfWidth, float halfHeight, boolean up, boolean lit){
        drawScreen(centerX - halfWidth * 1.4f, centerY - halfHeight * 1.5f,
            centerX + halfWidth * 1.4f, centerY + halfHeight * 1.5f);

        int r = lit ? 90 : 70, g = lit ? 220 : 78, b = lit ? 110 : 82;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        float tipY = up ? centerY + halfHeight : centerY - halfHeight;
        float baseY = up ? centerY - halfHeight : centerY + halfHeight;
        buffer.pos(centerX, tipY, -0.005).color(r, g, b, 255).endVertex();
        buffer.pos(centerX - halfWidth, baseY, -0.005).color(r, g, b, 255).endVertex();
        buffer.pos(centerX + halfWidth, baseY, -0.005).color(r, g, b, 255).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * Dark inset the label is drawn on. Without it the label sits directly on whatever texture is
     * behind it -- a mid grey by default, and anything at all once camouflaged -- and the floor's
     * dye colour was regularly too close to the background to read.
     */
    private static void drawScreen(float minX, float minY, float maxX, float maxY){
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(minX, maxY, -0.003).color(SCREEN_R, SCREEN_G, SCREEN_B, SCREEN_A).endVertex();
        buffer.pos(maxX, maxY, -0.003).color(SCREEN_R, SCREEN_G, SCREEN_B, SCREEN_A).endVertex();
        buffer.pos(maxX, minY, -0.003).color(SCREEN_R, SCREEN_G, SCREEN_B, SCREEN_A).endVertex();
        buffer.pos(minX, minY, -0.003).color(SCREEN_R, SCREEN_G, SCREEN_B, SCREEN_A).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * The floor's dye colour if it reads against the inset screen, white otherwise. Keeps the
     * per-floor colouring meaningful without letting dark dyes disappear -- grey is the default
     * floor colour and scores 2.21, which is what made the label hard to read.
     */
    static int readableColor(EnumDyeColor color){
        int rgb = color.getColorValue();
        return contrastRatio(relativeLuminance(rgb), relativeLuminance(SCREEN_R << 16 | SCREEN_G << 8 | SCREEN_B)) < MIN_CONTRAST
            ? EnumDyeColor.WHITE.getColorValue()
            : rgb;
    }

    /** WCAG relative luminance, including the sRGB -> linear step. */
    private static double relativeLuminance(int rgb){
        return 0.2126 * linearize((rgb >> 16 & 0xFF) / 255d)
            + 0.7152 * linearize((rgb >> 8 & 0xFF) / 255d)
            + 0.0722 * linearize((rgb & 0xFF) / 255d);
    }

    private static double linearize(double channel){
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static double contrastRatio(double a, double b){
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
}
