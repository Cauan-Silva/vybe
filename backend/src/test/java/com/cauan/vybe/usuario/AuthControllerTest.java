package com.cauan.vybe.usuario;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.seguranca.ProtecaoAuth;
import com.cauan.vybe.seguranca.RelogioAjustavel;
import com.cauan.vybe.seguranca.SegurancaConfig;
import com.cauan.vybe.usuario.AuthDtos.SessaoResponse;
import com.cauan.vybe.usuario.AuthDtos.UsuarioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login, cadastro e o filtro do token de ponta a ponta (MockMvc, sem banco: o serviço e o
 * repositório são simulados). Cada teste usa um IP próprio porque o limitador vive na memória
 * do contexto, que é compartilhado entre os testes.
 */
@WebMvcTest(AuthController.class)
@Import({SegurancaConfig.class, ProtecaoAuth.class, AuthControllerTest.Relogio.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        RelogioAjustavel relogioAjustavel() {
            return new RelogioAjustavel(Instant.parse("2026-11-19T12:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RelogioAjustavel relogio;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private Clock clock;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UsuarioRepository usuarios;

    @BeforeEach
    void setUp() {
        when(authService.entrar(argThat(r -> r != null && "errada".equals(r.senha()))))
                .thenThrow(new Excecoes.CredenciaisInvalidas());
        when(authService.entrar(argThat(r -> r != null && "certa".equals(r.senha()))))
                .thenReturn(sessao());
        when(authService.cadastrar(any())).thenReturn(sessao());
    }

    private static SessaoResponse sessao() {
        return new SessaoResponse("tok", new UsuarioResponse(1L, "Cauan", "cauan@email.com"),
                Instant.parse("2026-11-26T12:00:00Z"));
    }

    private MockHttpServletRequestBuilder login(String email, String senha, String ip) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
    }

    private MockHttpServletRequestBuilder cadastro(String email, String ip) {
        return post("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cauan\",\"email\":\"" + email + "\",\"senha\":\"senha-forte\"}")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
    }

    @Test
    void depoisDeCincoSenhasErradasResponde429SemConsultarOServico() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("alvo@email.com", "errada", "10.0.0.1")).andExpect(status().isUnauthorized());
        }

        mvc.perform(login("alvo@email.com", "certa", "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("tentativas")));

        verify(authService, times(5)).entrar(argThat(r -> r != null && "alvo@email.com".equals(r.email())));
    }

    @Test
    void emailComOutraGrafiaContaComoOMesmoLogin() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login(i % 2 == 0 ? "Grafia@Email.com" : "grafia@email.com", "errada", "10.0.0.2"));
        }
        mvc.perform(login("GRAFIA@email.com", "certa", "10.0.0.2")).andExpect(status().isTooManyRequests());
    }

    @Test
    void outroIpNaoFicaBloqueado() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("ip@email.com", "errada", "10.0.0.3"));
        }
        mvc.perform(login("ip@email.com", "certa", "10.0.0.4")).andExpect(status().isOk());
    }

    @Test
    void loginCertoZeraAsFalhas() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(login("zera@email.com", "errada", "10.0.0.5")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("zera@email.com", "certa", "10.0.0.5")).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            mvc.perform(login("zera@email.com", "errada", "10.0.0.5")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("zera@email.com", "certa", "10.0.0.5")).andExpect(status().isOk());
    }

    @Test
    void bloqueioAcabaDepoisDe15Minutos() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("espera@email.com", "errada", "10.0.0.6"));
        }
        mvc.perform(login("espera@email.com", "certa", "10.0.0.6")).andExpect(status().isTooManyRequests());

        relogio.avancar(Duration.ofMinutes(15));

        mvc.perform(login("espera@email.com", "certa", "10.0.0.6")).andExpect(status().isOk());
    }

    @Test
    void cadastroTemLimitePorIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(cadastro("novo" + i + "@email.com", "10.0.1.1")).andExpect(status().isCreated());
        }
        mvc.perform(cadastro("novo10@email.com", "10.0.1.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("cadastros")));

        mvc.perform(cadastro("outro@email.com", "10.0.1.2")).andExpect(status().isCreated());
    }

    private String token(long usuarioId) {
        Instant agora = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("vybe-api")
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofHours(1)))
                .subject(String.valueOf(usuarioId))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void tokenDeUsuarioQueExistePassaPeloFiltro() throws Exception {
        when(usuarios.existsById(41L)).thenReturn(true);

        // Não há controlador nesta rota no teste: 404 quer dizer que o login foi aceito.
        mvc.perform(get("/api/eu/qualquer-coisa").header("Authorization", "Bearer " + token(41L)))
                .andExpect(status().isNotFound());
        verify(usuarios, atLeastOnce()).existsById(41L);
    }

    @Test
    void tokenDeUsuarioExcluidoERecusado() throws Exception {
        when(usuarios.existsById(42L)).thenReturn(false);

        mvc.perform(get("/api/eu/qualquer-coisa").header("Authorization", "Bearer " + token(42L)))
                .andExpect(status().isUnauthorized());
    }
}
