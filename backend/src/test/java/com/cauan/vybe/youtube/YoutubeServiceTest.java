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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YoutubeServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-14T15:00:00Z");

    @Mock
    private YoutubeCliente cliente;
    @Mock
    private MusicaRepository musicas;
    @Mock
    private ArtistaRepository artistas;
    @Mock
    private AlbumRepository albuns;

    /** Relógio que o teste pode adiantar. */
    private final AtomicReference<Instant> instante = new AtomicReference<>(AGORA);
    private YoutubeService service;

    @BeforeEach
    void setUp() {
        Clock relogio = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zona) { return this; }
            @Override public Instant instant() { return instante.get(); }
        };
        service = new YoutubeService(cliente, musicas, artistas, albuns, relogio);

        lenient().when(cliente.configurado()).thenReturn(true);
        lenient().when(artistas.findByNomeIgnoreCase(anyString())).thenReturn(Optional.empty());
        lenient().when(artistas.save(any(Artista.class))).thenAnswer(inv -> {
            Artista a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });
        lenient().when(albuns.findByArtistaIdAndTituloIgnoreCase(anyLong(), anyString())).thenReturn(Optional.empty());
        lenient().when(albuns.save(any(Album.class))).thenAnswer(inv -> {
            Album a = inv.getArgument(0);
            a.setId(20L);
            return a;
        });
        lenient().when(musicas.findByOrigemAndIdExterno(eq(Origem.YOUTUBE), anyString())).thenReturn(Optional.empty());
        lenient().when(musicas.save(any(Musica.class))).thenAnswer(inv -> {
            Musica m = inv.getArgument(0);
            if (m.getId() == null) m.setId(30L);
            return m;
        });
    }

    private static Video video(String id, String titulo, int duracao, boolean incorporavel) {
        return new Video(id, titulo, "Canal", "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg", duracao, incorporavel);
    }

    @Test
    void transformaOVideoEmMusicaComOEnderecoDoPlayer() {
        when(cliente.buscar("numb")).thenReturn(List.of(video("kXYiU_JCYtU", "Linkin Park - Numb (Official Video)", 187, true)));

        MusicaResponse m = service.buscar("numb").get(0);

        assertThat(m.origem()).isEqualTo(Origem.YOUTUBE);
        assertThat(m.audioUrl()).isEqualTo("youtube:kXYiU_JCYtU");
        assertThat(m.paginaUrl()).isEqualTo("https://www.youtube.com/watch?v=kXYiU_JCYtU");
        assertThat(m.artistaNome()).isEqualTo("Linkin Park");
        assertThat(m.titulo()).isEqualTo("Numb");
        assertThat(m.duracaoSegundos()).isEqualTo(187);
    }

    @Test
    void deixaDeForaAoVivoENaoIncorporaveis() {
        when(cliente.buscar("show")).thenReturn(List.of(
                video("aovivo00001", "Live 24/7", 0, true),
                video("bloqueado01", "Artista - Bloqueado", 200, false),
                video("bom00000001", "Artista - Boa", 200, true)));

        assertThat(service.buscar("show")).extracting(MusicaResponse::titulo).containsExactly("Boa");
    }

    @Test
    void repeteABuscaDoCacheSemGastarCota() {
        when(cliente.buscar("numb")).thenReturn(List.of());

        service.buscar("numb");
        service.buscar("  NUMB ");
        verify(cliente, times(1)).buscar(anyString());

        instante.set(AGORA.plus(YoutubeService.VALIDADE_CACHE).plus(Duration.ofMinutes(1)));
        service.buscar("numb");
        verify(cliente, times(2)).buscar(anyString());
    }

    @Test
    void semChaveNaoChamaAApi() {
        when(cliente.configurado()).thenReturn(false);

        assertThat(service.buscar("numb")).isEmpty();
        verify(cliente, never()).buscar(anyString());
    }

    @Test
    void lerDuracaoNoFormatoDoYoutube() {
        assertThat(YoutubeCliente.segundos("PT4M13S")).isEqualTo(253);
        assertThat(YoutubeCliente.segundos("PT1H2M3S")).isEqualTo(3723);
        assertThat(YoutubeCliente.segundos("P0D")).isZero();
        assertThat(YoutubeCliente.segundos("bobagem")).isZero();
    }

    // ---------- Aba Vídeos ----------

    @Test
    void videosEmGeralNaoGravamNadaEDecodificamOTitulo() {
        when(cliente.buscar("receita", false, YoutubeService.LIMITE_VIDEOS)).thenReturn(List.of(
                new Video("receita0001", "Bolo de cenoura &amp; cobertura", "Cozinha d&#39;Ana", null, 600, true),
                new Video("bloqueado01", "Não embutível", "Canal", null, 300, false)));

        List<YoutubeDtos.VideoResponse> videos = service.buscarVideos("receita");

        assertThat(videos).hasSize(1);
        assertThat(videos.get(0).titulo()).isEqualTo("Bolo de cenoura & cobertura");
        assertThat(videos.get(0).canal()).isEqualTo("Cozinha d'Ana");
        verify(musicas, never()).save(any());
    }

    @Test
    void buscaDeVideosEDeMusicasTemCachesSeparados() {
        when(cliente.buscar("numb")).thenReturn(List.of());
        when(cliente.buscar("numb", false, YoutubeService.LIMITE_VIDEOS)).thenReturn(List.of());

        service.buscar("numb");
        service.buscarVideos("numb");

        verify(cliente).buscar("numb");
        verify(cliente).buscar("numb", false, YoutubeService.LIMITE_VIDEOS);
    }

    @Test
    void emAltaFicaUmaHoraEmCache() {
        when(cliente.populares(YoutubeService.LIMITE_VIDEOS, null))
                .thenReturn(List.of(new Video("emalta00001", "Em alta", "Canal", null, 0, true)));

        assertThat(service.videosEmAlta()).extracting(YoutubeDtos.VideoResponse::duracaoSegundos).containsExactly(0);
        service.videosEmAlta();
        verify(cliente, times(1)).populares(YoutubeService.LIMITE_VIDEOS, null);

        instante.set(AGORA.plus(YoutubeService.VALIDADE_EM_ALTA).plus(Duration.ofMinutes(1)));
        service.videosEmAlta();
        verify(cliente, times(2)).populares(YoutubeService.LIMITE_VIDEOS, null);
    }

    // ---------- Categorias e canal ----------

    @Test
    void emAltaPorCategoriaTemCachePorCategoria() {
        when(cliente.populares(YoutubeService.LIMITE_VIDEOS, 20)).thenReturn(List.of());
        when(cliente.populares(YoutubeService.LIMITE_VIDEOS, 17)).thenReturn(List.of());

        service.videosEmAlta(20);
        service.videosEmAlta(20);
        service.videosEmAlta(17);

        verify(cliente, times(1)).populares(YoutubeService.LIMITE_VIDEOS, 20);
        verify(cliente, times(1)).populares(YoutubeService.LIMITE_VIDEOS, 17);
    }

    @Test
    void canalTrazOsDadosEOsVideosRecentesComCache() {
        String id = "UCabcdefghijklmnopqrstuv";
        when(cliente.canal(id)).thenReturn(new YoutubeDtos.CanalInfo(id, "Cozinha d&#39;Ana", null, 1200L, 80L, "UUabc"));
        when(cliente.videosDaPlaylist("UUabc", YoutubeService.LIMITE_VIDEOS)).thenReturn(List.of(
                new Video("receita0001", "Bolo", "Cozinha", null, 600, true, id, "2026-10-10T12:00:00Z", 5000L, 300L)));

        YoutubeDtos.CanalResponse canal = service.canal(id);
        service.canal(id);

        assertThat(canal.nome()).isEqualTo("Cozinha d'Ana");
        assertThat(canal.inscritos()).isEqualTo(1200L);
        assertThat(canal.videos()).extracting(YoutubeDtos.VideoResponse::visualizacoes).containsExactly(5000L);
        verify(cliente, times(1)).canal(id);
    }

    @Test
    void canalComIdEstranhoNemChamaAApi() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.canal("../x"))
                .isInstanceOf(com.cauan.vybe.comum.Excecoes.NaoEncontrado.class);
        verify(cliente, never()).canal(anyString());
    }

    // ---------- Interesses ----------

    @Test
    void interesseBuscaVideosDosUltimos30DiasEGuardaSeisHoras() {
        when(cliente.buscarRecentes(eq("valorant"), eq(YoutubeService.LIMITE_INTERESSE), any(Instant.class)))
                .thenReturn(List.of(new Video("valorant001", "Clutch 1v5", "Canal", null, 600, true)));

        assertThat(service.videosDoInteresse("  valorant ")).extracting(YoutubeDtos.VideoResponse::titulo)
                .containsExactly("Clutch 1v5");
        service.videosDoInteresse("VALORANT"); // mesma busca: vem do cache
        verify(cliente, times(1)).buscarRecentes(eq("valorant"), eq(YoutubeService.LIMITE_INTERESSE),
                eq(AGORA.minus(YoutubeService.RECENCIA_INTERESSE)));
    }

    @Test
    void interesseVazioOuSemChaveNaoGastaCota() {
        assertThat(service.videosDoInteresse("   ")).isEmpty();
        lenient().when(cliente.configurado()).thenReturn(false);
        assertThat(service.videosDoInteresse("valorant")).isEmpty();
        verify(cliente, never()).buscarRecentes(anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
