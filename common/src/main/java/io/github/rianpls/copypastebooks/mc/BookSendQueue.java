package io.github.rianpls.copypastebooks.mc;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Paces outgoing book packets; both delays are configurable (settings / "/copypastebooks
 * delay ...").
 *
 * Survival (book-edit packets): some servers reject edits that arrive too quickly. The
 * next packet waits for both the configured wall-clock delay and the equivalent number
 * of ClientLevel game-time ticks. The latter is only a conservative second gate: the
 * client advances that clock locally between synchronizations, so it is not an exact
 * measurement of server TPS.
 *
 * Creative (creative-slot packets): no server-side rate limit, plain wall-clock pause.
 */
public final class BookSendQueue {

    private static final Deque<Runnable> SURVIVAL = new ArrayDeque<>();
    private static final Deque<Runnable> CREATIVE = new ArrayDeque<>();
    private static long survivalSentAt;
    private static long survivalSentGameTime = Long.MIN_VALUE;
    private static long creativeSentAt;

    private BookSendQueue() {
    }

    public static void enqueueSurvival(Runnable send) {
        synchronized (SURVIVAL) {
            SURVIVAL.add(send);
        }
    }

    public static void enqueueCreative(Runnable send) {
        synchronized (CREATIVE) {
            CREATIVE.add(send);
        }
    }

    /** Called once per client tick; sends at most one packet per queue per interval. */
    public static void tick() {
        Runnable creative = null;
        synchronized (CREATIVE) {
            if (!CREATIVE.isEmpty()
                    && System.currentTimeMillis() - creativeSentAt >= CPB.config().creativeDelayMs) {
                creativeSentAt = System.currentTimeMillis();
                creative = CREATIVE.poll();
            }
        }
        if (creative != null) {
            creative.run();
        }

        Runnable survival = null;
        synchronized (SURVIVAL) {
            if (!SURVIVAL.isEmpty() && survivalReady()) {
                survivalSentAt = System.currentTimeMillis();
                survivalSentGameTime = currentGameTime();
                survival = SURVIVAL.poll();
            }
        }
        if (survival != null) {
            survival.run();
        }
    }

    private static boolean survivalReady() {
        int delayMs = CPB.config().survivalDelayMs;
        if (System.currentTimeMillis() - survivalSentAt < delayMs) {
            return false;
        }
        long gameTime = currentGameTime();
        if (gameTime == Long.MIN_VALUE || survivalSentGameTime == Long.MIN_VALUE) {
            return true; // no level (or first send) — wall clock alone decides
        }
        long minServerTicks = Math.max(1, Math.round(delayMs / 50.0));
        return gameTime - survivalSentGameTime >= minServerTicks;
    }

    private static long currentGameTime() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Long.MIN_VALUE : level.getGameTime();
    }

    public static boolean isBusy() {
        synchronized (SURVIVAL) {
            if (!SURVIVAL.isEmpty()) {
                return true;
            }
        }
        synchronized (CREATIVE) {
            return !CREATIVE.isEmpty();
        }
    }

    public static void clear() {
        synchronized (SURVIVAL) {
            SURVIVAL.clear();
            // Reset the timestamps too: a new world may have a smaller gameTime than the
            // previous one, and a stale mark would stall the queue until it "catches up".
            survivalSentAt = 0;
            survivalSentGameTime = Long.MIN_VALUE;
        }
        synchronized (CREATIVE) {
            CREATIVE.clear();
            creativeSentAt = 0;
        }
    }
}
