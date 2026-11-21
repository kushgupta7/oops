package com.bits.itsm.escalation;

import com.bits.itsm.support.IncidentSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Chain of Responsibility for automatic escalation of unacknowledged P1 incidents.
 * Each handler owns one level of the chain (Technician &rarr; Team Lead &rarr; System Administrator &rarr; IT Manager)
 * and its own timer. A handler either keeps the incident (its timer has not expired: the chain stops) or
 * escalates to its level and passes the request to its successor. Adding a new level = inserting one handler.
 */
public abstract class EscalationHandler {
    /** Request object travelling along the chain. */
    public record Context(IncidentSnapshot incident, Instant responseDeadline, Instant now, Consumer<Step> escalate) { }

    /** One escalation step that the chain decided to take. */
    public record Step(IncidentSnapshot incident, EscalationLevel from, EscalationLevel to, Instant at) { }

    private EscalationHandler next;

    /** Links the successor and returns it, so chains read as {@code a.then(b).then(c)}. */
    public EscalationHandler then(EscalationHandler successor) {
        this.next = successor;
        return successor;
    }

    public final void handle(Context context) {
        if (!responsible(context)) return;  // this level still owns the incident; stop here
        onReach(context);
        if (next != null) next.handle(context);
    }

    /** The level this handler represents. */
    public abstract EscalationLevel level();

    /** True when this level's timer has expired, i.e. the incident must reach (at least) this level. */
    protected abstract boolean responsible(Context context);

    /** Action when the incident reaches this level. */
    protected void onReach(Context context) { }

    /** Handler for a level reached after {@code delay} past the response deadline (strictly after for delay zero). */
    static EscalationHandler timed(EscalationLevel level, Duration delay) {
        return new EscalationHandler() {
            @Override public EscalationLevel level() { return level; }
            @Override protected boolean responsible(Context c) {
                Duration overdue = Duration.between(c.responseDeadline(), c.now());
                return delay.isZero() ? c.now().isAfter(c.responseDeadline()) : overdue.compareTo(delay) >= 0;
            }
            @Override protected void onReach(Context c) {
                EscalationLevel from = EscalationLevel.values()[level.ordinal() - 1];
                c.escalate().accept(new Step(c.incident(), from, level, c.now()));
            }
        };
    }

    /** First link: the assigned technician. Always responsible for an overdue P1, never escalates by itself. */
    static EscalationHandler technician() {
        return new EscalationHandler() {
            @Override public EscalationLevel level() { return EscalationLevel.TECHNICIAN; }
            @Override protected boolean responsible(Context c) { return true; }
        };
    }
}
