package com.supermartijn642.movingelevators;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.gui.WidgetScreen;
import com.supermartijn642.core.registry.ClientRegistrationHandler;
import com.supermartijn642.core.render.TextureAtlases;
import com.supermartijn642.movingelevators.blocks.BankIndicatorBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.BankLobbyPanelBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.CamoBlockEntity;
import com.supermartijn642.movingelevators.blocks.DisplayBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.ElevatorInputBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.RemoteDisplayBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.ElevatorDoorBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.RemoteCallPanelBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.RemoteIndicatorBlockEntityRenderer;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import com.supermartijn642.movingelevators.gui.ElevatorScreen;
import com.supermartijn642.movingelevators.gui.BankLobbyScreen;
import com.supermartijn642.movingelevators.gui.CarControlsScreen;
import com.supermartijn642.movingelevators.gui.FloorSelectScreen;
import com.supermartijn642.movingelevators.model.CamoBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created 3/28/2020 by SuperMartijn642
 */
@Mod.EventBusSubscriber(Side.CLIENT)
public class MovingElevatorsClient {

    public static final ResourceLocation OVERLAY_TEXTURE_LOCATION = new ResourceLocation("movingelevators", "blocks/block_overlays");
    public static TextureAtlasSprite OVERLAY_SPRITE;
    /** The doors are drawn by a renderer rather than baked, so their texture is needed directly. */
    public static final ResourceLocation METAL_TEXTURE_LOCATION = new ResourceLocation("movingelevators", "blocks/metal_silver");
    public static TextureAtlasSprite METAL_SPRITE;

    public static void register(){
        ClientRegistrationHandler handler = ClientRegistrationHandler.get("movingelevators");
        // Renderers
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.elevator_tile, ElevatorInputBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.display_tile, DisplayBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.button_tile, ElevatorInputBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.remote_display_tile, RemoteDisplayBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.remote_indicator_tile, RemoteIndicatorBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.remote_call_panel_tile, RemoteCallPanelBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.elevator_car_panel_tile, ElevatorCarPanelBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.bank_lobby_panel_tile, BankLobbyPanelBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.bank_indicator_tile, BankIndicatorBlockEntityRenderer::new);
        handler.registerCustomBlockEntityRenderer(() -> MovingElevators.elevator_door_tile, ElevatorDoorBlockEntityRenderer::new);
        // Register texture
        handler.registerAtlasSprite(TextureAtlases.getBlocks(), OVERLAY_TEXTURE_LOCATION.getResourcePath());
        handler.registerAtlasSprite(TextureAtlases.getBlocks(), METAL_TEXTURE_LOCATION.getResourcePath());
        // Baked models
        handler.registerBlockModelOverwrite(() -> MovingElevators.elevator_block, CamoBakedModel::new);
        handler.registerBlockModelOverwrite(() -> MovingElevators.display_block, CamoBakedModel::new);
        handler.registerBlockModelOverwrite(() -> MovingElevators.button_block, CamoBakedModel::new);
        handler.registerBlockModelOverwrite(() -> MovingElevators.remote_display_block, CamoBakedModel::new);
        // Block render types
        handler.registerBlockModelTranslucentRenderType(() -> MovingElevators.elevator_block);
        handler.registerBlockModelTranslucentRenderType(() -> MovingElevators.display_block);
        handler.registerBlockModelTranslucentRenderType(() -> MovingElevators.button_block);
        handler.registerBlockModelTranslucentRenderType(() -> MovingElevators.remote_display_block);
    }

    @SubscribeEvent
    public static void onColorHandlerEvent(ColorHandlerEvent.Block e){
        e.getBlockColors().registerBlockColorHandler(
            (state, blockAndTintGetter, pos, p_92570_) -> {
                if(blockAndTintGetter == null || pos == null)
                    return 0;
                TileEntity entity = blockAndTintGetter.getTileEntity(pos);
                return entity instanceof CamoBlockEntity && ((CamoBlockEntity)entity).hasCamoState() ? ClientUtils.getMinecraft().getBlockColors().colorMultiplier(((CamoBlockEntity)entity).getCamoState(), blockAndTintGetter, pos, p_92570_) : 0;
            },
            MovingElevators.elevator_block, MovingElevators.display_block, MovingElevators.button_block, MovingElevators.remote_display_block
        );
    }

    @SubscribeEvent
    public static void onTextureStitchPre(TextureStitchEvent.Post e){
        if(e.getMap() == ClientUtils.getTextureManager().getTexture(TextureAtlases.getBlocks())){
            OVERLAY_SPRITE = e.getMap().getAtlasSprite(OVERLAY_TEXTURE_LOCATION.toString());
            METAL_SPRITE = e.getMap().getAtlasSprite(METAL_TEXTURE_LOCATION.toString());
        }
    }

    public static void openElevatorScreen(BlockPos pos){
        ClientUtils.displayScreen(WidgetScreen.of(new ElevatorScreen(pos)));
    }

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

    /**
     * Drops a leading "Floor" from a label, so a panel reads "3" rather than "Floor 3". The default
     * floor names are "Floor 0", "Floor 1" and so on, and a panel has no room to repeat the word on
     * every floor.
     *
     * @return the identifier alone, or the original label if stripping would leave nothing
     */
    public static String stripFloorPrefix(String label){
        if(label == null)
            return null;
        Matcher matcher = FLOOR_PREFIX.matcher(label);
        if(!matcher.find())
            return label;
        String remainder = label.substring(matcher.end()).trim();
        // A floor genuinely named just "Floor" keeps its name rather than rendering blank.
        return remainder.isEmpty() ? label.trim() : remainder;
    }

    public static void openFloorSelectScreen(BlockPos pos){
        ClientUtils.displayScreen(WidgetScreen.of(new FloorSelectScreen(pos)));
    }

    public static void openCarControlsScreen(BlockPos pos){
        ClientUtils.displayScreen(WidgetScreen.of(new CarControlsScreen(pos)));
    }

    public static void openBankLobbyScreen(BlockPos pos){
        ClientUtils.displayScreen(WidgetScreen.of(new BankLobbyScreen(pos)));
    }

    /**
     * The name shown for a floor: whatever it was named, or a generated one.
     * <p>
     * Generated names count from one, so the lowest floor of a shaft is "Floor 1". The index stays
     * zero-based everywhere else -- this is the one place an index becomes something a player reads,
     * which is why the offset lives here and not in the elevator itself. Naming a floor by hand still
     * overrides it entirely.
     *
     * @param floor zero-based floor index
     */
    public static String formatFloorDisplayName(String name, int floor){
        return name == null ? translate("movingelevators.floor_name", TextComponents.number(floor + 1).get()) : name;
    }

    /**
     * A translated string with nothing in it but the words.
     * <p>
     * Core Lib's {@code format()} is {@code getFormattedText()}, which wraps every piece of a
     * component in its style code <em>and a trailing reset code</em>, so "Floor %s" filled in with 10
     * comes back as a section sign and an "r" on either side of the number -- twelve characters to
     * say seven. Anything that only draws the result never notices, since the font renderer eats
     * the codes. Anything that measures, truncates or scrolls it a character at a time counts
     * them, and the panel screens keep the first three characters of a floor's identifier for the
     * button face: those three were the reset code and the "1" of "10", so floor 10 drew as "1",
     * and floors 1 to 9 were right only by luck.
     * <p>
     * These labels have no styling to lose. What colour a readout draws in is decided at the draw
     * call, from the floor's dye, so the codes were never carrying anything to begin with.
     */
    private static String translate(String key, Object... args){
        return TextComponents.translation(key, args).get().getUnformattedText();
    }

    /**
     * What a readout should actually show: the emergency notice while the elevator is stopped for
     * one, and the floor name otherwise.
     * <p>
     * Every readout in the mod has to agree about an emergency. A panel still calmly showing a floor
     * next to one flashing "E"/"ST" reads as a broken panel rather than a stopped elevator, so the
     * choice is made here once instead of being repeated -- and eventually forgotten -- at each
     * renderer.
     *
     * @param floor zero-based floor index
     */
    public static String formatDisplayLabel(ElevatorGroup group, int floor){
        if(group == null)
            // A readout that has lost its shaft has no emergency to report, and should not go blank.
            return formatFloorDisplayName(null, floor);
        // A recalled car is the one thing on a readout worth interrupting anything else for.
        if(group.isFireRecalled()){
            String scrolled = translate("movingelevators.fire_recall.marquee") + "  ";
            int steps = scrolled.length() - 1;
            if(steps >= 1){
                int step = group.marqueeStep(steps);
                return scrolled.substring(step, Math.min(step + 2, scrolled.length()));
            }
        }
        // An elevator nobody can call should say so where people would otherwise stand waiting for it.
        if(group.isOutOfService()){
            String scrolled = translate("movingelevators.out_of_service.marquee") + "  ";
            int steps = scrolled.length() - 1;
            if(steps >= 1){
                int step = group.marqueeStep(steps);
                return scrolled.substring(step, Math.min(step + 2, scrolled.length()));
            }
        }
        // A car on independent service is working, and will still never answer the button you just
        // pressed. Alternating with the floor says both things at once: it is alive, and it is not
        // yours. Two letters because that is what a floor readout has room for.
        if(group.getServiceMode() == ElevatorGroup.ServiceMode.INDEPENDENT && group.isAnnounceFlashOn())
            return translate("movingelevators.independent_service.short");
        if(group.isEmergencyStopped())
            return translate(group.isEmergencyFlashOn()
                ? "movingelevators.emergency.flash_first" : "movingelevators.emergency.flash_second");
        return formatFloorDisplayName(group.getFloorDisplayName(floor), floor);
    }

    /**
     * As {@link #formatDisplayLabel(ElevatorGroup, int)}, but for a readout that speaks for a
     * particular landing and can therefore announce a car sent to it.
     * <p>
     * A bank picks a car and tells the person who asked; standing in a lobby of four shafts, that is
     * only half an answer, because they still have to work out which door to stand at. Flashing the
     * car's own name on its own landing readout is the other half, and it costs nothing -- the display
     * is already there and already says something nobody needs while the car is on its way.
     *
     * @param landingY the y level of the floor this readout belongs to
     */
    public static String formatDisplayLabel(ElevatorGroup group, int floor, int landingY){
        // An emergency outranks an announcement: a car nobody should board matters more than which
        // car it is.
        if(group != null && !group.isEmergencyStopped() && group.getName() != null
            && group.isAnnouncingAt(landingY) && group.isAnnounceFlashOn())
            return group.getName();
        return formatDisplayLabel(group, floor);
    }

    /**
     * Two characters of a scrolling "overload", or null when the cabin is not too full.
     * <p>
     * A two-character window because that is all a floor readout is: it shows a floor number, so it
     * has room for a floor number. Scrolling a word through it says the thing anyway, and a readout
     * that is visibly doing something unusual is itself part of the message.
     * <p>
     * The word comes from the language file rather than the elevator, since it is a word, and the two
     * trailing blanks let it clear the screen before coming round again instead of running together.
     */
    public static String overloadMarquee(ElevatorGroup group){
        if(group == null || !group.isOverloaded())
            return null;
        String scrolled = translate("movingelevators.overload.marquee") + "  ";
        int steps = scrolled.length() - 1;
        if(steps < 1)
            return null;
        int step = group.marqueeStep(steps);
        return scrolled.substring(step, Math.min(step + 2, scrolled.length()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e){
        if(e.phase == TickEvent.Phase.END && !ClientUtils.getMinecraft().isGamePaused() && ClientUtils.getWorld() != null)
            ElevatorGroupCapability.tickWorldCapability(ClientUtils.getWorld());
    }
}
