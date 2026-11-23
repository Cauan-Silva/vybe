package com.cauan.vybe.seguranca;

import com.cauan.vybe.usuario.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Um token assinado continua valendo até expirar, mesmo que a conta tenha sido apagada.
 * Este validador confere no banco, a cada pedido com login, se o usuário do token ainda existe.
 * (O VYBE ainda não tem troca de senha nem conta desativada; quando tiver, a conferência entra aqui.)
 */
public class ValidadorUsuarioDoToken implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALIDO = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "A conta deste login não existe mais", null);

    private final UsuarioRepository usuarios;

    public ValidadorUsuarioDoToken(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(INVALIDO);
        }
        return usuarios.existsById(id)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALIDO);
    }
}
