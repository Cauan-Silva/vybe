package com.cauan.vybe.youtube;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.UsuarioRepository;
import com.cauan.vybe.youtube.FeedDtos.Canal;
import com.cauan.vybe.youtube.FeedDtos.Feed;
import com.cauan.vybe.youtube.FeedDtos.Interesse;
import com.cauan.vybe.youtube.YoutubeDtos.VideoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aba Vídeos com a cara de quem usa:
 * - "Para você": vídeos novos dos canais que a pessoa mais assistiu nos últimos 30 dias;
 * - "Inscrições": vídeos novos dos canais que ela segue no VYBE;
 * - "Seus interesses": vídeos recentes sobre até 3 temas escolhidos por ela.
 *
 * Cota do YouTube: os vídeos de um canal custam cerca de 3 unidades e ficam 1 hora em cache
 * (YoutubeService.canal); cada interesse é uma busca de 100 unidades, guardada 6 horas.
 */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    static final int DIAS_PARA_VOCE = 30;
    static final int CANAIS_PARA_VOCE = 5;
    static final int LIMITE_PARA_VOCE = 12;
    static final int CANAIS_NAS_INSCRICOES = 15;
    static final int LIMITE_INSCRICOES = 24;
    static final int MAXIMO_SEGUIDOS = 100;
    static final int MAXIMO_INTERESSES = 3;

    private static final Pattern ID_CANAL = Pattern.compile("[A-Za-z0-9_-]{10,40}");

    private final YoutubeService youtube;
    private final VisualizacaoVideoRepository visualizacoes;
    private final VideoUsuarioRepository videos;
    private final CanalSeguidoRepository seguidos;
    private final InteresseVideoRepository interesses;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public FeedService(YoutubeService youtube,
                       VisualizacaoVideoRepository visualizacoes,
                       VideoUsuarioRepository videos,
                       CanalSeguidoRepository seguidos,
                       InteresseVideoRepository interesses,
                       UsuarioRepository usuarios,
                       Clock clock) {
        this.youtube = youtube;
        this.visualizacoes = visualizacoes;
        this.videos = videos;
        this.seguidos = seguidos;
        this.interesses = interesses;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    // ---------- Para você ----------

    /** Vídeos novos dos canais mais assistidos, misturados (um de cada canal por vez) e sem os já vistos. */
    @Transactional(readOnly = true)
    public Feed paraVoce(Long usuarioId) {
        if (!youtube.disponivel()) {
            return new Feed(List.of(), List.of());
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        List<Canal> base = visualizacoes
                .topCanais(usuarioId, agora.minusDays(DIAS_PARA_VOCE), agora.plusMinutes(1),
                        PageRequest.of(0, CANAIS_PARA_VOCE * 2))
                .stream()
                .filter(c -> c.getCanalId() != null && ID_CANAL.matcher(c.getCanalId()).matches())
                .limit(CANAIS_PARA_VOCE)
                .map(c -> new Canal(c.getCanalId(), c.getNome(), null))
                .toList();
        if (base.isEmpty()) {
            return new Feed(List.of(), List.of());
        }
        Set<String> vistos = new HashSet<>(videos.idsAssistidos(usuarioId));
        List<List<VideoResponse>> porCanal = new ArrayList<>();
        List<Canal> usados = new ArrayList<>();
        for (Canal c : base) {
            List<VideoResponse> doCanal = videosDoCanal(c.id()).stream()
                    .filter(v -> !vistos.contains(v.id()))
                    .toList();
            if (!doCanal.isEmpty()) {
                porCanal.add(doCanal);
                usados.add(c);
            }
        }
        return new Feed(usados, intercalar(porCanal, LIMITE_PARA_VOCE));
    }

    // ---------- Inscrições ----------

    /** Vídeos mais recentes dos canais seguidos, do mais novo para o mais antigo. */
    @Transactional(readOnly = true)
    public Feed inscricoes(Long usuarioId) {
        List<Canal> canais = seguidos.findByUsuarioIdOrderBySeguidoEmDesc(usuarioId).stream().map(Canal::de).toList();
        if (canais.isEmpty() || !youtube.disponivel()) {
            return new Feed(canais, List.of());
        }
        Map<String, VideoResponse> unicos = new LinkedHashMap<>();
        canais.stream().limit(CANAIS_NAS_INSCRICOES)
                .flatMap(c -> videosDoCanal(c.id()).stream())
                .forEach(v -> unicos.putIfAbsent(v.id(), v));
        List<VideoResponse> lista = unicos.values().stream()
                .sorted(Comparator.comparing(VideoResponse::publicadoEm,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(LIMITE_INSCRICOES)
                .toList();
        return new Feed(canais, lista);
    }

    @Transactional(readOnly = true)
    public List<Canal> canaisSeguidos(Long usuarioId) {
        return seguidos.findByUsuarioIdOrderBySeguidoEmDesc(usuarioId).stream().map(Canal::de).toList();
    }

    /** Seguir de novo um canal já seguido só atualiza nome e foto. */
    @Transactional
    public Canal seguir(Long usuarioId, Canal dados) {
        if (dados == null || dados.id() == null || !ID_CANAL.matcher(dados.id()).matches()) {
            throw new Excecoes.RegraNegocio("Canal inválido");
        }
        String nome = dados.nome() == null || dados.nome().isBlank() ? "Canal" : cortar(dados.nome().trim(), 150);
        String avatar = dados.avatarUrl() != null && dados.avatarUrl().startsWith("https://")
                && dados.avatarUrl().length() <= 500 ? dados.avatarUrl() : null;
        if (avatar == null && youtube.disponivel()) {
            // Seguindo pelo player (sem a foto): completa com os dados do canal (cache de 1 hora).
            try {
                YoutubeDtos.CanalResponse info = youtube.canal(dados.id());
                if (info != null) {
                    if (info.avatarUrl() != null && info.avatarUrl().startsWith("https://")
                            && info.avatarUrl().length() <= 500) {
                        avatar = info.avatarUrl();
                    }
                    if (info.nome() != null && !info.nome().isBlank()) {
                        nome = cortar(info.nome().trim(), 150);
                    }
                }
            } catch (RuntimeException e) {
                // sem a foto: aparece a inicial do nome
            }
        }
        CanalSeguido canal = seguidos.findByUsuarioIdAndCanalId(usuarioId, dados.id()).orElse(null);
        if (canal == null) {
            if (seguidos.countByUsuarioId(usuarioId) >= MAXIMO_SEGUIDOS) {
                throw new Excecoes.RegraNegocio("Você já segue " + MAXIMO_SEGUIDOS + " canais. Deixe de seguir algum para adicionar outro.");
            }
            canal = new CanalSeguido(usuarios.getReferenceById(usuarioId), dados.id(), nome, avatar, LocalDateTime.now(clock));
        } else {
            canal.setNome(nome);
            if (avatar != null) {
                canal.setAvatarUrl(avatar);
            }
        }
        return Canal.de(seguidos.save(canal));
    }

    @Transactional
    public void deixarDeSeguir(Long usuarioId, String canalId) {
        seguidos.findByUsuarioIdAndCanalId(usuarioId, canalId).ifPresent(seguidos::delete);
    }

    // ---------- Interesses ----------

    @Transactional(readOnly = true)
    public List<Interesse> interesses(Long usuarioId) {
        return interesses.findByUsuarioIdOrderByCriadoEmAsc(usuarioId).stream().map(Interesse::de).toList();
    }

    /** Até 3 temas, de 2 a 60 caracteres. Repetido (sem diferenciar maiúsculas) devolve o que já existe. */
    @Transactional
    public Interesse adicionarInteresse(Long usuarioId, String termo) {
        String limpo = termo == null ? "" : termo.trim().replaceAll("\\s+", " ");
        if (limpo.length() < 2 || limpo.length() > 60) {
            throw new Excecoes.RegraNegocio("O interesse precisa ter de 2 a 60 caracteres.");
        }
        List<InteresseVideo> atuais = interesses.findByUsuarioIdOrderByCriadoEmAsc(usuarioId);
        for (InteresseVideo i : atuais) {
            if (i.getTermo().toLowerCase(Locale.ROOT).equals(limpo.toLowerCase(Locale.ROOT))) {
                return Interesse.de(i);
            }
        }
        if (atuais.size() >= MAXIMO_INTERESSES) {
            throw new Excecoes.RegraNegocio("Você pode acompanhar até " + MAXIMO_INTERESSES
                    + " interesses. Remova um para adicionar outro.");
        }
        return Interesse.de(interesses.save(new InteresseVideo(usuarios.getReferenceById(usuarioId), limpo,
                LocalDateTime.now(clock))));
    }

    @Transactional
    public void removerInteresse(Long usuarioId, Long interesseId) {
        interesses.findByIdAndUsuarioId(interesseId, usuarioId).ifPresent(interesses::delete);
    }

    /** Só os interesses da própria pessoa (cada busca gasta cota). */
    @Transactional(readOnly = true)
    public List<VideoResponse> videosDoInteresse(Long usuarioId, Long interesseId) {
        InteresseVideo interesse = interesses.findByIdAndUsuarioId(interesseId, usuarioId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Interesse não encontrado"));
        return youtube.videosDoInteresse(interesse.getTermo());
    }

    // ---------- Apoio ----------

    /** Vídeos de um canal; um canal apagado ou com problema não derruba a lista inteira. */
    private List<VideoResponse> videosDoCanal(String canalId) {
        try {
            return youtube.canal(canalId).videos();
        } catch (Excecoes.NaoEncontrado e) {
            return List.of();
        } catch (RuntimeException e) {
            log.warn("Não foi possível carregar os vídeos do canal {}: {}", canalId, e.getMessage());
            return List.of();
        }
    }

    /** Um vídeo de cada lista por vez (variedade), sem repetir, até o limite. */
    static List<VideoResponse> intercalar(List<List<VideoResponse>> listas, int limite) {
        List<Iterator<VideoResponse>> iteradores = listas.stream().map(List::iterator).toList();
        Map<String, VideoResponse> resultado = new LinkedHashMap<>();
        boolean algum = true;
        while (algum && resultado.size() < limite) {
            algum = false;
            for (Iterator<VideoResponse> it : iteradores) {
                if (resultado.size() >= limite) {
                    break;
                }
                if (it.hasNext()) {
                    VideoResponse v = it.next();
                    algum = true;
                    if (v != null && Objects.nonNull(v.id())) {
                        resultado.putIfAbsent(v.id(), v);
                    }
                }
            }
        }
        return new ArrayList<>(resultado.values());
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
