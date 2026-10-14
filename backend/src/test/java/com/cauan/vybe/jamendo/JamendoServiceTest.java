package com.cauan.vybe.jamendo;

import com.cauan.vybe.catalogo.Album;
import com.cauan.vybe.catalogo.AlbumRepository;
import com.cauan.vybe.catalogo.Artista;
import com.cauan.vybe.catalogo.ArtistaRepository;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.jamendo.JamendoDtos.Faixa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JamendoServiceTest {

    @Mock
    private JamendoCliente cliente;
    @Mock
    private MusicaRepository musicas;
    @Mock
    private ArtistaRepository artistas;
    @Mock
    private AlbumRepository albuns;

    private JamendoService service;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        service = new JamendoService(cliente, musicas, artistas, albuns, relogio);
    }

    private static Faixa faixa(String id, String album, int duracao) {
        return new Faixa(id, "Faixa " + id, duracao, "Artista Livre", album, "https://img/capa.jpg",
                "https://img/faixa.jpg", "https://audio/" + id + ".mp3",
                "http://creativecommons.org/licenses/by/3.0/", "https://www.jamendo.com/track/" + id);
    }

    /** Repositórios devolvem o que recebem, já com id. */
    private void bancoVazio() {
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
        lenient().when(musicas.findByOrigemAndIdExterno(any(), anyString())).thenReturn(Optional.empty());
        lenient().when(musicas.save(any(Musica.class))).thenAnswer(inv -> {
            Musica m = inv.getArgument(0);
            if (m.getId() == null) m.setId(30L);
            return m;
        });
    }

    @Test
    void semClientIdNaoChamaOJamendo() {
        when(cliente.configurado()).thenReturn(false);

        assertThat(service.buscar("lofi")).isEmpty();
        assertThat(service.populares()).isEmpty();
        verify(cliente, never()).buscar(anyString());
        verify(cliente, never()).populares();
    }

    @Test
    void importaFaixaComoMusicaDoJamendoComCreditos() {
        bancoVazio();
        when(cliente.configurado()).thenReturn(true);
        when(cliente.buscar("lofi")).thenReturn(List.of(faixa("123", "Disco Livre", 200)));

        List<MusicaResponse> resultado = service.buscar("  lofi ");

        assertThat(resultado).hasSize(1);
        MusicaResponse m = resultado.get(0);
        assertThat(m.origem()).isEqualTo(Origem.JAMENDO);
        assertThat(m.audioUrl()).isEqualTo("https://audio/123.mp3");
        assertThat(m.capaUrl()).isEqualTo("https://img/capa.jpg");
        assertThat(m.albumTitulo()).isEqualTo("Disco Livre");
        assertThat(m.licencaUrl()).contains("creativecommons.org");
        assertThat(m.paginaUrl()).isEqualTo("https://www.jamendo.com/track/123");
    }

    @Test
    void singleSemAlbumUsaOTituloDaFaixa() {
        bancoVazio();
        when(cliente.configurado()).thenReturn(true);
        when(cliente.buscar("x")).thenReturn(List.of(faixa("7", "", 150)));

        assertThat(service.buscar("x").get(0).albumTitulo()).isEqualTo("Faixa 7");
    }

    @Test
    void faixaJaImportadaEAtualizadaENaoDuplicada() {
        bancoVazio();
        Musica existente = new Musica();
        existente.setId(99L);
        existente.setOrigem(Origem.JAMENDO);
        existente.setIdExterno("123");
        existente.setUrlExterna("https://endereco-antigo.mp3");
        when(musicas.findByOrigemAndIdExterno(Origem.JAMENDO, "123")).thenReturn(Optional.of(existente));
        when(cliente.configurado()).thenReturn(true);
        when(cliente.buscar("lofi")).thenReturn(List.of(faixa("123", "Disco Livre", 200)));

        MusicaResponse m = service.buscar("lofi").get(0);

        assertThat(m.id()).isEqualTo(99L);
        assertThat(existente.getUrlExterna()).isEqualTo("https://audio/123.mp3");
    }

    @Test
    void ignoraFaixasSemDuracaoOuSemAudio() {
        bancoVazio();
        when(cliente.configurado()).thenReturn(true);
        Faixa semAudio = new Faixa("2", "Sem áudio", 100, "Artista", "", null, null, "", null, null);
        when(cliente.buscar("x")).thenReturn(List.of(faixa("1", "Disco", 0), semAudio));

        assertThat(service.buscar("x")).isEmpty();
        verify(musicas, never()).save(any());
    }

    @Test
    void popularesFicamEmCacheEntreVisitas() {
        bancoVazio();
        when(cliente.configurado()).thenReturn(true);
        when(cliente.populares()).thenReturn(List.of(faixa("5", "Disco", 180)));

        service.populares();
        service.populares();

        verify(cliente, times(1)).populares();
    }
}
