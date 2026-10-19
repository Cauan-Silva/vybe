package com.cauan.vybe.youtube;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.UsuarioRepository;
import com.cauan.vybe.youtube.MeusVideosDtos.DadosVideo;
import com.cauan.vybe.youtube.MeusVideosDtos.VideoSalvo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * "Assistir depois", histórico e posição dos vídeos de cada usuário. Tudo fica no banco do
 * VYBE, sem gastar a cota do YouTube.
 */
@Service
public class MeusVideosService {

    static final int LIMITE_HISTORICO = 30;
    /** Faltando menos que isso para o fim, o vídeo conta como terminado e recomeça do zero. */
    static final int MARGEM_FIM_SEGUNDOS = 10;

    private static final Pattern ID_VIDEO = Pattern.compile("[A-Za-z0-9_-]{6,20}");
    private static final Pattern ID_CANAL = Pattern.compile("[A-Za-z0-9_-]{10,40}");
    private static final int DURACAO_MAXIMA = 3 * 24 * 3600;

    /** Teto para o tempo assistido de uma visualização (ao vivo não tem duração). */
    static final int TEMPO_MAXIMO_SEGUNDOS = 12 * 3600;

    private final VideoUsuarioRepository videos;
    private final VisualizacaoVideoRepository visualizacoes;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public MeusVideosService(VideoUsuarioRepository videos, VisualizacaoVideoRepository visualizacoes,
                             UsuarioRepository usuarios, Clock clock) {
        this.videos = videos;
        this.visualizacoes = visualizacoes;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    /** Abriu um vídeo: devolve o id para o player mandar depois quanto foi assistido. */
    @Transactional
    public Long registrarVisualizacao(Long usuarioId, DadosVideo dados) {
        validarId(dados);
        VisualizacaoVideo v = new VisualizacaoVideo(usuarios.getReferenceById(usuarioId), dados.id(),
                titulo(dados), canal(dados), canalId(dados), capaUrl(dados), LocalDateTime.now(clock));
        return visualizacoes.save(v).getId();
    }

    /** O player manda o total assistido até agora (não a diferença). */
    @Transactional
    public void registrarTempoVisualizacao(Long usuarioId, Long visualizacaoId, int segundos) {
        VisualizacaoVideo v = visualizacoes.findByIdAndUsuarioId(visualizacaoId, usuarioId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Visualização não encontrada"));
        v.setSegundosAssistidos(Math.max(0, Math.min(segundos, TEMPO_MAXIMO_SEGUNDOS)));
    }

    @Transactional(readOnly = true)
    public List<VideoSalvo> assistirDepois(Long usuarioId) {
        return videos.findByUsuarioIdAndAssistirDepoisTrueOrderBySalvoEmDesc(usuarioId).stream()
                .map(VideoSalvo::de).toList();
    }

    @Transactional(readOnly = true)
    public List<VideoSalvo> historico(Long usuarioId) {
        return videos.findByUsuarioIdAndAssistidoEmIsNotNullOrderByAssistidoEmDesc(
                        usuarioId, PageRequest.of(0, LIMITE_HISTORICO)).stream()
                .map(VideoSalvo::de).toList();
    }

    @Transactional
    public VideoSalvo salvarParaDepois(Long usuarioId, DadosVideo dados) {
        VideoUsuario v = obterOuCriar(usuarioId, dados);
        if (!v.isAssistirDepois()) {
            v.setAssistirDepois(true);
            v.setSalvoEm(LocalDateTime.now(clock));
        }
        return VideoSalvo.de(videos.save(v));
    }

    @Transactional
    public void removerDeDepois(Long usuarioId, String videoId) {
        videos.findByUsuarioIdAndVideoId(usuarioId, videoId).ifPresent(v -> {
            if (v.getAssistidoEm() == null) {
                videos.delete(v); // não está no histórico: não sobra motivo para guardar
            } else {
                v.setAssistirDepois(false);
                v.setSalvoEm(null);
            }
        });
    }

    /**
     * Chamado pelo player de tempos em tempos: coloca o vídeo no topo do histórico e guarda
     * até onde a pessoa assistiu. Perto do fim, conta como terminado.
     */
    @Transactional
    public VideoSalvo registrarProgresso(Long usuarioId, DadosVideo dados, int posicaoSegundos) {
        VideoUsuario v = obterOuCriar(usuarioId, dados);
        int duracao = v.getDuracaoSegundos();
        int posicao = duracao <= 0 ? 0 : Math.max(0, Math.min(posicaoSegundos, duracao));
        if (duracao > 0 && posicao >= duracao - MARGEM_FIM_SEGUNDOS) {
            posicao = duracao; // terminou: a barrinha fica cheia e o próximo play começa do zero
        }
        v.setPosicaoSegundos(posicao);
        v.setAssistidoEm(LocalDateTime.now(clock));
        return VideoSalvo.de(videos.save(v));
    }

    @Transactional
    public void limparHistorico(Long usuarioId) {
        videos.apagarSoDoHistorico(usuarioId);
        videos.zerarHistorico(usuarioId);
    }

    /** Acha a linha do vídeo ou cria, sempre atualizando a cópia dos dados com o que chegou. */
    private VideoUsuario obterOuCriar(Long usuarioId, DadosVideo dados) {
        validarId(dados);
        VideoUsuario v = videos.findByUsuarioIdAndVideoId(usuarioId, dados.id()).orElseGet(() -> {
            VideoUsuario novo = new VideoUsuario();
            novo.setUsuario(usuarios.getReferenceById(usuarioId));
            novo.setVideoId(dados.id());
            return novo;
        });
        v.setTitulo(titulo(dados));
        v.setCanal(canal(dados));
        v.setCanalId(canalId(dados));
        v.setCapaUrl(capaUrl(dados));
        v.setDuracaoSegundos(Math.max(0, Math.min(dados.duracaoSegundos(), DURACAO_MAXIMA)));
        return v;
    }

    private static void validarId(DadosVideo dados) {
        if (dados == null || dados.id() == null || !ID_VIDEO.matcher(dados.id()).matches()) {
            throw new Excecoes.RegraNegocio("Vídeo inválido");
        }
    }

    private static String titulo(DadosVideo dados) {
        return cortar(vazio(dados.titulo()) ? "Sem título" : dados.titulo().trim(), 300);
    }

    private static String canal(DadosVideo dados) {
        return vazio(dados.canal()) ? null : cortar(dados.canal().trim(), 150);
    }

    private static String canalId(DadosVideo dados) {
        return dados.canalId() != null && ID_CANAL.matcher(dados.canalId()).matches() ? dados.canalId() : null;
    }

    private static String capaUrl(DadosVideo dados) {
        return dados.capaUrl() != null && dados.capaUrl().startsWith("https://")
                && dados.capaUrl().length() <= 500 ? dados.capaUrl() : null;
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
