package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.domain.Campaign;

/**
 * Edición en curso de una campaña: una copia de la definición que solo sustituye a la guardada al pulsar
 * «Guardar» en la revisión. Al cerrar los menús sin guardar se descarta.
 */
public final class Draft {

    public final Campaign campaign;
    public final boolean creating;
    private boolean finished;

    public Draft(Campaign campaign, boolean creating) {
        this.campaign = campaign;
        this.creating = creating;
    }

    public boolean finished() {
        return finished;
    }

    public void finish() {
        finished = true;
    }
}
