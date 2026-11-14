package com.plgroup.goalspl.domain;

import java.util.ArrayList;
import java.util.List;

/** Recompensa individual: se entrega a cada jugador cuando su aportación personal llega a {@code amount}. */
public final class PersonalMilestone {

    public long amount;
    public List<Reward> rewards = new ArrayList<>();

    public PersonalMilestone(long amount) {
        this.amount = amount;
    }

    public PersonalMilestone copy() {
        PersonalMilestone p = new PersonalMilestone(amount);
        p.rewards = new ArrayList<>(rewards);
        return p;
    }
}
