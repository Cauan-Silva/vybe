package com.cauan.vybe.spotify;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.spotify.SpotifyApi.Artista;
import com.cauan.vybe.spotify.SpotifyApi.Faixa;
import com.cauan.vybe.spotify.SpotifyApi.ItemPlaylist;
import com.cauan.vybe.spotify.SpotifyApi.Pagina;
import com.cauan.vybe.spotify.SpotifyApi.Perfil;
import com.cauan.vybe.spotify.SpotifyApi.Playlist;
import com.cauan.vybe.spotify.SpotifyApi.Token;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Conversa com o Spotify: login (accounts.spotify.com) e Web API (api.spotify.com).
 * Não guarda nada; quem cuida dos tokens e do banco é o SpotifyService.
 */
@Component
public class SpotifyCliente {

    /**
     * Leitura de playlists (próprias e colaborativas) e dos mais ouvidos, mais o necessário
     * para o player: "streaming", "user-read-email" e "user-read-private" (exigidos pelo
     * Web Playback SDK) e "user-modify-playback-state" (para mandar tocar no player do VYBE).
     * Nada é alterado nas playlists ou na biblioteca da pessoa.
     */
    static final String ESCOPOS = "playlist-read-private playlist-read-collaborative user-top-read "
            + "streaming user-read-email user-read-private user-modify-playback-state";

    private static final int LIMITE_PLAYLISTS = 200;
    /** Resultados por busca (10 cabe no limite do modo de desenvolvimento do Spotify). */
    static final int LIMITE_BUSCA = 10;

    /** Quando o Spotify recusa o token (revogado ou expirado sem renovação). */
    public static class TokenRecusado extends RuntimeException {
        public TokenRecusado() {
            super("O Spotify recusou o acesso");
        }
    }

    private static final String API = "https://api.spotify.com/v1";

    private final RestClient contas;
    private final RestClient api;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public SpotifyCliente(RestClient.Builder builder,
                          @Value("${vybe.spotify.client-id:}") String clientId,
                          @Value("${vybe.spotify.client-secret:}") String clientSecret,
                          @Value("${vybe.spotify.redirect-uri}") String redirectUri) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(10_000);
        this.contas = builder.clone().baseUrl("https://accounts.spotify.com").requestFactory(fabrica).build();
        this.api = builder.clone().requestFactory(fabrica).build();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri;
    }

    public boolean configurado() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    /** Página de autorização do Spotify para onde mandamos a pessoa. */
    public String urlAutorizacao(String estado) {
        return UriComponentsBuilder.fromUriString("https://accounts.spotify.com/authorize")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", ESCOPOS)
                .queryParam("state", estado)
                .encode()
                .build()
                .toUriString();
    }

    /** Troca o código recebido no callback pelos tokens. */
    public Token trocarCodigo(String codigo) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", codigo);
        form.add("redirect_uri", redirectUri);
        return pedirToken(form);
    }

    /** Pede um access token novo com o refresh token (o access dura cerca de 1 hora). */
    public Token renovar(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return pedirToken(form);
    }

    public Perfil perfil(String token) {
        return get(token, "/me", new ParameterizedTypeReference<>() {});
    }

    /** Playlists da pessoa (até 200), seguindo a paginação. */
    public List<Playlist> playlists(String token) {
        List<Playlist> todas = new ArrayList<>();
        String caminho = "/me/playlists?limit=50";
        while (caminho != null && todas.size() < LIMITE_PLAYLISTS) {
            Pagina<Playlist> pagina = get(token, caminho, new ParameterizedTypeReference<>() {});
            if (pagina == null || pagina.items() == null) break;
            pagina.items().stream().filter(p -> p != null && p.id() != null).forEach(todas::add);
            caminho = proximo(pagina.next());
        }
        return todas;
    }

    /** Músicas de uma playlist (só funciona nas próprias ou colaborativas), até o limite. */
    public List<Faixa> faixasDaPlaylist(String token, String playlistId, int limite) {
        List<Faixa> faixas = new ArrayList<>();
        String caminho = "/playlists/" + playlistId + "/items?limit=50&additional_types=track";
        while (caminho != null && faixas.size() < limite) {
            Pagina<ItemPlaylist> pagina = get(token, caminho, new ParameterizedTypeReference<>() {});
            if (pagina == null || pagina.items() == null) break;
            for (ItemPlaylist item : pagina.items()) {
                Faixa faixa = item == null ? null : item.faixa();
                // Ignora episódios de podcast e arquivos locais sem id.
                if (faixa != null && "track".equals(faixa.type()) && faixa.name() != null && faixas.size() < limite) {
                    faixas.add(faixa);
                }
            }
            caminho = proximo(pagina.next());
        }
        return faixas;
    }

    /** Busca no catálogo inteiro do Spotify (só faixas). */
    public List<Faixa> buscarFaixas(String token, String termo) {
        String q = URLEncoder.encode(termo, StandardCharsets.UTF_8).replace("+", "%20");
        SpotifyApi.ResultadoBusca resultado = get(token, "/search?type=track&limit=" + LIMITE_BUSCA + "&q=" + q,
                new ParameterizedTypeReference<>() {});
        if (resultado == null || resultado.tracks() == null || resultado.tracks().items() == null) {
            return List.of();
        }
        return resultado.tracks().items().stream().filter(f -> f != null && f.id() != null).toList();
    }

    public List<Artista> artistasMaisOuvidos(String token, String periodo) {
        Pagina<Artista> pagina = get(token, "/me/top/artists?limit=10&time_range=" + periodo,
                new ParameterizedTypeReference<>() {});
        return pagina == null || pagina.items() == null ? List.of() : pagina.items();
    }

    public List<Faixa> musicasMaisOuvidas(String token, String periodo) {
        Pagina<Faixa> pagina = get(token, "/me/top/tracks?limit=10&time_range=" + periodo,
                new ParameterizedTypeReference<>() {});
        return pagina == null || pagina.items() == null ? List.of() : pagina.items();
    }

    private Token pedirToken(MultiValueMap<String, String> form) {
        String credenciais = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        try {
            return contas.post()
                    .uri("/api/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credenciais)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Token.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401) {
                throw new TokenRecusado();
            }
            throw new Excecoes.ServicoExterno("O Spotify não respondeu. Tente de novo em instantes.", e);
        } catch (RestClientException e) {
            throw new Excecoes.ServicoExterno("O Spotify não respondeu. Tente de novo em instantes.", e);
        }
    }

    private <T> T get(String token, String caminho, ParameterizedTypeReference<T> tipo) {
        try {
            // URI pronta (sem template): os links "next" do Spotify já vêm codificados.
            return api.get()
                    .uri(URI.create(API + caminho))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(tipo);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401) {
                throw new TokenRecusado();
            }
            if (status.value() == 403) {
                throw new Excecoes.RegraNegocio(
                        "O Spotify não liberou este conteúdo. Só é possível ler playlists criadas por você ou colaborativas.");
            }
            if (status.value() == 429) {
                throw new Excecoes.ServicoExterno("Muitos pedidos ao Spotify. Espere um minuto e tente de novo.", e);
            }
            throw new Excecoes.ServicoExterno("O Spotify não respondeu. Tente de novo em instantes.", e);
        } catch (RestClientException e) {
            throw new Excecoes.ServicoExterno("O Spotify não respondeu. Tente de novo em instantes.", e);
        }
    }

    /** O "next" vem como URL completa; usamos só o caminho depois de /v1. */
    private static String proximo(String next) {
        if (next == null || next.isBlank()) return null;
        int i = next.indexOf("/v1/");
        return i >= 0 ? next.substring(i + 3) : null;
    }
}
