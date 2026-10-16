package com.cauan.vybe.youtube;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.youtube.YoutubeDtos.ItemBusca;
import com.cauan.vybe.youtube.YoutubeDtos.Miniatura;
import com.cauan.vybe.youtube.YoutubeDtos.RespostaBusca;
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
 * e os detalhes dos vídeos (videos.list) custam 1, então dá cerca de 100 buscas por dia.
 */
@Component
public class YoutubeCliente {

    private static final String API = "https://www.googleapis.com/youtube/v3";
    static final int LIMITE = 10;
    /** Ordem de preferência das miniaturas (a primeira que existir). */
    private static final List<String> MINIATURAS = List.of("high", "medium", "standard", "default");

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

    /**
     * Busca vídeos de música que podem ser tocados fora do YouTube (embeddable) e
     * completa com duração e miniatura. A ordem é a da busca.
     */
    public List<Video> buscar(String termo) {
        RespostaBusca busca = get("/search?part=id&type=video&videoEmbeddable=true&videoCategoryId=10"
                + "&maxResults=" + LIMITE + "&q=" + codificar(termo), RespostaBusca.class);
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
        if (ids.isEmpty()) {
            return List.of();
        }

        RespostaVideos detalhes = get("/videos?part=snippet,contentDetails,status&id=" + String.join(",", ids),
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
        String titulo = v.snippet() == null ? null : v.snippet().title();
        String canal = v.snippet() == null ? null : v.snippet().channelTitle();
        String capa = null;
        if (v.snippet() != null && v.snippet().thumbnails() != null) {
            for (String tamanho : MINIATURAS) {
                Miniatura m = v.snippet().thumbnails().get(tamanho);
                if (m != null && m.url() != null) {
                    capa = m.url();
                    break;
                }
            }
        }
        int duracao = v.contentDetails() == null ? 0 : segundos(v.contentDetails().duration());
        boolean incorporavel = v.status() != null && v.status().embeddable();
        return new Video(v.id(), titulo, canal, capa, duracao, incorporavel);
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
