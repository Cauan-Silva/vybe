package com.cauan.vybe.biblioteca;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.seguranca.UsuarioAtual;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Rotas do usuário logado ("eu"). */
@RestController
@RequestMapping("/api/eu")
public class BibliotecaController {

    private final BibliotecaService service;

    public BibliotecaController(BibliotecaService service) {
        this.service = service;
    }

    @GetMapping("/curtidas")
    public List<MusicaResponse> curtidas(@AuthenticationPrincipal Jwt jwt) {
        return service.curtidas(UsuarioAtual.id(jwt));
    }

    /** Só os ids, para marcar o coração nas listas sem baixar tudo. */
    @GetMapping("/curtidas/ids")
    public List<Long> idsCurtidos(@AuthenticationPrincipal Jwt jwt) {
        return service.idsCurtidos(UsuarioAtual.id(jwt));
    }

    @PutMapping("/curtidas/{musicaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void curtir(@PathVariable Long musicaId, @AuthenticationPrincipal Jwt jwt) {
        service.curtir(UsuarioAtual.id(jwt), musicaId);
    }

    @DeleteMapping("/curtidas/{musicaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descurtir(@PathVariable Long musicaId, @AuthenticationPrincipal Jwt jwt) {
        service.descurtir(UsuarioAtual.id(jwt), musicaId);
    }

    public record ReproducaoCriada(Long id) {
    }

    public record Tempo(int segundos) {
    }

    @PostMapping("/reproducoes/{musicaId}")
    public ReproducaoCriada registrarReproducao(@PathVariable Long musicaId, @AuthenticationPrincipal Jwt jwt) {
        return new ReproducaoCriada(service.registrarReproducao(UsuarioAtual.id(jwt), musicaId));
    }

    /** O player manda de tempos em tempos quanto da música já foi ouvido. */
    @PutMapping("/reproducoes/{reproducaoId}/tempo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarTempo(@PathVariable Long reproducaoId, @RequestBody Tempo tempo,
                               @AuthenticationPrincipal Jwt jwt) {
        service.registrarTempo(UsuarioAtual.id(jwt), reproducaoId, tempo.segundos());
    }

    @GetMapping("/recentes")
    public List<MusicaResponse> recentes(@AuthenticationPrincipal Jwt jwt) {
        return service.recentes(UsuarioAtual.id(jwt));
    }
}
