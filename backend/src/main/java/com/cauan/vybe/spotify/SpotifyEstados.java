package com.cauan.vybe.spotify;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * Cada estado também guarda um "code verifier" do PKCE (RFC 7636). O desafio (hash do
 * verifier) vai junto para o Spotify; na troca do código mandamos o verifier. É o que permite
 * o programa logar sem Client Secret.
 *
 * Fica em memória: basta para uma instância da API. Com várias, iria para o banco.
 */
@Component
public class SpotifyEstados {

    private static final Duration VALIDADE = Duration.ofMinutes(10);

    private record Pendente(Long usuarioId, Instant criadoEm, String verificador) {
    }

    /** O que sai do callback: de quem é o pedido e o verifier do PKCE. */
    public record Pedido(Long usuarioId, String verificador) {
    }

    private final Map<String, Pendente> pendentes = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock clock;

    public SpotifyEstados(Clock clock) {
        this.clock = clock;
    }

    public String gerar(Long usuarioId) {
        limparVencidos();
        String estado = aleatorio(32);
        pendentes.put(estado, new Pendente(usuarioId, clock.instant(), aleatorio(64)));
        return estado;
    }

    /** Desafio do PKCE para mandar ao Spotify junto com este estado. */
    public String desafio(String estado) {
        Pendente pendente = pendentes.get(estado);
        if (pendente == null) {
            throw new IllegalArgumentException("Estado desconhecido");
        }
        return desafioDe(pendente.verificador());
    }

    /** Devolve o dono do estado e o verifier, e descarta o estado (uso único). */
    public Optional<Pedido> consumir(String estado) {
        if (estado == null) {
            return Optional.empty();
        }
        Pendente pendente = pendentes.remove(estado);
        if (pendente == null || vencido(pendente)) {
            return Optional.empty();
        }
        return Optional.of(new Pedido(pendente.usuarioId(), pendente.verificador()));
    }

    /** code_challenge = BASE64URL(SHA-256(code_verifier)), sem "=" no fim. */
    static String desafioDe(String verificador) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verificador.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Texto aleatório seguro em base64url (32 bytes = 43 caracteres; 64 bytes = 86). */
    private String aleatorio(int bytes) {
        byte[] valor = new byte[bytes];
        aleatorio.nextBytes(valor);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valor);
    }

    private boolean vencido(Pendente p) {
        return clock.instant().isAfter(p.criadoEm().plus(VALIDADE));
    }

    private void limparVencidos() {
        pendentes.values().removeIf(this::vencido);
    }
}
