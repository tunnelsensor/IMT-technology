package com.senne.starktech.training;

import com.senne.starktech.security.SecurityCoreBlockEntity;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * De app Training op de desktop (hoofdstuk 26), aan de kant van de server: wat hij laat zien (de training die nu bezig
 * is, en de rapporten), en de knop Stop.
 */
public final class Trainings {
    private Trainings() {}

    /** Voor de desktop: "now" (de training bij deze kern, of leeg) en "reports" (de nieuwste eerst). */
    public static CompoundTag snapshot(ServerLevel level, SecurityCoreBlockEntity core) {
        CompoundTag data = new CompoundTag();
        Training.Session session = Training.at(level, core.getBlockPos());
        if (session != null) {
            CompoundTag now = new CompoundTag();
            now.putString("name", session.settings().name());
            now.putString("trainee", session.traineeName());
            now.putString("mode", session.settings().mode().title());
            now.putBoolean("running", session.running());
            now.putInt("lives", session.lives());
            now.putInt("max", session.settings().lives());
            now.putInt("hits", session.hits());
            now.putLong("ticks", session.ticks(level.getGameTime()));
            now.putInt("limit", session.settings().seconds());
            data.put("now", now);
        }
        ListTag reports = new ListTag();
        List<TrainingReport> list = core.trainingReports();
        for (int i = list.size() - 1; i >= 0; i--) {
            TrainingReport report = list.get(i);
            CompoundTag tag = report.save();
            tag.putBoolean("passed", report.result().passed());
            tag.putString("text", report.result().text());
            reports.add(tag);
        }
        data.put("reports", reports);
        return data;
    }

    /** De knop Stop: de training bij deze kern stopt. Geeft de melding voor de taakbalk. */
    public static String stop(ServerPlayer player, SecurityCoreBlockEntity core) {
        Training.Session session = Training.at(player.serverLevel(), core.getBlockPos());
        if (session == null) {
            return "No training is running";
        }
        Training.end(player.serverLevel(), session, Training.Result.STOPPED);
        return "Training " + session.settings().name() + " stopped";
    }
}
