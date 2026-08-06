package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.render.CustomBlockEntityRenderer;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws the floor label for a {@link RemoteDisplayBlockEntity} on its facing side.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class RemoteDisplayBlockEntityRenderer implements CustomBlockEntityRenderer<RemoteDisplayBlockEntity> {

    /**
     * Font rendering is expensive, so skip it past this distance. Matches
     * {@link DisplayBlockEntityRenderer}.
     */
    private static final double TEXT_RENDER_DISTANCE = 15 * 15;
    /** Largest label height, as a fraction of the block face. */
    private static final float MAX_SCALE = 1 / 12f;
    /** Fraction of the block face the label is allowed to span horizontally. */
    private static final float MAX_WIDTH = 0.7f;

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

    /** Inset panel the label sits on, so it stays readable over any camouflage. */
    private static final int PANEL_R = 16, PANEL_G = 16, PANEL_B = 18, PANEL_A = 240;
    /** Padding around the label, in block units. */
    private static final float PANEL_PAD_X = 0.07f, PANEL_PAD_Y = 0.05f;

    @Override
    public void render(RemoteDisplayBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
        ElevatorGroup group = entity.getGroup();
        if(group == null)
            return;

        int floor = group.getCabinFloorNumber();
        if(floor < 0 || floor >= group.getFloorCount())
            return;

        BlockPos pos = entity.getPos();
        Vec3d cameraPos = RenderUtils.getCameraPosition();
        if(cameraPos.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TEXT_RENDER_DISTANCE)
            return;

        String label = stripFloorPrefix(MovingElevatorsClient.formatFloorDisplayName(group.getFloorDisplayName(floor), floor));
        if(label == null || label.isEmpty())
            return;

        EnumFacing facing = entity.getFacing();
        EnumDyeColor color = group.getFloorDisplayColor(floor);

        GlStateManager.pushMatrix();

        // Same framing as DisplayBlockEntityRenderer: move to the block centre, turn to face the
        // display's side, then step just proud of that face so the label is not z-fighting the block.
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, -0.51);

        this.drawFloorLabel(label, color);

        GlStateManager.popMatrix();
    }

    /**
     * Drops a leading "Floor" from a label, so the panel reads "3" rather than "Floor 3". The
     * default floor names are "Floor 0", "Floor 1" and so on, and a panel this small has no room to
     * repeat the word on every floor.
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

    private void drawFloorLabel(String label, EnumDyeColor color){
        FontRenderer fontRenderer = ClientUtils.getFontRenderer();
        int width = Math.max(fontRenderer.getStringWidth(label), 1);
        // Shrink long floor names so they stay on the block instead of spilling past its edges.
        float scale = Math.min(MAX_SCALE, MAX_WIDTH / width);

        float halfWidth = width * scale / 2;
        float halfHeight = fontRenderer.FONT_HEIGHT * scale / 2;
        this.drawPanel(0.5f - halfWidth - PANEL_PAD_X, 0.5f - halfHeight - PANEL_PAD_Y,
            0.5f + halfWidth + PANEL_PAD_X, 0.5f + halfHeight + PANEL_PAD_Y);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5, 0.5, -0.005);
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
     * Dark inset the label is drawn on. Without it the label sits directly on the block texture,
     * which is a mid grey by default and can be anything at all once camouflaged -- the floor's dye
     * colour was regularly too close to the background to read.
     */
    private void drawPanel(float minX, float minY, float maxX, float maxY){
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(minX, maxY, -0.003).color(PANEL_R, PANEL_G, PANEL_B, PANEL_A).endVertex();
        buffer.pos(maxX, maxY, -0.003).color(PANEL_R, PANEL_G, PANEL_B, PANEL_A).endVertex();
        buffer.pos(maxX, minY, -0.003).color(PANEL_R, PANEL_G, PANEL_B, PANEL_A).endVertex();
        buffer.pos(minX, minY, -0.003).color(PANEL_R, PANEL_G, PANEL_B, PANEL_A).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * WCAG's AA threshold for large text. The label is a big glyph rather than body copy, and the
     * stricter 4.5 body-text bar also rejected colours that read perfectly well here -- magenta
     * scores 4.45 against the panel. At 3.0 the six genuinely dark dyes are replaced (grey, brown,
     * blue, red, purple, black) and the rest keep their colour.
     */
    private static final double MIN_CONTRAST = 3;

    /**
     * The floor's dye colour if it reads against the inset panel, white otherwise. Keeps the
     * per-floor colouring meaningful without letting dark dyes disappear -- grey is the default
     * floor colour and scores 2.21, which is what made the label hard to read.
     */
    static int readableColor(EnumDyeColor color){
        int rgb = color.getColorValue();
        return contrastRatio(relativeLuminance(rgb), relativeLuminance(PANEL_R << 16 | PANEL_G << 8 | PANEL_B)) < MIN_CONTRAST
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
