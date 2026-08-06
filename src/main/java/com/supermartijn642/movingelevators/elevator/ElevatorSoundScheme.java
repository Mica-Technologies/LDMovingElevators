package com.supermartijn642.movingelevators.elevator;

import com.supermartijn642.movingelevators.MovingElevators;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Which sound an elevator makes for a given moment, and how loud.
 * <p>
 * A scheme is a complete set of answers, so adding the selectable schemes planned for later means
 * writing another set rather than editing call sites. Everything that makes a noise asks a scheme
 * instead of naming a sound, which is also what keeps volumes and pitches consistent across the
 * blocks -- they were drifting apart as literals at each call.
 * <p>
 * {@link Moment} is deliberately wider than what is currently filled in. A moment with no entry is
 * silent, so the events an elevator <em>could</em> announce are written down in one place, and giving
 * one a sound later is filling a slot rather than finding where it would go.
 * <p>
 * Created for the Mica Technologies fork.
 */
public enum ElevatorSoundScheme {

    STANDARD {
        @Override
        protected void fill(Map<Moment,Entry> entries){
            // A mellow single bell as each floor goes by -- present, but well under the arrival so
            // passing a floor never sounds like getting to one.
            entries.put(Moment.PASSING_FLOOR, new Entry(() -> MovingElevators.passing_floor_sound, 0.45f, 0.8f));
            // The car settling, not a chime: a short hydraulic thump under the two notes.
            entries.put(Moment.ARRIVED, new Entry(() -> MovingElevators.arrive_sound, 0.35f, 0.55f));
            // Ding-dong, and the interval has to be wide to survive the sample. The bell rings for
            // well over a second, so a second strike close to the first in pitch lands inside its
            // tail and the pair reads as one note -- which a falling fourth did. Nearly an octave
            // apart, and the low note is audibly slower, so the two stay distinct.
            entries.put(Moment.ARRIVAL_CHIME, new Entry(() -> MovingElevators.arrive_ding_sound, 1f, 1.6f));
            entries.put(Moment.ARRIVAL_CHIME_SECOND, new Entry(() -> MovingElevators.arrive_ding_sound, 1f, 0.9f));
            // Pitched down from the piston default, since a big sliding door should sound heavier and
            // slower than a block being shoved.
            entries.put(Moment.DOORS_OPENING, new Entry(() -> MovingElevators.door_open_sound, 0.45f, 0.8f));
            entries.put(Moment.DOORS_CLOSING, new Entry(() -> MovingElevators.door_close_sound, 0.45f, 0.75f));
            // DEPARTING, CALL_ACCEPTED and OBSTRUCTED are intentionally silent for now.
        }
    };

    /** Something an elevator can announce. Not all of them do yet. */
    public enum Moment {
        DEPARTING,
        PASSING_FLOOR,
        ARRIVED,
        ARRIVAL_CHIME,
        /** The second note of the arrival chime, so a scheme can make it a ding-dong or a single note. */
        ARRIVAL_CHIME_SECOND,
        DOORS_OPENING,
        DOORS_CLOSING,
        CALL_ACCEPTED,
        OBSTRUCTED
    }

    /** The sound is a supplier because registry entries are not populated when a scheme is built. */
    public static final class Entry {

        private final Supplier<SoundEvent> sound;
        private final float volume, pitch;

        public Entry(Supplier<SoundEvent> sound, float volume, float pitch){
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private Map<Moment,Entry> entries;

    protected abstract void fill(Map<Moment,Entry> entries);

    private Map<Moment,Entry> entries(){
        if(this.entries == null){
            Map<Moment,Entry> built = new EnumMap<>(Moment.class);
            this.fill(built);
            this.entries = built;
        }
        return this.entries;
    }

    /**
     * Plays this scheme's sound for a moment, if it has one. Server side only -- the server tells
     * nearby clients, so the sound reaches everyone rather than only whoever pressed the button.
     *
     * @return whether anything was played
     */
    public boolean play(World level, Vec3d pos, Moment moment){
        if(level == null || level.isRemote)
            return false;
        Entry entry = this.entries().get(moment);
        if(entry == null)
            return false;
        SoundEvent sound = entry.sound.get();
        if(sound == null)
            return false;
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.BLOCKS, entry.volume, entry.pitch);
        return true;
    }

    /**
     * Ticks between the two notes of the arrival chime. A scheme's business, not the elevator's:
     * how far apart the notes have to be to read as two depends entirely on how long its chime
     * sample rings for.
     */
    public int chimeGapTicks(){
        return 11;
    }

    /**
     * The scheme in use. A per-elevator choice later; one global answer while there is one scheme.
     */
    public static ElevatorSoundScheme current(){
        return STANDARD;
    }
}
