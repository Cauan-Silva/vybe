package com.cauan.vybe.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conta tentativas por chave (ex.: e-mail + IP) numa janela de tempo que começa na primeira
 * tentativa. Ao chegar ao máximo, a chave fica bloqueada até a janela acabar.
 * <p>
 * Tudo fica na memória (uma instância da API só). Para não crescer sem limite, as entradas
 * vencidas são descartadas de tempos em tempos e o total de chaves tem um teto.
 */
public class LimitadorTentativas {

    private record Contagem(Instant inicio, int tentativas) {
    }

    private final int maximo;
    private final Duration janela;
    private final Clock clock;
    private final int maxChaves;
    private final Map<String, Contagem> contagens = new ConcurrentHashMap<>();
    private volatile Instant proximaLimpeza;

    public LimitadorTentativas(int maximo, Duration janela, Clock clock, int maxChaves) {
        this.maximo = maximo;
        this.janela = janela;
        this.clock = clock;
        this.maxChaves = maxChaves;
        this.proximaLimpeza = clock.instant().plus(janela);
    }

    public boolean bloqueado(String chave) {
        Contagem c = vigente(chave);
        return c != null && c.tentativas() >= maximo;
    }

    /** Quanto falta para a chave ser liberada (zero se não está bloqueada). */
    public Duration esperaRestante(String chave) {
        Contagem c = vigente(chave);
        if (c == null || c.tentativas() < maximo) {
            return Duration.ZERO;
        }
        return Duration.between(clock.instant(), c.inicio().plus(janela));
    }

    public void registrar(String chave) {
        Instant agora = clock.instant();
        contagens.compute(chave, (k, c) -> c == null || venceu(c, agora)
                ? new Contagem(agora, 1)
                : new Contagem(c.inicio(), c.tentativas() + 1));
        if (contagens.size() > maxChaves || !agora.isBefore(proximaLimpeza)) {
            descartarVencidas(agora);
        }
        if (contagens.size() > maxChaves) {
            // Muitas chaves ainda dentro da janela (ex.: ataque vindo de muitos IPs): solta as
            // outras para a memória não crescer. A chave atual continua sendo contada.
            Iterator<String> it = contagens.keySet().iterator();
            while (contagens.size() > maxChaves && it.hasNext()) {
                if (!it.next().equals(chave)) {
                    it.remove();
                }
            }
        }
    }

    public void limpar(String chave) {
        contagens.remove(chave);
    }

    int tamanho() {
        return contagens.size();
    }

    private Contagem vigente(String chave) {
        Contagem c = contagens.get(chave);
        if (c != null && venceu(c, clock.instant())) {
            contagens.remove(chave, c);
            return null;
        }
        return c;
    }

    private boolean venceu(Contagem c, Instant agora) {
        return !agora.isBefore(c.inicio().plus(janela));
    }

    /** Passa por todas as chaves no máximo uma vez por janela (ou quando passa do teto). */
    private void descartarVencidas(Instant agora) {
        proximaLimpeza = agora.plus(janela);
        contagens.values().removeIf(c -> venceu(c, agora));
    }
}
