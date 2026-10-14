package com.cauan.vybe.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record CadastroRequest(
            @NotBlank @Size(max = 100) String nome,
            @NotBlank @Email(message = "e-mail inválido") @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72, message = "a senha precisa ter entre 8 e 72 caracteres") String senha) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {
    }

    public record UsuarioResponse(Long id, String nome, String email) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail());
        }
    }

    public record SessaoResponse(String token, UsuarioResponse usuario, Instant expiraEm) {
    }
}
