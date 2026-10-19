package com.cauan.vybe.arquivos;

import com.cauan.vybe.comum.Excecoes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinoDiscoTest {

    @TempDir
    Path pasta;

    @Test
    void gravaLeEApaga() throws Exception {
        DestinoDisco destino = new DestinoDisco(pasta.toString());
        destino.gravar("audio/abc.mp3", new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "audio/mpeg");

        assertThat(destino.ler("audio/abc.mp3").contentLength()).isEqualTo(3);
        assertThat(destino.enderecoDireto("audio/abc.mp3")).isEmpty(); // no disco a API entrega

        destino.apagar("audio/abc.mp3");
        assertThat(Files.exists(pasta.resolve("audio/abc.mp3"))).isFalse();
    }

    @Test
    void criaAsPastasENaoSaiDaRaiz() {
        DestinoDisco destino = new DestinoDisco(pasta.toString());

        assertThat(Files.isDirectory(pasta.resolve("audio"))).isTrue();
        assertThat(Files.isDirectory(pasta.resolve("capas"))).isTrue();
        assertThatThrownBy(() -> destino.ler("../fora.txt")).isInstanceOf(Excecoes.NaoEncontrado.class);
        assertThatThrownBy(() -> destino.ler("audio/nao-existe.mp3")).isInstanceOf(Excecoes.NaoEncontrado.class);
    }
}
