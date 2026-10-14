package com.cauan.vybe.spotify;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;

import java.util.List;

/** Respostas da API do VYBE para a página do Spotify. */
public final class SpotifyDtos {

    private SpotifyDtos() {
    }

    /**
     * configurado: há Client ID/Secret no servidor. conectado: este usuário ligou a conta.
     * podeTocar: a conexão tem a permissão do player (conexões antigas precisam reconectar).
     */
    public record Status(boolean configurado, boolean conectado, String nome, boolean podeTocar) {
    }

    /** Access token para o player do Spotify no navegador (o SDK pede um token próprio). */
    public record TokenPlayer(String accessToken, int expiraEmSegundos) {
    }

    public record UrlAutorizacao(String url) {
    }

    /** importavel: o Spotify só deixa ler playlists próprias ou colaborativas. */
    public record PlaylistSpotify(String id, String nome, String capaUrl, int quantidade, String dono,
                                  boolean importavel) {
    }

    public record ArtistaOuvido(String nome, String imagemUrl, String spotifyUrl) {
    }

    /** musica: a faixa já no catálogo do VYBE, pronta para tocar (nula se a conta não pode tocar). */
    public record MusicaOuvida(String titulo, String artistas, String capaUrl, String spotifyUrl,
                               MusicaResponse musica) {
    }

    public record MaisOuvidos(List<ArtistaOuvido> artistas, List<MusicaOuvida> musicas) {
    }

    /**
     * Resultado da importação: a playlist criada no VYBE e o que ficou de fora.
     * viaSpotify: as faixas foram trazidas do próprio Spotify (tocam pelo player do Spotify);
     * se falso, foram procuradas equivalentes livres no catálogo e no Jamendo.
     */
    public record Importacao(Long playlistId,
                             String nome,
                             int analisadas,
                             int encontradas,
                             List<String> naoEncontradas,
                             boolean viaSpotify) {
    }
}
