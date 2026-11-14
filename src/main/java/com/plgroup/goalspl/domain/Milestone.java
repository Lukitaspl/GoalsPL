package com.plgroup.goalspl.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Hito comunitario: al llegar el progreso total a {@code percent} % se anuncia (si {@code announce}) y se entregan
 * sus recompensas según la política. El hito del 100 % son las recompensas de completar la campaña.
 */
public final class Milestone {

    public int percent;
    public RewardPolicy policy = RewardPolicy.PARTICIPANTS;
    public boolean announce = true;
    public List<Reward> rewards = new ArrayList<>();

    public Milestone(int percent) {
        this.percent = percent;
    }

    public Milestone copy() {
        Milestone m = new Milestone(percent);
        m.policy = policy;
        m.announce = announce;
        m.rewards = new ArrayList<>(rewards);
        return m;
    }
}
