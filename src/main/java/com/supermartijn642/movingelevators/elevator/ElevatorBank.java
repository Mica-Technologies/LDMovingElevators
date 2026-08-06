package com.supermartijn642.movingelevators.elevator;

import java.util.List;

/**
 * Picks which car of a bank answers a destination request.
 * <p>
 * Deliberately stateless and outside {@link ElevatorGroup}: a bank is not a thing elevators belong
 * to, it is a view a lobby panel takes of several of them. Elevators keep working alone, and none of
 * them knows it is in a bank -- which is what stops banking from being a mode the rest of the mod has
 * to reason about.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class ElevatorBank {

    /**
     * A car already coming to this landing for somebody travelling the same way is not merely the
     * best answer, it is the answer -- sharing the trip is the whole reason to have a bank. Nothing
     * else can score below this.
     */
    private static final double SHARE_SCORE = -1000;
    /** A car that is mid-trip has to finish before it starts yours. Heading away is worse than towards. */
    private static final double MOVING_TOWARDS_PENALTY = 8, MOVING_AWAY_PENALTY = 32;
    /** Each call already queued is another stop between the request and the answer. */
    private static final double QUEUED_CALL_PENALTY = 4;

    /** Which car was chosen, and which of its floors it is collecting from. */
    public static final class Assignment {

        public final ElevatorGroup group;
        public final int pickupY;

        private Assignment(ElevatorGroup group, int pickupY){
            this.group = group;
            this.pickupY = pickupY;
        }
    }

    /**
     * Chooses the car that should collect from {@code panelY} and carry the passenger to
     * {@code destinationY}.
     *
     * @param groups the elevators this lobby panel is bound to
     * @return the assignment, or null if no bound elevator serves both floors
     */
    public static Assignment pick(List<ElevatorGroup> groups, int panelY, int destinationY){
        Assignment best = null;
        double bestScore = Double.MAX_VALUE;
        for(ElevatorGroup group : groups){
            // A halted car is not a slow car, it is one that is not coming. Sending somebody to wait
            // for it would be worse than telling them no elevator is available.
            if(group == null || group.isEmergencyStopped() || group.getFloorNumber(destinationY) == -1)
                continue;
            int pickupY = nearestFloorY(group, panelY);
            // A car that does not stop at this landing cannot collect from it, and one whose only
            // stop here is the destination itself has nothing to do.
            if(pickupY == Integer.MIN_VALUE || pickupY == destinationY)
                continue;
            double score = score(group, pickupY, destinationY);
            if(score < bestScore){
                bestScore = score;
                best = new Assignment(group, pickupY);
            }
        }
        return best;
    }

    /**
     * The floor of this elevator nearest the panel, so a panel hung a block above or below a landing
     * still speaks for that landing rather than for nothing.
     */
    private static int nearestFloorY(ElevatorGroup group, int panelY){
        int best = Integer.MIN_VALUE, bestDistance = Integer.MAX_VALUE;
        for(int floor = 0; floor < group.getFloorCount(); floor++){
            int y = group.getFloorYLevel(floor);
            int distance = Math.abs(y - panelY);
            if(distance < bestDistance){
                bestDistance = distance;
                best = y;
            }
        }
        return best;
    }

    /** Lower is better. */
    private static double score(ElevatorGroup group, int pickupY, int destinationY){
        boolean up = destinationY > pickupY;
        if(group.hasBankedCallFrom(pickupY, up))
            return SHARE_SCORE;

        double score = Math.abs(group.getCurrentY() - pickupY);
        if(group.isMoving()){
            int travel = group.getTravelDirection();
            boolean towards = travel == 0 || travel == (int)Math.signum(pickupY - group.getCurrentY());
            score += towards ? MOVING_TOWARDS_PENALTY : MOVING_AWAY_PENALTY;
        }
        score += group.getCallQueue().size() * QUEUED_CALL_PENALTY;
        return score;
    }
}
