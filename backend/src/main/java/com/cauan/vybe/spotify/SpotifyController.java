package com.cauan.vybe.spotify;

import com.cauan.vybe.seguranca.UsuarioAtual;
import com.cauan.vybe.spotify.SpotifyDtos.Importacao;
import com.cauan.vybe.spotify.SpotifyDtos.MaisOuvidos;
import com.cauan.vybe.spotify.SpotifyDtos.PlaylistSpotify;
import com.cauan.vybe.spotify.SpotifyDtos.Status;
import com.cauan.vybe.spotify.SpotifyDtos.TokenPlayer;
import com.cauan.vybe.spotify.SpotifyDtos.UrlAutorizacao;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spotify")
public class SpotifyController {

    private final SpotifyService service;

    public SpotifyController(SpotifyService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Status status(@AuthenticationPrincipal Jwt jwt) {
        return service.status(UsuarioAtual.id(jwt));
    }

    /** Devolve o endereço da página de autorização do Spotify; o front-end redireciona para lá. */
    @GetMapping("/autorizar")
    public UrlAutorizacao autorizar(@AuthenticationPrincipal Jwt jwt) {
        return service.autorizar(UsuarioAtual.id(jwt));
    }

    /** Público: o Spotify manda o navegador para cá com ?code=...&state=... (ou ?error=...). */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String codigo,
                                         @RequestParam(value = "state", required = false) String estado,
                                         @RequestParam(value = "error", required = false) String erro) {
        return ResponseEntity.status(HttpStatus.FOUND).location(service.concluir(codigo, estado, erro)).build();
    }

    @DeleteMapping("/conexao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desconectar(@AuthenticationPrincipal Jwt jwt) {
        service.desconectar(UsuarioAtual.id(jwt));
    }

    @GetMapping("/playlists")
    public List<PlaylistSpotify> playlists(@AuthenticationPrincipal Jwt jwt) {
        return service.playlists(UsuarioAtual.id(jwt));
    }

    @PostMapping("/playlists/{id}/importar")
    public Importacao importar(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return service.importar(UsuarioAtual.id(jwt), id);
    }

    /** Token para o player do Spotify no navegador (só para contas com a permissão do player). */
    @GetMapping("/token")
    public TokenPlayer token(@AuthenticationPrincipal Jwt jwt) {
        return service.tokenDoPlayer(UsuarioAtual.id(jwt));
    }

    /** periodo: short_term (4 semanas), medium_term (6 meses) ou long_term (1 ano). */
    @GetMapping("/mais-ouvidos")
    public MaisOuvidos maisOuvidos(@RequestParam(value = "periodo", defaultValue = "medium_term") String periodo,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.maisOuvidos(UsuarioAtual.id(jwt), periodo);
    }
}
