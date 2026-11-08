package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.cuffs.Handcuffs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De zintuigen van een Raven-soldaat (hoofdstuk 60, jouw wens: "het duurt even voor ze je zien", zoals in Hitman).
 *
 * Vroeger zag een soldaat je meteen, rondom en zo ver als zijn FOLLOW_RANGE (NearestAttackableTargetGoal). Nu kijkt hij
 * in een <b>kegel</b> voor zich uit ({@code [guards] viewAngle}, {@code viewRange}), niet door muren of rook, en vult
 * zich een <b>meter</b> (0-100) zolang hij een speler in beeld heeft:
 * <ul>
 *   <li>sneller als je dichtbij staat ({@code spotSecondsNear} vlak voor hem, {@code spotSecondsFar} aan de rand), als je
 *       rent ({@code sprintFactor}) of midden in zijn blik staat; trager als je sluipt ({@code sneakFactor}) of in zijn
 *       ooghoek staat ({@code peripheralFactor});</li>
 *   <li>een zoekende soldaat ziet je {@code searchFactor} keer zo snel, en na een alarm zijn alle bewakers van die basis
 *       {@code memoryMinutes} lang {@code memoryFactor} keer zo scherp (jouw antwoord 9: "ze onthouden het");</li>
 *   <li>uit het zicht zakt de meter in {@code decaySeconds} weer naar nul.</li>
 * </ul>
 * Bij {@code suspiciousAt} wordt hij <b>achterdochtig</b> en komt hij kijken (RavenInvestigateGoal); bij 100 heeft hij je:
 * dan is het <b>gevecht</b> (RavenCombatGoal), en sinds stap 4 roept hij eerst versterking. Raakt hij je in het gevecht
 * {@code lostSeconds} kwijt, dan gaat hij <b>zoeken</b> rond je laatste plek ({@code searchSeconds}) en daarna weer rustig
 * zijn route lopen. Geraakt worden of een maat die je al heeft (HurtByTargetGoal, alertSquad) is nog steeds meteen
 * gevecht.
 *
 * Wat je spel ervan ziet (het "?" en "!" boven zijn hoofd, het oogje op je scherm, ook zonder bril) gaat via {@link Watcher}.
 */
public final class RavenSenses {
    private final RavenSoldierEntity soldier;
    private RavenAlert state = RavenAlert.CALM;
    /** De meter (0-100). */
    private float suspicion;
    /** De speler die de meter vult, of null. */
    private @Nullable Player suspect;
    /** Waar hij zijn doelwit (of de speler die hem achterdochtig maakt) het laatst zag. */
    private @Nullable Vec3 lastSeen;
    /** Waar hij heen gaat om te kijken (achterdochtig), of het midden van zijn zoektocht (zoekend). */
    private @Nullable Vec3 lookAt;
    /** Na een half radiogesprek (stap 4) komt hij langzaam kijken. */
    private boolean slow;
    /** Gevecht: zoveel ticks zag hij zijn doelwit al niet meer. */
    private int lostTicks;
    /** Zoekend: tot deze speltijd. */
    private long searchUntil;
    /** Tot deze speltijd is hij extra scherp (na een alarm). */
    private long memoryUntil;
    /** Roepen via de radio (stap 4): hoe lang hij al bezig is (ticks), en of het om een lichaam gaat (stap 5). */
    private int callTicks;
    private boolean callingBody;
    /** Het lichaam dat hij gaat melden (stap 5, zie Bodies), of null. */
    private @Nullable net.minecraft.world.entity.Entity bodyToReport;
    /** De ingang van zijn site (RavenSites), opgezocht als het nodig is. */
    private @Nullable net.minecraft.core.BlockPos siteEntry;
    private long siteCheckedAt = -1_000_000;

    public RavenSenses(RavenSoldierEntity soldier) {
        this.soldier = soldier;
    }

    /** Wie hij nu ziet, en hoe snel die zijn meter vult (per tick). */
    private record Sighting(Player player, double rate) {}

    // ================================================================ Elke tick

    public void tick(ServerLevel level) {
        if (Handcuffs.isCuffed(soldier)) {
            // Geboeid (hoofdstuk 48): hij doet niets meer, en zijn meter zakt. Was hij aan het roepen, dan was dat een half gesprek.
            interrupted(level);
            decay();
            if (state != RavenAlert.CALM) {
                state = RavenAlert.CALM;
                lookAt = null;
            }
            sync();
            return;
        }
        LivingEntity target = soldier.getTarget();
        if (state == RavenAlert.CALLING) {
            if (target != null && target.isAlive()) {
                interrupted(level); // geraakt: hij vecht, en zijn maten hoorden een half gesprek
            } else {
                calling(level);
                sync();
                return;
            }
        }
        if (target != null && target.isAlive()) {
            combat(level, target);
            sync();
            return;
        }
        if (state == RavenAlert.COMBAT) {
            // Zijn doelwit is weg (dood, verdwenen, of hij raakte het kwijt): zoeken waar hij het het laatst zag.
            startSearching(level, lastSeen != null ? lastSeen : soldier.position());
        }
        Sighting best = look(level);
        if (best != null) {
            suspect = best.player();
            lastSeen = best.player().position();
            suspicion = Math.min(100.0f, suspicion + (float) best.rate());
            if (suspicion >= 100.0f) {
                spotted(level, best.player());
            } else if (suspicion >= ImtConfig.Guards.SUSPICIOUS_AT) {
                if (state == RavenAlert.CALM) {
                    state = RavenAlert.SUSPICIOUS;
                    slow = false;
                }
                if (state == RavenAlert.SUSPICIOUS) {
                    lookAt = best.player().position();
                }
            }
        } else {
            decay();
        }
        // Lichamen (stap 5): een gevallen maat, een geboeide, een gevloerde robot. Hij gaat erheen en meldt het.
        if (state != RavenAlert.COMBAT && soldier.tickCount % 10 == 0) {
            lookForBodies(level);
        }
        if (state == RavenAlert.SUSPICIOUS && bodyToReport != null) {
            if (!Bodies.isBody(bodyToReport)) {
                // Weg (weggesleept en verdwenen), of de robot staat weer op: niets te melden.
                Bodies.markReported(bodyToReport, false);
                bodyToReport = null;
            } else if (soldier.distanceTo(bodyToReport) < 3.5) {
                startCall(true);
            } else {
                lookAt = bodyToReport.position();
            }
        }
        if (state == RavenAlert.SEARCHING && level.getGameTime() >= searchUntil) {
            calmDown();
        }
        sync();
    }

    /** Ziet hij een lichaam dat nog niemand meldde, en is er nog geen alarm? Dan gaat hij erheen om het te melden. */
    private void lookForBodies(ServerLevel level) {
        if (bodyToReport != null) {
            return;
        }
        net.minecraft.world.entity.Entity body = Bodies.visible(soldier, ImtConfig.Guards.VIEW_RANGE, ImtConfig.Guards.VIEW_ANGLE / 2.0);
        if (body == null || !RavenSites.shouldCall(level, soldier)) {
            return;
        }
        Bodies.markReported(body, true);
        bodyToReport = body;
        state = RavenAlert.SUSPICIOUS;
        slow = false;
        lookAt = body.position();
        suspicion = Math.max(suspicion, ImtConfig.Guards.SUSPICIOUS_AT);
        soldier.getLookControl().setLookAt(body);
    }

    /** In het gevecht: ziet hij zijn doelwit nog? Zo niet, dan raakt hij het na een tijdje kwijt en gaat hij zoeken. */
    private void combat(ServerLevel level, LivingEntity target) {
        state = RavenAlert.COMBAT;
        suspicion = 100.0f;
        suspect = target instanceof Player player ? player : null;
        boolean sees = soldier.hasLineOfSight(target) && soldier.distanceTo(target) <= ImtConfig.Guards.VIEW_RANGE * 1.5;
        if (sees) {
            lostTicks = 0;
            lastSeen = target.position();
            if (soldier.tickCount % 20 == 0) {
                RavenSites.touch(level, soldier, lastSeen); // het alarm van zijn site loopt door zolang er contact is
            }
        } else if (++lostTicks >= Math.round(ImtConfig.Guards.LOST_SECONDS * 20)) {
            soldier.setTarget(null);
            startSearching(level, lastSeen != null ? lastSeen : target.position());
        }
    }

    /** De meter zakt (uit het zicht). */
    private void decay() {
        if (suspicion <= 0) {
            return;
        }
        suspicion = Math.max(0.0f, suspicion - 100.0f / (Math.max(0.5f, ImtConfig.Guards.DECAY_SECONDS) * 20.0f));
        if (suspicion <= 0) {
            suspect = null;
        }
    }

    // ================================================================ Kijken

    /**
     * De speler in zijn kegel die zijn meter het snelst vult, met die snelheid (per tick). Spelers in creative of
     * spectator ziet hij niet (die vielen ook vroeger buiten zijn doelwitten).
     */
    private @Nullable Sighting look(ServerLevel level) {
        double range = ImtConfig.Guards.VIEW_RANGE;
        double half = ImtConfig.Guards.VIEW_ANGLE / 2.0;
        Vec3 eye = soldier.getEyePosition();
        Vec3 facing = Vec3.directionFromRotation(0, soldier.getYHeadRot());
        Sighting best = null;
        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            double distance = soldier.distanceTo(player);
            if (distance > range) {
                continue;
            }
            double angle = flatAngle(facing, player.getEyePosition().subtract(eye));
            if (angle > half) {
                continue;
            }
            if (!soldier.hasLineOfSight(player)) {
                continue;
            }
            double rate = rate(level, player, distance, angle > half * 0.5);
            if (best == null || rate > best.rate()) {
                best = new Sighting(player, rate);
            }
        }
        return best;
    }

    /** Hoeveel de meter per tick stijgt voor deze speler, op deze afstand (zie de uitleg bovenaan). */
    private double rate(ServerLevel level, Player player, double distance, boolean peripheral) {
        double seconds = Mth.lerp(Mth.clamp(distance / ImtConfig.Guards.VIEW_RANGE, 0.0, 1.0),
                ImtConfig.Guards.SPOT_SECONDS_NEAR, ImtConfig.Guards.SPOT_SECONDS_FAR);
        double rate = 100.0 / (Math.max(0.1, seconds) * 20.0);
        if (peripheral) {
            rate *= ImtConfig.Guards.PERIPHERAL_FACTOR;
        }
        if (player.isSprinting()) {
            rate *= ImtConfig.Guards.SPRINT_FACTOR;
        } else if (player.isShiftKeyDown() || player.isCrouching()) {
            rate *= ImtConfig.Guards.SNEAK_FACTOR;
        }
        if (state == RavenAlert.SEARCHING) {
            rate *= ImtConfig.Guards.SEARCH_FACTOR;
        } else if (state == RavenAlert.SUSPICIOUS) {
            rate *= 1.5;
        }
        if (level.getGameTime() < memoryUntil) {
            rate *= ImtConfig.Guards.MEMORY_FACTOR;
        }
        return rate;
    }

    /** De hoek (graden) tussen twee richtingen, plat gemeten (omhoog of omlaag kijken telt niet). */
    static double flatAngle(Vec3 a, Vec3 b) {
        double la = Math.sqrt(a.x * a.x + a.z * a.z);
        double lb = Math.sqrt(b.x * b.x + b.z * b.z);
        if (la < 1.0e-6 || lb < 1.0e-6) {
            return 0;
        }
        double cos = (a.x * b.x + a.z * b.z) / (la * lb);
        return Math.toDegrees(Math.acos(Mth.clamp(cos, -1.0, 1.0)));
    }

    // ================================================================ Van de ene toestand naar de andere

    /**
     * Zijn meter is vol: hij heeft je. Is er nog geen alarm en vecht er nog niemand, dan roept hij eerst via de radio
     * (stap 4, jouw antwoord 4): één seconde de radio pakken, dan praten; pas daarna schiet hij. Anders doet hij meteen mee.
     */
    private void spotted(ServerLevel level, Player player) {
        suspicion = 100.0f;
        suspect = player;
        lastSeen = player.position();
        lostTicks = 0;
        if (RavenSites.shouldCall(level, soldier)) {
            startCall(false);
        } else {
            engage(player);
        }
    }

    /**
     * Hij had iemand op zijn rug voor een sluipaanval, maar kwam los (8 november, Takedown): hij draait zich om naar die
     * speler en heeft hem meteen; is er nog geen alarm, dan roept hij dus via de radio.
     */
    public void escaped(ServerLevel level, Player player) {
        float yaw = (float) (Mth.atan2(player.getZ() - soldier.getZ(), player.getX() - soldier.getX()) * Mth.RAD_TO_DEG) - 90.0f;
        soldier.setYRot(yaw);
        soldier.setYBodyRot(yaw);
        soldier.setYHeadRot(yaw);
        spotted(level, player);
        sync();
    }

    /** Hij begint te roepen: over jou, of over een lichaam (stap 5). */
    private void startCall(boolean body) {
        state = RavenAlert.CALLING;
        callingBody = body;
        callTicks = 0;
        soldier.getNavigation().stop();
    }

    /** Het gevecht (RavenCombatGoal). */
    private void engage(LivingEntity target) {
        state = RavenAlert.COMBAT;
        lostTicks = 0;
        callTicks = 0;
        soldier.setTarget(target);
    }

    /** Hij staat stil en roept: na {@code radioGrabSeconds} heeft hij zijn radio, na {@code radioCallSeconds} is het gesprek af. */
    private void calling(ServerLevel level) {
        soldier.getNavigation().stop();
        if (callingBody && bodyToReport != null) {
            soldier.getLookControl().setLookAt(bodyToReport);
        } else if (suspect != null && suspect.isAlive()) {
            soldier.getLookControl().setLookAt(suspect, 30.0f, 30.0f);
            if (soldier.hasLineOfSight(suspect)) {
                lastSeen = suspect.position();
            }
        }
        callTicks++;
        int grab = grabTicks();
        if (callTicks == grab) {
            RavenRadio.grab(level, soldier);
        }
        if (callTicks < Math.max(grab + 1, Math.round(ImtConfig.Guards.RADIO_CALL_SECONDS * 20))) {
            return;
        }
        // Afgemaakt: alarm voor de hele site (of zijn maten in de buurt), en dan het gevecht (of zoeken, bij een lichaam).
        boolean body = callingBody;
        Player target = (!body || suspicion >= 100.0f) && suspect != null && suspect.isAlive() && !suspect.isCreative()
                && !suspect.isSpectator() ? suspect : null;
        Vec3 where = body && bodyToReport != null ? bodyToReport.position() : lastSeen != null ? lastSeen : soldier.position();
        bodyToReport = null;
        callingBody = false;
        RavenSites.raiseAlarm(level, soldier, target, where, body);
        if (target != null) {
            engage(target);
        } else {
            startSearching(level, where);
        }
    }

    private static int grabTicks() {
        return Math.max(1, Math.round(ImtConfig.Guards.RADIO_GRAB_SECONDS * 20));
    }

    /** Hoe ver hij is met roepen (ticks), voor de tests. */
    public int callTicks() {
        return callTicks;
    }

    /**
     * Hij is gestopt met roepen voor hij klaar was (dood, geboeid, geraakt). Had hij zijn radio al vast, dan hoorden
     * zijn maten een half gesprek (RavenSites.halfCall): ze worden achterdochtig en komen langzaam kijken.
     */
    public void interrupted(ServerLevel level) {
        if (state != RavenAlert.CALLING) {
            return;
        }
        boolean heard = callTicks >= grabTicks();
        state = soldier.getTarget() != null ? RavenAlert.COMBAT : RavenAlert.SUSPICIOUS;
        callTicks = 0;
        if (callingBody && bodyToReport != null) {
            Bodies.markReported(bodyToReport, false); // een ander mag het alsnog melden
        }
        callingBody = false;
        bodyToReport = null;
        if (state == RavenAlert.SUSPICIOUS) {
            lookAt = lastSeen != null ? lastSeen : soldier.position();
        }
        if (heard) {
            RavenSites.halfCall(level, soldier);
        }
    }

    /**
     * Alarm van een maat, of het team dat binnenkomt (RavenSites): dit doelwit en deze laatste plek, en hij is een tijd
     * extra scherp. Zonder doelwit (de speler is weg) gaat hij zoeken op die plek.
     */
    public void alarmed(ServerLevel level, @Nullable LivingEntity target, Vec3 where, long memoryUntil) {
        remember(memoryUntil);
        if (Handcuffs.isCuffed(soldier)) {
            return;
        }
        lastSeen = where;
        if (target != null && target.isAlive() && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            suspect = target instanceof Player player ? player : null;
            suspicion = 100.0f;
            engage(target);
        } else if (state == RavenAlert.CALM || state == RavenAlert.SUSPICIOUS) {
            startSearching(level, where);
        }
    }

    /** De ingang van zijn site (RavenSites), of null; zolang er geen is, zoekt hij om de tien seconden opnieuw. */
    public @Nullable net.minecraft.core.BlockPos siteEntry(ServerLevel level) {
        long now = level.getGameTime();
        if (siteEntry == null && now - siteCheckedAt >= 200) {
            siteCheckedAt = now;
            siteEntry = RavenSites.findEntry(level, soldier.blockPosition());
        }
        return siteEntry;
    }

    /** Hij is je kwijt: zoeken rond {@code centre}, zo lang als {@code [guards] searchSeconds}. */
    public void startSearching(ServerLevel level, Vec3 centre) {
        state = RavenAlert.SEARCHING;
        lookAt = centre;
        lastSeen = centre;
        slow = false;
        lostTicks = 0;
        searchUntil = level.getGameTime() + Math.round(ImtConfig.Guards.SEARCH_SECONDS * 20);
        suspicion = Math.max(suspicion, ImtConfig.Guards.SUSPICIOUS_AT);
    }

    /** Weer rustig: terug naar zijn route of post. */
    public void calmDown() {
        state = RavenAlert.CALM;
        lookAt = null;
        slow = false;
        if (bodyToReport != null) {
            Bodies.markReported(bodyToReport, false);
            bodyToReport = null;
        }
        suspicion = Math.min(suspicion, ImtConfig.Guards.SUSPICIOUS_AT - 1);
    }

    /**
     * Er was iets op deze plek (lawaai, stap 3; een maat die halverwege zijn radiogesprek viel, stap 4): een rustige of
     * achterdochtige soldaat gaat er kijken ({@code slowly}: op zijn gemak), een zoekende zoekt voortaan daar, en in het
     * gevecht is het zijn nieuwe laatste plek als hij zijn doelwit nu niet ziet.
     */
    public void noticed(Vec3 where, boolean slowly) {
        switch (state) {
            case CALM, SUSPICIOUS -> {
                state = RavenAlert.SUSPICIOUS;
                lookAt = where;
                slow = slowly;
                suspicion = Math.max(suspicion, ImtConfig.Guards.SUSPICIOUS_AT);
            }
            case SEARCHING -> {
                lookAt = where;
                lastSeen = where;
            }
            case COMBAT, CALLING -> {
                if (lostTicks > 0) {
                    lastSeen = where;
                }
            }
        }
    }

    /**
     * Hij heeft rondgekeken op de plek waar hij heen ging (RavenInvestigateGoal). Ziet hij nog steeds iemand die zijn
     * meter boven de helft houdt, dan gaat hij daar kijken; anders is hij weer rustig.
     */
    public void doneLooking() {
        if (state != RavenAlert.SUSPICIOUS) {
            return;
        }
        if (bodyToReport != null) {
            startCall(true); // hij is bij het lichaam (of komt er niet bij): melden
            return;
        }
        if (suspect != null && suspect.isAlive() && suspicion >= ImtConfig.Guards.SUSPICIOUS_AT) {
            lookAt = suspect.position();
            slow = false;
        } else {
            calmDown();
        }
    }

    /** Lawaai (stap 3, zie Noises): hij hoorde iets op deze plek en kijkt er meteen heen; rustig gaat hij kijken. */
    public void heard(Vec3 at, Noises.Kind kind) {
        noticed(at, false);
        if (state == RavenAlert.SUSPICIOUS || state == RavenAlert.SEARCHING) {
            soldier.getLookControl().setLookAt(at.x, at.y + 1.0, at.z);
        }
    }

    /** Na een alarm (stap 4): hij is een tijd extra scherp. */
    public void remember(long until) {
        memoryUntil = Math.max(memoryUntil, until);
    }

    // ================================================================ Wat anderen willen weten

    public RavenAlert state() {
        return state;
    }

    public float suspicion() {
        return suspicion;
    }

    public @Nullable Player suspect() {
        return suspect;
    }

    public @Nullable Vec3 lastSeen() {
        return lastSeen;
    }

    public @Nullable Vec3 lookAt() {
        return lookAt;
    }

    /** Komt hij langzaam kijken (na een half radiogesprek)? */
    public boolean slow() {
        return slow;
    }

    public long memoryUntil() {
        return memoryUntil;
    }

    /** Opgeslagen met de soldaat (zie RavenSoldierEntity): alleen wat de wereld moet onthouden. */
    public void setMemoryUntil(long until) {
        memoryUntil = until;
    }

    /** Naar je spel: de toestand, de meter en wie hem vult (voor het "?"/"!" en het oogje op je scherm). */
    private void sync() {
        soldier.syncAlert(state, Math.round(suspicion), suspect == null || suspicion <= 0 ? -1 : suspect.getId());
    }
}
