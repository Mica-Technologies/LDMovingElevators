package com.supermartijn642.movingelevators;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.gui.WidgetScreen;
import com.supermartijn642.core.registry.ClientRegistrationHandler;
import com.supermartijn642.core.render.TextureAtlases;
import com.supermartijn642.movingelevators.blocks.CamoBlockEntity;
import com.supermartijn642.movingelevators.blocks.DisplayBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.ElevatorInputBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.RemoteDisplayBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.ElevatorCarPanelBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.ElevatorDoorBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.RemoteCallPanelBlockEntityRenderer;
import com.supermartijn642.movingelevators.blocks.RemoteIndicatorBlockEntityRenderer;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import com.supermartijn642.movingelevators.gui.ElevatorScreen;
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
        return name == null ? TextComponents.translation("movingelevators.floor_name", TextComponents.number(floor + 1).get()).format() : name;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e){
        if(e.phase == TickEvent.Phase.END && !ClientUtils.getMinecraft().isGamePaused() && ClientUtils.getWorld() != null)
            ElevatorGroupCapability.tickWorldCapability(ClientUtils.getWorld());
    }
}
