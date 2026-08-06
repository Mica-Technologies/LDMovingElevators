package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.ClientUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.EnumDyeColor;
import org.lwjgl.opengl.GL11;

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

    /** Inset screen the label sits on, so it stays readable whatever is behind it. */
    private static final int SCREEN_R = 16, SCREEN_G = 16, SCREEN_B = 18, SCREEN_A = 240;

    /**
     * Call arrow colours, against the near-black screen above.
     * <p>
     * The first attempt used a dark slate for the unlit state, which scored 2.24 against that screen
     * and was effectively invisible in game. An unlit button still has to read as a button, so the
     * "off" grey is now a clearly legible 6.84, and "on" is a warm amber at 12.36 -- unmistakably
     * lit, and distinct from the white floor readout above it.
     */
    private static final int UNLIT_R = 150, UNLIT_G = 156, UNLIT_B = 160;
    private static final int LIT_R = 255, LIT_G = 200, LIT_B = 90;

    /**
     * Bezel left around a call arrow, in block units. Stated as a margin rather than a multiple of
     * the arrow, because a triangle only fills half its bounding box and a proportional inset
     * therefore reads as a big empty square however the arrow is sized.
     */
    private static final float ARROW_BEZEL = 0.5f / 16;

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
     * Draws at full brightness, whatever the light is where the panel is standing.
     * <p>
     * These are lit displays -- the readout, the call arrows, the floor lamps -- so they should look
     * lit. It matters most exactly where it is hardest: inside a moving cabin, which is the dimmest
     * place a panel is ever mounted and the moment you most want to read the floor.
     */
    private static void fullBright(Runnable draw){
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        try{
            draw.run();
        }finally{
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }
    }

    /**
     * Converts an x stated as the player sees it into the mirrored space these panels draw in.
     * <p>
     * The surrounding transform flips x -- which is why the label below is scaled by a negative
     * factor, cancelling the flip so the text reads the right way round. Raw geometry gets no such
     * cancellation, so anything drawn at a literal x lands mirrored about the block centre. That is
     * how the car panel ended up with its up arrow on the right and its button bank off-centre: both
     * looked correct in the source and neither was symmetric about 0.5, so the flip showed.
     * <p>
     * Every helper here takes coordinates as seen and applies this itself, so callers never have to
     * think about it.
     */
    private static float mirrorX(float x){
        return 1 - x;
    }

    /**
     * Draws the label inside a fixed-size window, scaling the text down to fit rather than sizing the
     * window to the text. A readout that changes shape with the length of the floor name does not
     * look like a display; a constant window does.
     */
    static void drawFittedLabel(String label, EnumDyeColor color, float centerX, float centerY,
                                float halfWidth, float halfHeight, float padding){
        fullBright(() -> drawFittedLabelUnlit(label, color, centerX, centerY, halfWidth, halfHeight, padding));
    }

    private static void drawFittedLabelUnlit(String label, EnumDyeColor color, float centerX, float centerY,
                                             float halfWidth, float halfHeight, float padding){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        int width = Math.max(fontRenderer.getStringWidth(label), 1);
        float scale = Math.min((halfHeight - padding) * 2 / fontRenderer.FONT_HEIGHT,
            (halfWidth - padding) * 2 / width);

        drawScreen(mirrorX(centerX) - halfWidth, centerY - halfHeight,
            mirrorX(centerX) + halfWidth, centerY + halfHeight);

        GlStateManager.pushMatrix();
        GlStateManager.translate(mirrorX(centerX), centerY, -0.005);
        GlStateManager.scale(-scale, -scale, 1);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        fontRenderer.drawString(label, -width / 2, -fontRenderer.FONT_HEIGHT / 2, readableColor(color));
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
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
        fullBright(() -> drawCenteredLabelUnlit(label, color, centerX, centerY, maxScale, maxWidth, padding));
    }

    private static void drawCenteredLabelUnlit(String label, EnumDyeColor color, float centerX, float centerY,
                                               float maxScale, float maxWidth, float padding){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        int width = Math.max(fontRenderer.getStringWidth(label), 1);
        // Shrink long floor names so they stay on the panel instead of spilling past its edges.
        float scale = Math.min(maxScale, maxWidth / width);

        float halfWidth = width * scale / 2;
        float halfHeight = fontRenderer.FONT_HEIGHT * scale / 2;
        drawScreen(mirrorX(centerX) - halfWidth - padding, centerY - halfHeight - padding,
            mirrorX(centerX) + halfWidth + padding, centerY + halfHeight + padding);

        GlStateManager.pushMatrix();
        GlStateManager.translate(mirrorX(centerX), centerY, -0.005);
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
    static void drawArrow(float x, float centerY, float halfWidth, float halfHeight, boolean up, boolean lit){
        fullBright(() -> drawArrowUnlit(x, centerY, halfWidth, halfHeight, up, lit));
    }

    private static void drawArrowUnlit(float x, float centerY, float halfWidth, float halfHeight, boolean up, boolean lit){
        float centerX = mirrorX(x);
        drawScreen(centerX - halfWidth - ARROW_BEZEL, centerY - halfHeight - ARROW_BEZEL,
            centerX + halfWidth + ARROW_BEZEL, centerY + halfHeight + ARROW_BEZEL);

        int r = lit ? LIT_R : UNLIT_R, g = lit ? LIT_G : UNLIT_G, b = lit ? LIT_B : UNLIT_B;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // The surrounding transform mirrors this space, which flips triangle winding -- so of the two
        // arrows one came out back-facing and was culled away entirely. Rather than hand-winding each
        // one for a mirrored frame, just draw both faces.
        GlStateManager.disableCull();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        float tipY = up ? centerY + halfHeight : centerY - halfHeight;
        float baseY = up ? centerY - halfHeight : centerY + halfHeight;
        buffer.pos(centerX, tipY, -0.005).color(r, g, b, 255).endVertex();
        buffer.pos(centerX - halfWidth, baseY, -0.005).color(r, g, b, 255).endVertex();
        buffer.pos(centerX + halfWidth, baseY, -0.005).color(r, g, b, 255).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * A round-ish floor button on the car panel's bank: a dark socket with a lamp in it, lit when
     * that floor is a selected destination.
     */
    static void drawButton(float x, float centerY, float half, boolean lit){
        fullBright(() -> drawButtonUnlit(x, centerY, half, lit));
    }

    private static void drawButtonUnlit(float x, float centerY, float half, boolean lit){
        float centerX = mirrorX(x);
        drawScreen(centerX - half - ARROW_BEZEL, centerY - half - ARROW_BEZEL,
            centerX + half + ARROW_BEZEL, centerY + half + ARROW_BEZEL);

        int r = lit ? LIT_R : UNLIT_R, g = lit ? LIT_G : UNLIT_G, b = lit ? LIT_B : UNLIT_B;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(centerX - half, centerY + half, -0.005).color(r, g, b, 255).endVertex();
        buffer.pos(centerX + half, centerY + half, -0.005).color(r, g, b, 255).endVertex();
        buffer.pos(centerX + half, centerY - half, -0.005).color(r, g, b, 255).endVertex();
        buffer.pos(centerX - half, centerY - half, -0.005).color(r, g, b, 255).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.enableCull();
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
