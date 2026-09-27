package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;
import org.opencomputers.api.ComponentException;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * OC speaker: {@code beep(freq, duration)} queues tone events the host app
 * renders (Android ToneGenerator). Events are drained via {@link #drainBeeps}.
 */
public class SpeakerComponent extends AbstractComponent {
    /** A queued tone request, in OC units (freq Hz, duration in ticks ~ 50ms). */
    public record Beep(double frequency, double durationTicks) {
    }

    private final Queue<Beep> beeps = new ArrayDeque<>();

    public SpeakerComponent() {
        super("speaker");
    }

    /** Pending beep events for the host to render. */
    public Queue<Beep> drainBeeps() {
        Queue<Beep> out = new ArrayDeque<>(beeps);
        beeps.clear();
        return out;
    }

    @Callback(doc = "function([frequency:number[, durationInTicks:number]]) -- Play a short square-wave tone.")
    public Object[] beep(Object[] args) {
        double freq = args.length > 0 && args[0] != null ? doubleArg(args, 0, "number") : 440.0;
        double dur = args.length > 1 && args[1] != null ? doubleArg(args, 1, "number") : 2.0;
        if (freq < 20 || freq > 2000) {
            throw new ComponentException("invalid frequency, must be in [20, 2000]");
        }
        if (dur < 0.1 || dur > 5) {
            throw new ComponentException("invalid duration, must be in [0.1, 5]");
        }
        beeps.add(new Beep(freq, dur));
        return new Object[]{true};
    }
}
