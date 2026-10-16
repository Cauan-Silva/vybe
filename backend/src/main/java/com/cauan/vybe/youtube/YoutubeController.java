package com.cauan.vybe.youtube;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.youtube.YoutubeDtos.Status;
import org.springframework.web.bind.annotation.GetMapping;
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

    public YoutubeController(YoutubeService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Status status() {
        return new Status(service.disponivel());
    }

    /** Ex.: GET /api/youtube/busca?q=numb */
    @GetMapping("/busca")
    public List<MusicaResponse> buscar(@RequestParam(value = "q", required = false) String termo) {
        return service.buscar(termo);
    }
}
