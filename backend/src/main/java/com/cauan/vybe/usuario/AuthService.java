package com.cauan.vybe.usuario;

import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.AuthDtos.CadastroRequest;
import com.cauan.vybe.usuario.AuthDtos.LoginRequest;
import com.cauan.vybe.usuario.AuthDtos.SessaoResponse;
import com.cauan.vybe.usuario.AuthDtos.UsuarioResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration validade;

    public AuthService(UsuarioRepository repository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       Clock clock,
                       @Value("${vybe.jwt.validade-horas}") long validadeHoras) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.validade = Duration.ofHours(validadeHoras);
    }

    @Transactional
    public SessaoResponse cadastrar(CadastroRequest req) {
        String email = req.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new Excecoes.Conflito("Já existe uma conta com esse e-mail");
        }
        Usuario usuario = repository.save(new Usuario(req.nome().trim(), email,
                passwordEncoder.encode(req.senha()), LocalDateTime.now(clock)));
        return gerarSessao(usuario);
    }

    @Transactional(readOnly = true)
    public SessaoResponse entrar(LoginRequest req) {
        Usuario usuario = repository.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()))
                .orElseThrow(Excecoes.CredenciaisInvalidas::new);
        return gerarSessao(usuario);
    }

    private SessaoResponse gerarSessao(Usuario usuario) {
        Instant agora = clock.instant();
        Instant expira = agora.plus(validade);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("vybe-api")
                .issuedAt(agora)
                .expiresAt(expira)
                .subject(String.valueOf(usuario.getId()))
                .claim("nome", usuario.getNome())
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new SessaoResponse(token, UsuarioResponse.de(usuario), expira);
    }
}
