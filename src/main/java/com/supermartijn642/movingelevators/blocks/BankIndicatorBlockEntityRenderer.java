package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.gui.ScreenUtils;
import com.supermartijn642.core.render.CustomBlockEntityRenderer;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.core.render.TextureAtlases;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws a {@link BankIndicatorBlockEntity}: a metal plate carrying one column per lift in the bank,
 * each column giving that lift's name, the floor its cabin is at, and which way it is going.
 * <p>
 * Laid out in columns rather than as a list because a bank is read by comparing its cars against each
 * other -- "which one is nearest me" -- and a reader compares side by side far faster than top to
 * bottom. It also matches the shape of the thing being described: the columns stand in the same left
 * to right order as the shafts were linked, so a lobby can be linked door order and the plate then
 * mirrors the wall the passenger is looking at.
 * <p>
 * The plate itself is drawn here, metal and all, rather than being a baked block model. Its width has
 * to follow the number of linked elevators, and a baked model cannot vary with block entity data --
 * nor could 1.12's four bits of metadata carry a facing and six widths. So this takes the same way out
 * the sliding doors do: the block's model is empty and the renderer is the only thing that draws it.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankIndicatorBlockEntityRenderer implements CustomBlockEntityRenderer<BankIndicatorBlockEntity> {

    /**
     * Squared, so this is 30 blocks. A bank indicator is a landing fixture like the single indicator,
     * the call panel and the lobby panel -- read from across a lobby, not from arm's length as the
     * panel inside the cabin is -- so it takes the same cutoff those use.
     * <p>
     * Text is expensive to draw, which is why there is a cutoff at all. Past 64 blocks raising this
     * alone would do nothing anyway: block entities stop being rendered at that range unless they ask
     * for more.
     * <p>
     * Unlike the other landing fixtures, crossing this line takes the plate itself with it, since
     * nothing else draws one. That is deliberate: a bare metal slab with no readout on it would be a
     * worse thing to see at forty blocks than nothing at all.
     */
    private static final double TEXT_RENDER_DISTANCE = 30 * 30;

    /**
     * Gap left between the plate's front face and the contents drawn on it, so nothing z-fights the
     * metal. The same 0.01 every other panel renderer leaves.
     */
    private static final double FACE_CLEARANCE = 0.01;

    /**
     * Puts the drawing plane just proud of where the plate's face sits, exactly as the other panels do
     * -- the full-block displays step to -0.51, and a wall plate's face is {@link
     * WallPanelBlock#PLATE_DEPTH} in from the block edge, so the plane has to come in with it.
     */
    private static final double LABEL_DEPTH = 0.5 - WallPanelBlock.PLATE_DEPTH - FACE_CLEARANCE;

    /**
     * Where the metal lives in that plane's space: z runs away from the viewer, so the face is at the
     * clearance and the back of the plate is one plate-depth further in, flush against the wall.
     */
    private static final float PLATE_FRONT_Z = (float)FACE_CLEARANCE;
    private static final float PLATE_BACK_Z = (float)(FACE_CLEARANCE + WallPanelBlock.PLATE_DEPTH);

    // Face layout, in sixteenths, stated as the player sees it. FloorLabelRenderer mirrors x for us, so
    // these are ordinary left-to-right coordinates and nothing here flips anything by hand. The plate
    // is symmetric about the block centre, so the metal below needs no mirroring either.
    /** Vertical bounds, matching {@link BankIndicatorBlock}'s collision and selection box exactly. */
    private static final float PLATE_MIN_Y = 4 / 16f, PLATE_MAX_Y = 12 / 16f;
    /** The plate grows outwards from here, so adding an elevator never shifts the ones already shown. */
    private static final float PLATE_CENTER_X = 8 / 16f;

    /**
     * Width of one elevator's column, and the metal surround left at each end of the row of them.
     * <p>
     * Seven pixels because that is what the column's contents need: a floor readout wide enough to hold
     * two or three characters at a legible size, and a pair of call arrows under it that each still
     * clear FloorLabelRenderer's half-pixel bezel. It also lands the common cases well -- two elevators
     * give a 15.5-pixel plate, a hair inside the block, and six give 43.5, which reaches 0.86 of a block
     * past each side and so stays inside the one-block overhang a block may occupy.
     */
    private static final float COLUMN_WIDTH = 7 / 16f;
    private static final float PLATE_EDGE_MARGIN = 0.75f / 16;

    /**
     * How many cars the plate will show, read from the block entity rather than restated here so the
     * plate and the status readout can never disagree about how many are on the face.
     * <p>
     * Six because seven would push the plate past a block's worth of overhang on each side, which is as
     * far as anything may reach out of its own block, and because a bank of seven on one landing is
     * already past the point where the plate reads as a single instrument.
     * <p>
     * Beyond six, the first six in link order are drawn and the rest are left off the plate. Nothing is
     * lost: right-clicking the block reports every binding it holds, and a second indicator linked to
     * the remaining shafts shows them at full size.
     */
    private static final int MAX_COLUMNS = BankIndicatorBlockEntity.MAX_SHOWN;

    /**
     * The narrowest the plate ever gets, in columns.
     * <p>
     * Two, so that an indicator with nothing linked still draws a plate rather than nothing -- an empty
     * block where a player has just placed something reads as a mod failing, where a dark empty screen
     * reads as a fixture waiting to be linked. A bank of one gets the same floor, so a lone indicator
     * looks like a fixture rather than a stub, and both cases then match the size baked into the item.
     */
    private static final int MIN_COLUMNS = 2;

    /**
     * Metal left visible around the glass. A flat margin rather than a fraction of the plate, for the
     * reason the lobby panel states in reverse: that plate is much taller than it is wide, this one is
     * much wider than it is tall, and either way a proportional inset leaves a fat surround on one axis
     * and a hairline on the other. The same 0.6 that fixture uses, so a lobby wall of mixed fixtures has
     * one surround width rather than two.
     */
    private static final float GLASS_MARGIN = 0.6f / 16;
    private static final float GLASS_MIN_Y = PLATE_MIN_Y + GLASS_MARGIN, GLASS_MAX_Y = PLATE_MAX_Y - GLASS_MARGIN;

    /**
     * The glass, and its depth: the same near-black, near-opaque sheet the lobby panel uses, for the
     * same reasons. Near-opaque so the metal behind it cannot mottle the readout, but not fully opaque,
     * because a completely opaque sheet reads as paint where a faint bleed of the plate behind it reads
     * as glass.
     * <p>
     * Matched to the lobby panel by eye rather than by shared constant: the two are separate renderers
     * and neither should reach into the other, but they hang on the same wall and would look like
     * different products if their screens were different colours.
     */
    private static final int GLASS_R = 10, GLASS_G = 10, GLASS_B = 12, GLASS_A = 245;

    /**
     * Behind everything FloorLabelRenderer draws, which sits at -0.003 and -0.005, and in front of the
     * metal at {@link #PLATE_FRONT_Z}. Smaller z is towards the viewer, so this is the back layer of the
     * display; the gaps have to be wide enough that neither the column insets above nor the plate below
     * z-fight it.
     */
    private static final double GLASS_DEPTH = -0.001;

    /** Full lightmap, the value FloorLabelRenderer uses for the same reason. */
    private static final int FULL_BRIGHT = 240;

    /**
     * Dark gap left between neighbouring columns, taken half from each. Without it two columns' insets
     * abut into one continuous dark band and the plate reads as a single wide readout showing nonsense
     * rather than as several small ones each showing a car.
     */
    private static final float COLUMN_GUTTER = 0.5f / 16;
    /** What is left of a column once its share of the gutter is paid: 3.25 pixels either side of centre. */
    private static final float CONTENT_HALF_WIDTH = COLUMN_WIDTH / 2 - COLUMN_GUTTER / 2;

    // Rows within a column, in sixteenths. The glass is 6.8 pixels tall and holds three things, so the
    // budget is stated once here and adding to it means taking from something else: 1.5 for the name,
    // 2.1 for the floor, 2.2 for the arrows and their bezels, and 0.25 of dark between each and at each
    // end. The floor gets the most because it is the one line a passenger is actually reading.
    private static final float NAME_CENTER_Y = 10.4f / 16, NAME_HALF_HEIGHT = 0.75f / 16;
    private static final float FLOOR_CENTER_Y = 8.35f / 16, FLOOR_HALF_HEIGHT = 1.05f / 16;
    private static final float ARROW_CENTER_Y = 5.95f / 16, ARROW_HALF_HEIGHT = 0.6f / 16;

    /**
     * Gap between the text and the edge of its window. Half the lobby panel's, because these windows are
     * half its height -- the same absolute padding would leave a 1.5-pixel window almost entirely
     * padding and shrink the glyph to nothing.
     */
    private static final float LABEL_PADDING = 0.15f / 16;

    /**
     * The up and down arrows, offset either side of the column's centre.
     * <p>
     * A pair rather than one arrow that turns over, because that is what a hall lantern is and because
     * "stopped" then has an appearance of its own -- both dark -- instead of having to be spelled with
     * an arrow that must point somewhere regardless. The offset is a quarter of the column, so with
     * FloorLabelRenderer's half-pixel bezel the two sockets sit 0.25 pixels apart and stop 0.125 pixels
     * short of the column's edge.
     */
    private static final float ARROW_OFFSET_X = 1.625f / 16;
    private static final float ARROW_HALF_WIDTH = 1 / 16f;

    /**
     * White for the elevator's name, rather than the floor's dye colour used just below it.
     * <p>
     * The name identifies the machine, not a floor, so no floor's colour has any claim on it -- and
     * leaving it white is what makes the coloured line underneath legible as a floor. White also scores
     * just under 20:1 against the glass, the highest contrast available, which a line this small needs.
     */
    private static final EnumDyeColor NAME_COLOR = EnumDyeColor.WHITE;

    /**
     * Guards the texture-tiling loop against a zero-width final segment when the plate's edge lands on a
     * block boundary and floating point puts it a hair the wrong side of one.
     */
    private static final float SEGMENT_EPSILON = 1e-4f;

    @Override
    public void render(BankIndicatorBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
        TextureAtlasSprite sprite = MovingElevatorsClient.METAL_SPRITE;
        if(sprite == null)
            return;

        BlockPos pos = entity.getPos();
        Vec3d cameraPos = RenderUtils.getCameraPosition();
        if(cameraPos.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TEXT_RENDER_DISTANCE)
            return;

        List<ElevatorGroup> groups = entity.getGroups();
        int columns = Math.min(groups.size(), MAX_COLUMNS);
        // The plate is sized from the columns rather than the columns squeezed into a plate, so every
        // readout is the same width whether the bank has two cars or six. A small bank simply gets a
        // small plate.
        float halfWidth = Math.max(columns, MIN_COLUMNS) * COLUMN_WIDTH / 2 + PLATE_EDGE_MARGIN;

        EnumFacing facing = entity.getFacing();

        GlStateManager.pushMatrix();

        // Same framing as the other wall fixtures, with the drawing plane pulled back to the plate.
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, LABEL_DEPTH);

        this.drawPlate(entity, sprite, PLATE_CENTER_X - halfWidth, PLATE_CENTER_X + halfWidth);
        // Drawn whether or not there are any columns, so an unlinked indicator shows a dark, empty
        // screen. That looks switched off, which is something a player can go and fix; bare metal looks
        // like the readout failed to draw.
        this.drawGlass(PLATE_CENTER_X - halfWidth + GLASS_MARGIN, PLATE_CENTER_X + halfWidth - GLASS_MARGIN);

        for(int column = 0; column < columns; column++)
            drawColumn(groups.get(column), columnCenterX(column, columns));

        GlStateManager.popMatrix();
    }

    /**
     * Where a column's centre falls, counting outwards from the plate's own centre so the row stays
     * symmetric however many there are.
     */
    private static float columnCenterX(int column, int columns){
        return PLATE_CENTER_X + (column + 0.5f - columns / 2f) * COLUMN_WIDTH;
    }

    /** One car's readout: what it is called, where it is, and which way it is going. */
    private static void drawColumn(ElevatorGroup group, float centerX){
        // An unnamed elevator draws no name window rather than an empty one. Most shafts never get a
        // name, and a row of blank sockets across the top of the plate would read as a row of broken
        // readouts; the floor lines simply sit under a strip of plain glass instead.
        String name = group.getName();
        if(name != null && !name.isEmpty())
            FloorLabelRenderer.drawFittedLabel(name, NAME_COLOR, centerX, NAME_CENTER_Y,
                CONTENT_HALF_WIDTH, NAME_HALF_HEIGHT, LABEL_PADDING);

        int floor = group.getCabinFloorNumber();
        if(floor >= 0 && floor < group.getFloorCount()){
            // The two-argument form: this plate speaks for a bank rather than for a landing, so there is
            // no floor whose arriving car it could announce. It still gets the emergency, out-of-service
            // and independent-service readouts, which is the whole point of going through the helper.
            String label = MovingElevatorsClient.stripFloorPrefix(
                MovingElevatorsClient.formatDisplayLabel(group, floor));
            if(label != null && !label.isEmpty())
                FloorLabelRenderer.drawFittedLabel(label, group.getFloorDisplayColor(floor), centerX,
                    FLOOR_CENTER_Y, CONTENT_HALF_WIDTH, FLOOR_HALF_HEIGHT, LABEL_PADDING);
        }

        // Which way the car is going, not which way it was called: this plate is a report on the bank,
        // and the calls belong to the landing fixtures. A stopped car lights neither arrow.
        int direction = group.getTravelDirection();
        FloorLabelRenderer.drawArrow(centerX - ARROW_OFFSET_X, ARROW_CENTER_Y,
            ARROW_HALF_WIDTH, ARROW_HALF_HEIGHT, true, direction > 0);
        FloorLabelRenderer.drawArrow(centerX + ARROW_OFFSET_X, ARROW_CENTER_Y,
            ARROW_HALF_WIDTH, ARROW_HALF_HEIGHT, false, direction < 0);
    }

    /**
     * The metal the whole fixture is made of, as a textured box in the plate's own plane.
     * <p>
     * Lit from the block's own position even where the plate hangs over its neighbours. The doors take
     * the same shortcut, and the alternative -- lighting each overhanging segment from the block it
     * covers -- would make a plate spanning a lit doorway and a dark wall change brightness partway
     * along, which looks far worse than a plate that is uniformly a little wrong.
     */
    private void drawPlate(BankIndicatorBlockEntity entity, TextureAtlasSprite sprite, float x0, float x1){
        int combinedLight = entity.getWorld().getCombinedLight(entity.getPos(), 0);
        int sky = combinedLight >> 16 & 0xFFFF, block = combinedLight & 0xFFFF;

        ScreenUtils.bindTexture(TextureAtlases.getBlocks());
        // The surrounding transform mirrors this space, which flips winding -- the same thing that culled
        // away one of the call arrows. Rather than hand-wind a box for a mirrored frame, draw both faces
        // and let the depth test hide the inside of a closed, opaque box.
        GlStateManager.disableCull();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        float y0 = PLATE_MIN_Y, y1 = PLATE_MAX_Y, z0 = PLATE_FRONT_Z, z1 = PLATE_BACK_Z;

        // The two ends, whose texture runs across the plate's depth and so never leaves one block cell.
        this.quad(buffer, sprite, sky, block, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, z0, y0, z1, y1);
        this.quad(buffer, sprite, sky, block, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, z0, y0, z1, y1);

        // Everything else is textured off x, and a wide plate's x leaves its own block entirely. Feeding
        // an out-of-range coordinate to getInterpolatedU samples whatever sprite sits next to the metal
        // on the atlas -- the same failure that drew a bucket across the foot of the car panels. So the
        // long faces are cut at every block boundary and each piece takes one copy of the sprite, which
        // both keeps the sampling in range and holds the brushed grain at the scale the rest of the wall
        // is drawn at.
        for(float cursor = x0; cursor < x1 - SEGMENT_EPSILON; ){
            float next = Math.min((float)Math.floor(cursor) + 1, x1);
            float u0 = cursor - (float)Math.floor(cursor), u1 = u0 + (next - cursor);
            this.quad(buffer, sprite, sky, block, cursor, y0, z0, cursor, y1, z0, next, y1, z0, next, y0, z0, u0, y0, u1, y1); // front
            this.quad(buffer, sprite, sky, block, next, y0, z1, next, y1, z1, cursor, y1, z1, cursor, y0, z1, u0, y0, u1, y1); // back
            this.quad(buffer, sprite, sky, block, cursor, y1, z0, cursor, y1, z1, next, y1, z1, next, y1, z0, u0, z0, u1, z1); // top
            this.quad(buffer, sprite, sky, block, cursor, y0, z1, cursor, y0, z0, next, y0, z0, next, y0, z1, u0, z0, u1, z1); // bottom
            cursor = next;
        }

        Tessellator.getInstance().draw();

        GlStateManager.enableCull();
    }

    private void quad(BufferBuilder buffer, TextureAtlasSprite sprite, int sky, int block,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float u0, float v0, float u1, float v1){
        float minU = sprite.getInterpolatedU(u0 * 16), maxU = sprite.getInterpolatedU(u1 * 16);
        float minV = sprite.getInterpolatedV(v0 * 16), maxV = sprite.getInterpolatedV(v1 * 16);
        buffer.pos(ax, ay, az).color(255, 255, 255, 255).tex(minU, maxV).lightmap(sky, block).endVertex();
        buffer.pos(bx, by, bz).color(255, 255, 255, 255).tex(minU, minV).lightmap(sky, block).endVertex();
        buffer.pos(cx, cy, cz).color(255, 255, 255, 255).tex(maxU, minV).lightmap(sky, block).endVertex();
        buffer.pos(dx, dy, dz).color(255, 255, 255, 255).tex(maxU, maxV).lightmap(sky, block).endVertex();
    }

    /**
     * The sheet of glass the columns lie on.
     * <p>
     * Drawn here rather than through FloorLabelRenderer, whose own inset is private and is sized per
     * element -- a dozen small sockets scattered across bare metal look like a dozen stickers, not like
     * one display. It is also what holds the columns together as one instrument: without it, six
     * separate stacks of sockets on a wide plate read as six fixtures that happen to touch.
     */
    private void drawGlass(float minX, float maxX){
        // Full lightmap, for the reason FloorLabelRenderer draws its insets full-bright: the display is
        // lit, and a lobby is often not. An ambient-lit sheet would go black at night behind readouts
        // that stayed bright, leaving the columns floating in mid-air. The metal around it is left on
        // ambient light on purpose -- it is metal, and a self-lit plate would glow.
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // Mirrored space flips winding here too, and the sheet is symmetric about the plate's centre, so
        // mirroring cannot move it -- but it can still turn it back-facing.
        GlStateManager.disableCull();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(minX, GLASS_MAX_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        buffer.pos(maxX, GLASS_MAX_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        buffer.pos(maxX, GLASS_MIN_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        buffer.pos(minX, GLASS_MIN_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }
}
