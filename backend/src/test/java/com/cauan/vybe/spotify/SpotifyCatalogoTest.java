package com.cauan.vybe.spotify;

import com.cauan.vybe.catalogo.Album;
import com.cauan.vybe.catalogo.AlbumRepository;
import com.cauan.vybe.catalogo.Artista;
import com.cauan.vybe.catalogo.ArtistaRepository;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.spotify.SpotifyApi.ArtistaSimples;
import com.cauan.vybe.spotify.SpotifyApi.Faixa;
import com.cauan.vybe.spotify.SpotifyApi.Imagem;
import com.cauan.vybe.spotify.SpotifyApi.Links;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpotifyCatalogoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 14, 12, 0);

    @Mock
    private MusicaRepository musicas;
    @Mock
    private ArtistaRepository artistas;
    @Mock
    private AlbumRepository albuns;

    private SpotifyCatalogo catalogo;

    @BeforeEach
    void setUp() {
        catalogo = new SpotifyCatalogo(musicas, artistas, albuns,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
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
        lenient().when(musicas.findByOrigemAndIdExterno(eq(Origem.SPOTIFY), anyString())).thenReturn(Optional.empty());
        lenient().when(musicas.save(any(Musica.class))).thenAnswer(inv -> {
            Musica m = inv.getArgument(0);
            if (m.getId() == null) m.setId(30L);
            return m;
        });
    }

    private static Faixa faixa(String id, String titulo, String artista, int duracaoMs) {
        return new Faixa(id, titulo, "track", duracaoMs, List.of(new ArtistaSimples(artista)),
                new SpotifyApi.Album("Meteora", List.of(new Imagem("https://i.scdn.co/capa.jpg"))),
                new Links("https://open.spotify.com/track/" + id));
    }

    @Test
    void guardaOsDadosEOEnderecoDoPlayer() {
        List<MusicaResponse> salvas = catalogo.salvar(List.of(faixa("abc123", "Numb", "Linkin Park", 185_586)));

        MusicaResponse m = salvas.get(0);
        assertThat(m.origem()).isEqualTo(Origem.SPOTIFY);
        assertThat(m.audioUrl()).isEqualTo("spotify:track:abc123");
        assertThat(m.duracaoSegundos()).isEqualTo(186); // arredonda para cima
        assertThat(m.artistaNome()).isEqualTo("Linkin Park");
        assertThat(m.albumTitulo()).isEqualTo("Meteora");
        assertThat(m.capaUrl()).isEqualTo("https://i.scdn.co/capa.jpg");
        assertThat(m.paginaUrl()).isEqualTo("https://open.spotify.com/track/abc123");
    }

    @Test
    void faixasInvalidasViramNuloNaMesmaPosicao() {
        Faixa semId = new Faixa(null, "Arquivo local", "track", 100_000, List.of(new ArtistaSimples("Eu")), null, null);
        Faixa semArtista = new Faixa("x1", "Sem artista", "track", 100_000, List.of(), null, null);

        List<MusicaResponse> salvas = catalogo.salvar(List.of(semId, faixa("ok1", "Numb", "Linkin Park", 1_000), semArtista));

        assertThat(salvas).hasSize(3);
        assertThat(salvas.get(0)).isNull();
        assertThat(salvas.get(1)).isNotNull();
        assertThat(salvas.get(2)).isNull();
    }

    @Test
    void reaproveitaAMusicaJaImportada() {
        Musica existente = new Musica();
        existente.setId(99L);
        existente.setOrigem(Origem.SPOTIFY);
        existente.setIdExterno("abc123");
        lenient().when(musicas.findByOrigemAndIdExterno(Origem.SPOTIFY, "abc123")).thenReturn(Optional.of(existente));

        MusicaResponse m = catalogo.salvar(List.of(faixa("abc123", "Numb", "Linkin Park", 185_000))).get(0);

        assertThat(m.id()).isEqualTo(99L);
        verify(musicas, never()).findByOrigemAndIdExterno(eq(Origem.JAMENDO), anyString());
    }
}
