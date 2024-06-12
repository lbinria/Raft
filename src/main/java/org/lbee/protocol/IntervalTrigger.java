package org.lbee.protocol;

public class IntervalTrigger {

    /* *
     * La classe IntervalTrigger permet de déclencher une action à intervalle régulier
     * Elle prend en paramètre une action à effectuer et un intervalle de temps
     * Elle possède une méthode run() qui permet de vérifier si l'intervalle de temps est écoulé
     * Si c'est le cas, elle exécute l'action et met à jour le prochain déclenchement
     * */

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
