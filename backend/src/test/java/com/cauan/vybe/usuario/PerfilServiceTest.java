package com.cauan.vybe.usuario;

import com.cauan.vybe.arquivos.ArmazenamentoService;
import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.PerfilDtos.PerfilResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private ArmazenamentoService armazenamento;

    private PerfilService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new PerfilService(usuarios, armazenamento);
        usuario = new Usuario("Cauan", "cauan@exemplo.com", "hash", LocalDateTime.of(2026, 10, 1, 9, 0));
        usuario.setId(1L);
        lenient().when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
    }

    @Test
    void perfilSemImagensTemEnderecosNulos() {
        PerfilResponse p = service.perfil(1L);

        assertThat(p.nome()).isEqualTo("Cauan");
        assertThat(p.fotoUrl()).isNull();
        assertThat(p.bannerUrl()).isNull();
        assertThat(p.membroDesde()).isEqualTo("2026-10-01");
    }

    @Test
    void atualizarLimpaNomeEFraseEFraseVaziaApaga() {
        PerfilResponse p = service.atualizar(1L, "  LoLvSk   Gamer ", "  Ouvindo Valorant o dia todo ");
        assertThat(p.nome()).isEqualTo("LoLvSk Gamer");
        assertThat(p.bio()).isEqualTo("Ouvindo Valorant o dia todo");

        assertThat(service.atualizar(1L, null, "   ").bio()).isNull();
        assertThat(usuario.getNome()).isEqualTo("LoLvSk Gamer"); // nome nulo não mexe
    }

    @Test
    void atualizarRecusaNomeCurtoEFraseLonga() {
        assertThatThrownBy(() -> service.atualizar(1L, "x", null)).isInstanceOf(Excecoes.RegraNegocio.class);
        assertThatThrownBy(() -> service.atualizar(1L, null, "a".repeat(PerfilService.TAMANHO_BIO + 1)))
                .isInstanceOf(Excecoes.RegraNegocio.class);
    }

    @Test
    void trocarFotoGuardaANovaEApagaAAntigaSoDepoisDeConfirmar() {
        usuario.setFotoArquivo("antiga.png");
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "eu.png", "image/png", new byte[]{1, 2, 3});
        when(armazenamento.salvar(arquivo, Tipo.CAPA)).thenReturn("12345678-nova.png");

        PerfilResponse p = service.trocarFoto(1L, arquivo);

        assertThat(p.fotoUrl()).isEqualTo("/api/usuarios/1/foto?v=12345678");
        verify(armazenamento).apagarSeFalhar("12345678-nova.png", Tipo.CAPA);
        verify(armazenamento).apagarAposConfirmar("antiga.png", Tipo.CAPA);
    }

    @Test
    void removerBannerApagaDepoisDeConfirmar() {
        usuario.setBannerArquivo("banner.jpg");

        assertThat(service.removerBanner(1L).bannerUrl()).isNull();
        verify(armazenamento).apagarAposConfirmar("banner.jpg", Tipo.CAPA);
    }

    @Test
    void imagemQueNaoExisteDaNaoEncontrado() {
        assertThatThrownBy(() -> service.arquivoDaImagem(1L, true)).isInstanceOf(Excecoes.NaoEncontrado.class);
        when(usuarios.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.arquivoDaImagem(9L, false)).isInstanceOf(Excecoes.NaoEncontrado.class);
        usuario.setFotoArquivo("12345678-eu.png");
        assertThat(service.arquivoDaImagem(1L, false)).isEqualTo("12345678-eu.png");
    }
}
