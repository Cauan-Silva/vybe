package com.cauan.vybe.seguranca;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;

/**
 * Confere o segredo que assina os tokens antes de a API subir. O valor padrão do
 * application.yml é público (está no GitHub): com ele, qualquer pessoa conseguiria criar
 * um token válido. Por isso ele só é aceito nos perfis "dev" (mvn spring-boot:run) e "test".
 */
public final class SegredoJwt {

    /** Tem que ser igual ao padrão de vybe.jwt.segredo no application.yml. */
    public static final String PADRAO_DESENVOLVIMENTO = "segredo-de-desenvolvimento-troque-em-producao-123";

    /** HS256 precisa de uma chave de pelo menos 256 bits. */
    static final int MINIMO_BYTES = 32;

    private static final Set<String> PERFIS_LIBERADOS = Set.of("dev", "test");

    private SegredoJwt() {
    }

    public static void validar(String segredo, Collection<String> perfisAtivos) {
        int bytes = segredo == null ? 0 : segredo.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MINIMO_BYTES) {
            throw new IllegalStateException("O segredo dos tokens (variável JWT_SEGREDO) precisa ter pelo menos "
                    + MINIMO_BYTES + " bytes. Gere um valor aleatório, por exemplo com: openssl rand -hex 48");
        }
        boolean liberado = perfisAtivos.stream().anyMatch(PERFIS_LIBERADOS::contains);
        if (PADRAO_DESENVOLVIMENTO.equals(segredo) && !liberado) {
            throw new IllegalStateException("A variável JWT_SEGREDO não foi definida e a API estava usando o segredo "
                    + "público de desenvolvimento, que permitiria a qualquer pessoa forjar logins. Defina JWT_SEGREDO "
                    + "com um valor aleatório de pelo menos " + MINIMO_BYTES + " bytes (ex.: openssl rand -hex 48) "
                    + "ou, só no seu computador, ligue o perfil \"dev\".");
        }
    }
}
