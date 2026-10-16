package com.cauan.vybe.configuracao;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.configuracao.Chaves.Chave;
import com.cauan.vybe.spotify.SpotifyCliente;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Tela de Configurações do programa: cada pessoa informa as próprias chaves do Spotify,
 * YouTube e Jamendo. Só existe no programa (vybe.desktop.ativo=true); na versão web as
 * chaves são do servidor e ninguém pode trocá-las pela tela.
 */
@RestController
@RequestMapping("/api/configuracoes")
@ConditionalOnProperty(name = "vybe.desktop.ativo", havingValue = "true")
public class ConfiguracaoController {

    private static final Pattern SPOTIFY_ID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern YOUTUBE = Pattern.compile("AIza[0-9A-Za-z_-]{35}");
    private static final Pattern JAMENDO = Pattern.compile("[0-9A-Za-z]{4,64}");

    /**
     * O que a tela mostra. O Client ID do Spotify e o do Jamendo não são segredo; da chave do
     * YouTube só voltam os 4 últimos caracteres.
     */
    public record Configuracoes(String spotifyClientId,
                                String spotifyRedirectUri,
                                String jamendoClientId,
                                boolean youtubeConfigurado,
                                String youtubeFinal) {
    }

    /** null = não mexer; "" = apagar. */
    public record Alteracao(String spotifyClientId, String youtubeApiKey, String jamendoClientId) {
    }

    private final Chaves chaves;
    private final SpotifyCliente spotify;

    public ConfiguracaoController(Chaves chaves, SpotifyCliente spotify) {
        this.chaves = chaves;
        this.spotify = spotify;
    }

    @GetMapping
    public Configuracoes ler() {
        String youtube = chaves.valor(Chave.YOUTUBE_API_KEY);
        return new Configuracoes(
                chaves.valor(Chave.SPOTIFY_CLIENT_ID),
                spotify.redirectUri(),
                chaves.valor(Chave.JAMENDO_CLIENT_ID),
                !youtube.isEmpty(),
                youtube.length() >= 4 ? youtube.substring(youtube.length() - 4) : null);
    }

    @PutMapping
    public Configuracoes alterar(@RequestBody Alteracao alteracao) {
        validar(alteracao.spotifyClientId(), SPOTIFY_ID,
                "O Client ID do Spotify tem 32 letras e números (copie do painel do app no Spotify).");
        validar(alteracao.youtubeApiKey(), YOUTUBE,
                "A chave do YouTube começa com \"AIza\" e tem 39 caracteres.");
        validar(alteracao.jamendoClientId(), JAMENDO, "O Client ID do Jamendo parece incompleto.");

        if (alteracao.spotifyClientId() != null) chaves.salvar(Chave.SPOTIFY_CLIENT_ID, alteracao.spotifyClientId());
        if (alteracao.youtubeApiKey() != null) chaves.salvar(Chave.YOUTUBE_API_KEY, alteracao.youtubeApiKey());
        if (alteracao.jamendoClientId() != null) chaves.salvar(Chave.JAMENDO_CLIENT_ID, alteracao.jamendoClientId());
        return ler();
    }

    private static void validar(String valor, Pattern formato, String mensagem) {
        if (valor != null && !valor.isBlank() && !formato.matcher(valor.trim()).matches()) {
            throw new Excecoes.RegraNegocio(mensagem);
        }
    }
}
