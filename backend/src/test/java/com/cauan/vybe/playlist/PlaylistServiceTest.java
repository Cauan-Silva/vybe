package com.cauan.vybe.playlist;

import com.cauan.vybe.catalogo.Album;
import com.cauan.vybe.catalogo.Artista;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.Usuario;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private PlaylistRepository playlists;
    @Mock
    private PlaylistMusicaRepository itens;
    @Mock
    private MusicaRepository musicas;
    @Mock
    private UsuarioRepository usuarios;

    private PlaylistService service;
    private Playlist minhaPlaylist;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        service = new PlaylistService(playlists, itens, musicas, usuarios, relogio);

        Usuario eu = new Usuario("Cauan", "cauan@email.com", "hash", LocalDateTime.now());
        eu.setId(1L);
        minhaPlaylist = new Playlist("Para estudar", eu, LocalDateTime.now());
        minhaPlaylist.setId(5L);
    }

    private Musica musica(long id) {
        Artista artista = new Artista("Banda", LocalDateTime.now());
        artista.setId(10L);
        Album album = new Album("Disco", artista, LocalDateTime.now());
        album.setId(20L);
        Musica m = new Musica();
        m.setId(id);
        m.setTitulo("Faixa " + id);
        m.setArtista(artista);
        m.setAlbum(album);
        return m;
    }

    @Test
    void playlistDeOutraPessoaNaoAparece() {
        when(playlists.findByIdAndDonoId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhe(2L, 5L)).isInstanceOf(Excecoes.NaoEncontrado.class);
    }

    @Test
    void adicionaNoFimDaPlaylist() {
        when(playlists.findByIdAndDonoId(5L, 1L)).thenReturn(Optional.of(minhaPlaylist));
        when(itens.existsByPlaylistIdAndMusicaId(5L, 30L)).thenReturn(false);
        when(musicas.findById(30L)).thenReturn(Optional.of(musica(30L)));
        when(itens.maiorPosicao(5L)).thenReturn(3);
        when(itens.findByPlaylistIdOrderByPosicaoAsc(5L)).thenReturn(List.of());

        service.adicionar(1L, 5L, 30L);

        ArgumentCaptor<PlaylistMusica> salvo = ArgumentCaptor.forClass(PlaylistMusica.class);
        verify(itens).save(salvo.capture());
        assertThat(salvo.getValue().getPosicao()).isEqualTo(4);
        assertThat(minhaPlaylist.getAtualizadaEm()).isEqualTo(LocalDateTime.of(2026, 10, 14, 12, 0));
    }

    @Test
    void naoDeixaAMesmaMusicaEntrarDuasVezes() {
        when(playlists.findByIdAndDonoId(5L, 1L)).thenReturn(Optional.of(minhaPlaylist));
        when(itens.existsByPlaylistIdAndMusicaId(5L, 30L)).thenReturn(true);

        assertThatThrownBy(() -> service.adicionar(1L, 5L, 30L))
                .isInstanceOf(Excecoes.Conflito.class)
                .hasMessageContaining("Para estudar");
        verify(itens, never()).save(any());
    }

    @Test
    void naoRemoveItemDeOutraPlaylist() {
        when(playlists.findByIdAndDonoId(5L, 1L)).thenReturn(Optional.of(minhaPlaylist));
        when(itens.findByIdAndPlaylistId(99L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(1L, 5L, 99L)).isInstanceOf(Excecoes.NaoEncontrado.class);
        verify(itens, never()).delete(any());
    }
}
