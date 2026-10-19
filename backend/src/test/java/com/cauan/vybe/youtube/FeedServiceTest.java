package com.cauan.vybe.youtube;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.UsuarioRepository;
import com.cauan.vybe.youtube.FeedDtos.Canal;
import com.cauan.vybe.youtube.FeedDtos.Feed;
import com.cauan.vybe.youtube.YoutubeDtos.CanalResponse;
import com.cauan.vybe.youtube.YoutubeDtos.VideoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
class FeedServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 19, 11, 0);
    private static final String VALORANT = "UCvalorantbrasil00000001";
    private static final String CRIADOR = "UCcriadordevalorant00002";

    @Mock
    private YoutubeService youtube;
    @Mock
    private VisualizacaoVideoRepository visualizacoes;
    @Mock
    private VideoUsuarioRepository videos;
    @Mock
    private CanalSeguidoRepository seguidos;
    @Mock
    private InteresseVideoRepository interesses;
    @Mock
    private UsuarioRepository usuarios;

    private FeedService service;

    record ContagemCanal(String nome, String canalId) implements VisualizacaoVideoRepository.ContagemCanal {
        public String getNome() { return nome; }
        public String getCanalId() { return canalId; }
        public Long getVezes() { return 3L; }
        public Long getSegundos() { return 900L; }
    }

    private static VideoResponse video(String id, String canal, String publicado) {
        return new VideoResponse(id, "Vídeo " + id, canal, null, null, 300, null, null, publicado);
    }

    private static CanalResponse canal(String id, VideoResponse... lista) {
        return new CanalResponse(id, "Canal", null, null, null, List.of(lista));
    }

    @BeforeEach
    void setUp() {
        service = new FeedService(youtube, visualizacoes, videos, seguidos, interesses, usuarios,
                Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        lenient().when(youtube.disponivel()).thenReturn(true);
        lenient().when(videos.idsAssistidos(1L)).thenReturn(List.of());
        lenient().when(seguidos.save(any(CanalSeguido.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(interesses.save(any(InteresseVideo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void paraVoceMisturaOsCanaisMaisAssistidosESemOsJaVistos() {
        when(visualizacoes.topCanais(any(), any(), any(), any())).thenReturn(List.of(
                new ContagemCanal("VALORANT // Brasil", VALORANT), new ContagemCanal("Criador", CRIADOR),
                new ContagemCanal("Sem id", null)));
        when(youtube.canal(VALORANT)).thenReturn(canal(VALORANT,
                video("valorantA01", "V", "2026-10-18T10:00:00Z"), video("valorantA02", "V", "2026-10-17T10:00:00Z"),
                video("valorantA03", "V", "2026-10-16T10:00:00Z")));
        when(youtube.canal(CRIADOR)).thenReturn(canal(CRIADOR,
                video("criadorB001", "C", "2026-10-18T09:00:00Z"), video("criadorB002", "C", "2026-10-15T09:00:00Z")));
        when(videos.idsAssistidos(1L)).thenReturn(List.of("valorantA01"));

        Feed feed = service.paraVoce(1L);

        assertThat(feed.canais()).extracting(Canal::nome).containsExactly("VALORANT // Brasil", "Criador");
        // um de cada canal por vez, e o já assistido some
        assertThat(feed.videos()).extracting(VideoResponse::id)
                .containsExactly("valorantA02", "criadorB001", "valorantA03", "criadorB002");
    }

    @Test
    void paraVoceVazioParaQuemAindaNaoAssistiuNada() {
        when(visualizacoes.topCanais(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service.paraVoce(1L).videos()).isEmpty();
        verify(youtube, never()).canal(any());
    }

    @Test
    void canalComProblemaNaoDerrubaOFeed() {
        when(visualizacoes.topCanais(any(), any(), any(), any())).thenReturn(List.of(
                new ContagemCanal("Apagado", VALORANT), new ContagemCanal("Criador", CRIADOR)));
        when(youtube.canal(VALORANT)).thenThrow(new Excecoes.NaoEncontrado("Canal não encontrado"));
        when(youtube.canal(CRIADOR)).thenReturn(canal(CRIADOR, video("criadorB001", "C", "2026-10-18T09:00:00Z")));

        Feed feed = service.paraVoce(1L);

        assertThat(feed.canais()).extracting(Canal::id).containsExactly(CRIADOR);
        assertThat(feed.videos()).hasSize(1);
    }

    @Test
    void inscricoesJuntamOsCanaisDoMaisNovoParaOMaisAntigo() {
        when(seguidos.findByUsuarioIdOrderBySeguidoEmDesc(1L)).thenReturn(List.of(
                new CanalSeguido(null, VALORANT, "VALORANT // Brasil", null, AGORA),
                new CanalSeguido(null, CRIADOR, "Criador", null, AGORA)));
        when(youtube.canal(VALORANT)).thenReturn(canal(VALORANT,
                video("valorantA01", "V", "2026-10-18T10:00:00Z"), video("valorantA02", "V", "2026-10-10T10:00:00Z")));
        when(youtube.canal(CRIADOR)).thenReturn(canal(CRIADOR, video("criadorB001", "C", "2026-10-15T09:00:00Z")));

        Feed feed = service.inscricoes(1L);

        assertThat(feed.videos()).extracting(VideoResponse::id)
                .containsExactly("valorantA01", "criadorB001", "valorantA02");
    }

    @Test
    void seguirGuardaDadosLimposESeguirDeNovoSoAtualiza() {
        when(seguidos.findByUsuarioIdAndCanalId(1L, VALORANT)).thenReturn(Optional.empty());
        Canal salvo = service.seguir(1L, new Canal(VALORANT, "  VALORANT // Brasil ", "http://inseguro/foto.jpg"));
        assertThat(salvo.nome()).isEqualTo("VALORANT // Brasil");
        assertThat(salvo.avatarUrl()).isNull(); // só https

        CanalSeguido existente = new CanalSeguido(null, VALORANT, "Antigo", null, AGORA);
        when(seguidos.findByUsuarioIdAndCanalId(1L, VALORANT)).thenReturn(Optional.of(existente));
        service.seguir(1L, new Canal(VALORANT, "Novo nome", "https://yt3.ggpht.com/foto.jpg"));
        assertThat(existente.getNome()).isEqualTo("Novo nome");
        assertThat(existente.getAvatarUrl()).isEqualTo("https://yt3.ggpht.com/foto.jpg");
    }

    @Test
    void seguirRecusaIdInvalido() {
        assertThatThrownBy(() -> service.seguir(1L, new Canal("../x", "x", null)))
                .isInstanceOf(Excecoes.RegraNegocio.class);
        verify(seguidos, never()).save(any());
    }

    @Test
    void interessesAteTresSemRepetirEComTamanhoCerto() {
        List<InteresseVideo> atuais = new ArrayList<>();
        when(interesses.findByUsuarioIdOrderByCriadoEmAsc(1L)).thenAnswer(inv -> List.copyOf(atuais));
        when(interesses.save(any(InteresseVideo.class))).thenAnswer(inv -> {
            atuais.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        assertThat(service.adicionarInteresse(1L, "  valorant   clips ").termo()).isEqualTo("valorant clips");
        service.adicionarInteresse(1L, "VALORANT CLIPS"); // repetido: não cria outro
        assertThat(atuais).hasSize(1);
        service.adicionarInteresse(1L, "VCT");
        service.adicionarInteresse(1L, "radiante");
        assertThatThrownBy(() -> service.adicionarInteresse(1L, "quarto"))
                .isInstanceOf(Excecoes.RegraNegocio.class).hasMessageContaining("até 3");
        assertThatThrownBy(() -> service.adicionarInteresse(1L, "x"))
                .isInstanceOf(Excecoes.RegraNegocio.class);
    }

    @Test
    void videosDeInteresseSoDaPropriaPessoa() {
        when(interesses.findByIdAndUsuarioId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.videosDoInteresse(2L, 5L)).isInstanceOf(Excecoes.NaoEncontrado.class);
        verify(youtube, never()).videosDoInteresse(any());
    }

    @Test
    void intercalarNaoRepeteERespeitaOLimite() {
        List<VideoResponse> a = List.of(video("aaaaaa1", "A", null), video("aaaaaa2", "A", null));
        List<VideoResponse> b = List.of(video("aaaaaa1", "B", null), video("bbbbbb2", "B", null));

        assertThat(FeedService.intercalar(List.of(a, b), 10)).extracting(VideoResponse::id)
                .containsExactly("aaaaaa1", "aaaaaa2", "bbbbbb2");
        assertThat(FeedService.intercalar(List.of(a, b), 2)).hasSize(2);
    }

    @Test
    void seguirPeloPlayerCompletaFotoENomeDoCanal() {
        when(seguidos.findByUsuarioIdAndCanalId(1L, VALORANT)).thenReturn(Optional.empty());
        when(youtube.canal(VALORANT)).thenReturn(new CanalResponse(VALORANT, "VALORANT // Brasil",
                "https://yt3.ggpht.com/valorant.jpg", 1_500_000L, 900L, List.of()));

        Canal salvo = service.seguir(1L, new Canal(VALORANT, "VALORANT", null));

        assertThat(salvo.avatarUrl()).isEqualTo("https://yt3.ggpht.com/valorant.jpg");
        assertThat(salvo.nome()).isEqualTo("VALORANT // Brasil");
    }
}
