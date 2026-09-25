package com.senne.starktech.client.dev;

import com.senne.starktech.StarkTech;
import com.senne.starktech.client.EdithClientEvents;
import com.senne.starktech.edith.EdithAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een draaiboek: een rij stappen die het spel tick voor tick afwerkt, alsof iemand achter het
 * toetsenbord zit. Elke stap wacht eerst een aantal ticks (20 ticks = 1 seconde) en doet dan
 * iets: een toets indrukken, de speler verplaatsen, een scherm openen, een screenshot maken...
 *
 * Alleen voor tijdens het ontwikkelen: de screenshot-rondleiding (VisualCheck) en de showcase
 * gebruiken dit.
 */
public final class DevScript {
    /**
     * Eén stap: eerst zoveel ticks wachten, dan iets doen. Met een {@code until} wacht de stap
     * daarna ook nog tot dat waar is (maar hooguit {@code maxTicks}), zie waitUntil.
     */
    private record Step(int waitTicks, Runnable action, @Nullable BooleanSupplier until, int maxTicks) {
        Step(int waitTicks, Runnable action) {
            this(waitTicks, action, null, 0);
        }
    }

    private final List<Step> steps = new ArrayList<>();
    private int index;
    private int waiting;
    private int held;
    private boolean started;

    /** Voert elke tick een stukje uit. Geeft true terug als alles gedaan is. */
    public boolean tick() {
        if (!started) {
            started = true;
            waiting = steps.isEmpty() ? 0 : steps.getFirst().waitTicks();
        }
        if (index >= steps.size()) {
            return true;
        }
        if (waiting > 0) {
            waiting--;
            return false;
        }
        Step step = steps.get(index);
        // Een wachtstap: hier blijven tot het zover is, of tot het te lang duurt.
        if (step.until() != null && held < step.maxTicks() && !step.until().getAsBoolean()) {
            held++;
            return false;
        }
        held = 0;
        index++;
        step.action().run();
        waiting = index < steps.size() ? steps.get(index).waitTicks() : 0;
        return index >= steps.size();
    }

    public boolean isDone() {
        return started && index >= steps.size();
    }

    /** Hoe lang het hele draaiboek duurt, in ticks (alle wachttijden samen). */
    public int totalTicks() {
        int total = 0;
        for (Step step : steps) {
            // Van een wachtstap weten we niet hoe lang hij duurt: we rekenen de helft van het maximum.
            total += step.waitTicks() + 1 + step.maxTicks() / 2;
        }
        return total;
    }

    // ================================================================ Bouwstenen

    public DevScript step(int waitTicks, Runnable action) {
        steps.add(new Step(waitTicks, action));
        return this;
    }

    /** Alleen wachten. */
    public DevScript pause(int waitTicks) {
        return step(waitTicks, () -> {});
    }

    /**
     * Wachten tot iets zo is, bijvoorbeeld tot een drone geland is. Duurt het langer dan
     * {@code maxTicks}, dan gaat het draaiboek toch verder (anders kan het eeuwig blijven hangen).
     */
    public DevScript waitUntil(int maxTicks, BooleanSupplier condition) {
        steps.add(new Step(0, () -> {}, condition, maxTicks));
        return this;
    }

    /** Een screenshot in de map screenshots van het spel. */
    public DevScript shot(int waitTicks, String name) {
        return step(waitTicks, () -> {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                    message -> StarkTech.LOGGER.info("[dev] {}", message.getString()));
        });
    }

    public DevScript open(int waitTicks, Supplier<Screen> screen) {
        return step(waitTicks, () -> Minecraft.getInstance().setScreen(screen.get()));
    }

    public DevScript close(int waitTicks) {
        return step(waitTicks, () -> Minecraft.getInstance().setScreen(null));
    }

    public DevScript camera(int waitTicks, CameraType type) {
        return step(waitTicks, () -> Minecraft.getInstance().options.setCameraType(type));
    }

    /** Een grote titel midden in beeld, zoals bij /title. */
    public DevScript title(int waitTicks, String title, String subtitle) {
        return step(waitTicks, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTimes(5, 50, 15);
            mc.gui.setSubtitle(Component.literal(subtitle));
            mc.gui.setTitle(Component.literal(title));
        });
    }

    /** Een E.D.I.T.H.-actie, precies zoals een sneltoets of het G-menu die verstuurt. */
    public DevScript action(int waitTicks, EdithAction action) {
        return step(waitTicks, () -> EdithClientEvents.sendAction(action));
    }

    /** Een toets indrukken of loslaten, zoals een speler dat doet. */
    public DevScript key(int waitTicks, Supplier<KeyMapping> key, boolean down) {
        return step(waitTicks, () -> key.get().setDown(down));
    }

    /** Een toets kort aantikken (een klik). */
    public DevScript tap(int waitTicks, Supplier<KeyMapping> key) {
        return key(waitTicks, key, true).key(2, key, false);
    }

    /** Kijkrichting van de speler zetten (yaw 0 = zuid, pitch + = omlaag). */
    public DevScript look(int waitTicks, float yaw, float pitch) {
        return step(waitTicks, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.setYRot(yaw);
                player.setXRot(pitch);
                player.yRotO = yaw;
                player.xRotO = pitch;
            }
        });
    }

    /** Kijk naar een punt in de wereld (het midden van een blok, of een wezen). */
    public DevScript lookAt(int waitTicks, Vec3 target) {
        return step(waitTicks, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
                player.yRotO = player.getYRot();
                player.xRotO = player.getXRot();
            }
        });
    }

    /** Zet de speler op een plek en laat hem daar (in creative) zweven. */
    public DevScript view(int waitTicks, double x, double y, double z, float yaw, float pitch) {
        return onServer(waitTicks, player -> {
            player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
            // Wie in creative vliegend de grond raakt, stopt met vliegen; dan zou je hier naar
            // beneden vallen en naar de verkeerde kant kijken.
            if (player.getAbilities().mayfly) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
        });
    }

    /** Zet de speler op de grond neer, zonder te laten vliegen. */
    public DevScript stand(int waitTicks, double x, double y, double z, float yaw, float pitch) {
        return onServer(waitTicks, player -> {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
        });
    }

    /** Iets doen op de server (in hetzelfde spel), met de echte speler daar. */
    public DevScript onServer(int waitTicks, Consumer<ServerPlayer> action) {
        return step(waitTicks, () -> DevWorld.onServer(action));
    }

    public DevScript command(int waitTicks, String command) {
        return step(waitTicks, () -> DevWorld.command(command));
    }

    /** Schrijft in de log waar de speler is en hoe snel hij gaat. */
    public DevScript probe(int waitTicks, String label) {
        return step(waitTicks, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            Vec3 v = player.getDeltaMovement();
            StarkTech.LOGGER.info(String.format(Locale.ROOT,
                    "[dev] %-16s y=%7.2f  v=(%5.2f, %5.2f, %5.2f)  |v|=%4.2f  aan=%s  grond=%s",
                    label, player.getY(), v.x, v.y, v.z, v.length(),
                    com.senne.starktech.client.SuitFlight.isEngaged(), player.onGround()));
        });
    }
}
