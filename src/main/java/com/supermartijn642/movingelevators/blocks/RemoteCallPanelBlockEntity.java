package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.movingelevators.MovingElevators;

/**
 * Backing entity for {@link RemoteCallPanelBlock}. The controller binding and group lookup come from
 * {@link RemoteBoundBlockEntity}; the call state itself lives on the elevator group, so there is
 * nothing to keep here.
 */
public class RemoteCallPanelBlockEntity extends RemoteBoundBlockEntity {

    public RemoteCallPanelBlockEntity(){
        super(MovingElevators.remote_call_panel_tile);
    }
}
