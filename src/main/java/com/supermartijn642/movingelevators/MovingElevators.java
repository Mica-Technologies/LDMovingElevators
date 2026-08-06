package com.supermartijn642.movingelevators;

import com.google.common.collect.Sets;
import com.supermartijn642.core.CommonUtils;
import com.supermartijn642.core.block.BaseBlockEntityType;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.core.item.BaseBlockItem;
import com.supermartijn642.core.item.CreativeItemGroup;
import com.supermartijn642.core.item.ItemProperties;
import com.supermartijn642.core.network.PacketChannel;
import com.supermartijn642.core.registry.GeneratorRegistrationHandler;
import com.supermartijn642.core.registry.RegistrationHandler;
import com.supermartijn642.core.registry.RegistryEntryAcceptor;
import com.supermartijn642.movingelevators.blocks.*;
import com.supermartijn642.movingelevators.elevator.ElevatorGroupCapability;
import com.supermartijn642.movingelevators.generators.*;
import com.supermartijn642.movingelevators.packets.*;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Created 4/5/2020 by SuperMartijn642
 */
@Mod(modid = "@mod_id@", name = "@mod_name@", version = "@mod_version@", dependencies = "required-after:forge@@forge_dependency@;required-after:supermartijn642corelib@@core_library_dependency@;required-after:supermartijn642configlib@@config_library_dependency@")
public class MovingElevators {

    public static final Set<String> CAMOUFLAGE_MOD_BLACKLIST = Sets.newHashSet("secretroomsmod", "movingelevators");
    public static final PacketChannel CHANNEL = PacketChannel.create("movingelevators");

    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "elevator_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static ControllerBlock elevator_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "elevatorblocktile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<ControllerBlockEntity> elevator_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "display_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static DisplayBlock display_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "displayblocktile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<DisplayBlockEntity> display_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "button_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static RemoteControllerBlock button_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "buttonblocktile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<RemoteControllerBlockEntity> button_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "remote_display_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static RemoteDisplayBlock remote_display_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "remotedisplaytile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<RemoteDisplayBlockEntity> remote_display_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "remote_indicator_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static RemoteIndicatorBlock remote_indicator_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "remoteindicatortile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<RemoteIndicatorBlockEntity> remote_indicator_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "remote_call_panel_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static RemoteCallPanelBlock remote_call_panel_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "remotecallpaneltile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<RemoteCallPanelBlockEntity> remote_call_panel_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "elevator_car_panel_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static ElevatorCarPanelBlock elevator_car_panel_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "elevatorcarpaneltile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<ElevatorCarPanelBlockEntity> elevator_car_panel_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "elevator_door_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static ElevatorDoorBlock elevator_door_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "elevatordoortile", registry = RegistryEntryAcceptor.Registry.BLOCK_ENTITY_TYPES)
    public static BaseBlockEntityType<ElevatorDoorBlockEntity> elevator_door_tile;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "elevator_single_door_block", registry = RegistryEntryAcceptor.Registry.BLOCKS)
    public static ElevatorSingleDoorBlock elevator_single_door_block;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "arrive_sound", registry = RegistryEntryAcceptor.Registry.SOUND_EVENTS)
    public static SoundEvent arrive_sound;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "passing_floor_sound", registry = RegistryEntryAcceptor.Registry.SOUND_EVENTS)
    public static SoundEvent passing_floor_sound;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "arrive_ding_sound", registry = RegistryEntryAcceptor.Registry.SOUND_EVENTS)
    public static SoundEvent arrive_ding_sound;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "door_open_sound", registry = RegistryEntryAcceptor.Registry.SOUND_EVENTS)
    public static SoundEvent door_open_sound;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "door_close_sound", registry = RegistryEntryAcceptor.Registry.SOUND_EVENTS)
    public static SoundEvent door_close_sound;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "modern_chime_sound", registry = RegistryEntryAcceptor.Registry.SOUND_EVENTS)
    public static SoundEvent modern_chime_sound;
    @RegistryEntryAcceptor(namespace = "movingelevators", identifier = "modern_passing_floor_sound", registry = RegistryEntryAcceptor.Registry.SOUND_EVENTS)
    public static SoundEvent modern_passing_floor_sound;

    public static final CreativeItemGroup GROUP = CreativeItemGroup.create("movingelevators", () -> elevator_block.asItem());

    public MovingElevators(){
        CHANNEL.registerMessage(PacketAddElevatorGroup.class, PacketAddElevatorGroup::new, true);
        CHANNEL.registerMessage(PacketDecreaseCabinDepth.class, PacketDecreaseCabinDepth::new, true);
        CHANNEL.registerMessage(PacketDecreaseCabinDepthOffset.class, PacketDecreaseCabinDepthOffset::new, true);
        CHANNEL.registerMessage(PacketDecreaseCabinHeight.class, PacketDecreaseCabinHeight::new, true);
        CHANNEL.registerMessage(PacketDecreaseCabinHeightOffset.class, PacketDecreaseCabinHeightOffset::new, true);
        CHANNEL.registerMessage(PacketDecreaseCabinSideOffset.class, PacketDecreaseCabinSideOffset::new, true);
        CHANNEL.registerMessage(PacketDecreaseCabinWidth.class, PacketDecreaseCabinWidth::new, true);
        CHANNEL.registerMessage(PacketElevatorSpeed.class, PacketElevatorSpeed::new, true);
        CHANNEL.registerMessage(PacketIncreaseCabinDepth.class, PacketIncreaseCabinDepth::new, true);
        CHANNEL.registerMessage(PacketIncreaseCabinDepthOffset.class, PacketIncreaseCabinDepthOffset::new, true);
        CHANNEL.registerMessage(PacketIncreaseCabinHeight.class, PacketIncreaseCabinHeight::new, true);
        CHANNEL.registerMessage(PacketIncreaseCabinHeightOffset.class, PacketIncreaseCabinHeightOffset::new, true);
        CHANNEL.registerMessage(PacketIncreaseCabinSideOffset.class, PacketIncreaseCabinSideOffset::new, true);
        CHANNEL.registerMessage(PacketIncreaseCabinWidth.class, PacketIncreaseCabinWidth::new, true);
        CHANNEL.registerMessage(PacketOnElevator.class, PacketOnElevator::new, true);
        CHANNEL.registerMessage(PacketRemoveElevatorGroup.class, PacketRemoveElevatorGroup::new, true);
        CHANNEL.registerMessage(PacketRequestFloor.class, PacketRequestFloor::new, true);
        CHANNEL.registerMessage(PacketDoorControl.class, PacketDoorControl::new, true);
        CHANNEL.registerMessage(PacketToggleElevatorSounds.class, PacketToggleElevatorSounds::new, true);
        CHANNEL.registerMessage(PacketCycleElevatorSoundScheme.class, PacketCycleElevatorSoundScheme::new, true);
        CHANNEL.registerMessage(PacketSetFloorName.class, PacketSetFloorName::new, true);
        CHANNEL.registerMessage(PacketSyncElevatorMovement.class, PacketSyncElevatorMovement::new, true);
        CHANNEL.registerMessage(PacketToggleShowControllerButtons.class, PacketToggleShowControllerButtons::new, true);
        CHANNEL.registerMessage(PacketUpdateElevatorGroups.class, PacketUpdateElevatorGroups::new, true);

        MovingElevatorsConfig.init();

        register();
        if(CommonUtils.getEnvironmentSide().isClient())
            MovingElevatorsClient.register();
        registerGenerators();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e){
        ElevatorGroupCapability.register();
    }

    private static void register(){
        RegistrationHandler handler = RegistrationHandler.get("movingelevators");
        // Blocks
        Supplier<BlockProperties> properties = () -> BlockProperties.create(Material.ROCK, MapColor.GRAY).sound(SoundType.METAL).destroyTime(1.5f).explosionResistance(6);
        handler.registerBlock("elevator_block", () -> new ControllerBlock(properties.get()));
        handler.registerBlock("display_block", () -> new DisplayBlock(properties.get()));
        handler.registerBlock("button_block", () -> new RemoteControllerBlock(properties.get()));
        handler.registerBlock("remote_display_block", () -> new RemoteDisplayBlock(properties.get()));
        handler.registerBlock("remote_indicator_block", () -> new RemoteIndicatorBlock(properties.get()));
        handler.registerBlock("remote_call_panel_block", () -> new RemoteCallPanelBlock(properties.get()));
        handler.registerBlock("elevator_car_panel_block", () -> new ElevatorCarPanelBlock(properties.get()));
        handler.registerBlock("elevator_door_block", () -> new ElevatorDoorBlock(properties.get()));
        handler.registerBlock("elevator_single_door_block", () -> new ElevatorSingleDoorBlock(properties.get()));
        // Block entities
        handler.registerBlockEntityType("elevatorblocktile", () -> BaseBlockEntityType.create(ControllerBlockEntity::new, elevator_block));
        handler.registerBlockEntityType("displayblocktile", () -> BaseBlockEntityType.create(DisplayBlockEntity::new, display_block));
        handler.registerBlockEntityType("buttonblocktile", () -> BaseBlockEntityType.create(RemoteControllerBlockEntity::new, button_block));
        handler.registerBlockEntityType("remotedisplaytile", () -> BaseBlockEntityType.create(RemoteDisplayBlockEntity::new, remote_display_block));
        handler.registerBlockEntityType("remoteindicatortile", () -> BaseBlockEntityType.create(RemoteIndicatorBlockEntity::new, remote_indicator_block));
        handler.registerBlockEntityType("remotecallpaneltile", () -> BaseBlockEntityType.create(RemoteCallPanelBlockEntity::new, remote_call_panel_block));
        handler.registerBlockEntityType("elevatorcarpaneltile", () -> BaseBlockEntityType.create(ElevatorCarPanelBlockEntity::new, elevator_car_panel_block));
        handler.registerBlockEntityType("elevatordoortile", () -> BaseBlockEntityType.create(ElevatorDoorBlockEntity::new, elevator_door_block, elevator_single_door_block));
        // Items
        handler.registerItem("elevator_block", () -> new BaseBlockItem(elevator_block, ItemProperties.create().group(GROUP)));
        handler.registerItem("display_block", () -> new BaseBlockItem(display_block, ItemProperties.create().group(GROUP)));
        handler.registerItem("button_block", () -> new RemoteControllerBlockItem(button_block, ItemProperties.create().group(GROUP)));
        // Reuses RemoteControllerBlockItem so ControllerBlock's bind-on-right-click path, which keys
        // off that item class, works for the display too.
        handler.registerItem("remote_display_block", () -> new RemoteControllerBlockItem(remote_display_block, ItemProperties.create().group(GROUP)));
        handler.registerItem("remote_indicator_block", () -> new RemoteControllerBlockItem(remote_indicator_block, ItemProperties.create().group(GROUP)));
        handler.registerItem("remote_call_panel_block", () -> new RemoteControllerBlockItem(remote_call_panel_block, ItemProperties.create().group(GROUP)));
        handler.registerItem("elevator_car_panel_block", () -> new RemoteControllerBlockItem(elevator_car_panel_block, ItemProperties.create().group(GROUP)));
        handler.registerItem("elevator_door_block", () -> new BaseBlockItem(elevator_door_block, ItemProperties.create().group(GROUP)));
        handler.registerItem("elevator_single_door_block", () -> new BaseBlockItem(elevator_single_door_block, ItemProperties.create().group(GROUP)));
        // Sounds
        handler.registerSoundEvent("arrive_sound", () -> new SoundEvent(new ResourceLocation("movingelevators", "arrive_sound")));
        handler.registerSoundEvent("passing_floor_sound", () -> new SoundEvent(new ResourceLocation("movingelevators", "passing_floor_sound")));
        handler.registerSoundEvent("arrive_ding_sound", () -> new SoundEvent(new ResourceLocation("movingelevators", "arrive_ding_sound")));
        handler.registerSoundEvent("door_open_sound", () -> new SoundEvent(new ResourceLocation("movingelevators", "door_open_sound")));
        handler.registerSoundEvent("door_close_sound", () -> new SoundEvent(new ResourceLocation("movingelevators", "door_close_sound")));
        handler.registerSoundEvent("modern_chime_sound", () -> new SoundEvent(new ResourceLocation("movingelevators", "modern_chime_sound")));
        handler.registerSoundEvent("modern_passing_floor_sound", () -> new SoundEvent(new ResourceLocation("movingelevators", "modern_passing_floor_sound")));
    }

    private static void registerGenerators(){
        GeneratorRegistrationHandler handler = GeneratorRegistrationHandler.get("movingelevators");
        handler.addGenerator(MovingElevatorsModelGenerator::new);
        handler.addGenerator(MovingElevatorsBlockStateGenerator::new);
        handler.addGenerator(MovingElevatorsLanguageGenerator::new);
        handler.addGenerator(MovingElevatorsLootTableGenerator::new);
        handler.addGenerator(MovingElevatorsRecipeGenerator::new);
        handler.addGenerator(MovingElevatorsTagGenerator::new);
    }
}
