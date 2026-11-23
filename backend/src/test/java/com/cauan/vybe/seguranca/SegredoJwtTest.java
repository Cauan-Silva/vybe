package com.cauan.vybe.seguranca;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegredoJwtTest {

    private static final String FORTE = "a".repeat(20) + "b".repeat(20);

    @Test
    void recusaSegredoPadraoForaDeDesenvolvimento() {
        assertThatThrownBy(() -> SegredoJwt.validar(SegredoJwt.PADRAO_DESENVOLVIMENTO, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SEGREDO");
        assertThatThrownBy(() -> SegredoJwt.validar(SegredoJwt.PADRAO_DESENVOLVIMENTO, List.of("nuvem")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SegredoJwt.validar(SegredoJwt.PADRAO_DESENVOLVIMENTO, List.of("desktop")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recusaSegredoCurtoForaDeDesenvolvimento() {
        assertThatThrownBy(() -> SegredoJwt.validar("curto-demais", List.of("nuvem")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
        assertThatThrownBy(() -> SegredoJwt.validar("", List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SegredoJwt.validar(null, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void contaBytesENaoCaracteres() {
        // 16 vezes "ç" = 32 bytes em UTF-8
        assertThatCode(() -> SegredoJwt.validar("ç".repeat(16), List.of("nuvem"))).doesNotThrowAnyException();
    }

    @Test
    void aceitaSegredoForte() {
        assertThatCode(() -> SegredoJwt.validar(FORTE, List.of("nuvem"))).doesNotThrowAnyException();
        assertThatCode(() -> SegredoJwt.validar(FORTE, List.of())).doesNotThrowAnyException();
    }

    @Test
    void aceitaSegredoPadraoNosPerfisDeDesenvolvimentoETeste() {
        assertThatCode(() -> SegredoJwt.validar(SegredoJwt.PADRAO_DESENVOLVIMENTO, List.of("dev")))
                .doesNotThrowAnyException();
        assertThatCode(() -> SegredoJwt.validar(SegredoJwt.PADRAO_DESENVOLVIMENTO, List.of("test")))
                .doesNotThrowAnyException();
    }

    @Test
    void perfilDeDesenvolvimentoAindaExigeTamanhoMinimoParaAChaveHmac() {
        assertThatThrownBy(() -> SegredoJwt.validar("curto", List.of("dev")))
                .isInstanceOf(IllegalStateException.class);
    }
}
