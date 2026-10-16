package com.cauan.vybe.configuracao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * As chaves das integrações, num lugar só.
 *
 * Cada chave vem, nesta ordem: do valor salvo pela tela de Configurações (só existe no
 * programa) ou da variável de ambiente (versão web). Os clientes do Spotify, YouTube e
 * Jamendo perguntam aqui a cada chamada, então trocar uma chave vale na hora, sem reiniciar.
 */
@Component
public class Chaves {

    public enum Chave {
        JAMENDO_CLIENT_ID,
        SPOTIFY_CLIENT_ID,
        SPOTIFY_CLIENT_SECRET,
        YOUTUBE_API_KEY
    }

    private final ConfiguracaoRepository repositorio;
    private final Map<Chave, String> doAmbiente = new EnumMap<>(Chave.class);
    private final boolean programa;
    /** Cópia em memória do que está no banco (carregada na primeira leitura). */
    private volatile Map<Chave, String> salvas;

    public Chaves(ConfiguracaoRepository repositorio,
                  @Value("${vybe.jamendo.client-id:}") String jamendo,
                  @Value("${vybe.spotify.client-id:}") String spotifyId,
                  @Value("${vybe.spotify.client-secret:}") String spotifySecret,
                  @Value("${vybe.youtube.api-key:}") String youtube,
                  @Value("${vybe.desktop.ativo:false}") boolean programa) {
        this.repositorio = repositorio;
        this.programa = programa;
        doAmbiente.put(Chave.JAMENDO_CLIENT_ID, limpar(jamendo));
        doAmbiente.put(Chave.SPOTIFY_CLIENT_ID, limpar(spotifyId));
        // No programa o Spotify usa PKCE: um Client Secret embutido no instalador ficaria exposto.
        doAmbiente.put(Chave.SPOTIFY_CLIENT_SECRET, programa ? "" : limpar(spotifySecret));
        doAmbiente.put(Chave.YOUTUBE_API_KEY, limpar(youtube));
    }

    /** Valor atual da chave ("" se não houver). */
    public String valor(Chave chave) {
        if (chave == Chave.SPOTIFY_CLIENT_SECRET) {
            return doAmbiente.get(chave); // nunca vem da tela
        }
        String salva = salvas().get(chave);
        return salva != null && !salva.isBlank() ? salva : doAmbiente.get(chave);
    }

    public boolean programa() {
        return programa;
    }

    /** Salva (ou apaga, com valor vazio) uma chave definida pela tela de Configurações. */
    @Transactional
    public void salvar(Chave chave, String valor) {
        if (chave == Chave.SPOTIFY_CLIENT_SECRET) {
            throw new IllegalArgumentException("O Client Secret do Spotify não é configurável pela tela");
        }
        String limpo = limpar(valor);
        if (limpo.isEmpty()) {
            repositorio.deleteById(chave.name());
        } else {
            repositorio.save(new Configuracao(chave.name(), limpo));
        }
        salvas = null; // relê na próxima vez
    }

    private Map<Chave, String> salvas() {
        Map<Chave, String> atuais = salvas;
        if (atuais == null) {
            atuais = new EnumMap<>(Chave.class);
            for (Configuracao c : repositorio.findAll()) {
                try {
                    atuais.put(Chave.valueOf(c.getChave()), c.getValor());
                } catch (IllegalArgumentException e) {
                    // chave antiga que não existe mais: ignora
                }
            }
            salvas = atuais;
        }
        return atuais;
    }

    private static String limpar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
