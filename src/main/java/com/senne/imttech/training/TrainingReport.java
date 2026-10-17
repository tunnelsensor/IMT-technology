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
 */
public record TrainingReport(String name, String trainee, Training.Mode mode, Training.Result result, long ticks, int hits,
                             int lives, String hitsBy, int downed, long when) {

    /** In één zin (Engels): "Training Bootcamp: Senne reached the goal in 2:14 (3 hits: Guard 2 x3; 4 robots down)". */
    public String line() {
        StringBuilder text = new StringBuilder("Training ").append(name).append(": ").append(trainee).append(' ')
                .append(result.text()).append(" after ").append(time(ticks)).append(" (")
                .append(hits).append(hits == 1 ? " hit" : " hits");
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
        return tag;
    }

    public static TrainingReport load(CompoundTag tag) {
        Training.Result result;
        try {
            result = Training.Result.valueOf(tag.getString("Result"));
        } catch (IllegalArgumentException e) {
            result = Training.Result.STOPPED;
        }
        return new TrainingReport(tag.getString("Name"), tag.getString("Trainee"), Training.Mode.byName(tag.getString("Mode")),
                result, tag.getLong("Ticks"), tag.getInt("Hits"), tag.getInt("Lives"), tag.getString("HitsBy"),
                tag.getInt("Downed"), tag.getLong("When"));
    }
}
