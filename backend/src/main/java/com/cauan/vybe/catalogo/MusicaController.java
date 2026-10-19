package com.cauan.vybe.catalogo;

import com.cauan.vybe.arquivos.ArmazenamentoService;
import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.arquivos.StreamingService;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.EnvioService.DadosEnvio;
import com.cauan.vybe.seguranca.UsuarioAtual;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/musicas")
public class MusicaController {

    private final CatalogoService catalogo;
    private final EnvioService envio;
    private final ArmazenamentoService armazenamento;
    private final StreamingService streaming;

    public MusicaController(CatalogoService catalogo,
                            EnvioService envio,
                            ArmazenamentoService armazenamento,
                            StreamingService streaming) {
        this.catalogo = catalogo;
        this.envio = envio;
        this.armazenamento = armazenamento;
        this.streaming = streaming;
    }

    /** Músicas adicionadas por último. */
    @GetMapping
    public List<MusicaResponse> novidades() {
        return catalogo.novidades();
    }

    @GetMapping("/{id}")
    public MusicaResponse buscar(@PathVariable Long id) {
        return catalogo.musica(id);
    }

    /** Áudio em pedaços (HTTP Range). Veja StreamingService. */
    @GetMapping("/{id}/audio")
    public ResponseEntity<?> audio(@PathVariable Long id,
                                   @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        Musica musica = catalogo.obterMusica(id);
        if (musica.getOrigem() != Origem.UPLOAD) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(musica.getUrlExterna())).build();
        }
        // Versão hospedada: o áudio vem direto do R2, que entende os pedaços (Range) sozinho.
        var direto = armazenamento.enderecoDireto(musica.getArquivo(), Tipo.AUDIO);
        if (direto.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(direto.get())
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate()).build();
        }
        MediaType tipo = musica.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(musica.getContentType());
        return streaming.responder(armazenamento.carregar(musica.getArquivo(), Tipo.AUDIO), tipo, range);
    }

    /**
     * Envio de música (formulário multipart). O navegador calcula a duração
     * do áudio antes de enviar e manda em duracaoSegundos.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MusicaResponse enviar(@RequestParam("arquivo") MultipartFile arquivo,
                                 @RequestParam(value = "capa", required = false) MultipartFile capa,
                                 @RequestParam("titulo") String titulo,
                                 @RequestParam("artista") String artista,
                                 @RequestParam(value = "album", required = false) String album,
                                 @RequestParam("duracaoSegundos") int duracaoSegundos,
                                 @AuthenticationPrincipal Jwt jwt) {
        return envio.enviar(new DadosEnvio(titulo, artista, album, duracaoSegundos), arquivo, capa,
                UsuarioAtual.id(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        envio.excluir(id, UsuarioAtual.id(jwt));
    }
}
