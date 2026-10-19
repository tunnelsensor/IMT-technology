package com.senne.imttech.training;

import net.minecraft.nbt.CompoundTag;

/**
 * Het rapport van één training (hoofdstuk 26): wat I.M.T na afloop zegt, en wat de kern bewaart voor de app
 * Training (de laatste {@code maxReports}).
 *
 * @param name    de naam van de training
 * @param trainee wie de trainee was
 * @param mode    trainingsschoten of echt
 * @param result  hoe hij afliep
 * @param ticks   hoe lang hij duurde (zonder het aftellen)
 * @param hits    hoe vaak de trainee geraakt werd
 * @param lives   hoeveel levens hij over had
 * @param hitsBy  wie hem raakte: "Guard 2 x3, Security turret x1" (leeg = niemand)
 * @param downed  hoeveel robots en drones er neergingen (de Ion Blaster)
 * @param when    wanneer hij voorbij was (speltijd)
 * @param kind    een gewone training, of (hoofdstuk 27) een inbraak- of uitbraakoefening
 * @param closest bij een inbraak: hoe dicht hij bij de kern kwam (blokken); anders -1
 */
public record TrainingReport(String name, String trainee, Training.Mode mode, Training.Result result, long ticks, int hits,
                             int lives, String hitsBy, int downed, long when, Training.Kind kind, int closest) {

    /** Een rapport van een gewone training (zoals vóór hoofdstuk 27). */
    public TrainingReport(String name, String trainee, Training.Mode mode, Training.Result result, long ticks, int hits,
                          int lives, String hitsBy, int downed, long when) {
        this(name, trainee, mode, result, ticks, hits, lives, hitsBy, downed, when, Training.Kind.TRAINING, -1);
    }

    /**
     * In één zin (Engels): "Training Bootcamp: Senne reached the goal after 2:14 (3 hits: Guard 2 x3; 4 robots down)", of
     * (hoofdstuk 27) "Break-in drill Vault: Senne hacked the core after 4:12 (closest 0 blocks from the core; 1 hit: ...)".
     */
    public String line() {
        StringBuilder text = new StringBuilder(kind.title()).append(' ').append(name).append(": ").append(trainee).append(' ')
                .append(result.text()).append(" after ").append(time(ticks)).append(" (");
        if (closest >= 0) {
            text.append("closest ").append(closest).append(closest == 1 ? " block" : " blocks").append(" from the core; ");
        }
        text.append(hits).append(hits == 1 ? " hit" : " hits");
        if (!hitsBy.isEmpty()) {
            text.append(": ").append(hitsBy);
        }
        if (downed > 0) {
            text.append("; ").append(downed).append(downed == 1 ? " robot down" : " robots down");
        }
        return text.append(')').toString();
    }

    /** Ticks als minuten:seconden ("2:14"). */
    public static String time(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Trainee", trainee);
        tag.putString("Mode", mode.name());
        tag.putString("Result", result.name());
        tag.putLong("Ticks", ticks);
        tag.putInt("Hits", hits);
        tag.putInt("Lives", lives);
        tag.putString("HitsBy", hitsBy);
        tag.putInt("Downed", downed);
        tag.putLong("When", when);
        tag.putString("Kind", kind.name());
        tag.putInt("Closest", closest);
        return tag;
    }

    public static TrainingReport load(CompoundTag tag) {
        Training.Result result;
        try {
            result = Training.Result.valueOf(tag.getString("Result"));
        } catch (IllegalArgumentException e) {
            result = Training.Result.STOPPED;
        }
        // Een rapport van vóór hoofdstuk 27 heeft geen soort (een gewone training) en geen afstand.
        return new TrainingReport(tag.getString("Name"), tag.getString("Trainee"), Training.Mode.byName(tag.getString("Mode")),
                result, tag.getLong("Ticks"), tag.getInt("Hits"), tag.getInt("Lives"), tag.getString("HitsBy"),
                tag.getInt("Downed"), tag.getLong("When"), Training.Kind.byName(tag.getString("Kind")),
                tag.contains("Closest") ? tag.getInt("Closest") : -1);
    }
}
