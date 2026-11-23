package com.cauan.vybe.usuario;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.seguranca.ProtecaoAuth;
import com.cauan.vybe.usuario.AuthDtos.CadastroRequest;
import com.cauan.vybe.usuario.AuthDtos.LoginRequest;
import com.cauan.vybe.usuario.AuthDtos.SessaoResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ProtecaoAuth protecao;

    public AuthController(AuthService service, ProtecaoAuth protecao) {
        this.service = service;
        this.protecao = protecao;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public SessaoResponse cadastrar(@Valid @RequestBody CadastroRequest req, HttpServletRequest pedido) {
        protecao.registrarCadastro(pedido.getRemoteAddr());
        return service.cadastrar(req);
    }

    /** Senha errada conta para o limite de tentativas; login certo zera a contagem. */
    @PostMapping("/login")
    public SessaoResponse entrar(@Valid @RequestBody LoginRequest req, HttpServletRequest pedido) {
        String ip = pedido.getRemoteAddr();
        protecao.verificarLogin(req.email(), ip);
        try {
            SessaoResponse sessao = service.entrar(req);
            protecao.sucessoLogin(req.email(), ip);
            return sessao;
        } catch (Excecoes.CredenciaisInvalidas e) {
            protecao.falhaLogin(req.email(), ip);
            throw e;
        }
    }
}
