package com.cauan.vybe.seguranca;

import com.cauan.vybe.usuario.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Público: ouvir e navegar pelo catálogo (o elemento &lt;audio&gt; do navegador não envia
 * cabeçalho de login, então o áudio e as capas precisam ser públicos).
 * Com login: enviar e excluir músicas, curtidas, recentes e playlists.
 */
@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // Páginas do VYBE podem aparecer em quadros só dentro do próprio VYBE: a janela
                // flutuante dos vídeos carrega /pip-video.html num quadro. Sites de fora, não.
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/musicas/**", "/api/artistas/**",
                                "/api/albuns/**", "/api/busca", "/api/jamendo/**", "/api/youtube/status",
                                "/api/usuarios/*/foto", "/api/usuarios/*/banner").permitAll()
                        // O Spotify redireciona o navegador para cá, sem o token do VYBE.
                        // Quem é o usuário vem do parâmetro "state" (veja SpotifyEstados). A página
                        // /app/spotify só avisa o resultado no navegador do celular (app Android).
                        .requestMatchers(HttpMethod.GET, "/api/spotify/callback", "/api/spotify/app/spotify").permitAll()
                        // Programa: o Electron pede o desligamento com um token próprio (veja DesktopController).
                        .requestMatchers(HttpMethod.POST, "/api/desktop/desligar").permitAll()
                        // Fora de /api só há as telas do programa (HTML, JS, CSS e imagens); na versão web não há nada.
                        .requestMatchers(pedido -> !pedido.getRequestURI().startsWith("/api/")).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Não deixa a API subir com o segredo público do application.yml (veja SegredoJwt). */
    @Bean
    public SecretKey chaveJwt(@Value("${vybe.jwt.segredo}") String segredo, Environment ambiente) {
        SegredoJwt.validar(segredo, Arrays.asList(ambiente.getActiveProfiles()));
        return new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey chaveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chaveJwt));
    }

    /** Além da assinatura e da validade, confere no banco se a conta do token ainda existe. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey chaveJwt, UsuarioRepository usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new ValidadorUsuarioDoToken(usuarios)));
        return decoder;
    }
}
