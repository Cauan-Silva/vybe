package com.cauan.vybe.youtube;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.youtube.YoutubeDtos.Status;
import com.cauan.vybe.youtube.YoutubeDtos.VideoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Busca no YouTube. Exige login (só o status é público) para que visitantes
 * não gastem a cota diária da chave.
 */
@RestController
@RequestMapping("/api/youtube")
public class YoutubeController {

    private final YoutubeService service;
    private final EquivalenteService equivalentes;

    public YoutubeController(YoutubeService service, EquivalenteService equivalentes) {
        this.service = service;
        this.equivalentes = equivalentes;
    }

    /** App Android: o vídeo do YouTube com a mesma música de uma faixa do Spotify. */
    @GetMapping("/equivalente/{musicaId}")
    public EquivalenteService.Equivalente equivalente(@PathVariable Long musicaId) {
        return equivalentes.equivalente(musicaId);
    }

    @GetMapping("/status")
    public Status status() {
        return new Status(service.disponivel());
    }

    /** Aba Vídeos: qualquer vídeo. Ex.: GET /api/youtube/videos?q=receita de bolo */
    @GetMapping("/videos")
    public List<VideoResponse> videos(@RequestParam(value = "q", required = false) String termo) {
        return service.buscarVideos(termo);
    }

    /** Aba Vídeos: os vídeos em alta no Brasil, de todas as categorias ou de uma (ex.: ?categoria=20). */
    @GetMapping("/videos/em-alta")
    public List<VideoResponse> emAlta(@RequestParam(value = "categoria", required = false) Integer categoria) {
        return service.videosEmAlta(categoria);
    }

    /** Página do canal: dados e vídeos mais recentes. */
    @GetMapping("/canais/{id}")
    public YoutubeDtos.CanalResponse canal(@PathVariable String id) {
        return service.canal(id);
    }

    /** Ex.: GET /api/youtube/busca?q=numb */
    @GetMapping("/busca")
    public List<MusicaResponse> buscar(@RequestParam(value = "q", required = false) String termo) {
        return service.buscar(termo);
    }
}
