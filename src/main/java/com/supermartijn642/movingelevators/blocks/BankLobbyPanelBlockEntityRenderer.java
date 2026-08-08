package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.render.CustomBlockEntityRenderer;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws a {@link BankLobbyPanelBlockEntity}: one tall sheet of dark glass in the plate's metal
 * surround, with the landing's identifier across the top of it and a destination-dispatch keypad
 * filling the rest.
 * <p>
 * The keypad is a picture of a keypad, not a control -- clicking anywhere on the panel opens the
 * dispatch screen, so no key has a state of its own and every one of them is drawn unlit. That is
 * also why there are twelve: the reference fixture's bottom row is star-zero-hash, and dropping the
 * third key would leave a hole that reads as a broken panel rather than as a deliberate layout.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankLobbyPanelBlockEntityRenderer implements CustomBlockEntityRenderer<BankLobbyPanelBlockEntity> {

    /**
     * Squared, so this is 30 blocks. A lobby panel is a landing fixture like the call panel and the
     * indicator -- read from across a lobby, not from arm's length as the car panel is -- so it takes
     * the same cutoff those use.
     * <p>
     * Text is expensive to draw, which is why there is a cutoff at all. Past 64 blocks raising this
     * alone would do nothing anyway: block entities stop being rendered at that range unless they ask
     * for more.
     */
    private static final double TEXT_RENDER_DISTANCE = 30 * 30;

    /**
     * Matches the plate's own depth, so the contents sit just proud of the metal rather than hanging
     * in the air where a full block's face would have been.
     */
    private static final double LABEL_DEPTH = 0.5 - WallPanelBlock.PLATE_DEPTH - 0.01;

    // Face layout, in sixteenths, stated as the player sees it. FloorLabelRenderer mirrors x for us,
    // so these are ordinary left-to-right coordinates and nothing here flips anything by hand.
    /** The plate's own bounds, mirroring the block's shape. Everything below is derived from them. */
    private static final float PLATE_MIN_X = 4 / 16f, PLATE_MAX_X = 12 / 16f;
    private static final float PLATE_MIN_Y = 2 / 16f, PLATE_MAX_Y = 15 / 16f;
    /**
     * Metal left visible around the glass. Stated as a flat margin rather than a fraction of the
     * plate, because the plate is much taller than it is wide and a proportional inset would leave a
     * fat surround at the top and a hairline one at the sides.
     */
    private static final float GLASS_MARGIN = 0.6f / 16;
    private static final float GLASS_MIN_X = PLATE_MIN_X + GLASS_MARGIN, GLASS_MAX_X = PLATE_MAX_X - GLASS_MARGIN;
    private static final float GLASS_MIN_Y = PLATE_MIN_Y + GLASS_MARGIN, GLASS_MAX_Y = PLATE_MAX_Y - GLASS_MARGIN;

    /**
     * Prompt window at the top of the glass. Fixed size, like the car panel's readout: a window that
     * shrink-wraps its text changes shape with the floor name and stops looking like a display.
     */
    private static final float PROMPT_X = 8 / 16f, PROMPT_Y = 13.2f / 16;
    private static final float PROMPT_HALF_WIDTH = 2.9f / 16, PROMPT_HALF_HEIGHT = 1 / 16f;
    private static final float PROMPT_PADDING = 0.3f / 16;

    /**
     * White, not the floor's dye colour. The bank API names a floor but does not colour it, and a
     * colour would be a lie anyway on a panel that speaks for several elevators at once, since each of
     * them may have dyed that floor differently. White also scores just under 20:1 against the glass
     * below -- the highest contrast available, which is what a small readout on a tall plate needs.
     */
    private static final EnumDyeColor PROMPT_COLOR = EnumDyeColor.WHITE;

    /**
     * The glass itself: near-black, and near-opaque so the metal texture behind it -- or whatever the
     * panel has been camouflaged as -- cannot mottle the readout. The remaining alpha is deliberate:
     * a completely opaque sheet reads as paint, a faint bleed of the plate behind it reads as glass.
     * <p>
     * Slightly deeper than the inset FloorLabelRenderer draws under each key, which puts those sockets
     * at 1.04:1 against this. That is a hairline by design -- the sockets should suggest key wells,
     * not outline them -- while the unlit keys themselves still land at 7.1:1 against the glass, well
     * clear of the 2.24 that made the first attempt's unlit buttons invisible in game.
     */
    private static final int GLASS_R = 10, GLASS_G = 10, GLASS_B = 12, GLASS_A = 245;

    /**
     * Behind everything FloorLabelRenderer draws, which sits at -0.003 and -0.005, but still proud of
     * the plate face at 0. Smaller z is towards the viewer, so this is the back layer; the gap has to
     * be wide enough that the key sockets do not z-fight the sheet they lie on.
     */
    private static final double GLASS_DEPTH = -0.001;

    /** Full lightmap, the value FloorLabelRenderer uses for the same reason. */
    private static final int FULL_BRIGHT = 240;

    /**
     * The keypad. Three columns and four rows of it, filling the glass below the prompt.
     * <p>
     * The keys are small because the plate is only eight pixels wide and the helper adds a half-pixel
     * bezel around each one: at this size the twelve sockets sit a quarter-pixel apart, a quarter-pixel
     * clear of the prompt window and no nearer than that to the metal. Growing them further makes the
     * grid touch one or the other.
     */
    private static final int KEY_COLUMNS = 3, KEY_ROWS = 4;
    private static final float KEY_HALF = 0.45f / 16;
    private static final float KEY_LEFT_X = 5.85f / 16, KEY_COLUMN_GAP = 2.15f / 16;
    /** Rows run downwards from here, so the loop reads in keypad order: 1-2-3, then 4-5-6, and so on. */
    private static final float KEY_TOP_Y = 11 / 16f, KEY_ROW_GAP = 2.4f / 16;

    @Override
    public void render(BankLobbyPanelBlockEntity entity, float partialTicks, int combinedOverlay, float alpha){
        BlockPos pos = entity.getPos();
        Vec3d cameraPos = RenderUtils.getCameraPosition();
        if(cameraPos.squareDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TEXT_RENDER_DISTANCE)
            return;

        EnumFacing facing = entity.getFacing();

        GlStateManager.pushMatrix();

        // Same framing as the other wall fixtures, with the drawing plane pulled back to the plate.
        GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, LABEL_DEPTH);

        this.drawGlass();

        // An unbound panel keeps its glass and its keys but shows no floor: it should look switched
        // off, which is a state a player can fix, rather than blank-plated, which looks like a bug.
        if(entity.isBound()){
            // Resolved here rather than inside the readout, so every question the readout asks is
            // asked of one answer. See BankLobbyPanelBlockEntity#getBankFloors(List).
            String label = readout(entity, entity.getGroups());
            if(label != null && !label.isEmpty())
                FloorLabelRenderer.drawFittedLabel(label, PROMPT_COLOR, PROMPT_X, PROMPT_Y,
                    PROMPT_HALF_WIDTH, PROMPT_HALF_HEIGHT, PROMPT_PADDING);
        }

        this.drawKeypad();

        GlStateManager.popMatrix();
    }

    /**
     * What the landing calls itself, taken from the bank rather than from any one elevator.
     * <p>
     * Resolving the bound elevators allocates, so the caller does it once, inside the distance cutoff
     * above, and hands the result in. The fallback costs a second pass over that list, but only on
     * landings nobody has named, and a lobby panel that cannot say which floor it is standing on is
     * worth less than the pass.
     *
     * @param groups this panel's bank, already resolved
     * @return null when no bound elevator serves this landing, which draws as a dark screen
     */
    private static String readout(BankLobbyPanelBlockEntity entity, List<ElevatorGroup> groups){
        // The one readout in the mod that was not saying anything during an emergency, and the one a
        // waiting passenger most needs to hear it from -- they are standing at the lobby deciding
        // whether to take the stairs.
        if(BankLobbyPanelBlockEntity.isBankOutOfService(groups)){
            // Any bound elevator will do: this only borrows the bank's flash beat, which is off world
            // time and therefore the same answer from every car in the building.
            ElevatorGroup group = groups.isEmpty() ? null : groups.get(0);
            return TextComponents.translation(group == null || group.isEmergencyFlashOn()
                ? "movingelevators.emergency.flash_first" : "movingelevators.emergency.flash_second").format();
        }
        int panelY = entity.getLandingY(groups);
        String name = BankLobbyPanelBlockEntity.getFloorName(groups, panelY);
        if(name == null){
            // Unnamed: number it by where the landing sits in the bank's own floor list, so the panel
            // agrees with the dispatch screen rather than showing a raw world y.
            int floor = BankLobbyPanelBlockEntity.getBankFloors(groups).indexOf(panelY);
            if(floor < 0)
                return null;
            name = MovingElevatorsClient.formatFloorDisplayName(null, floor);
        }
        // "Floor 3" does not fit a plate this narrow, and the word is the same on every floor anyway.
        return MovingElevatorsClient.stripFloorPrefix(name);
    }

    /** Every key unlit: nothing on this panel has per-key state, so a lit one would mean nothing. */
    private void drawKeypad(){
        for(int row = 0; row < KEY_ROWS; row++){
            float y = KEY_TOP_Y - row * KEY_ROW_GAP;
            for(int column = 0; column < KEY_COLUMNS; column++)
                FloorLabelRenderer.drawButton(KEY_LEFT_X + column * KEY_COLUMN_GAP, y, KEY_HALF, false);
        }
    }

    /**
     * The sheet of glass the prompt and the keypad lie on.
     * <p>
     * Drawn here rather than through FloorLabelRenderer, whose own inset is private and is sized per
     * element -- twelve key sockets and a readout window on bare metal look like twelve stickers, not
     * like one touchscreen.
     */
    private void drawGlass(){
        // Full lightmap, for the reason FloorLabelRenderer draws its insets full-bright: the panel is
        // lit, and a lobby is often not. An ambient-lit sheet would go black at night behind key
        // sockets that stayed bright, leaving the keypad floating in mid-air.
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // The surrounding transform mirrors this space, which flips winding -- the same thing that
        // culled away one of the call arrows. The sheet is symmetric about the plate's centre, so
        // mirroring cannot move it, but it can still turn it back-facing.
        GlStateManager.disableCull();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(GLASS_MIN_X, GLASS_MAX_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        buffer.pos(GLASS_MAX_X, GLASS_MAX_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        buffer.pos(GLASS_MAX_X, GLASS_MIN_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        buffer.pos(GLASS_MIN_X, GLASS_MIN_Y, GLASS_DEPTH).color(GLASS_R, GLASS_G, GLASS_B, GLASS_A).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }
}
