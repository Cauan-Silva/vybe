package com.cauan.vybe.usuario;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.AuthDtos.CadastroRequest;
import com.cauan.vybe.usuario.AuthDtos.LoginRequest;
import com.cauan.vybe.usuario.AuthDtos.SessaoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository repository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtEncoder jwtEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        service = new AuthService(repository, passwordEncoder, jwtEncoder, relogio, 168);
    }

    private void jwtDevolveToken() {
        when(jwtEncoder.encode(any())).thenReturn(
                Jwt.withTokenValue("token-teste").header("alg", "HS256").claim("sub", "1").build());
    }

    @Test
    void cadastroGuardaEmailMinusculoESenhaCriptografada() {
        when(repository.existsByEmailIgnoreCase("cauan@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-forte")).thenReturn("hash");
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        jwtDevolveToken();

        SessaoResponse sessao = service.cadastrar(new CadastroRequest("Cauan", " Cauan@Email.com ", "senha-forte"));

        assertThat(sessao.token()).isEqualTo("token-teste");
        assertThat(sessao.usuario().email()).isEqualTo("cauan@email.com");
        assertThat(sessao.expiraEm()).isEqualTo(Instant.parse("2026-10-21T12:00:00Z"));
    }

    @Test
    void naoCadastraEmailRepetido() {
        when(repository.existsByEmailIgnoreCase("cauan@email.com")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(new CadastroRequest("Cauan", "cauan@email.com", "senha-forte")))
                .isInstanceOf(Excecoes.Conflito.class);
        verify(repository, never()).save(any());
    }

    @Test
    void senhaErradaNaoEntra() {
        Usuario u = new Usuario("Cauan", "cauan@email.com", "hash", LocalDateTime.now());
        when(repository.findByEmailIgnoreCase("cauan@email.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar(new LoginRequest("cauan@email.com", "errada")))
                .isInstanceOf(Excecoes.CredenciaisInvalidas.class);
        verify(jwtEncoder, never()).encode(any());
    }
}
