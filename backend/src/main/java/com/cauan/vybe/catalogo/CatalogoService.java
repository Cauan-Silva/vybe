package com.cauan.vybe.catalogo;

import com.cauan.vybe.catalogo.CatalogoDtos.AlbumDetalhe;
import com.cauan.vybe.catalogo.CatalogoDtos.AlbumResumo;
import com.cauan.vybe.catalogo.CatalogoDtos.ArtistaDetalhe;
import com.cauan.vybe.catalogo.CatalogoDtos.ArtistaResumo;
import com.cauan.vybe.catalogo.CatalogoDtos.BuscaResponse;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.comum.Excecoes;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Consultas do catálogo (só leitura). */
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private static final int LIMITE_NOVIDADES = 30;
    private static final int LIMITE_BUSCA = 20;

    private final MusicaRepository musicas;
    private final ArtistaRepository artistas;
    private final AlbumRepository albuns;

    public CatalogoService(MusicaRepository musicas, ArtistaRepository artistas, AlbumRepository albuns) {
        this.musicas = musicas;
        this.artistas = artistas;
        this.albuns = albuns;
    }

    public List<MusicaResponse> novidades() {
        return MusicaResponse.de(
                musicas.findByOrigemOrderByCriadaEmDesc(Origem.UPLOAD, PageRequest.of(0, LIMITE_NOVIDADES)));
    }

    public MusicaResponse musica(Long id) {
        return MusicaResponse.de(obterMusica(id));
    }

    public Musica obterMusica(Long id) {
        return musicas.findWithDetalhesById(id)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Música não encontrada"));
    }

    public List<ArtistaResumo> artistas() {
        return artistas.findAllByOrderByNomeAsc().stream().map(ArtistaResumo::de).toList();
    }

    public ArtistaDetalhe artista(Long id) {
        Artista artista = artistas.findById(id)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Artista não encontrado"));
        return new ArtistaDetalhe(
                artista.getId(),
                artista.getNome(),
                albuns.findByArtistaIdOrderByTituloAsc(id).stream().map(AlbumResumo::de).toList(),
                MusicaResponse.de(musicas.findByArtistaIdOrderByCriadaEmDesc(id)));
    }

    public AlbumDetalhe album(Long id) {
        Album album = obterAlbum(id);
        return new AlbumDetalhe(
                album.getId(),
                album.getTitulo(),
                album.getArtista().getId(),
                album.getArtista().getNome(),
                album.capaUrl(),
                MusicaResponse.de(musicas.findByAlbumIdOrderByCriadaEmAsc(id)));
    }

    public Album obterAlbum(Long id) {
        return albuns.findWithArtistaById(id)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Álbum não encontrado"));
    }

    public BuscaResponse buscar(String termo) {
        String limpo = termo == null ? "" : termo.trim().toLowerCase();
        if (limpo.isEmpty()) {
            return new BuscaResponse(List.of(), List.of(), List.of());
        }
        // Remove os curingas do LIKE para que um "%" digitado não vire "qualquer coisa".
        String padrao = "%" + limpo.replace("\\", "").replace("%", "").replace("_", "") + "%";
        PageRequest pagina = PageRequest.of(0, LIMITE_BUSCA);
        return new BuscaResponse(
                MusicaResponse.de(musicas.buscar(padrao, pagina)),
                artistas.buscar(padrao, pagina).stream().map(ArtistaResumo::de).toList(),
                albuns.buscar(padrao, pagina).stream().map(AlbumResumo::de).toList());
    }
}
