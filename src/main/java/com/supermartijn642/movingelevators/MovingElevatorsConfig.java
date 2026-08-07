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
    public static final Supplier<Integer> elevatorDwellTicks;
    public static final Supplier<Integer> bankedDwellTicks;
    public static final Supplier<Integer> emergencyHoldTicks;
    public static final Supplier<Integer> doorLinkRange;
    public static final Supplier<Integer> shaftScanReach;
    public static final Supplier<Integer> maxCabinSpeed;
    public static final Supplier<Integer> movingCabinLight;

    static{
        IConfigBuilder builder = ConfigBuilders.newTomlConfig("movingelevators", null, false);

        builder.push("General");
        maxCabinHorizontalSize = builder.comment("What should be the maximum width of an elevator cabin? Higher numbers may cause lag.").define("maxCabinHorizontalSize", 11, 1, 15);
        maxCabinVerticalSize = builder.comment("What should be the maximum height of an elevator cabin? Higher numbers may cause lag.").define("maxCabinVerticalSize", 11, 1, 15);
        allowUnbreakableBlocks = builder.comment("Is the elevator allowed to move unbreakable blocks? If set to true, this may allow players to move blocks like bedrock and portals using elevators!").define("allowUnbreakableBlocks", false);
        doorAutoCloseTicks = builder.comment("How long elevator doors stay open before closing on their own, in ticks. 20 ticks is one second.").define("doorAutoCloseTicks", 240, 20, 1200);
        elevatorDwellTicks = builder.comment("How long an elevator waits at a floor before moving on to its next call, in ticks. 20 ticks is one second. This is boarding time: pressing 'close doors' inside the cabin cuts it short. A floor the elevator was sent to by a bank lobby panel is held longer, since whoever called it is walking over rather than already standing there.").define("elevatorDwellTicks", 200, 0, 1200);
        bankedDwellTicks = builder.comment("How long an elevator holds a floor it was sent to by a bank lobby panel, in ticks. Longer than the ordinary wait because whoever called it is walking over rather than standing at the doors. 'Close doors' inside the cabin cuts it short.").define("bankedDwellTicks", 300, 0, 2400);
        emergencyHoldTicks = builder.comment("How long an elevator stays out of service after an emergency stop, in ticks. This is a minimum: if somebody is still in the shaft when it expires, it waits again.").define("emergencyHoldTicks", 600, 20, 2400);
        doorLinkRange = builder.comment("How far an elevator door will look for a landing to attach itself to when placed, in blocks.").define("doorLinkRange", 12, 1, 32);
        shaftScanReach = builder.comment("How far above and below a moving cabin counts as being in its way, in blocks. The cabin also always sweeps everything it has passed through since the last check, so this is the warning it gets about what lies ahead rather than the whole of what it notices.").define("shaftScanReach", 10, 0, 32);
        maxCabinSpeed = builder.comment("The fastest an elevator may be set to travel, in tenths of a block per tick. 10 is one block per tick. Lowering this also lowers the top of the speed slider in the elevator's screen.").define("maxCabinSpeed", 10, 1, 10);
        movingCabinLight = builder.comment(
            "Minimum light level inside an elevator cabin while it is moving, 0-15. A cabin is lifted out of the world while it travels, so nothing inside it lights anything and it goes dark until it arrives. This is the cabin's own courtesy lighting: it affects how the cabin looks, not the shaft around it. Set to 0 for the old behaviour, where a moving cabin is as dark as the shaft it passes through.")
            .define("movingCabinLight", 6, 0, 15);
        builder.pop();

        builder.build();
    }

    public static void init(){
    }
}
