package com.cauan.vybe.usuario;

import com.cauan.vybe.arquivos.ArmazenamentoService;
import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.PerfilDtos.PerfilResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Perfil: nome, frase curta, foto e banner. As imagens vão para a mesma pasta das capas
 * (JPG, PNG ou WebP, até 5 MB); a antiga só é apagada depois que a troca foi confirmada no banco.
 */
@Service
public class PerfilService {

    static final int TAMANHO_BIO = 160;

    private final UsuarioRepository usuarios;
    private final ArmazenamentoService armazenamento;

    public PerfilService(UsuarioRepository usuarios, ArmazenamentoService armazenamento) {
        this.usuarios = usuarios;
        this.armazenamento = armazenamento;
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfil(Long usuarioId) {
        return PerfilResponse.de(obter(usuarioId));
    }

    /** Nome de 2 a 100 caracteres; frase até 160 (vazia apaga). */
    @Transactional
    public PerfilResponse atualizar(Long usuarioId, String nome, String bio) {
        Usuario u = obter(usuarioId);
        if (nome != null) {
            String limpo = nome.trim().replaceAll("\\s+", " ");
            if (limpo.length() < 2 || limpo.length() > 100) {
                throw new Excecoes.RegraNegocio("O nome precisa ter de 2 a 100 caracteres.");
            }
            u.setNome(limpo);
        }
        if (bio != null) {
            String limpa = bio.trim();
            if (limpa.length() > TAMANHO_BIO) {
                throw new Excecoes.RegraNegocio("A frase pode ter no máximo " + TAMANHO_BIO + " caracteres.");
            }
            u.setBio(limpa.isEmpty() ? null : limpa);
        }
        return PerfilResponse.de(u);
    }

    @Transactional
    public PerfilResponse trocarFoto(Long usuarioId, MultipartFile arquivo) {
        Usuario u = obter(usuarioId);
        String nova = armazenamento.salvar(arquivo, Tipo.CAPA);
        armazenamento.apagarSeFalhar(nova, Tipo.CAPA);
        armazenamento.apagarAposConfirmar(u.getFotoArquivo(), Tipo.CAPA);
        u.setFotoArquivo(nova);
        return PerfilResponse.de(u);
    }

    @Transactional
    public PerfilResponse trocarBanner(Long usuarioId, MultipartFile arquivo) {
        Usuario u = obter(usuarioId);
        String novo = armazenamento.salvar(arquivo, Tipo.CAPA);
        armazenamento.apagarSeFalhar(novo, Tipo.CAPA);
        armazenamento.apagarAposConfirmar(u.getBannerArquivo(), Tipo.CAPA);
        u.setBannerArquivo(novo);
        return PerfilResponse.de(u);
    }

    @Transactional
    public PerfilResponse removerFoto(Long usuarioId) {
        Usuario u = obter(usuarioId);
        armazenamento.apagarAposConfirmar(u.getFotoArquivo(), Tipo.CAPA);
        u.setFotoArquivo(null);
        return PerfilResponse.de(u);
    }

    @Transactional
    public PerfilResponse removerBanner(Long usuarioId) {
        Usuario u = obter(usuarioId);
        armazenamento.apagarAposConfirmar(u.getBannerArquivo(), Tipo.CAPA);
        u.setBannerArquivo(null);
        return PerfilResponse.de(u);
    }

    /** Nome do arquivo da foto ou do banner de qualquer usuário (as imagens são públicas, como as capas). */
    @Transactional(readOnly = true)
    public String arquivoDaImagem(Long usuarioId, boolean banner) {
        Usuario u = obter(usuarioId);
        String arquivo = banner ? u.getBannerArquivo() : u.getFotoArquivo();
        if (arquivo == null) {
            throw new Excecoes.NaoEncontrado(banner ? "Sem banner" : "Sem foto");
        }
        return arquivo;
    }

    private Usuario obter(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(() -> new Excecoes.NaoEncontrado("Usuário não encontrado"));
    }
}
