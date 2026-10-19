package com.cauan.vybe.usuario;

import com.cauan.vybe.arquivos.ArmazenamentoService;
import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.resumo.ResumoDtos.MaisOuvidas;
import com.cauan.vybe.resumo.ResumoService;
import com.cauan.vybe.seguranca.UsuarioAtual;
import com.cauan.vybe.usuario.PerfilDtos.AtualizarPerfil;
import com.cauan.vybe.usuario.PerfilDtos.PerfilResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/** Perfil do usuário logado e as imagens públicas de perfil. */
@RestController
public class PerfilController {

    private final PerfilService service;
    private final ResumoService resumo;
    private final ArmazenamentoService armazenamento;

    public PerfilController(PerfilService service, ResumoService resumo, ArmazenamentoService armazenamento) {
        this.service = service;
        this.resumo = resumo;
        this.armazenamento = armazenamento;
    }

    @GetMapping("/api/eu/perfil")
    public PerfilResponse perfil(@AuthenticationPrincipal Jwt jwt) {
        return service.perfil(UsuarioAtual.id(jwt));
    }

    @PutMapping("/api/eu/perfil")
    public PerfilResponse atualizar(@RequestBody AtualizarPerfil dados, @AuthenticationPrincipal Jwt jwt) {
        return service.atualizar(UsuarioAtual.id(jwt), dados.nome(), dados.bio());
    }

    @PostMapping("/api/eu/perfil/foto")
    public PerfilResponse trocarFoto(@RequestPart("arquivo") MultipartFile arquivo, @AuthenticationPrincipal Jwt jwt) {
        return service.trocarFoto(UsuarioAtual.id(jwt), arquivo);
    }

    @DeleteMapping("/api/eu/perfil/foto")
    public PerfilResponse removerFoto(@AuthenticationPrincipal Jwt jwt) {
        return service.removerFoto(UsuarioAtual.id(jwt));
    }

    @PostMapping("/api/eu/perfil/banner")
    public PerfilResponse trocarBanner(@RequestPart("arquivo") MultipartFile arquivo, @AuthenticationPrincipal Jwt jwt) {
        return service.trocarBanner(UsuarioAtual.id(jwt), arquivo);
    }

    @DeleteMapping("/api/eu/perfil/banner")
    public PerfilResponse removerBanner(@AuthenticationPrincipal Jwt jwt) {
        return service.removerBanner(UsuarioAtual.id(jwt));
    }

    /** ?periodo=mes (padrão) ou sempre. */
    @GetMapping("/api/eu/perfil/mais-ouvidas")
    public MaisOuvidas maisOuvidas(@RequestParam(defaultValue = "mes") String periodo, @AuthenticationPrincipal Jwt jwt) {
        return resumo.maisOuvidas(UsuarioAtual.id(jwt), periodo);
    }

    /** Públicas (como as capas): o <img> do navegador não manda o login. O endereço muda a cada troca. */
    @GetMapping("/api/usuarios/{id}/foto")
    public ResponseEntity<Resource> foto(@PathVariable Long id) {
        return imagem(service.arquivoDaImagem(id, false));
    }

    @GetMapping("/api/usuarios/{id}/banner")
    public ResponseEntity<Resource> banner(@PathVariable Long id) {
        return imagem(service.arquivoDaImagem(id, true));
    }

    /** No disco a API entrega; no R2 (versão hospedada), redireciona para o endereço direto. */
    private ResponseEntity<Resource> imagem(String nome) {
        var direto = armazenamento.enderecoDireto(nome, Tipo.CAPA);
        if (direto.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(direto.get())
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate()).build();
        }
        Resource arquivo = armazenamento.carregar(nome, Tipo.CAPA);
        MediaType tipo = MediaTypeFactory.getMediaType(arquivo).orElse(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(arquivo);
    }
}
