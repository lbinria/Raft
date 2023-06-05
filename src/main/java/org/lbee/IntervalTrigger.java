package org.lbee;

public class IntervalTrigger {

    private long next;
    private long interval;
    private final Runnable action;
    public IntervalTrigger(Runnable action, long interval) {
        this.action = action;
        this.interval = interval;
        this.next = System.currentTimeMillis() + interval;
    }

    public void run() {
        if (System.currentTimeMillis() >= next) {
            action.run();
            this.next = System.currentTimeMillis() + interval;
        }
    }

}
