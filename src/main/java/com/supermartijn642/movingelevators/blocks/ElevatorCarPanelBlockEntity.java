package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.movingelevators.MovingElevators;

/**
 * Backing entity for {@link ElevatorCarPanelBlock}. The controller binding and group lookup come from
 * {@link RemoteBoundBlockEntity}; destinations live on the elevator group's call queue, so the panel
 * itself holds no state.
 */
public class ElevatorCarPanelBlockEntity extends RemoteBoundBlockEntity {

    public ElevatorCarPanelBlockEntity(){
        super(MovingElevators.elevator_car_panel_tile);
    }

    /** Not dispatched past where the car panel's renderer stops drawing; see {@link TextRenderCutoff}. */
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public double getMaxRenderDistanceSquared(){
        return TextRenderCutoff.dispatchRangeSquared(TextRenderCutoff.CAR_PANEL);
    }
}
