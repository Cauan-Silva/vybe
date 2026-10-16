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
}
