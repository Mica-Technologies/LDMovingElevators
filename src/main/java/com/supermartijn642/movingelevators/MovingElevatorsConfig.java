package com.supermartijn642.movingelevators;

import com.supermartijn642.configlib.api.ConfigBuilders;
import com.supermartijn642.configlib.api.IConfigBuilder;

import java.util.function.Supplier;

/**
 * Created 05/02/2022 by SuperMartijn642
 */
public class MovingElevatorsConfig {

    public static final Supplier<Integer> maxCabinHorizontalSize;
    public static final Supplier<Integer> maxCabinVerticalSize;
    public static final Supplier<Boolean> allowUnbreakableBlocks;
    public static final Supplier<Integer> doorAutoCloseTicks;
    public static final Supplier<Integer> movingCabinLight;

    static{
        IConfigBuilder builder = ConfigBuilders.newTomlConfig("movingelevators", null, false);

        builder.push("General");
        maxCabinHorizontalSize = builder.comment("What should be the maximum width of an elevator cabin? Higher numbers may cause lag.").define("maxCabinHorizontalSize", 11, 1, 15);
        maxCabinVerticalSize = builder.comment("What should be the maximum height of an elevator cabin? Higher numbers may cause lag.").define("maxCabinVerticalSize", 11, 1, 15);
        allowUnbreakableBlocks = builder.comment("Is the elevator allowed to move unbreakable blocks? If set to true, this may allow players to move blocks like bedrock and portals using elevators!").define("allowUnbreakableBlocks", false);
        doorAutoCloseTicks = builder.comment("How long elevator doors stay open before closing on their own, in ticks. 20 ticks is one second.").define("doorAutoCloseTicks", 240, 20, 1200);
        movingCabinLight = builder.comment(
            "Minimum light level inside an elevator cabin while it is moving, 0-15. A cabin is lifted out of the world while it travels, so nothing inside it lights anything and it goes dark until it arrives. This is the cabin's own courtesy lighting: it affects how the cabin looks, not the shaft around it. Set to 0 for the old behaviour, where a moving cabin is as dark as the shaft it passes through.")
            .define("movingCabinLight", 6, 0, 15);
        builder.pop();

        builder.build();
    }

    public static void init(){
    }
}
