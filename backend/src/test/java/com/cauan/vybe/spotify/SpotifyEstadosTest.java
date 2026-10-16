package com.cauan.vybe.spotify;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyEstadosTest {

    private final SpotifyEstados estados =
            new SpotifyEstados(Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC));

    @Test
    void desafioSegueOExemploDaRfc7636() {
        // Exemplo oficial do apêndice B da RFC 7636 (PKCE)
        assertThat(SpotifyEstados.desafioDe("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void oVerifierDevolvidoNoCallbackCorrespondeAoDesafioEnviado() {
        String estado = estados.gerar(7L);
        String desafio = estados.desafio(estado);

        SpotifyEstados.Pedido pedido = estados.consumir(estado).orElseThrow();

        assertThat(pedido.usuarioId()).isEqualTo(7L);
        assertThat(pedido.verificador()).hasSizeBetween(43, 128); // limites da RFC
        assertThat(SpotifyEstados.desafioDe(pedido.verificador())).isEqualTo(desafio);
        assertThat(estados.consumir(estado)).isEmpty(); // uso único
    }
}
