package com.cauan.vybe.seguranca;

import com.cauan.vybe.comum.Excecoes;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Freio contra adivinhação de senha e cadastro em massa:
 * <ul>
 *   <li>login: depois de 5 senhas erradas para o mesmo e-mail vindas do mesmo IP em 15 minutos,
 *       responde 429 até a janela acabar. Um login certo zera a contagem;</li>
 *   <li>cadastro: no máximo 10 tentativas por IP por hora.</li>
 * </ul>
 * O IP é o de {@code request.getRemoteAddr()}: na nuvem, o Spring já o troca pelo do cliente
 * (server.forward-headers-strategy=framework, atrás do proxy do Render).
 */
@Component
public class ProtecaoAuth {

    static final int MAX_FALHAS_LOGIN = 5;
    static final Duration JANELA_LOGIN = Duration.ofMinutes(15);
    static final int MAX_CADASTROS = 10;
    static final Duration JANELA_CADASTRO = Duration.ofHours(1);
    private static final int MAX_CHAVES = 10_000;

    private final LimitadorTentativas falhasLogin;
    private final LimitadorTentativas cadastros;

    public ProtecaoAuth(Clock clock) {
        this.falhasLogin = new LimitadorTentativas(MAX_FALHAS_LOGIN, JANELA_LOGIN, clock, MAX_CHAVES);
        this.cadastros = new LimitadorTentativas(MAX_CADASTROS, JANELA_CADASTRO, clock, MAX_CHAVES);
    }

    /** Chamar antes de conferir a senha. */
    public void verificarLogin(String email, String ip) {
        String chave = chaveLogin(email, ip);
        if (falhasLogin.bloqueado(chave)) {
            throw new Excecoes.MuitasTentativas(
                    "Muitas tentativas de login com senha errada. Tente de novo mais tarde.",
                    falhasLogin.esperaRestante(chave));
        }
    }

    public void falhaLogin(String email, String ip) {
        falhasLogin.registrar(chaveLogin(email, ip));
    }

    public void sucessoLogin(String email, String ip) {
        falhasLogin.limpar(chaveLogin(email, ip));
    }

    /** Conta a tentativa de cadastro e recusa se o IP já passou do limite. */
    public void registrarCadastro(String ip) {
        String chave = ip == null ? "" : ip;
        if (cadastros.bloqueado(chave)) {
            throw new Excecoes.MuitasTentativas(
                    "Muitos cadastros feitos a partir desta rede. Tente de novo mais tarde.",
                    cadastros.esperaRestante(chave));
        }
        cadastros.registrar(chave);
    }

    private static String chaveLogin(String email, String ip) {
        String normalizado = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return normalizado + "|" + ip;
    }
}
