package com.cauan.vybe.seguranca;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorTentativasTest {

    private RelogioAjustavel relogio;
    private LimitadorTentativas limitador;

    @BeforeEach
    void setUp() {
        relogio = new RelogioAjustavel(Instant.parse("2026-11-19T12:00:00Z"));
        limitador = new LimitadorTentativas(5, Duration.ofMinutes(15), relogio, 100);
    }

    @Test
    void bloqueiaDepoisDoMaximoDeTentativas() {
        for (int i = 0; i < 4; i++) {
            limitador.registrar("cauan@email.com|1.2.3.4");
        }
        assertThat(limitador.bloqueado("cauan@email.com|1.2.3.4")).isFalse();

        limitador.registrar("cauan@email.com|1.2.3.4");

        assertThat(limitador.bloqueado("cauan@email.com|1.2.3.4")).isTrue();
        assertThat(limitador.esperaRestante("cauan@email.com|1.2.3.4")).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void chavesDiferentesNaoSeMisturam() {
        for (int i = 0; i < 5; i++) {
            limitador.registrar("a|1.2.3.4");
        }
        assertThat(limitador.bloqueado("a|1.2.3.4")).isTrue();
        assertThat(limitador.bloqueado("a|5.6.7.8")).isFalse();
        assertThat(limitador.bloqueado("b|1.2.3.4")).isFalse();
    }

    @Test
    void liberaQuandoAJanelaPassa() {
        for (int i = 0; i < 5; i++) {
            limitador.registrar("chave");
        }
        relogio.avancar(Duration.ofMinutes(14));
        assertThat(limitador.bloqueado("chave")).isTrue();
        assertThat(limitador.esperaRestante("chave")).isEqualTo(Duration.ofMinutes(1));

        relogio.avancar(Duration.ofMinutes(1));

        assertThat(limitador.bloqueado("chave")).isFalse();
        assertThat(limitador.esperaRestante("chave")).isEqualTo(Duration.ZERO);
    }

    @Test
    void limparZeraAsTentativas() {
        for (int i = 0; i < 5; i++) {
            limitador.registrar("chave");
        }
        limitador.limpar("chave");

        assertThat(limitador.bloqueado("chave")).isFalse();
        assertThat(limitador.tamanho()).isZero();
    }

    @Test
    void entradasVencidasSaoDescartadas() {
        for (int i = 0; i < 50; i++) {
            limitador.registrar("chave-" + i);
        }
        relogio.avancar(Duration.ofMinutes(16));

        limitador.registrar("nova");

        assertThat(limitador.tamanho()).isEqualTo(1);
    }

    @Test
    void memoriaTemLimiteMesmoComMuitasChavesNaJanela() {
        for (int i = 0; i < 1_000; i++) {
            limitador.registrar("chave-" + i);
        }
        assertThat(limitador.tamanho()).isLessThanOrEqualTo(100);
    }
}
