package com.cauan.vybe.youtube;

import com.cauan.vybe.seguranca.UsuarioAtual;
import com.cauan.vybe.youtube.FeedDtos.Canal;
import com.cauan.vybe.youtube.FeedDtos.Feed;
import com.cauan.vybe.youtube.FeedDtos.Interesse;
import com.cauan.vybe.youtube.FeedDtos.NovoInteresse;
import com.cauan.vybe.youtube.YoutubeDtos.VideoResponse;
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

/** Aba Vídeos personalizada do usuário logado. */
@RestController
@RequestMapping("/api/eu/videos")
public class FeedController {

    private final FeedService service;

    public FeedController(FeedService service) {
        this.service = service;
    }

    @GetMapping("/para-voce")
    public Feed paraVoce(@AuthenticationPrincipal Jwt jwt) {
        return service.paraVoce(UsuarioAtual.id(jwt));
    }

    @GetMapping("/inscricoes")
    public Feed inscricoes(@AuthenticationPrincipal Jwt jwt) {
        return service.inscricoes(UsuarioAtual.id(jwt));
    }

    @GetMapping("/canais-seguidos")
    public List<Canal> canaisSeguidos(@AuthenticationPrincipal Jwt jwt) {
        return service.canaisSeguidos(UsuarioAtual.id(jwt));
    }

    @PutMapping("/canais-seguidos")
    public Canal seguir(@RequestBody Canal canal, @AuthenticationPrincipal Jwt jwt) {
        return service.seguir(UsuarioAtual.id(jwt), canal);
    }

    @DeleteMapping("/canais-seguidos/{canalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deixarDeSeguir(@PathVariable String canalId, @AuthenticationPrincipal Jwt jwt) {
        service.deixarDeSeguir(UsuarioAtual.id(jwt), canalId);
    }

    @GetMapping("/interesses")
    public List<Interesse> interesses(@AuthenticationPrincipal Jwt jwt) {
        return service.interesses(UsuarioAtual.id(jwt));
    }

    @PostMapping("/interesses")
    public Interesse adicionarInteresse(@RequestBody NovoInteresse novo, @AuthenticationPrincipal Jwt jwt) {
        return service.adicionarInteresse(UsuarioAtual.id(jwt), novo == null ? null : novo.termo());
    }

    @DeleteMapping("/interesses/{interesseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerInteresse(@PathVariable Long interesseId, @AuthenticationPrincipal Jwt jwt) {
        service.removerInteresse(UsuarioAtual.id(jwt), interesseId);
    }

    @GetMapping("/interesses/{interesseId}/videos")
    public List<VideoResponse> videosDoInteresse(@PathVariable Long interesseId, @AuthenticationPrincipal Jwt jwt) {
        return service.videosDoInteresse(UsuarioAtual.id(jwt), interesseId);
    }
}
