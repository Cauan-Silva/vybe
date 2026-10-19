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
import com.cauan.vybe.youtube.YoutubeDtos.VideoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

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
    /** Em alta muda ao longo do dia, mas custa só 1 unidade: guarda por 1 hora. */
    static final Duration VALIDADE_EM_ALTA = Duration.ofHours(1);
    static final int LIMITE_VIDEOS = 12;
    private static final int MAXIMO_NO_CACHE = 300;
    private static final int TAMANHO_MAXIMO_URL = 500;

    private record Guardado(List<Video> videos, Instant em) {
    }

    private record GuardadoCanal(YoutubeDtos.CanalResponse canal, Instant em) {
    }

    private final YoutubeCliente cliente;
    private final MusicaRepository musicas;
    private final ArtistaRepository artistas;
    private final AlbumRepository albuns;
    private final Clock clock;
    private final Map<String, Guardado> cache = new ConcurrentHashMap<>();
    private final Map<String, GuardadoCanal> canais = new ConcurrentHashMap<>();

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

    /** Busca de músicas: os vídeos viram músicas do catálogo, prontas para o player. */
    @Transactional
    public List<MusicaResponse> buscar(String termo) {
        String limpo = limpar(termo);
        if (!disponivel() || limpo.isEmpty()) {
            return List.of();
        }
        return importar(doCache("musica:" + limpo, VALIDADE_CACHE, () -> cliente.buscar(limpo)));
    }

    /** Aba Vídeos: busca qualquer vídeo (sem o filtro de música) e não grava nada no banco. */
    public List<VideoResponse> buscarVideos(String termo) {
        String limpo = limpar(termo);
        if (!disponivel() || limpo.isEmpty()) {
            return List.of();
        }
        return paraVideos(doCache("video:" + limpo, VALIDADE_CACHE,
                () -> cliente.buscar(limpo, false, LIMITE_VIDEOS)));
    }

    /** Aba Vídeos, antes de buscar: os vídeos em alta no Brasil. */
    public List<VideoResponse> videosEmAlta() {
        return videosEmAlta(null);
    }

    /** Em alta no Brasil, de uma categoria do YouTube (ex.: 20 = Games) ou de todas (null). */
    public List<VideoResponse> videosEmAlta(Integer categoria) {
        if (!disponivel()) {
            return List.of();
        }
        if (categoria != null && (categoria < 1 || categoria > 50)) {
            throw new com.cauan.vybe.comum.Excecoes.RegraNegocio("Categoria inválida");
        }
        return paraVideos(doCache("em-alta:" + (categoria == null ? "todas" : categoria), VALIDADE_EM_ALTA,
                () -> cliente.populares(LIMITE_VIDEOS, categoria)));
    }

    /**
     * Página do canal: dados e vídeos mais recentes. Custa cerca de 3 unidades (canal, lista
     * de uploads e detalhes) e fica 1 hora em cache.
     */
    public YoutubeDtos.CanalResponse canal(String canalId) {
        if (!disponivel()) {
            throw new com.cauan.vybe.comum.Excecoes.RegraNegocio("A integração com o YouTube não está configurada.");
        }
        if (canalId == null || !canalId.matches("[A-Za-z0-9_-]{10,40}")) {
            throw new com.cauan.vybe.comum.Excecoes.NaoEncontrado("Canal não encontrado");
        }
        Instant agora = clock.instant();
        GuardadoCanal guardado = canais.get(canalId);
        if (guardado != null && agora.isBefore(guardado.em().plus(VALIDADE_EM_ALTA))) {
            return guardado.canal();
        }
        YoutubeDtos.CanalInfo info = cliente.canal(canalId);
        List<Video> videos = info.uploads() == null ? List.of() : cliente.videosDaPlaylist(info.uploads(), LIMITE_VIDEOS);
        YoutubeDtos.CanalResponse canal = new YoutubeDtos.CanalResponse(
                info.id(),
                info.nome() == null ? "Canal" : HtmlUtils.htmlUnescape(info.nome()),
                info.avatarUrl(),
                info.inscritos(),
                info.totalVideos(),
                paraVideos(videos));
        if (canais.size() >= MAXIMO_NO_CACHE) {
            canais.clear();
        }
        canais.put(canalId, new GuardadoCanal(canal, agora));
        return canal;
    }

    private static String limpar(String termo) {
        String limpo = termo == null ? "" : termo.trim().replaceAll("\\s+", " ");
        return limpo.length() > 100 ? limpo.substring(0, 100) : limpo;
    }

    /** Quanto tempo para trás valem os vídeos de um interesse. */
    static final Duration RECENCIA_INTERESSE = Duration.ofDays(30);
    static final int LIMITE_INTERESSE = 8;

    /** Vídeos recentes e relevantes sobre um tema (100 unidades, guardado 6 horas). */
    public List<VideoResponse> videosDoInteresse(String termo) {
        String limpo = limpar(termo);
        if (limpo.isEmpty() || !disponivel()) {
            return List.of();
        }
        return paraVideos(doCache("interesse:" + limpo, VALIDADE_CACHE,
                () -> cliente.buscarRecentes(limpo, LIMITE_INTERESSE, clock.instant().minus(RECENCIA_INTERESSE))));
    }

    /** Vídeos do cache (se ainda valem) ou da API. */
    private List<Video> doCache(String chaveBruta, Duration validade, java.util.function.Supplier<List<Video>> buscar) {
        String chave = chaveBruta.toLowerCase(Locale.ROOT);
        Instant agora = clock.instant();
        Guardado guardado = cache.get(chave);
        if (guardado != null && agora.isBefore(guardado.em().plus(validade))) {
            return guardado.videos();
        }
        List<Video> novos = buscar.get();
        if (cache.size() >= MAXIMO_NO_CACHE) {
            cache.clear(); // simples: começa de novo em vez de controlar qual é o mais antigo
        }
        cache.put(chave, new Guardado(novos, agora));
        return novos;
    }

    /** Só os que podem ser embutidos; o título vem com entidades HTML (&amp;, &#39;) e é decodificado. */
    static List<VideoResponse> paraVideos(List<Video> videos) {
        return videos.stream()
                .filter(v -> v.id() != null && v.incorporavel())
                .map(v -> new VideoResponse(
                        v.id(),
                        v.titulo() == null ? "Sem título" : HtmlUtils.htmlUnescape(v.titulo()),
                        v.canal() == null ? "" : HtmlUtils.htmlUnescape(v.canal()),
                        v.canalId(),
                        v.capaUrl(),
                        v.duracaoSegundos(),
                        v.visualizacoes(),
                        v.curtidas(),
                        v.publicadoEm()))
                .toList();
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
