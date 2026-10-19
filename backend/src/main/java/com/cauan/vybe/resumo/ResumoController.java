package com.cauan.vybe.resumo;

import com.cauan.vybe.resumo.ResumoDtos.ResumoResponse;
import com.cauan.vybe.seguranca.UsuarioAtual;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Seu mês no VYBE" do usuário logado. */
@RestController
@RequestMapping("/api/eu/resumo")
public class ResumoController {

    private final ResumoService service;

    public ResumoController(ResumoService service) {
        this.service = service;
    }

    /** ?mes=2026-10 (sem o parâmetro, o mês atual). */
    @GetMapping
    public ResumoResponse resumo(@RequestParam(required = false) String mes, @AuthenticationPrincipal Jwt jwt) {
        return service.resumo(UsuarioAtual.id(jwt), mes);
    }
}
