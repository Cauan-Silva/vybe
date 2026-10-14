package com.cauan.vybe.jamendo;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.jamendo.JamendoDtos.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo Creative Commons do Jamendo (público). */
@RestController
@RequestMapping("/api/jamendo")
public class JamendoController {

    private final JamendoService service;

    public JamendoController(JamendoService service) {
        this.service = service;
    }

    /** O front-end usa para saber se mostra as seções do Jamendo. */
    @GetMapping("/status")
    public Status status() {
        return new Status(service.disponivel());
    }

    /** Ex.: GET /api/jamendo/busca?q=lofi */
    @GetMapping("/busca")
    public List<MusicaResponse> buscar(@RequestParam(value = "q", required = false) String termo) {
        return service.buscar(termo);
    }

    @GetMapping("/populares")
    public List<MusicaResponse> populares() {
        return service.populares();
    }
}
