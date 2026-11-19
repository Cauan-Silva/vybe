package com.cauan.vybe.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A chave dos tokens (criada ao subir a API) recusa o segredo público fora dos perfis dev/test. */
class SegurancaConfigInicioTest {

    private final SegurancaConfig config = new SegurancaConfig();

    private static MockEnvironment perfis(String... perfis) {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles(perfis);
        return ambiente;
    }

    @Test
    void naoCriaAChaveComSegredoPadraoEmProducao() {
        assertThatThrownBy(() -> config.chaveJwt(SegredoJwt.PADRAO_DESENVOLVIMENTO, perfis("nuvem")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SEGREDO");
        assertThatThrownBy(() -> config.chaveJwt(SegredoJwt.PADRAO_DESENVOLVIMENTO, perfis()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void criaAChaveComSegredoPadraoNoPerfilDev() {
        assertThat(config.chaveJwt(SegredoJwt.PADRAO_DESENVOLVIMENTO, perfis("dev"))).isNotNull();
    }

    @Test
    void criaAChaveComSegredoForteEmProducao() {
        assertThat(config.chaveJwt("x".repeat(48), perfis("nuvem")).getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
