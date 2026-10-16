package com.cauan.vybe.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Formato das respostas da Web API do Spotify (só os campos que usamos).
 * Desde fevereiro de 2026, as músicas de uma playlist vêm em "item" (o antigo
 * "track" ficou obsoleto) e a contagem em "items.total" (antes "tracks.total").
 */
public final class SpotifyApi {

    private SpotifyApi() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(@JsonProperty("access_token") String accessToken,
                        @JsonProperty("refresh_token") String refreshToken,
                        @JsonProperty("expires_in") int expiraEmSegundos,
                        /** Permissões concedidas, separadas por espaço (ex.: "streaming user-top-read"). */
                        @JsonProperty("scope") String escopos) {

        public Token(String accessToken, String refreshToken, int expiraEmSegundos) {
            this(accessToken, refreshToken, expiraEmSegundos, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perfil(String id, @JsonProperty("display_name") String nome) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagina<T>(List<T> items, String next, int total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Imagem(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(String spotify) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contagem(int total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dono(String id, @JsonProperty("display_name") String nome) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Playlist(String id,
                           String name,
                           List<Imagem> images,
                           Dono owner,
                           boolean collaborative,
                           Contagem items,
                           Contagem tracks) {

        public int quantidade() {
            if (items != null) return items.total();
            return tracks != null ? tracks.total() : 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtistaSimples(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Album(String name, List<Imagem> images) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Faixa(String id,
                        String name,
                        String type,
                        @JsonProperty("duration_ms") int duracaoMs,
                        List<ArtistaSimples> artists,
                        Album album,
                        @JsonProperty("external_urls") Links links) {

        /** Endereço que o player do Spotify entende. */
        public String uri() {
            return "spotify:track:" + id;
        }
    }

    /** Item de playlist: usa "item" e, se faltar, o antigo "track". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemPlaylist(Faixa item, Faixa track) {
        public Faixa faixa() {
            return item != null ? item : track;
        }
    }

    /** Resposta de /search?type=track: as faixas vêm dentro de "tracks". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultadoBusca(Pagina<Faixa> tracks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artista(String name, List<Imagem> images, @JsonProperty("external_urls") Links links) {
    }
}
