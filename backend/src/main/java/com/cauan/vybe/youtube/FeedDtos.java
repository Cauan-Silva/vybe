package com.cauan.vybe.youtube;

import com.cauan.vybe.youtube.YoutubeDtos.VideoResponse;

import java.util.List;

/** Aba Vídeos personalizada: "Para você", canais seguidos ("Inscrições") e interesses. */
public final class FeedDtos {

    private FeedDtos() {
    }

    /** Canal seguido (ou usado como base do "Para você"). */
    public record Canal(String id, String nome, String avatarUrl) {

        static Canal de(CanalSeguido c) {
            return new Canal(c.getCanalId(), c.getNome(), c.getAvatarUrl());
        }
    }

    public record Interesse(Long id, String termo) {

        static Interesse de(InteresseVideo i) {
            return new Interesse(i.getId(), i.getTermo());
        }
    }

    public record NovoInteresse(String termo) {
    }

    /** canais: em quem a lista se baseou ("Porque você assiste…" ou os canais seguidos). */
    public record Feed(List<Canal> canais, List<VideoResponse> videos) {
    }
}
