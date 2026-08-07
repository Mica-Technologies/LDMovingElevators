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
        // The doors are drawn by their block entity renderer so they can slide, so the baked model
        // is empty -- otherwise the chunk would draw a snapped leaf behind the animated one. The
        // particle texture is still needed for breaking particles.
        this.model("block/elevator_door_block_hidden")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver");
        this.model("item/elevator_door_block")
            .parent("block/elevator_door_block_closed");
        this.model("item/elevator_single_door_block")
            .parent("block/elevator_door_block_closed");
        this.model("block/elevator_car_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(2, -2, 14, 14, 15, 16).allFaces(face -> face.texture("metal")));
        // Its own model rather than the block's, with the front face darkened. Everything a panel
        // actually shows -- readout, arrows, buttons -- is drawn by its block entity renderer, and an
        // item does not run one, so inheriting the block model gives an icon of blank metal on every
        // side. That is what made these look like they were facing backwards: there is no front to
        // see. A screen baked into the item only is enough to tell the panels apart in a hotbar, and
        // cannot show through in the world, where the renderer draws over this face anyway.
        this.model("item/elevator_car_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .texture("screen", "blocks/display")
            .element(element -> element.shape(2, -2, 14, 14, 15, 16)
                .allFaces(face -> face.texture("metal"))
                .face(EnumFacing.NORTH, face -> face.texture("screen"))
                .face(EnumFacing.SOUTH, face -> face.texture("screen")));
        // The same plate as the car panel. It was half height while its face was empty below the
        // readout; it now carries door and alarm controls there instead.
        this.model("block/bank_car_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(2, -2, 14, 14, 15, 16).allFaces(face -> face.texture("metal")));
        // Its own model rather than the block's, with the front face darkened. Everything a panel
        // actually shows -- readout, arrows, buttons -- is drawn by its block entity renderer, and an
        // item does not run one, so inheriting the block model gives an icon of blank metal on every
        // side. That is what made these look like they were facing backwards: there is no front to
        // see. A screen baked into the item only is enough to tell the panels apart in a hotbar, and
        // cannot show through in the world, where the renderer draws over this face anyway.
        this.model("item/bank_car_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .texture("screen", "blocks/display")
            .element(element -> element.shape(2, -2, 14, 14, 15, 16)
                .allFaces(face -> face.texture("metal"))
                .face(EnumFacing.NORTH, face -> face.texture("screen"))
                .face(EnumFacing.SOUTH, face -> face.texture("screen")));
        this.model("block/bank_lobby_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .element(element -> element.shape(4, 2, 14, 12, 15, 16).allFaces(face -> face.texture("metal")));
        // Its own model rather than the block's, with the front face darkened. Everything a panel
        // actually shows -- readout, arrows, buttons -- is drawn by its block entity renderer, and an
        // item does not run one, so inheriting the block model gives an icon of blank metal on every
        // side. That is what made these look like they were facing backwards: there is no front to
        // see. A screen baked into the item only is enough to tell the panels apart in a hotbar, and
        // cannot show through in the world, where the renderer draws over this face anyway.
        this.model("item/bank_lobby_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .texture("screen", "blocks/display")
            .element(element -> element.shape(4, 2, 14, 12, 15, 16)
                .allFaces(face -> face.texture("metal"))
                .face(EnumFacing.NORTH, face -> face.texture("screen"))
                .face(EnumFacing.SOUTH, face -> face.texture("screen")));
        // Its own model rather than the block's, with the front face darkened. Everything a panel
        // actually shows -- readout, arrows, buttons -- is drawn by its block entity renderer, and an
        // item does not run one, so inheriting the block model gives an icon of blank metal on every
        // side. That is what made these look like they were facing backwards: there is no front to
        // see. A screen baked into the item only is enough to tell the panels apart in a hotbar, and
        // cannot show through in the world, where the renderer draws over this face anyway.
        this.model("item/remote_call_panel_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .texture("screen", "blocks/display")
            .element(element -> element.shape(5, 1, 14, 11, 15, 16)
                .allFaces(face -> face.texture("metal"))
                .face(EnumFacing.NORTH, face -> face.texture("screen"))
                .face(EnumFacing.SOUTH, face -> face.texture("screen")));
        // Its own model rather than the block's, with the front face darkened. Everything a panel
        // actually shows -- readout, arrows, buttons -- is drawn by its block entity renderer, and an
        // item does not run one, so inheriting the block model gives an icon of blank metal on every
        // side. That is what made these look like they were facing backwards: there is no front to
        // see. A screen baked into the item only is enough to tell the panels apart in a hotbar, and
        // cannot show through in the world, where the renderer draws over this face anyway.
        this.model("item/remote_indicator_block")
            .parent("minecraft", "block/block")
            .texture("particle", "blocks/metal_silver")
            .texture("metal", "blocks/metal_silver")
            .texture("screen", "blocks/display")
            .element(element -> element.shape(1, 5.5f, 14, 15, 10.5f, 16)
                .allFaces(face -> face.texture("metal"))
                .face(EnumFacing.NORTH, face -> face.texture("screen"))
                .face(EnumFacing.SOUTH, face -> face.texture("screen")));
        this.model("item/remote_display_block")
            .parent("block/remote_display_block")
            .texture("overlay", "blocks/display_overlay")
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).allFaces(face -> face.texture("all")))
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).face(EnumFacing.NORTH, face -> face.texture("overlay")));
    }
}
