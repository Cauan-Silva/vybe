package com.cauan.vybe.spotify;

import com.cauan.vybe.catalogo.Album;
import com.cauan.vybe.catalogo.AlbumRepository;
import com.cauan.vybe.catalogo.Artista;
import com.cauan.vybe.catalogo.ArtistaRepository;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.spotify.SpotifyApi.Faixa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Traz faixas do Spotify para o catálogo do VYBE (origem SPOTIFY).
 *
 * Só os dados da faixa são guardados: título, artista, álbum, capa, duração e o endereço
 * spotify:track:... O áudio nunca passa pelo VYBE; quem toca é o player do Spotify no navegador.
 * Com um id nosso, a faixa funciona como as outras: curtir, playlist, fila e recentes.
 */
@Service
public class SpotifyCatalogo {

    private static final int TAMANHO_MAXIMO_URL = 500;

    private final MusicaRepository musicas;
    private final ArtistaRepository artistas;
    private final AlbumRepository albuns;
    private final Clock clock;

    public SpotifyCatalogo(MusicaRepository musicas, ArtistaRepository artistas, AlbumRepository albuns, Clock clock) {
        this.musicas = musicas;
        this.artistas = artistas;
        this.albuns = albuns;
        this.clock = clock;
    }

    /**
     * Cria ou atualiza as músicas. A lista devolvida tem o mesmo tamanho e ordem da recebida,
     * com null nas faixas que não servem (arquivos locais, sem id ou sem artista).
     */
    @Transactional
    public List<MusicaResponse> salvar(List<Faixa> faixas) {
        LocalDateTime agora = LocalDateTime.now(clock);
        List<MusicaResponse> resultado = new ArrayList<>(faixas.size());
        for (Faixa faixa : faixas) {
            resultado.add(valida(faixa) ? MusicaResponse.de(salvarUma(faixa, agora)) : null);
        }
        return resultado;
    }

    static boolean valida(Faixa faixa) {
        return faixa != null
                && faixa.id() != null && !faixa.id().isBlank() && faixa.id().length() <= 50
                && !vazio(faixa.name())
                && faixa.artists() != null && !faixa.artists().isEmpty()
                && faixa.artists().get(0) != null && !vazio(faixa.artists().get(0).name());
    }

    private Musica salvarUma(Faixa faixa, LocalDateTime agora) {
        String titulo = cortar(faixa.name().trim(), 200);
        String nomeArtista = cortar(faixa.artists().get(0).name().trim(), 150);
        String tituloAlbum = faixa.album() == null || vazio(faixa.album().name())
                ? titulo
                : cortar(faixa.album().name().trim(), 200);

        Artista artista = artistas.findByNomeIgnoreCase(nomeArtista)
                .orElseGet(() -> artistas.save(new Artista(nomeArtista, agora)));
        Album album = albuns.findByArtistaIdAndTituloIgnoreCase(artista.getId(), tituloAlbum)
                .orElseGet(() -> albuns.save(new Album(tituloAlbum, artista, agora)));
        String capa = faixa.album() == null || faixa.album().images() == null || faixa.album().images().isEmpty()
                ? null
                : faixa.album().images().get(0).url();
        if (album.getCapaArquivo() == null && album.getCapaUrlExterna() == null
                && !vazio(capa) && capa.length() <= TAMANHO_MAXIMO_URL) {
            album.setCapaUrlExterna(capa);
        }

        Musica musica = musicas.findByOrigemAndIdExterno(Origem.SPOTIFY, faixa.id()).orElseGet(() -> {
            Musica nova = new Musica();
            nova.setOrigem(Origem.SPOTIFY);
            nova.setIdExterno(faixa.id());
            nova.setCriadaEm(agora);
            return nova;
        });
        musica.setTitulo(titulo);
        musica.setArtista(artista);
        musica.setAlbum(album);
        // A tabela exige duração positiva; arredonda para cima para não virar zero.
        musica.setDuracaoSegundos(Math.max(1, (faixa.duracaoMs() + 999) / 1000));
        musica.setUrlExterna(faixa.uri());
        String pagina = faixa.links() == null ? null : faixa.links().spotify();
        musica.setPaginaUrl(vazio(pagina) || pagina.length() > 300 ? null : pagina);
        return musicas.save(musica);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
