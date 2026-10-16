package com.cauan.vybe.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Formato das respostas da YouTube Data API v3 (só os campos que usamos) e o resumo que o VYBE guarda. */
public final class YoutubeDtos {

    private YoutubeDtos() {
    }

    // ---------- search.list ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespostaBusca(List<ItemBusca> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemBusca(IdVideo id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdVideo(String videoId) {
    }

    // ---------- videos.list ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespostaVideos(List<VideoApi> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoApi(String id, Snippet snippet, Detalhes contentDetails, Situacao status,
                           Estatisticas statistics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String channelTitle, String channelId, String publishedAt,
                          Map<String, Miniatura> thumbnails) {
    }

    /** O YouTube manda os números como texto ("12345"); curtidas somem se o dono esconder. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Estatisticas(Long viewCount, Long likeCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Miniatura(String url) {
    }

    /** duration vem no formato ISO 8601, ex.: PT4M13S. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detalhes(String duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Situacao(boolean embeddable) {
    }

    // ---------- channels.list e playlistItems.list (página do canal) ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespostaCanais(List<CanalApi> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CanalApi(String id, SnippetCanal snippet, EstatisticasCanal statistics, DetalhesCanal contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnippetCanal(String title, Map<String, Miniatura> thumbnails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstatisticasCanal(Long subscriberCount, boolean hiddenSubscriberCount, Long videoCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetalhesCanal(PlaylistsDoCanal relatedPlaylists) {
    }

    /** uploads: a playlist com todos os vídeos enviados pelo canal, do mais novo ao mais antigo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaylistsDoCanal(String uploads) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespostaItensPlaylist(List<ItemPlaylist> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemPlaylist(DetalhesItem contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetalhesItem(String videoId) {
    }

    // ---------- VYBE ----------

    /**
     * Um vídeo já resumido. Os campos do fim (canal, data e números) só aparecem na aba Vídeos;
     * visualizações e curtidas podem ser nulas quando o dono esconde.
     */
    public record Video(String id, String titulo, String canal, String capaUrl, int duracaoSegundos,
                        boolean incorporavel, String canalId, String publicadoEm, Long visualizacoes,
                        Long curtidas) {

        public Video(String id, String titulo, String canal, String capaUrl, int duracaoSegundos,
                     boolean incorporavel) {
            this(id, titulo, canal, capaUrl, duracaoSegundos, incorporavel, null, null, null, null);
        }
    }

    /** Dados do canal que vêm da API (uploads é a playlist com os vídeos dele). */
    public record CanalInfo(String id, String nome, String avatarUrl, Long inscritos, Long totalVideos,
                            String uploads) {
    }

    public record Status(boolean configurado) {
    }

    /**
     * Vídeo da aba Vídeos. Não vira música do catálogo: só é mostrado e tocado na página.
     * duracaoSegundos = 0 indica transmissão ao vivo.
     */
    public record VideoResponse(String id,
                                String titulo,
                                String canal,
                                String canalId,
                                String capaUrl,
                                int duracaoSegundos,
                                Long visualizacoes,
                                Long curtidas,
                                String publicadoEm) {
    }

    /** Página do canal: dados do canal e os vídeos mais recentes. inscritos é nulo se o canal esconde. */
    public record CanalResponse(String id, String nome, String avatarUrl, Long inscritos, Long totalVideos,
                                List<VideoResponse> videos) {
    }
}
