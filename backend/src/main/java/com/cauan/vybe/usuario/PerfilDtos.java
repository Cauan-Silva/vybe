package com.cauan.vybe.usuario;

/** Perfil do usuário: nome, frase, foto e banner. */
public final class PerfilDtos {

    private PerfilDtos() {
    }

    public record PerfilResponse(Long id, String nome, String bio, String fotoUrl, String bannerUrl,
                                 String membroDesde) {

        static PerfilResponse de(Usuario u) {
            return new PerfilResponse(u.getId(), u.getNome(), u.getBio(), u.fotoUrl(), u.bannerUrl(),
                    u.getCriadoEm() == null ? null : u.getCriadoEm().toLocalDate().toString());
        }
    }

    public record AtualizarPerfil(String nome, String bio) {
    }
}
