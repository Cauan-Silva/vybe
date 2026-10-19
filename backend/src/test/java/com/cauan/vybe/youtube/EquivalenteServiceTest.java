package com.cauan.vybe.youtube;

import com.cauan.vybe.catalogo.Album;
import com.cauan.vybe.catalogo.Artista;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.youtube.YoutubeDtos.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquivalenteServiceTest {

    @Mock
    private MusicaRepository musicas;
    @Mock
    private YoutubeService youtube;

    private EquivalenteService service;
    private Musica musica;

    @BeforeEach
    void setUp() {
        service = new EquivalenteService(musicas, youtube);
        Artista artista = new Artista("Pageless", LocalDateTime.now());
        musica = new Musica();
        musica.setId(7L);
        musica.setTitulo("Stranger Than Heaven");
        musica.setArtista(artista);
        musica.setAlbum(new Album("Stranger Than Heaven", artista, LocalDateTime.now()));
        musica.setDuracaoSegundos(231);
        musica.setOrigem(Origem.SPOTIFY);
        lenient().when(musicas.findById(7L)).thenReturn(Optional.of(musica));
        lenient().when(youtube.disponivel()).thenReturn(true);
    }

    @Test
    void achaGuardaENaoBuscaDeNovo() {
        when(youtube.videosDeMusica("Pageless stranger than heaven")).thenReturn(List.of(
                new Video("ok000000001", "Pageless - Stranger Than Heaven (Official Visualizer)", "Pageless", null, 233, true)));

        assertThat(service.equivalente(7L).videoId()).isEqualTo("ok000000001");
        assertThat(musica.getYoutubeEquivalente()).isEqualTo("ok000000001");

        service.equivalente(7L); // já guardado: não gasta cota
        verify(youtube, org.mockito.Mockito.times(1)).videosDeMusica(any());
    }

    @Test
    void semResultadoBomAvisaQueNaoAchou() {
        when(youtube.videosDeMusica(any())).thenReturn(List.of(new Video("outra000001", "Outra coisa", "Canal", null, 600, true)));

        assertThatThrownBy(() -> service.equivalente(7L)).isInstanceOf(Excecoes.NaoEncontrado.class);
        assertThat(musica.getYoutubeEquivalente()).isNull();
    }

    @Test
    void soParaMusicasDoSpotify() {
        musica.setOrigem(Origem.YOUTUBE);

        assertThatThrownBy(() -> service.equivalente(7L)).isInstanceOf(Excecoes.RegraNegocio.class);
        verify(youtube, never()).videosDeMusica(any());
    }
}
