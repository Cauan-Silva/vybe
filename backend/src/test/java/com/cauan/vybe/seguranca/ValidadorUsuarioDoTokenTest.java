package com.cauan.vybe.seguranca;

import com.cauan.vybe.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidadorUsuarioDoTokenTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ValidadorUsuarioDoToken validador = new ValidadorUsuarioDoToken(usuarios);

    private Jwt token(String sub) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").issuedAt(Instant.now()).claim("nome", "Cauan");
        if (sub != null) {
            b.subject(sub);
        }
        return b.build();
    }

    @Test
    void aceitaTokenDeUsuarioQueExiste() {
        when(usuarios.existsById(7L)).thenReturn(true);
        assertThat(validador.validate(token("7")).hasErrors()).isFalse();
    }

    @Test
    void recusaTokenDeUsuarioQueNaoExisteMais() {
        when(usuarios.existsById(7L)).thenReturn(false);
        assertThat(validador.validate(token("7")).hasErrors()).isTrue();
    }

    @Test
    void recusaTokenSemIdValido() {
        assertThat(validador.validate(token("abc")).hasErrors()).isTrue();
        assertThat(validador.validate(token(null)).hasErrors()).isTrue();
    }
}
