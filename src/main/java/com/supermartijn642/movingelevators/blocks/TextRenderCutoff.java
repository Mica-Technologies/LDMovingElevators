package com.supermartijn642.movingelevators.blocks;

/**
 * How far away the text-only fixtures are still drawn, shared between their renderers, which stop
 * drawing past it, and their block entities, which tell Forge not to dispatch them past it.
 * <p>
 * The two have to agree. Before the block entities declared it, Forge did its per-entity setup for
 * every one of them out to its default 64 blocks, only for the renderer to return immediately.
 * <p>
 * Created for the Mica Technologies fork.
 */
public final class TextRenderCutoff {

    /** Font rendering is expensive, so the panels stop drawing beyond this many blocks. */
    public static final double PANEL = 30;
    /** The car panel's buttons are tiny, and it rides inside the cabin, so it stops sooner. */
    public static final double CAR_PANEL = 15;

    /**
     * Slack for Forge measuring from the player's feet while the renderers measure from the camera,
     * which sits at eye height and up to four blocks back in third person. Generous on purpose: the
     * renderer's own check stays the one that decides.
     */
    private static final double SLACK = 6;

    public static double squared(double distance){
        return distance * distance;
    }

    /** What a fixture with the given cutoff should return from {@code getMaxRenderDistanceSquared}. */
    public static double dispatchRangeSquared(double distance){
        return squared(distance + SLACK);
    }

    private TextRenderCutoff(){
    }
}
