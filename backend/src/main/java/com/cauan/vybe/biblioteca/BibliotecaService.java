package com.cauan.vybe.biblioteca;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Coisas pessoais de cada usuário: curtidas e tocadas recentemente. */
@Service
public class BibliotecaService {

    private static final int LIMITE_RECENTES = 12;
    static final int FOLGA_SEGUNDOS = 60;
    static final int TEMPO_MAXIMO_SEGUNDOS = 12 * 3600;

    private final CurtidaRepository curtidas;
    private final ReproducaoRepository reproducoes;
    private final MusicaRepository musicas;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public BibliotecaService(CurtidaRepository curtidas,
                             ReproducaoRepository reproducoes,
                             MusicaRepository musicas,
                             UsuarioRepository usuarios,
                             Clock clock) {
        this.curtidas = curtidas;
        this.reproducoes = reproducoes;
        this.musicas = musicas;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    /** Curtir duas vezes não dá erro: a segunda não faz nada. */
    @Transactional
    public void curtir(Long usuarioId, Long musicaId) {
        if (curtidas.existsByUsuarioIdAndMusicaId(usuarioId, musicaId)) {
            return;
        }
        curtidas.save(new Curtida(usuarios.getReferenceById(usuarioId), obterMusica(musicaId),
                LocalDateTime.now(clock)));
    }

    @Transactional
    public void descurtir(Long usuarioId, Long musicaId) {
        curtidas.deleteByUsuarioIdAndMusicaId(usuarioId, musicaId);
    }

    @Transactional(readOnly = true)
    public List<Long> idsCurtidos(Long usuarioId) {
        return curtidas.idsDasMusicas(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<MusicaResponse> curtidas(Long usuarioId) {
        return curtidas.findByUsuarioIdOrderByCriadaEmDesc(usuarioId).stream()
                .map(c -> MusicaResponse.de(c.getMusica()))
                .toList();
    }

    /** Começou a tocar: devolve o id para o player mandar depois quanto foi ouvido. */
    @Transactional
    public Long registrarReproducao(Long usuarioId, Long musicaId) {
        return reproducoes.save(new Reproducao(usuarios.getReferenceById(usuarioId), obterMusica(musicaId),
                LocalDateTime.now(clock))).getId();
    }

    /**
     * Quanto da reprodução foi ouvido até agora (o player manda o total, não a diferença).
     * Limitado à duração da música com uma folga, para um relógio errado não inflar o resumo.
     */
    @Transactional
    public void registrarTempo(Long usuarioId, Long reproducaoId, int segundos) {
        Reproducao r = reproducoes.findByIdAndUsuarioId(reproducaoId, usuarioId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Reprodução não encontrada"));
        int duracao = r.getMusica().getDuracaoSegundos();
        int limite = duracao > 0 ? duracao + FOLGA_SEGUNDOS : TEMPO_MAXIMO_SEGUNDOS;
        r.setSegundosOuvidos(Math.max(0, Math.min(segundos, limite)));
    }

    @Transactional(readOnly = true)
    public List<MusicaResponse> recentes(Long usuarioId) {
        List<Long> ids = reproducoes.idsRecentes(usuarioId, PageRequest.of(0, LIMITE_RECENTES));
        if (ids.isEmpty()) {
            return List.of();
        }
        // findByIdIn não garante a ordem; reordenamos pela posição na lista de ids.
        return musicas.findByIdIn(ids).stream()
                .sorted(Comparator.comparingInt(m -> ids.indexOf(m.getId())))
                .map(MusicaResponse::de)
                .toList();
    }

    private Musica obterMusica(Long musicaId) {
        return musicas.findById(musicaId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Música não encontrada"));
    }
}
