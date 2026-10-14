package com.cauan.vybe.playlist;

import com.cauan.vybe.playlist.PlaylistDtos.AdicionarRequest;
import com.cauan.vybe.playlist.PlaylistDtos.NomeRequest;
import com.cauan.vybe.playlist.PlaylistDtos.PlaylistDetalhe;
import com.cauan.vybe.playlist.PlaylistDtos.PlaylistResumo;
import com.cauan.vybe.seguranca.UsuarioAtual;
import jakarta.validation.Valid;
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

/** Playlists do usuário logado. */
@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistService service;

    public PlaylistController(PlaylistService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlaylistResumo> listar(@AuthenticationPrincipal Jwt jwt) {
        return service.listar(UsuarioAtual.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistResumo criar(@Valid @RequestBody NomeRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.criar(UsuarioAtual.id(jwt), req.nome());
    }

    @GetMapping("/{id}")
    public PlaylistDetalhe detalhe(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.detalhe(UsuarioAtual.id(jwt), id);
    }

    @PutMapping("/{id}")
    public PlaylistResumo renomear(@PathVariable Long id, @Valid @RequestBody NomeRequest req,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.renomear(UsuarioAtual.id(jwt), id, req.nome());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.excluir(UsuarioAtual.id(jwt), id);
    }

    @PostMapping("/{id}/musicas")
    public PlaylistDetalhe adicionar(@PathVariable Long id, @Valid @RequestBody AdicionarRequest req,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.adicionar(UsuarioAtual.id(jwt), id, req.musicaId());
    }

    @DeleteMapping("/{id}/musicas/{itemId}")
    public PlaylistDetalhe remover(@PathVariable Long id, @PathVariable Long itemId,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.remover(UsuarioAtual.id(jwt), id, itemId);
    }
}
