package com.cauan.vybe.youtube;

import com.cauan.vybe.catalogo.Album;
import com.cauan.vybe.catalogo.AlbumRepository;
import com.cauan.vybe.catalogo.Artista;
import com.cauan.vybe.catalogo.ArtistaRepository;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.youtube.YoutubeDtos.Video;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traz vídeos do YouTube para o catálogo (origem YOUTUBE).
 *
 * Como cada busca gasta 1% da cota diária, os resultados ficam guardados em memória por
 * 6 horas: buscar "linkin park" de novo não gasta nada. Cada vídeo vira uma Musica com id
 * nosso (curtir, playlist, fila e recentes); quem toca é o player oficial do YouTube.
 */
@Service
public class YoutubeService {

    static final Duration VALIDADE_CACHE = Duration.ofHours(6);
    private static final int MAXIMO_NO_CACHE = 300;
    private static final int TAMANHO_MAXIMO_URL = 500;

    private record Guardado(List<Video> videos, Instant em) {
    }

    private final YoutubeCliente cliente;
    private final MusicaRepository musicas;
    private final ArtistaRepository artistas;
    private final AlbumRepository albuns;
    private final Clock clock;
    private final Map<String, Guardado> cache = new ConcurrentHashMap<>();

    public YoutubeService(YoutubeCliente cliente,
                          MusicaRepository musicas,
                          ArtistaRepository artistas,
                          AlbumRepository albuns,
                          Clock clock) {
        this.cliente = cliente;
        this.musicas = musicas;
        this.artistas = artistas;
        this.albuns = albuns;
        this.clock = clock;
    }

    public boolean disponivel() {
        return cliente.configurado();
    }

    @Transactional
    public List<MusicaResponse> buscar(String termo) {
        String limpo = termo == null ? "" : termo.trim().replaceAll("\\s+", " ");
        if (!disponivel() || limpo.isEmpty()) {
            return List.of();
        }
        if (limpo.length() > 100) {
            limpo = limpo.substring(0, 100);
        }
        return importar(videos(limpo));
    }

    /** Vídeos do cache (se ainda valem) ou da API. */
    private List<Video> videos(String termo) {
        String chave = termo.toLowerCase(Locale.ROOT);
        Instant agora = clock.instant();
        Guardado guardado = cache.get(chave);
        if (guardado != null && agora.isBefore(guardado.em().plus(VALIDADE_CACHE))) {
            return guardado.videos();
        }
        List<Video> novos = cliente.buscar(termo);
        if (cache.size() >= MAXIMO_NO_CACHE) {
            cache.clear(); // simples: começa de novo em vez de controlar qual é o mais antigo
        }
        cache.put(chave, new Guardado(novos, agora));
        return novos;
    }

    /** Cria ou atualiza as músicas. Vídeos ao vivo, sem duração ou que não podem ser embutidos ficam de fora. */
    List<MusicaResponse> importar(List<Video> videos) {
        LocalDateTime agora = LocalDateTime.now(clock);
        List<MusicaResponse> resultado = new ArrayList<>();

        for (Video video : videos) {
            if (video.id() == null || video.id().isBlank() || video.id().length() > 50
                    || video.duracaoSegundos() <= 0 || !video.incorporavel()) {
                continue;
            }
            TituloVideo.Interpretacao nome = TituloVideo.interpretar(video.titulo(), video.canal());
            String titulo = cortar(nome.titulo(), 200);
            String nomeArtista = cortar(nome.artista(), 150);

            Artista artista = artistas.findByNomeIgnoreCase(nomeArtista)
                    .orElseGet(() -> artistas.save(new Artista(nomeArtista, agora)));
            // Vídeo não tem álbum: vira um "single" com o próprio título, como nos envios.
            Album album = albuns.findByArtistaIdAndTituloIgnoreCase(artista.getId(), titulo)
                    .orElseGet(() -> albuns.save(new Album(titulo, artista, agora)));
            if (album.getCapaArquivo() == null && album.getCapaUrlExterna() == null
                    && video.capaUrl() != null && video.capaUrl().length() <= TAMANHO_MAXIMO_URL) {
                album.setCapaUrlExterna(video.capaUrl());
            }

            Musica musica = musicas.findByOrigemAndIdExterno(Origem.YOUTUBE, video.id()).orElseGet(() -> {
                Musica nova = new Musica();
                nova.setOrigem(Origem.YOUTUBE);
                nova.setIdExterno(video.id());
                nova.setCriadaEm(agora);
                return nova;
            });
            musica.setTitulo(titulo);
            musica.setArtista(artista);
            musica.setAlbum(album);
            musica.setDuracaoSegundos(video.duracaoSegundos());
            musica.setUrlExterna("youtube:" + video.id());
            musica.setPaginaUrl("https://www.youtube.com/watch?v=" + video.id());

            resultado.add(MusicaResponse.de(musicas.save(musica)));
        }
        return resultado;
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
