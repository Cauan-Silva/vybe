package com.cauan.vybe.resumo;

import com.cauan.vybe.biblioteca.ReproducaoRepository;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.resumo.ResumoDtos.ArtistaTop;
import com.cauan.vybe.resumo.ResumoDtos.CanalTop;
import com.cauan.vybe.resumo.ResumoDtos.Dia;
import com.cauan.vybe.resumo.ResumoDtos.MusicaTop;
import com.cauan.vybe.resumo.ResumoDtos.ResumoResponse;
import com.cauan.vybe.resumo.ResumoDtos.VideoTop;
import com.cauan.vybe.youtube.VisualizacaoVideoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Monta o "Seu mês no VYBE" a partir das reproduções de músicas e das visualizações de vídeos.
 * As contas pesadas (rankings) ficam no banco; a linha do tempo, o dia e a hora preferidos
 * são calculados aqui a partir de um ponto por reprodução.
 */
@Service
public class ResumoService {

    static final int TAMANHO_RANKING = 5;
    /** Músicas buscadas para achar a capa de cada artista do ranking. */
    private static final int MUSICAS_PARA_CAPAS = 50;

    private final ReproducaoRepository reproducoes;
    private final VisualizacaoVideoRepository visualizacoes;
    private final MusicaRepository musicas;
    private final Clock clock;

    public ResumoService(ReproducaoRepository reproducoes,
                         VisualizacaoVideoRepository visualizacoes,
                         MusicaRepository musicas,
                         Clock clock) {
        this.reproducoes = reproducoes;
        this.visualizacoes = visualizacoes;
        this.musicas = musicas;
        this.clock = clock;
    }

    /** "mes" no formato aaaa-mm; nulo ou vazio = mês atual. */
    @Transactional(readOnly = true)
    public ResumoResponse resumo(Long usuarioId, String mes) {
        YearMonth atual = YearMonth.now(clock);
        YearMonth alvo = lerMes(mes, atual);
        LocalDateTime inicio = alvo.atDay(1).atStartOfDay();
        LocalDateTime fim = alvo.plusMonths(1).atDay(1).atStartOfDay();

        List<ReproducaoRepository.Ponto> pontosMusica = reproducoes.pontos(usuarioId, inicio, fim);
        List<VisualizacaoVideoRepository.Ponto> pontosVideo = visualizacoes.pontos(usuarioId, inicio, fim);

        // Linha do tempo: segundos por dia e por hora (música e vídeo somados para a hora).
        long[] musicaPorDia = new long[alvo.lengthOfMonth()];
        long[] videoPorDia = new long[alvo.lengthOfMonth()];
        long[] porHora = new long[24];
        long segundosMusica = 0;
        for (var p : pontosMusica) {
            long s = segundos(p.getSegundos());
            segundosMusica += s;
            musicaPorDia[p.getQuando().getDayOfMonth() - 1] += s;
            porHora[p.getQuando().getHour()] += s;
        }
        long segundosVideo = 0;
        for (var p : pontosVideo) {
            long s = segundos(p.getSegundos());
            segundosVideo += s;
            videoPorDia[p.getQuando().getDayOfMonth() - 1] += s;
            porHora[p.getQuando().getHour()] += s;
        }
        List<Dia> dias = new ArrayList<>(musicaPorDia.length);
        for (int i = 0; i < musicaPorDia.length; i++) {
            dias.add(new Dia(i + 1, musicaPorDia[i], videoPorDia[i]));
        }

        Integer horaFavorita = indiceDoMaior(porHora);
        long[] totalPorDia = new long[musicaPorDia.length];
        for (int i = 0; i < totalPorDia.length; i++) {
            totalPorDia[i] = musicaPorDia[i] + videoPorDia[i];
        }
        Integer dia = indiceDoMaior(totalPorDia);
        String diaFavorito = dia == null ? null : alvo.atDay(dia + 1).toString();

        // Rankings
        var contagens = reproducoes.topMusicas(usuarioId, inicio, fim, PageRequest.of(0, MUSICAS_PARA_CAPAS));
        Map<Long, Musica> porId = contagens.isEmpty() ? Map.of()
                : musicas.findByIdIn(contagens.stream().map(ReproducaoRepository.Contagem::getId).toList())
                .stream().collect(Collectors.toMap(Musica::getId, Function.identity()));

        List<MusicaTop> topMusicas = contagens.stream()
                .filter(c -> porId.containsKey(c.getId()))
                .limit(TAMANHO_RANKING)
                .map(c -> new MusicaTop(MusicaResponse.de(porId.get(c.getId())), numero(c.getVezes()),
                        numero(c.getSegundos())))
                .toList();

        // Capa do artista: a da música dele mais ouvida no mês.
        Map<Long, String> capaDoArtista = new HashMap<>();
        contagens.stream().map(c -> porId.get(c.getId())).filter(Objects::nonNull)
                .forEach(m -> capaDoArtista.putIfAbsent(m.getArtista().getId(), m.getAlbum().capaUrl()));

        List<ArtistaTop> topArtistas = reproducoes
                .topArtistas(usuarioId, inicio, fim, PageRequest.of(0, TAMANHO_RANKING)).stream()
                .map(a -> new ArtistaTop(a.getId(), a.getNome(), capaDoArtista.get(a.getId()),
                        numero(a.getVezes()), numero(a.getSegundos())))
                .toList();

        List<VideoTop> topVideos = visualizacoes
                .topVideos(usuarioId, inicio, fim, PageRequest.of(0, TAMANHO_RANKING)).stream()
                .map(v -> new VideoTop(v.getVideoId(), v.getTitulo(), v.getCanal(), v.getCanalId(), v.getCapaUrl(),
                        numero(v.getVezes()), numero(v.getSegundos())))
                .toList();

        List<CanalTop> topCanais = visualizacoes
                .topCanais(usuarioId, inicio, fim, PageRequest.of(0, TAMANHO_RANKING)).stream()
                .map(c -> new CanalTop(c.getNome(), c.getCanalId(), numero(c.getVezes()), numero(c.getSegundos())))
                .toList();

        var distintos = reproducoes.distintos(usuarioId, inicio, fim);

        return new ResumoResponse(
                alvo.toString(),
                mesAnterior(usuarioId, alvo),
                alvo.isBefore(atual) ? alvo.plusMonths(1).toString() : null,
                segundosMusica,
                segundosVideo,
                pontosMusica.size(),
                distintos == null ? 0 : numero(distintos.getMusicas()),
                distintos == null ? 0 : numero(distintos.getArtistas()),
                pontosVideo.size(),
                horaFavorita,
                diaFavorito,
                topMusicas,
                topArtistas,
                topVideos,
                topCanais,
                dias);
    }

    static final int TAMANHO_PERFIL = 10;
    /** Antes do VYBE existir: "sempre" começa aqui. */
    private static final LocalDateTime COMECO = LocalDateTime.of(2000, 1, 1, 0, 0);

    /** Músicas e artistas mais ouvidos do mês atual ou de sempre (página do perfil). */
    @Transactional(readOnly = true)
    public ResumoDtos.MaisOuvidas maisOuvidas(Long usuarioId, String periodo) {
        boolean sempre = "sempre".equalsIgnoreCase(periodo);
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDateTime inicio = sempre ? COMECO : YearMonth.from(agora).atDay(1).atStartOfDay();
        LocalDateTime fim = agora.plusMinutes(1);

        var contagens = reproducoes.topMusicas(usuarioId, inicio, fim, PageRequest.of(0, MUSICAS_PARA_CAPAS));
        Map<Long, Musica> porId = contagens.isEmpty() ? Map.of()
                : musicas.findByIdIn(contagens.stream().map(ReproducaoRepository.Contagem::getId).toList())
                .stream().collect(Collectors.toMap(Musica::getId, Function.identity()));
        List<MusicaTop> top = contagens.stream()
                .filter(c -> porId.containsKey(c.getId()))
                .limit(TAMANHO_PERFIL)
                .map(c -> new MusicaTop(MusicaResponse.de(porId.get(c.getId())), numero(c.getVezes()),
                        numero(c.getSegundos())))
                .toList();
        Map<Long, String> capaDoArtista = new HashMap<>();
        contagens.stream().map(c -> porId.get(c.getId())).filter(Objects::nonNull)
                .forEach(m -> capaDoArtista.putIfAbsent(m.getArtista().getId(), m.getAlbum().capaUrl()));
        List<ArtistaTop> artistas = reproducoes
                .topArtistas(usuarioId, inicio, fim, PageRequest.of(0, TAMANHO_PERFIL)).stream()
                .map(a -> new ArtistaTop(a.getId(), a.getNome(), capaDoArtista.get(a.getId()),
                        numero(a.getVezes()), numero(a.getSegundos())))
                .toList();
        return new ResumoDtos.MaisOuvidas(sempre ? "sempre" : "mes", top, artistas);
    }

    private static YearMonth lerMes(String mes, YearMonth atual) {
        if (mes == null || mes.isBlank()) {
            return atual;
        }
        try {
            YearMonth alvo = YearMonth.parse(mes.trim());
            if (alvo.isAfter(atual)) {
                throw new Excecoes.RegraNegocio("Esse mês ainda não chegou");
            }
            return alvo;
        } catch (DateTimeParseException e) {
            throw new Excecoes.RegraNegocio("Mês inválido: use o formato aaaa-mm");
        }
    }

    /** O mês anterior só aparece se houver algum registro (música ou vídeo) até ele. */
    private String mesAnterior(Long usuarioId, YearMonth alvo) {
        YearMonth primeiro = Stream.of(reproducoes.primeira(usuarioId), visualizacoes.primeira(usuarioId))
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .min(YearMonth::compareTo)
                .orElse(null);
        return primeiro != null && primeiro.isBefore(alvo) ? alvo.minusMonths(1).toString() : null;
    }

    /** Índice do maior valor positivo; nulo se tudo for zero. Empate fica com o primeiro. */
    static Integer indiceDoMaior(long[] valores) {
        Integer melhor = null;
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] > 0 && (melhor == null || valores[i] > valores[melhor])) {
                melhor = i;
            }
        }
        return melhor;
    }

    private static long segundos(Integer valor) {
        return valor == null ? 0 : Math.max(0, valor);
    }

    private static long numero(Long valor) {
        return valor == null ? 0 : valor;
    }
}
