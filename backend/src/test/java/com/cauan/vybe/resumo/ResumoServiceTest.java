package com.cauan.vybe.resumo;

import com.cauan.vybe.biblioteca.ReproducaoRepository;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.resumo.ResumoDtos.ResumoResponse;
import com.cauan.vybe.youtube.VisualizacaoVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumoServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 19, 8, 0);

    @Mock
    private ReproducaoRepository reproducoes;
    @Mock
    private VisualizacaoVideoRepository visualizacoes;
    @Mock
    private MusicaRepository musicas;

    private ResumoService service;

    record PontoMusica(LocalDateTime quando, Integer segundos) implements ReproducaoRepository.Ponto {
        public LocalDateTime getQuando() { return quando; }
        public Integer getSegundos() { return segundos; }
    }

    record PontoVideo(LocalDateTime quando, Integer segundos) implements VisualizacaoVideoRepository.Ponto {
        public LocalDateTime getQuando() { return quando; }
        public Integer getSegundos() { return segundos; }
    }

    record Artista(Long id, String nome, Long vezes, Long segundos) implements ReproducaoRepository.ContagemArtista {
        public Long getId() { return id; }
        public String getNome() { return nome; }
        public Long getVezes() { return vezes; }
        public Long getSegundos() { return segundos; }
    }

    @BeforeEach
    void setUp() {
        service = new ResumoService(reproducoes, visualizacoes, musicas,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        lenient().when(reproducoes.pontos(any(), any(), any())).thenReturn(List.of());
        lenient().when(visualizacoes.pontos(any(), any(), any())).thenReturn(List.of());
        lenient().when(reproducoes.topMusicas(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(reproducoes.topArtistas(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(visualizacoes.topVideos(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(visualizacoes.topCanais(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void semParametroUsaOMesAtualComUmDiaPorDiaDoMes() {
        ResumoResponse r = service.resumo(1L, null);

        assertThat(r.mes()).isEqualTo("2026-10");
        assertThat(r.dias()).hasSize(31);
        assertThat(r.mesSeguinte()).isNull(); // não dá para ir ao futuro
        assertThat(r.horaFavorita()).isNull();
        assertThat(r.diaFavorito()).isNull();
        verify(reproducoes).pontos(eq(1L), eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 11, 1, 0, 0)));
    }

    @Test
    void somaTemposMontaALinhaDoTempoEAchaDiaEHoraPreferidos() {
        lenient().when(reproducoes.pontos(any(), any(), any())).thenReturn(List.of(
                new PontoMusica(LocalDateTime.of(2026, 9, 3, 22, 10), 200),
                new PontoMusica(LocalDateTime.of(2026, 9, 3, 22, 40), 180),
                new PontoMusica(LocalDateTime.of(2026, 9, 10, 9, 0), 120),
                new PontoMusica(LocalDateTime.of(2026, 9, 11, 9, 0), null))); // antiga sem tempo
        lenient().when(visualizacoes.pontos(any(), any(), any())).thenReturn(List.of(
                new PontoVideo(LocalDateTime.of(2026, 9, 10, 9, 30), 600)));

        ResumoResponse r = service.resumo(1L, "2026-09");

        assertThat(r.segundosMusica()).isEqualTo(500);
        assertThat(r.segundosVideo()).isEqualTo(600);
        assertThat(r.reproducoes()).isEqualTo(4);
        assertThat(r.videosAssistidos()).isEqualTo(1);
        assertThat(r.dias()).hasSize(30);
        assertThat(r.dias().get(2).segundosMusica()).isEqualTo(380);
        assertThat(r.dias().get(9).segundosVideo()).isEqualTo(600);
        assertThat(r.horaFavorita()).isEqualTo(9); // 120 + 600 às 9h contra 380 às 22h
        assertThat(r.diaFavorito()).isEqualTo("2026-09-10");
        assertThat(r.mesSeguinte()).isEqualTo("2026-10");
    }

    @Test
    void mesAnteriorSoApareceSeHouverRegistroAntes() {
        lenient().when(reproducoes.primeira(1L)).thenReturn(LocalDateTime.of(2026, 10, 2, 10, 0));
        lenient().when(visualizacoes.primeira(1L)).thenReturn(LocalDateTime.of(2026, 8, 20, 10, 0));

        assertThat(service.resumo(1L, "2026-10").mesAnterior()).isEqualTo("2026-09");
        assertThat(service.resumo(1L, "2026-08").mesAnterior()).isNull();
    }

    @Test
    void rankingDeArtistasVemDoBanco() {
        lenient().when(reproducoes.topArtistas(any(), any(), any(), any()))
                .thenReturn(List.of(new Artista(7L, "Lua Azul", 12L, 2400L)));

        ResumoResponse r = service.resumo(1L, "2026-10");

        assertThat(r.artistas()).singleElement().satisfies(a -> {
            assertThat(a.nome()).isEqualTo("Lua Azul");
            assertThat(a.vezes()).isEqualTo(12);
            assertThat(a.segundos()).isEqualTo(2400);
        });
    }

    @Test
    void recusaMesInvalidoOuNoFuturo() {
        assertThatThrownBy(() -> service.resumo(1L, "outubro"))
                .isInstanceOf(Excecoes.RegraNegocio.class);
        assertThatThrownBy(() -> service.resumo(1L, "2026-11"))
                .isInstanceOf(Excecoes.RegraNegocio.class);
    }

    @Test
    void maiorIndiceIgnoraZerosEFicaComOPrimeiroNoEmpate() {
        assertThat(ResumoService.indiceDoMaior(new long[]{0, 0, 0})).isNull();
        assertThat(ResumoService.indiceDoMaior(new long[]{0, 5, 5, 1})).isEqualTo(1);
    }

    @Test
    void maisOuvidasDoMesComecaNoDia1EDeSempreDesdeOComeco() {
        service.maisOuvidas(1L, "mes");
        verify(reproducoes).topArtistas(eq(1L), eq(LocalDateTime.of(2026, 10, 1, 0, 0)), any(),
                eq(org.springframework.data.domain.PageRequest.of(0, ResumoService.TAMANHO_PERFIL)));

        var sempre = service.maisOuvidas(1L, "SEMPRE");
        assertThat(sempre.periodo()).isEqualTo("sempre");
        verify(reproducoes).topArtistas(eq(1L), eq(LocalDateTime.of(2000, 1, 1, 0, 0)), any(), any());
        assertThat(service.maisOuvidas(1L, "qualquer").periodo()).isEqualTo("mes");
    }
}
