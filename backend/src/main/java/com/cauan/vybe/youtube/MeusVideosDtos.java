package com.cauan.vybe.youtube;

/** Rotas /api/eu/videos: "Assistir depois", histórico e de onde continuar. */
public final class MeusVideosDtos {

    private MeusVideosDtos() {
    }

    /** O que o front-end manda sobre o vídeo (cópia do que ele recebeu da busca ou do em alta). */
    public record DadosVideo(String id, String titulo, String canal, String canalId, String capaUrl,
                             int duracaoSegundos) {
    }

    public record Progresso(DadosVideo video, int posicaoSegundos) {
    }

    /** Vídeo guardado para o usuário. assistidoEm é nulo se ele só está no "Assistir depois". */
    public record VideoSalvo(String id,
                             String titulo,
                             String canal,
                             String canalId,
                             String capaUrl,
                             int duracaoSegundos,
                             boolean assistirDepois,
                             int posicaoSegundos,
                             String assistidoEm) {

        static VideoSalvo de(VideoUsuario v) {
            return new VideoSalvo(v.getVideoId(), v.getTitulo(), v.getCanal(), v.getCanalId(), v.getCapaUrl(),
                    v.getDuracaoSegundos(), v.isAssistirDepois(), v.getPosicaoSegundos(),
                    v.getAssistidoEm() == null ? null : v.getAssistidoEm().toString());
        }
    }
}
