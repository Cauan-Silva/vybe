package com.cauan.vybe.seguranca;

import org.springframework.security.oauth2.jwt.Jwt;

/** O token guarda o id do usuário no campo "sub" (subject). */
public final class UsuarioAtual {

    private UsuarioAtual() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
