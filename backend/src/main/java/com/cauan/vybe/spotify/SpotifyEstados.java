package com.cauan.vybe.spotify;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O parâmetro "state" do OAuth.
 *
 * Antes de mandar a pessoa ao Spotify, geramos um valor aleatório e lembramos de quem é.
 * Quando o Spotify chama o callback, ele devolve o mesmo valor: assim sabemos qual
 * usuário está conectando e garantimos que o pedido saiu do VYBE (proteção contra CSRF).
 * Cada valor vale 10 minutos e só pode ser usado uma vez.
 *
 * Fica em memória: basta para uma instância da API. Com várias, iria para o banco.
 */
@Component
public class SpotifyEstados {

    private static final Duration VALIDADE = Duration.ofMinutes(10);

    private record Pendente(Long usuarioId, Instant criadoEm) {
    }

    private final Map<String, Pendente> pendentes = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock clock;

    public SpotifyEstados(Clock clock) {
        this.clock = clock;
    }

    public String gerar(Long usuarioId) {
        limparVencidos();
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String estado = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendentes.put(estado, new Pendente(usuarioId, clock.instant()));
        return estado;
    }

    /** Devolve o dono do estado e o descarta (uso único). */
    public Optional<Long> consumir(String estado) {
        if (estado == null) {
            return Optional.empty();
        }
        Pendente pendente = pendentes.remove(estado);
        if (pendente == null || vencido(pendente)) {
            return Optional.empty();
        }
        return Optional.of(pendente.usuarioId());
    }

    private boolean vencido(Pendente p) {
        return clock.instant().isAfter(p.criadoEm().plus(VALIDADE));
    }

    private void limparVencidos() {
        pendentes.values().removeIf(this::vencido);
    }
}
