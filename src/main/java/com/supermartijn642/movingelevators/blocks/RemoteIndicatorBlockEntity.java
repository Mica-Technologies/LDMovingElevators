package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.movingelevators.MovingElevators;

/**
 * Backing entity for the slim wall-mounted {@link RemoteIndicatorBlock}. Everything it needs -- the
 * controller binding and the group lookup -- lives in {@link RemoteBoundBlockEntity}; this block just
 * reports, so there is nothing to add.
 */
public class RemoteIndicatorBlockEntity extends RemoteBoundBlockEntity {

    public RemoteIndicatorBlockEntity(){
        super(MovingElevators.remote_indicator_tile);
    }
}
