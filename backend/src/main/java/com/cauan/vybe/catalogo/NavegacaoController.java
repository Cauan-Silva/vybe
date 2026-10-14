package com.cauan.vybe.catalogo;

import com.cauan.vybe.arquivos.ArmazenamentoService;
import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.catalogo.CatalogoDtos.AlbumDetalhe;
import com.cauan.vybe.catalogo.CatalogoDtos.ArtistaDetalhe;
import com.cauan.vybe.catalogo.CatalogoDtos.ArtistaResumo;
import com.cauan.vybe.catalogo.CatalogoDtos.BuscaResponse;
import com.cauan.vybe.comum.Excecoes;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Artistas, álbuns, capas e busca (públicos). */
@RestController
@RequestMapping("/api")
public class NavegacaoController {

    private final CatalogoService catalogo;
    private final ArmazenamentoService armazenamento;

    public NavegacaoController(CatalogoService catalogo, ArmazenamentoService armazenamento) {
        this.catalogo = catalogo;
        this.armazenamento = armazenamento;
    }

    @GetMapping("/artistas")
    public List<ArtistaResumo> artistas() {
        return catalogo.artistas();
    }

    @GetMapping("/artistas/{id}")
    public ArtistaDetalhe artista(@PathVariable Long id) {
        return catalogo.artista(id);
    }

    @GetMapping("/albuns/{id}")
    public AlbumDetalhe album(@PathVariable Long id) {
        return catalogo.album(id);
    }

    /** A URL da capa muda quando ela é trocada (?v=...), então pode ficar em cache por bastante tempo. */
    @GetMapping("/albuns/{id}/capa")
    public ResponseEntity<Resource> capa(@PathVariable Long id) {
        Album album = catalogo.obterAlbum(id);
        if (album.getCapaArquivo() == null) {
            throw new Excecoes.NaoEncontrado("Este álbum não tem capa");
        }
        Resource arquivo = armazenamento.carregar(album.getCapaArquivo(), Tipo.CAPA);
        MediaType tipo = MediaTypeFactory.getMediaType(arquivo).orElse(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(arquivo);
    }

    /** Ex.: GET /api/busca?q=samba — procura em títulos, artistas e álbuns. */
    @GetMapping("/busca")
    public BuscaResponse buscar(@RequestParam(value = "q", required = false) String termo) {
        return catalogo.buscar(termo);
    }
}
