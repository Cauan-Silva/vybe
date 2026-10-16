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
    public record VideoApi(String id, Snippet snippet, Detalhes contentDetails, Situacao status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String channelTitle, Map<String, Miniatura> thumbnails) {
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

    // ---------- VYBE ----------

    /** Um vídeo já resumido: o que precisamos para virar uma música do catálogo. */
    public record Video(String id, String titulo, String canal, String capaUrl, int duracaoSegundos,
                        boolean incorporavel) {
    }

    public record Status(boolean configurado) {
    }
}
