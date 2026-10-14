package com.cauan.vybe.usuario;

import com.cauan.vybe.usuario.AuthDtos.CadastroRequest;
import com.cauan.vybe.usuario.AuthDtos.LoginRequest;
import com.cauan.vybe.usuario.AuthDtos.SessaoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public SessaoResponse cadastrar(@Valid @RequestBody CadastroRequest req) {
        return service.cadastrar(req);
    }

    @PostMapping("/login")
    public SessaoResponse entrar(@Valid @RequestBody LoginRequest req) {
        return service.entrar(req);
    }
}
