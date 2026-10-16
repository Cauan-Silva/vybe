package com.cauan.vybe.youtube;

import com.cauan.vybe.seguranca.UsuarioAtual;
import com.cauan.vybe.youtube.MeusVideosDtos.DadosVideo;
import com.cauan.vybe.youtube.MeusVideosDtos.Progresso;
import com.cauan.vybe.youtube.MeusVideosDtos.VideoSalvo;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Vídeos do usuário logado: "Assistir depois", histórico e de onde continuar. */
@RestController
@RequestMapping("/api/eu/videos")
public class MeusVideosController {

    private final MeusVideosService service;

    public MeusVideosController(MeusVideosService service) {
        this.service = service;
    }

    @GetMapping("/assistir-depois")
    public List<VideoSalvo> assistirDepois(@AuthenticationPrincipal Jwt jwt) {
        return service.assistirDepois(UsuarioAtual.id(jwt));
    }

    @PutMapping("/assistir-depois")
    public VideoSalvo salvarParaDepois(@RequestBody DadosVideo video, @AuthenticationPrincipal Jwt jwt) {
        return service.salvarParaDepois(UsuarioAtual.id(jwt), video);
    }

    @DeleteMapping("/assistir-depois/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerDeDepois(@PathVariable String videoId, @AuthenticationPrincipal Jwt jwt) {
        service.removerDeDepois(UsuarioAtual.id(jwt), videoId);
    }

    @GetMapping("/historico")
    public List<VideoSalvo> historico(@AuthenticationPrincipal Jwt jwt) {
        return service.historico(UsuarioAtual.id(jwt));
    }

    @DeleteMapping("/historico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void limparHistorico(@AuthenticationPrincipal Jwt jwt) {
        service.limparHistorico(UsuarioAtual.id(jwt));
    }

    /** O player manda a posição a cada alguns segundos e ao pausar ou fechar. */
    @PutMapping("/progresso")
    public VideoSalvo registrarProgresso(@RequestBody Progresso progresso, @AuthenticationPrincipal Jwt jwt) {
        return service.registrarProgresso(UsuarioAtual.id(jwt), progresso.video(), progresso.posicaoSegundos());
    }
}
