package com.cauan.vybe.biblioteca;

import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BibliotecaServiceTest {

    @Mock
    private CurtidaRepository curtidas;
    @Mock
    private ReproducaoRepository reproducoes;
    @Mock
    private MusicaRepository musicas;
    @Mock
    private UsuarioRepository usuarios;

    private BibliotecaService service;

    @BeforeEach
    void setUp() {
        service = new BibliotecaService(curtidas, reproducoes, musicas, usuarios, Clock.systemUTC());
    }

    private static Reproducao reproducao(int duracaoMusica) {
        Musica m = new Musica();
        m.setDuracaoSegundos(duracaoMusica);
        return new Reproducao(null, m, LocalDateTime.now());
    }

    @Test
    void novaReproducaoComecaComZeroSegundos() {
        assertThat(reproducao(200).getSegundosOuvidos()).isZero();
    }

    @Test
    void guardaOTempoOuvido() {
        Reproducao r = reproducao(200);
        when(reproducoes.findByIdAndUsuarioId(5L, 1L)).thenReturn(Optional.of(r));

        service.registrarTempo(1L, 5L, 95);

        assertThat(r.getSegundosOuvidos()).isEqualTo(95);
    }

    @Test
    void tempoNaoPassaDaDuracaoComFolgaNemFicaNegativo() {
        Reproducao r = reproducao(200);
        when(reproducoes.findByIdAndUsuarioId(5L, 1L)).thenReturn(Optional.of(r));

        service.registrarTempo(1L, 5L, 99_999);
        assertThat(r.getSegundosOuvidos()).isEqualTo(200 + BibliotecaService.FOLGA_SEGUNDOS);

        service.registrarTempo(1L, 5L, -10);
        assertThat(r.getSegundosOuvidos()).isZero();
    }

    @Test
    void reproducaoDeOutraPessoaNaoPodeSerAlterada() {
        when(reproducoes.findByIdAndUsuarioId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarTempo(2L, 5L, 30))
                .isInstanceOf(Excecoes.NaoEncontrado.class);
    }
}
