package com.cauan.vybe.playlist;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class PlaylistDtos {

    private PlaylistDtos() {
    }

    public record NomeRequest(@NotBlank @Size(max = 100) String nome) {
    }

    public record AdicionarRequest(@NotNull Long musicaId) {
    }

    /** capaUrl: capa da primeira música, para a playlist ter uma imagem. */
    public record PlaylistResumo(Long id, String nome, long quantidade, String capaUrl) {
    }

    /** itemId identifica a música dentro da playlist (usado para remover). */
    public record ItemPlaylist(Long itemId, MusicaResponse musica) {
    }

    public record PlaylistDetalhe(Long id, String nome, List<ItemPlaylist> itens) {
    }
}
