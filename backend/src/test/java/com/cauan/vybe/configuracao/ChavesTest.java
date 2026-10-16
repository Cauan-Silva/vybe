package com.cauan.vybe.configuracao;

import com.cauan.vybe.configuracao.Chaves.Chave;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChavesTest {

    @Mock
    private ConfiguracaoRepository repositorio;

    private Chaves web() {
        return new Chaves(repositorio, "jamendo-env", "spotify-env", "secret-env", "youtube-env", false);
    }

    private Chaves programa() {
        return new Chaves(repositorio, "", "", "secret-que-nao-deve-ser-usado", "", true);
    }

    @Test
    void valorSalvoPelaTelaGanhaDaVariavelDeAmbiente() {
        when(repositorio.findAll()).thenReturn(List.of(new Configuracao("YOUTUBE_API_KEY", "da-tela")));
        Chaves chaves = web();

        assertThat(chaves.valor(Chave.YOUTUBE_API_KEY)).isEqualTo("da-tela");
        assertThat(chaves.valor(Chave.JAMENDO_CLIENT_ID)).isEqualTo("jamendo-env");
    }

    @Test
    void noProgramaOClientSecretDoAmbienteEIgnorado() {
        assertThat(programa().valor(Chave.SPOTIFY_CLIENT_SECRET)).isEmpty();
        assertThat(web().valor(Chave.SPOTIFY_CLIENT_SECRET)).isEqualTo("secret-env");
    }

    @Test
    void salvarVazioApagaESalvarReleNaProximaLeitura() {
        when(repositorio.findAll()).thenReturn(List.of());
        Chaves chaves = programa();
        assertThat(chaves.valor(Chave.SPOTIFY_CLIENT_ID)).isEmpty();

        chaves.salvar(Chave.SPOTIFY_CLIENT_ID, "  ");
        verify(repositorio).deleteById("SPOTIFY_CLIENT_ID");

        when(repositorio.findAll()).thenReturn(List.of(new Configuracao("SPOTIFY_CLIENT_ID", "abc")));
        chaves.salvar(Chave.SPOTIFY_CLIENT_ID, " abc ");
        verify(repositorio).save(any(Configuracao.class));
        assertThat(chaves.valor(Chave.SPOTIFY_CLIENT_ID)).isEqualTo("abc");
    }

    @Test
    void naoDeixaSalvarOClientSecret() {
        assertThatThrownBy(() -> programa().salvar(Chave.SPOTIFY_CLIENT_SECRET, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
