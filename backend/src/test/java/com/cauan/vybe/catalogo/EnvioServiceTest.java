package com.cauan.vybe.catalogo;

import com.cauan.vybe.arquivos.ArmazenamentoService;
import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.EnvioService.DadosEnvio;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.Usuario;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvioServiceTest {

    @Mock
    private MusicaRepository musicas;
    @Mock
    private ArtistaRepository artistas;
    @Mock
    private AlbumRepository albuns;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private ArmazenamentoService armazenamento;

    private EnvioService service;
    private final MockMultipartFile audio =
            new MockMultipartFile("arquivo", "faixa.mp3", "audio/mpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        service = new EnvioService(musicas, artistas, albuns, usuarios, armazenamento, relogio);
    }

    private Usuario usuario(long id) {
        Usuario u = new Usuario("Cauan", "cauan@email.com", "hash", LocalDateTime.now());
        u.setId(id);
        return u;
    }

    private void salvarDevolveOProprioObjetoComId() {
        when(artistas.save(any(Artista.class))).thenAnswer(inv -> {
            Artista a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });
        when(albuns.save(any(Album.class))).thenAnswer(inv -> {
            Album a = inv.getArgument(0);
            a.setId(20L);
            return a;
        });
        when(musicas.save(any(Musica.class))).thenAnswer(inv -> {
            Musica m = inv.getArgument(0);
            m.setId(30L);
            return m;
        });
    }

    @Test
    void semAlbumAMusicaViraUmSingleComOProprioTitulo() {
        salvarDevolveOProprioObjetoComId();
        when(artistas.findByNomeIgnoreCase("Banda Nova")).thenReturn(Optional.empty());
        when(albuns.findByArtistaIdAndTituloIgnoreCase(eq(10L), anyString())).thenReturn(Optional.empty());
        when(armazenamento.salvar(audio, Tipo.AUDIO)).thenReturn("abc.mp3");
        when(usuarios.getReferenceById(1L)).thenReturn(usuario(1L));

        MusicaResponse resposta = service.enviar(
                new DadosEnvio("  Primeira Faixa ", "Banda Nova", "", 185), audio, null, 1L);

        assertThat(resposta.titulo()).isEqualTo("Primeira Faixa");
        assertThat(resposta.albumTitulo()).isEqualTo("Primeira Faixa");
        assertThat(resposta.artistaNome()).isEqualTo("Banda Nova");
        assertThat(resposta.audioUrl()).isEqualTo("/api/musicas/30/audio");
        assertThat(resposta.enviadaPorId()).isEqualTo(1L);
        // Se o banco falhar, o arquivo salvo é apagado
        verify(armazenamento).apagarSeFalhar("abc.mp3", Tipo.AUDIO);
    }

    @Test
    void reaproveitaArtistaEAlbumExistentes() {
        Artista existente = new Artista("Banda Nova", LocalDateTime.now());
        existente.setId(10L);
        Album albumExistente = new Album("Disco Um", existente, LocalDateTime.now());
        albumExistente.setId(20L);
        when(artistas.findByNomeIgnoreCase("banda nova")).thenReturn(Optional.of(existente));
        when(albuns.findByArtistaIdAndTituloIgnoreCase(10L, "disco um")).thenReturn(Optional.of(albumExistente));
        when(armazenamento.salvar(audio, Tipo.AUDIO)).thenReturn("abc.mp3");
        when(usuarios.getReferenceById(1L)).thenReturn(usuario(1L));
        when(musicas.save(any(Musica.class))).thenAnswer(inv -> inv.getArgument(0));

        MusicaResponse resposta = service.enviar(
                new DadosEnvio("Faixa 2", "banda nova", "disco um", 200), audio, null, 1L);

        assertThat(resposta.artistaId()).isEqualTo(10L);
        assertThat(resposta.albumId()).isEqualTo(20L);
        verify(artistas, never()).save(any());
        verify(albuns, never()).save(any());
    }

    @Test
    void recusaDuracaoInvalidaSemSalvarArquivo() {
        assertThatThrownBy(() -> service.enviar(new DadosEnvio("Faixa", "Banda", null, 0), audio, null, 1L))
                .isInstanceOf(Excecoes.RegraNegocio.class);
        verify(armazenamento, never()).salvar(any(), any());
    }

    @Test
    void soQuemEnviouPodeExcluir() {
        Artista artista = new Artista("Banda", LocalDateTime.now());
        artista.setId(10L);
        Album album = new Album("Disco", artista, LocalDateTime.now());
        album.setId(20L);
        Musica musica = new Musica();
        musica.setId(30L);
        musica.setArtista(artista);
        musica.setAlbum(album);
        musica.setEnviadaPor(usuario(1L));
        when(musicas.findWithDetalhesById(30L)).thenReturn(Optional.of(musica));

        assertThatThrownBy(() -> service.excluir(30L, 2L)).isInstanceOf(Excecoes.AcessoNegado.class);
        verify(musicas, never()).delete(any());
    }

    @Test
    void excluirUltimaMusicaRemoveAlbumEArtista() {
        Artista artista = new Artista("Banda", LocalDateTime.now());
        artista.setId(10L);
        Album album = new Album("Disco", artista, LocalDateTime.now());
        album.setId(20L);
        album.setCapaArquivo("capa.jpg");
        Musica musica = new Musica();
        musica.setId(30L);
        musica.setArtista(artista);
        musica.setAlbum(album);
        musica.setArquivo("abc.mp3");
        musica.setEnviadaPor(usuario(1L));
        when(musicas.findWithDetalhesById(30L)).thenReturn(Optional.of(musica));
        when(musicas.countByAlbumId(20L)).thenReturn(0L);
        when(musicas.countByArtistaId(10L)).thenReturn(0L);

        service.excluir(30L, 1L);

        verify(musicas).delete(musica);
        verify(albuns).delete(album);
        verify(artistas).delete(artista);
        verify(armazenamento).apagarAposConfirmar("abc.mp3", Tipo.AUDIO);
        verify(armazenamento).apagarAposConfirmar("capa.jpg", Tipo.CAPA);
    }
}
