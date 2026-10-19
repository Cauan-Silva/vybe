package com.cauan.vybe.youtube;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.UsuarioRepository;
import com.cauan.vybe.youtube.MeusVideosDtos.DadosVideo;
import com.cauan.vybe.youtube.MeusVideosDtos.VideoSalvo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeusVideosServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 14, 0);

    @Mock
    private VideoUsuarioRepository videos;
    @Mock
    private VisualizacaoVideoRepository visualizacoes;
    @Mock
    private UsuarioRepository usuarios;

    private MeusVideosService service;

    @BeforeEach
    void setUp() {
        service = new MeusVideosService(videos, visualizacoes, usuarios, Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        lenient().when(videos.findByUsuarioIdAndVideoId(eq(1L), anyString())).thenReturn(Optional.empty());
        lenient().when(videos.save(any(VideoUsuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DadosVideo video(int duracao) {
        return new DadosVideo("abcdef12345", "Bolo de cenoura", "Cozinha", "UCabcdefghijklmnopqrstuv",
                "https://i.ytimg.com/vi/abcdef12345/hqdefault.jpg", duracao);
    }

    @Test
    void salvarParaDepoisMarcaEGuardaAData() {
        VideoSalvo salvo = service.salvarParaDepois(1L, video(600));

        assertThat(salvo.assistirDepois()).isTrue();
        assertThat(salvo.assistidoEm()).isNull();
        assertThat(salvo.titulo()).isEqualTo("Bolo de cenoura");
    }

    @Test
    void progressoEntraNoHistoricoComAPosicao() {
        VideoSalvo salvo = service.registrarProgresso(1L, video(600), 125);

        assertThat(salvo.posicaoSegundos()).isEqualTo(125);
        assertThat(salvo.assistidoEm()).isEqualTo(AGORA.toString());
    }

    @Test
    void pertoDoFimContaComoTerminado() {
        assertThat(service.registrarProgresso(1L, video(600), 595).posicaoSegundos()).isEqualTo(600);
        assertThat(service.registrarProgresso(1L, video(600), 9999).posicaoSegundos()).isEqualTo(600);
    }

    @Test
    void aoVivoNaoGuardaPosicao() {
        assertThat(service.registrarProgresso(1L, video(0), 300).posicaoSegundos()).isZero();
    }

    @Test
    void ignoraDadosEstranhosQueChegamDoNavegador() {
        DadosVideo estranho = new DadosVideo("abcdef12345", "  ", null, "<script>", "javascript:alert(1)", -5);

        VideoSalvo salvo = service.salvarParaDepois(1L, estranho);

        assertThat(salvo.titulo()).isEqualTo("Sem título");
        assertThat(salvo.canalId()).isNull();
        assertThat(salvo.capaUrl()).isNull();
        assertThat(salvo.duracaoSegundos()).isZero();
    }

    @Test
    void recusaIdDeVideoInvalido() {
        assertThatThrownBy(() -> service.salvarParaDepois(1L, new DadosVideo("../../x", "t", null, null, null, 10)))
                .isInstanceOf(Excecoes.RegraNegocio.class);
        verify(videos, never()).save(any());
    }

    @Test
    void tirarDoAssistirDepoisApagaSeNuncaFoiAssistido() {
        VideoUsuario soSalvo = new VideoUsuario();
        soSalvo.setAssistirDepois(true);
        lenient().when(videos.findByUsuarioIdAndVideoId(1L, "naovisto001")).thenReturn(Optional.of(soSalvo));

        service.removerDeDepois(1L, "naovisto001");

        verify(videos).delete(soSalvo);
    }

    @Test
    void tirarDoAssistirDepoisMantemNoHistorico() {
        VideoUsuario assistido = new VideoUsuario();
        assistido.setAssistirDepois(true);
        assistido.setAssistidoEm(AGORA.minusDays(1));
        lenient().when(videos.findByUsuarioIdAndVideoId(1L, "visto000001")).thenReturn(Optional.of(assistido));

        service.removerDeDepois(1L, "visto000001");

        assertThat(assistido.isAssistirDepois()).isFalse();
        verify(videos, never()).delete(any());
    }

    @Test
    void visualizacaoGuardaCopiaDosDadosEComecaComZero() {
        org.mockito.Mockito.when(visualizacoes.save(any(VisualizacaoVideo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrarVisualizacao(1L, video(600));

        var captor = org.mockito.ArgumentCaptor.forClass(VisualizacaoVideo.class);
        verify(visualizacoes).save(captor.capture());
        assertThat(captor.getValue().getTitulo()).isEqualTo("Bolo de cenoura");
        assertThat(captor.getValue().getAssistidoEm()).isEqualTo(AGORA);
        assertThat(captor.getValue().getSegundosAssistidos()).isZero();
    }

    @Test
    void visualizacaoRecusaIdInvalido() {
        assertThatThrownBy(() -> service.registrarVisualizacao(1L, new DadosVideo("<x>", "t", null, null, null, 0)))
                .isInstanceOf(Excecoes.RegraNegocio.class);
        verify(visualizacoes, never()).save(any());
    }

    @Test
    void tempoDaVisualizacaoTemLimitesESoDoProprioUsuario() {
        VisualizacaoVideo v = new VisualizacaoVideo();
        lenient().when(visualizacoes.findByIdAndUsuarioId(3L, 1L)).thenReturn(Optional.of(v));
        lenient().when(visualizacoes.findByIdAndUsuarioId(3L, 2L)).thenReturn(Optional.empty());

        service.registrarTempoVisualizacao(1L, 3L, 125);
        assertThat(v.getSegundosAssistidos()).isEqualTo(125);
        service.registrarTempoVisualizacao(1L, 3L, Integer.MAX_VALUE);
        assertThat(v.getSegundosAssistidos()).isEqualTo(MeusVideosService.TEMPO_MAXIMO_SEGUNDOS);
        assertThatThrownBy(() -> service.registrarTempoVisualizacao(2L, 3L, 10))
                .isInstanceOf(Excecoes.NaoEncontrado.class);
    }
}
