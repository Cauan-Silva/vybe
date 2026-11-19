package com.cauan.vybe.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio de teste que dá para adiantar. */
public class RelogioAjustavel extends Clock {

    private Instant agora;

    public RelogioAjustavel(Instant inicio) {
        this.agora = inicio;
    }

    public void avancar(Duration tempo) {
        agora = agora.plus(tempo);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
