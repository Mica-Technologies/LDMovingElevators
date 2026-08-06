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
        this.model("item/remote_indicator_block")
            .parent("block/remote_indicator_block");
        this.model("item/remote_display_block")
            .parent("block/remote_display_block")
            .texture("overlay", "blocks/display_overlay")
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).allFaces(face -> face.texture("all")))
            .element(element -> element.shape(0, 0, 0, 16, 16, 16).face(EnumFacing.NORTH, face -> face.texture("overlay")));
    }
}
