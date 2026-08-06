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
 * A scheme is a complete set of answers, so adding another one means writing another set rather than
 * editing call sites. Everything that makes a noise asks a scheme instead of naming a sound, which is
 * also what keeps volumes and pitches consistent across the blocks -- they were drifting apart as
 * literals at each call.
 * <p>
 * {@link Moment} is deliberately wider than what any scheme currently fills in. A moment with no entry
 * is silent, so the events an elevator <em>could</em> announce are written down in one place, and
 * giving one a sound later is filling a slot rather than finding where it would go.
 * <p>
 * Created for the Mica Technologies fork.
 */
public enum ElevatorSoundScheme {

    /**
     * Struck-bell chimes, in the vein of an older building. Arrival sounds the same whichever way the
     * car is about to go -- announcing direction is a modern habit.
     */
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
            // DEPARTING, CALL_ACCEPTED and OBSTRUCTED are intentionally silent.
        }
    },

    /**
     * Clean electronic chimes, and the arrival announces where the car is going next the way a modern
     * hall lantern does: rising for up, falling for down, and two flat notes when it is going nowhere.
     * <p>
     * That is the whole point of the direction being audible -- someone waiting on a landing can tell
     * from the next room whether the car that just arrived is the one they want, without watching the
     * indicator.
     */
    MODERN {
        @Override
        protected void fill(Map<Moment,Entry> entries){
            entries.put(Moment.PASSING_FLOOR, new Entry(() -> MovingElevators.modern_passing_floor_sound, 0.3f, 1.5f));
            // Modern gear stops quietly; the chime does the announcing, not the machinery.
            entries.put(Moment.ARRIVED, new Entry(() -> MovingElevators.arrive_sound, 0.25f, 0.7f));
            // Going nowhere: the same note twice, which is the plain "arrived" chime.
            entries.put(Moment.ARRIVAL_CHIME, new Entry(() -> MovingElevators.modern_chime_sound, 0.9f, 1.1f));
            entries.put(Moment.ARRIVAL_CHIME_SECOND, new Entry(() -> MovingElevators.modern_chime_sound, 0.9f, 1.1f));
            // Going up: low then high, so the pair rises.
            entries.put(Moment.ARRIVAL_CHIME_UP, new Entry(() -> MovingElevators.modern_chime_sound, 0.9f, 0.85f));
            entries.put(Moment.ARRIVAL_CHIME_UP_SECOND, new Entry(() -> MovingElevators.modern_chime_sound, 0.9f, 1.2f));
            // Going down: the same two notes the other way round, so the pair falls.
            entries.put(Moment.ARRIVAL_CHIME_DOWN, new Entry(() -> MovingElevators.modern_chime_sound, 0.9f, 1.2f));
            entries.put(Moment.ARRIVAL_CHIME_DOWN_SECOND, new Entry(() -> MovingElevators.modern_chime_sound, 0.9f, 0.85f));
            entries.put(Moment.DOORS_OPENING, new Entry(() -> MovingElevators.door_open_sound, 0.3f, 1.1f));
            entries.put(Moment.DOORS_CLOSING, new Entry(() -> MovingElevators.door_close_sound, 0.3f, 1.05f));
        }

        @Override
        public int chimeGapTicks(){
            // Tighter than Standard's: these chimes decay quickly, so the notes can sit closer
            // together without the second landing inside the first.
            return 7;
        }
    };

    /** Something an elevator can announce. Not all of them do yet. */
    public enum Moment {
        DEPARTING,
        PASSING_FLOOR,
        ARRIVED,
        /** Arrival with nothing else to serve. The pair is two flat notes. */
        ARRIVAL_CHIME,
        ARRIVAL_CHIME_SECOND,
        /** Arrival with somewhere to be above. A scheme that cares makes this pair rise. */
        ARRIVAL_CHIME_UP,
        ARRIVAL_CHIME_UP_SECOND,
        /** Arrival with somewhere to be below. A scheme that cares makes this pair fall. */
        ARRIVAL_CHIME_DOWN,
        ARRIVAL_CHIME_DOWN_SECOND,
        DOORS_OPENING,
        DOORS_CLOSING,
        CALL_ACCEPTED,
        OBSTRUCTED;

        /**
         * The chime for arriving when the car is next headed {@code direction} (1 up, -1 down, 0
         * nowhere). Callers pick a moment rather than a sound, so a scheme with one chime for every
         * direction and a scheme with three are both just tables.
         *
         * @param secondNote whether this is the second of the two notes
         */
        public static Moment arrivalChime(int direction, boolean secondNote){
            if(direction > 0)
                return secondNote ? ARRIVAL_CHIME_UP_SECOND : ARRIVAL_CHIME_UP;
            if(direction < 0)
                return secondNote ? ARRIVAL_CHIME_DOWN_SECOND : ARRIVAL_CHIME_DOWN;
            return secondNote ? ARRIVAL_CHIME_SECOND : ARRIVAL_CHIME;
        }
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
            // A scheme that does not distinguish direction gets the plain chime for both, so callers
            // can always ask for the directional moment without knowing whether this scheme cares.
            built.putIfAbsent(Moment.ARRIVAL_CHIME_UP, built.get(Moment.ARRIVAL_CHIME));
            built.putIfAbsent(Moment.ARRIVAL_CHIME_UP_SECOND, built.get(Moment.ARRIVAL_CHIME_SECOND));
            built.putIfAbsent(Moment.ARRIVAL_CHIME_DOWN, built.get(Moment.ARRIVAL_CHIME));
            built.putIfAbsent(Moment.ARRIVAL_CHIME_DOWN_SECOND, built.get(Moment.ARRIVAL_CHIME_SECOND));
            built.values().removeIf(entry -> entry == null);
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
        if(level == null || level.isRemote || moment == null)
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

    /** Translation key for this scheme's name, as shown in the elevator screen. */
    public String getNameTranslationKey(){
        return "movingelevators.sound_scheme." + this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public ElevatorSoundScheme next(){
        ElevatorSoundScheme[] schemes = values();
        return schemes[(this.ordinal() + 1) % schemes.length];
    }

    /** Falls back to {@link #STANDARD} for names from a save written by a version that had others. */
    public static ElevatorSoundScheme byName(String name){
        for(ElevatorSoundScheme scheme : values())
            if(scheme.name().equals(name))
                return scheme;
        return STANDARD;
    }
}
