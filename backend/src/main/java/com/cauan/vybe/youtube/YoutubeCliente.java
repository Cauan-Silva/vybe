package com.cauan.vybe.youtube;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.youtube.YoutubeDtos.CanalApi;
import com.cauan.vybe.youtube.YoutubeDtos.CanalInfo;
import com.cauan.vybe.youtube.YoutubeDtos.ItemBusca;
import com.cauan.vybe.youtube.YoutubeDtos.Miniatura;
import com.cauan.vybe.youtube.YoutubeDtos.RespostaBusca;
import com.cauan.vybe.youtube.YoutubeDtos.RespostaCanais;
import com.cauan.vybe.youtube.YoutubeDtos.RespostaItensPlaylist;
import com.cauan.vybe.youtube.YoutubeDtos.RespostaVideos;
import com.cauan.vybe.youtube.YoutubeDtos.Video;
import com.cauan.vybe.youtube.YoutubeDtos.VideoApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conversa com a YouTube Data API v3. Só faz chamadas HTTP; quem guarda no banco é o YoutubeService.
 *
 * Cota: o projeto no Google Cloud tem 10.000 unidades por dia. Uma busca (search.list) custa 100
 * e os detalhes dos vídeos (videos.list), assim como os vídeos em alta, custam 1. Dá cerca de
 * 100 buscas por dia.
 */
@Component
public class YoutubeCliente {

    private static final String API = "https://www.googleapis.com/youtube/v3";
    static final int LIMITE = 10;
    /** Ordem de preferência das miniaturas (a primeira que existir). */
    private static final List<String> MINIATURAS = List.of("high", "medium", "standard", "default");
    /** Partes pedidas em videos.list. statistics vem junto sem custo extra (a chamada custa 1 de qualquer jeito). */
    private static final String PARTES_VIDEO = "snippet,contentDetails,status,statistics";

    private final RestClient http;
    private final String chave;

    public YoutubeCliente(RestClient.Builder builder, @Value("${vybe.youtube.api-key:}") String chave) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(8_000);
        this.http = builder.clone().requestFactory(fabrica).build();
        this.chave = chave == null ? "" : chave.trim();
    }

    /** Sem chave configurada, a integração fica desligada (e o app funciona sem ela). */
    public boolean configurado() {
        return !chave.isEmpty();
    }

    /** Busca vídeos de música (categoria Música) que podem ser tocados fora do YouTube. */
    public List<Video> buscar(String termo) {
        return buscar(termo, true, LIMITE);
    }

    /**
     * Busca vídeos que podem ser embutidos e completa com duração e miniatura, na ordem da busca.
     * apenasMusica: filtra pela categoria Música (10); a aba Vídeos busca sem esse filtro.
     */
    public List<Video> buscar(String termo, boolean apenasMusica, int limite) {
        RespostaBusca busca = get("/search?part=id&type=video&videoEmbeddable=true"
                + (apenasMusica ? "&videoCategoryId=10" : "")
                + "&maxResults=" + limite + "&q=" + codificar(termo), RespostaBusca.class);
        if (busca == null || busca.items() == null) {
            return List.of();
        }
        List<String> ids = busca.items().stream()
                .map(ItemBusca::id)
                .filter(Objects::nonNull)
                .map(YoutubeDtos.IdVideo::videoId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        return detalhes(ids);
    }

    /** Vídeos em alta no Brasil (custa só 1 unidade da cota). */
    public List<Video> populares(int limite) {
        return populares(limite, null);
    }

    /**
     * Vídeos em alta no Brasil, opcionalmente de uma categoria (ex.: 20 = Games).
     * Algumas categorias não têm lista de em alta no Brasil; nesse caso devolve vazio.
     */
    public List<Video> populares(int limite, Integer categoria) {
        RespostaVideos resposta;
        try {
            resposta = get("/videos?part=" + PARTES_VIDEO + "&chart=mostPopular&regionCode=BR"
                    + (categoria == null ? "" : "&videoCategoryId=" + categoria)
                    + "&maxResults=" + limite, RespostaVideos.class);
        } catch (Excecoes.NaoEncontrado e) {
            return List.of();
        }
        if (resposta == null || resposta.items() == null) {
            return List.of();
        }
        return resposta.items().stream()
                .filter(v -> v != null && v.id() != null)
                .map(YoutubeCliente::resumir)
                .toList();
    }

    /** Dados de um canal: nome, foto, inscritos e a playlist com os vídeos enviados (1 unidade). */
    public CanalInfo canal(String canalId) {
        RespostaCanais resposta = get("/channels?part=snippet,statistics,contentDetails&id=" + codificar(canalId),
                RespostaCanais.class);
        if (resposta == null || resposta.items() == null || resposta.items().isEmpty()) {
            throw new Excecoes.NaoEncontrado("Canal não encontrado no YouTube");
        }
        CanalApi c = resposta.items().get(0);
        String nome = c.snippet() == null ? null : c.snippet().title();
        String avatar = c.snippet() == null ? null : miniatura(c.snippet().thumbnails());
        Long inscritos = c.statistics() == null || c.statistics().hiddenSubscriberCount()
                ? null : c.statistics().subscriberCount();
        Long totalVideos = c.statistics() == null ? null : c.statistics().videoCount();
        String uploads = c.contentDetails() == null || c.contentDetails().relatedPlaylists() == null
                ? null : c.contentDetails().relatedPlaylists().uploads();
        return new CanalInfo(c.id(), nome, avatar, inscritos, totalVideos, uploads);
    }

    /** Vídeos mais recentes de uma playlist (a de uploads do canal), já com detalhes. Custa 2 unidades. */
    public List<Video> videosDaPlaylist(String playlistId, int limite) {
        RespostaItensPlaylist itens;
        try {
            itens = get("/playlistItems?part=contentDetails&maxResults=" + limite + "&playlistId="
                    + codificar(playlistId), RespostaItensPlaylist.class);
        } catch (Excecoes.NaoEncontrado e) {
            return List.of(); // canal sem vídeos públicos
        }
        if (itens == null || itens.items() == null) {
            return List.of();
        }
        List<String> ids = itens.items().stream()
                .filter(i -> i != null && i.contentDetails() != null && i.contentDetails().videoId() != null)
                .map(i -> i.contentDetails().videoId())
                .distinct()
                .toList();
        return detalhes(ids);
    }

    private List<Video> detalhes(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        RespostaVideos detalhes = get("/videos?part=" + PARTES_VIDEO + "&id=" + String.join(",", ids),
                RespostaVideos.class);
        if (detalhes == null || detalhes.items() == null) {
            return List.of();
        }
        Map<String, VideoApi> porId = detalhes.items().stream()
                .filter(v -> v != null && v.id() != null)
                .collect(Collectors.toMap(VideoApi::id, Function.identity(), (a, b) -> a));

        return ids.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .map(YoutubeCliente::resumir)
                .toList();
    }

    static Video resumir(VideoApi v) {
        YoutubeDtos.Snippet sn = v.snippet();
        String titulo = sn == null ? null : sn.title();
        String canal = sn == null ? null : sn.channelTitle();
        String capa = sn == null ? null : miniatura(sn.thumbnails());
        int duracao = v.contentDetails() == null ? 0 : segundos(v.contentDetails().duration());
        boolean incorporavel = v.status() != null && v.status().embeddable();
        Long visualizacoes = v.statistics() == null ? null : v.statistics().viewCount();
        Long curtidas = v.statistics() == null ? null : v.statistics().likeCount();
        return new Video(v.id(), titulo, canal, capa, duracao, incorporavel,
                sn == null ? null : sn.channelId(), sn == null ? null : sn.publishedAt(), visualizacoes, curtidas);
    }

    /** A melhor miniatura disponível, na ordem de preferência. */
    private static String miniatura(Map<String, Miniatura> miniaturas) {
        if (miniaturas == null) {
            return null;
        }
        for (String tamanho : MINIATURAS) {
            Miniatura m = miniaturas.get(tamanho);
            if (m != null && m.url() != null) {
                return m.url();
            }
        }
        return null;
    }

    /** "PT4M13S" vira 253. Transmissões ao vivo ("P0D") e formatos estranhos viram 0. */
    static int segundos(String iso) {
        if (iso == null || iso.isBlank()) {
            return 0;
        }
        try {
            long s = Duration.parse(iso).getSeconds();
            return s > Integer.MAX_VALUE ? 0 : (int) s;
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private <T> T get(String caminho, Class<T> tipo) {
        try {
            return http.get()
                    .uri(URI.create(API + caminho + "&key=" + codificar(chave)))
                    .retrieve()
                    .body(tipo);
        } catch (RestClientResponseException e) {
            String corpo = e.getResponseBodyAsString();
            if (corpo.contains("quotaExceeded") || corpo.contains("dailyLimitExceeded")) {
                throw new Excecoes.ServicoExterno("O limite diário de buscas no YouTube acabou. Ele renova "
                        + "à meia-noite do horário do Pacífico (por volta das 4h ou 5h em Brasília).", e);
            }
            if (corpo.contains("videoChartNotFound") || corpo.contains("channelNotFound")
                    || corpo.contains("playlistNotFound")) {
                throw new Excecoes.NaoEncontrado("Não encontrado no YouTube");
            }
            if (corpo.contains("keyInvalid") || corpo.contains("API_KEY_INVALID")
                    || corpo.contains("accessNotConfigured") || corpo.contains("SERVICE_DISABLED")) {
                throw new Excecoes.ServicoExterno("O YouTube recusou a chave. Confira a YOUTUBE_API_KEY e se a "
                        + "YouTube Data API v3 está ativada no projeto do Google Cloud.", e);
            }
            throw new Excecoes.ServicoExterno("O YouTube não respondeu. Tente de novo em instantes.", e);
        } catch (RestClientException e) {
            throw new Excecoes.ServicoExterno("O YouTube não respondeu. Tente de novo em instantes.", e);
        }
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
