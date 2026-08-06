package com.supermartijn642.movingelevators.generators;

import com.supermartijn642.core.generator.BlockStateGenerator;
import com.supermartijn642.core.generator.ResourceCache;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.blocks.ElevatorDoorBlock;
import com.supermartijn642.movingelevators.blocks.ElevatorDoorBlockBase;
import com.supermartijn642.movingelevators.blocks.WallPanelBlock;
import net.minecraft.util.EnumFacing;

/**
 * Created 12/09/2022 by SuperMartijn642
 */
public class MovingElevatorsBlockStateGenerator extends BlockStateGenerator {

    public MovingElevatorsBlockStateGenerator(ResourceCache cache){
        super("movingelevators", cache);
    }

    @Override
    public void generate(){
        this.blockState(MovingElevators.elevator_block).variantsForAll((state, builder) -> builder.model("block/elevator_block"));
        this.blockState(MovingElevators.display_block).emptyVariant(builder -> builder.model("block/display_block"));
        this.blockState(MovingElevators.button_block).emptyVariant(builder -> builder.model("block/button_block"));
        this.blockState(MovingElevators.remote_display_block).emptyVariant(builder -> builder.model("block/remote_display_block"));
        // The plate model is authored facing north, so each variant just spins it around Y.
        this.blockState(MovingElevators.remote_indicator_block).variantsForAll((state, builder) ->
            builder.model("block/remote_indicator_block", 0, wallPanelRotation(state.get(WallPanelBlock.FACING))));
        this.blockState(MovingElevators.remote_call_panel_block).variantsForAll((state, builder) ->
            builder.model("block/remote_call_panel_block", 0, wallPanelRotation(state.get(WallPanelBlock.FACING))));
        this.blockState(MovingElevators.elevator_car_panel_block).variantsForAll((state, builder) ->
            builder.model("block/elevator_car_panel_block", 0, wallPanelRotation(state.get(WallPanelBlock.FACING))));
        // Doors carry an extra OPEN property, so each facing has an open and a closed variant.
        this.blockState(MovingElevators.elevator_single_door_block).variantsForAll((state, builder) ->
            builder.model(state.get(ElevatorDoorBlockBase.OPEN) ? "block/elevator_single_door_block_open" : "block/elevator_door_block_closed",
                0, wallPanelRotation(state.get(ElevatorDoorBlockBase.FACING))));
        this.blockState(MovingElevators.elevator_door_block).variantsForAll((state, builder) -> {
            String model = !state.get(ElevatorDoorBlock.OPEN) ? "block/elevator_door_block_closed"
                : state.get(ElevatorDoorBlock.RIGHT) ? "block/elevator_door_block_open_right"
                : "block/elevator_door_block_open_left";
            builder.model(model, 0, wallPanelRotation(state.get(ElevatorDoorBlock.FACING)));
        });
    }

    /** Wall panel models are authored facing north, so each variant just spins them around Y. */
    private static int wallPanelRotation(EnumFacing facing){
        return facing == EnumFacing.EAST ? 90 : facing == EnumFacing.SOUTH ? 180 : facing == EnumFacing.WEST ? 270 : 0;
    }
}
