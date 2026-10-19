package com.cauan.vybe.youtube;

import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.spotify.Correspondencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * App Android: o Spotify não libera o player dele em apps de celular, então as músicas do
 * Spotify tocam pelo vídeo equivalente no YouTube (player oficial). A primeira vez custa uma
 * busca (100 unidades da cota); depois o vídeo fica guardado na própria música.
 */
@Service
public class EquivalenteService {

    public record Equivalente(String videoId) {
    }

    private final MusicaRepository musicas;
    private final YoutubeService youtube;

    public EquivalenteService(MusicaRepository musicas, YoutubeService youtube) {
        this.musicas = musicas;
        this.youtube = youtube;
    }

    @Transactional
    public Equivalente equivalente(Long musicaId) {
        Musica musica = musicas.findById(musicaId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Música não encontrada"));
        if (musica.getOrigem() != Origem.SPOTIFY) {
            throw new Excecoes.RegraNegocio("Só músicas do Spotify precisam de equivalente no YouTube");
        }
        if (musica.getYoutubeEquivalente() != null) {
            return new Equivalente(musica.getYoutubeEquivalente());
        }
        if (!youtube.disponivel()) {
            throw new Excecoes.RegraNegocio("Para tocar músicas do Spotify no app, o servidor precisa da chave do YouTube.");
        }
        String artista = musica.getArtista().getNome();
        String termo = artista + " " + Correspondencia.normalizar(musica.getTitulo());
        var escolhido = Equivalencia.escolher(youtube.videosDeMusica(termo), musica.getTitulo(), artista,
                        musica.getDuracaoSegundos())
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Não achamos essa música no YouTube"));
        musica.setYoutubeEquivalente(escolhido.id());
        return new Equivalente(escolhido.id());
    }
}
