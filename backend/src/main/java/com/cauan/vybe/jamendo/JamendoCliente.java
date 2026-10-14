package com.cauan.vybe.jamendo;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.jamendo.JamendoDtos.Faixa;
import com.cauan.vybe.jamendo.JamendoDtos.Resposta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.util.List;
import java.util.function.Consumer;

/**
 * Conversa com a API do Jamendo (https://developer.jamendo.com/v3.0).
 * Só faz chamadas HTTP e devolve as faixas; quem guarda no banco é o JamendoService.
 */
@Component
public class JamendoCliente {

    private static final int LIMITE = 20;

    private final RestClient http;
    private final String clientId;

    public JamendoCliente(RestClient.Builder builder, @Value("${vybe.jamendo.client-id:}") String clientId) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(8_000);
        this.http = builder
                .baseUrl("https://api.jamendo.com/v3.0")
                .requestFactory(fabrica)
                .build();
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    /** Sem Client ID configurado, a integração fica desligada (e o app funciona sem ela). */
    public boolean configurado() {
        return !clientId.isEmpty();
    }

    /** Busca livre em nomes de faixa, álbum, artista e tags. */
    public List<Faixa> buscar(String termo) {
        return faixas(u -> u.queryParam("search", termo));
    }

    /** As mais ouvidas da semana. */
    public List<Faixa> populares() {
        return faixas(u -> u.queryParam("order", "popularity_week"));
    }

    private List<Faixa> faixas(Consumer<UriBuilder> filtro) {
        Resposta resposta;
        try {
            resposta = http.get()
                    .uri(u -> {
                        u.path("/tracks/")
                                .queryParam("client_id", clientId)
                                .queryParam("format", "json")
                                .queryParam("limit", LIMITE)
                                .queryParam("audioformat", "mp32")
                                .queryParam("imagesize", 300);
                        filtro.accept(u);
                        return u.build();
                    })
                    .retrieve()
                    .body(Resposta.class);
        } catch (RestClientException e) {
            throw new Excecoes.ServicoExterno("O Jamendo não respondeu. Tente de novo em instantes.", e);
        }

        if (resposta == null || resposta.headers() == null || !"success".equals(resposta.headers().status())) {
            String detalhe = resposta != null && resposta.headers() != null ? resposta.headers().mensagemErro() : null;
            throw new Excecoes.ServicoExterno(
                    "O Jamendo recusou o pedido" + (detalhe == null || detalhe.isBlank() ? "." : ": " + detalhe), null);
        }
        return resposta.results() == null ? List.of() : resposta.results();
    }
}
