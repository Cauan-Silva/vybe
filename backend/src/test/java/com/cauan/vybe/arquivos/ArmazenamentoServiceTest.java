package com.cauan.vybe.arquivos;

import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.comum.Excecoes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArmazenamentoServiceTest {

    @Mock
    private Destino destino;

    @Test
    void salvaComNomeGeradoNaPastaDoTipo() {
        ArmazenamentoService service = new ArmazenamentoService(destino);
        var arquivo = new MockMultipartFile("arquivo", "Minha Música.mp3", "audio/mpeg", new byte[]{1, 2, 3});

        String nome = service.salvar(arquivo, Tipo.AUDIO);

        assertThat(nome).matches("[0-9a-f-]{36}\\.mp3"); // o nome original nunca é usado
        verify(destino).gravar(eq("audio/" + nome), any(), eq(3L), eq("audio/mpeg"));
    }

    @Test
    void recusaFormatoErradoEImagemGrande() {
        ArmazenamentoService service = new ArmazenamentoService(destino);

        assertThatThrownBy(() -> service.salvar(new MockMultipartFile("a", "x.exe", "application/x-msdownload", new byte[]{1}), Tipo.AUDIO))
                .isInstanceOf(Excecoes.RegraNegocio.class);
        assertThatThrownBy(() -> service.salvar(new MockMultipartFile("a", "x.png", "image/png", new byte[6 * 1024 * 1024]), Tipo.CAPA))
                .isInstanceOf(Excecoes.RegraNegocio.class).hasMessageContaining("5 MB");
        verify(destino, never()).gravar(any(), any(), anyLong(), any());
    }

    @Test
    void enderecoDiretoVemDoDestino() {
        ArmazenamentoService service = new ArmazenamentoService(destino);
        when(destino.enderecoDireto("capas/abc-123.png")).thenReturn(Optional.of(URI.create("https://r2/capas/abc-123.png")));

        assertThat(service.enderecoDireto("abc-123.png", Tipo.CAPA)).contains(URI.create("https://r2/capas/abc-123.png"));
    }

    @Test
    void nomeEstranhoNemChegaAoDestino() {
        ArmazenamentoService service = new ArmazenamentoService(destino);

        assertThatThrownBy(() -> service.carregar("../../etc/passwd", Tipo.AUDIO)).isInstanceOf(Excecoes.NaoEncontrado.class);
        service.apagar("../segredo.txt", Tipo.CAPA); // só registra no log
        verify(destino, never()).ler(any());
        verify(destino, never()).apagar(startsWith("capas/.."));
    }
}
