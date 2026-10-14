package com.cauan.vybe.jamendo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Formato das respostas da API v3.0 do Jamendo (só os campos que usamos). */
public final class JamendoDtos {

    private JamendoDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resposta(Cabecalho headers, List<Faixa> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cabecalho(String status,
                            int code,
                            @JsonProperty("error_message") String mensagemErro) {
    }

    /** Uma faixa. Em singles, os campos de álbum vêm vazios. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Faixa(String id,
                        String name,
                        int duration,
                        @JsonProperty("artist_name") String artista,
                        @JsonProperty("album_name") String album,
                        @JsonProperty("album_image") String capaAlbum,
                        String image,
                        String audio,
                        @JsonProperty("license_ccurl") String licencaUrl,
                        @JsonProperty("shareurl") String paginaUrl) {
    }

    public record Status(boolean disponivel) {
    }
}
