package com.supermartijn642.movingelevators.generators;

import com.supermartijn642.core.generator.ModelGenerator;
import com.supermartijn642.core.generator.ResourceCache;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/**
 * Created 12/09/2022 by SuperMartijn642
 */
public class MovingElevatorsModelGenerator extends ModelGenerator {

    public MovingElevatorsModelGenerator(ResourceCache cache){
        super("movingelevators", cache);
    }

    @Override
    public void generate(){
        this.cubeAll("block/elevator_block", new ResourceLocation("movingelevators", "blocks/elevator"));
        this.cubeAll("block/display_block", new ResourceLocation("movingelevators", "blocks/display"));
        this.cubeAll("block/button_block", new ResourceLocation("movingelevators", "blocks/display"));
        this.cubeAll("block/remote_display_block", new ResourceLocation("movingelevators", "blocks/display"));
        this.model("item/elevator_block")
            .parent("block/elevator_block")
            .texture("overlay", "blocks/buttons")
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).allFaces(face -> face.texture("all")))
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).face(EnumFacing.NORTH, face -> face.texture("overlay").uv(0, 0, 11.5f, 11.5f)));
        this.model("item/display_block")
            .parent("block/display_block")
            .texture("overlay", "blocks/display_overlay")
            .texture("buttons", "blocks/display_buttons")
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).allFaces(face -> face.texture("all")))
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).face(EnumFacing.NORTH, face -> face.texture("overlay")))
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).face(EnumFacing.NORTH, face -> face.texture("buttons")));
        this.model("item/button_block")
            .parent("block/button_block")
            .texture("overlay", "blocks/buttons")
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).allFaces(face -> face.texture("all")))
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).face(EnumFacing.NORTH, face -> face.texture("overlay").uv(0, 0, 11.5f, 11.5f)));
        // Slim wall plate, authored facing north; the block state rotates it for the other sides.
        // The metal sits at high Z because a north-facing plate is mounted on its south neighbour --
        // putting it at Z=0 leaves it floating a block clear of the wall.
        this.model("block/remote_indicator_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(1, 5.5f, 14, 15, 10.5f, 16).allFaces(face -> face.texture("metal")));
        // Tall narrow landing panel: readout at the top, call buttons below.
        this.model("block/remote_call_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(5, 1, 14, 11, 15, 16).allFaces(face -> face.texture("metal")));
        // Sliding double doors. Closed, two leaves meet in the middle with a seam between them;
        // open, each has retracted into its side of the frame. Authored facing north.
        // One leaf per block: closed it fills its own block, open it retracts into its own side of
        // the frame. Two blocks side by side make the double door.
        this.model("block/elevator_door_block_closed")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(0, 0, 7, 16, 16, 9).allFaces(face -> face.texture("metal")));
        this.model("block/elevator_door_block_open_left")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(0, 0, 7, 3, 16, 9).allFaces(face -> face.texture("metal")));
        this.model("block/elevator_door_block_open_right")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(13, 0, 7, 16, 16, 9).allFaces(face -> face.texture("metal")));
        // The doors are drawn by their block entity renderer so they can slide, so the baked model
        // is empty -- otherwise the chunk would draw a snapped leaf behind the animated one. The
        // particle texture is still needed for breaking particles.
        this.model("block/elevator_door_block_hidden")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver");
        this.model("item/elevator_door_block")
            .parent("block/elevator_door_block_closed");
        // The single door reuses the closed leaf; open, it retracts to one side only.
        this.model("block/elevator_single_door_block_open")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(0, 0, 7, 3, 16, 9).allFaces(face -> face.texture("metal")));
        this.model("item/elevator_single_door_block")
            .parent("block/elevator_door_block_closed");
        this.model("block/elevator_car_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(2, 1, 14, 14, 15, 16).allFaces(face -> face.texture("metal")));
        this.model("item/elevator_car_panel_block")
            .parent("block/elevator_car_panel_block");
        this.model("block/bank_lobby_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(4, 2, 14, 12, 15, 16).allFaces(face -> face.texture("metal")));
        this.model("item/bank_lobby_panel_block")
            .parent("block/bank_lobby_panel_block");
        this.model("item/remote_call_panel_block")
            .parent("block/remote_call_panel_block");
        this.model("item/remote_indicator_block")
            .parent("block/remote_indicator_block");
        this.model("item/remote_display_block")
            .parent("block/remote_display_block")
            .texture("overlay", "blocks/display_overlay")
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).allFaces(face -> face.texture("all")))
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).face(EnumFacing.NORTH, face -> face.texture("overlay")));
    }
}
