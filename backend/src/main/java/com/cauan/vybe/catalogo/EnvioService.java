package com.cauan.vybe.catalogo;

import com.cauan.vybe.arquivos.ArmazenamentoService;
import com.cauan.vybe.arquivos.ArmazenamentoService.Tipo;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** Envio e exclusão de músicas. */
@Service
public class EnvioService {

    private static final int DURACAO_MAXIMA = 60 * 60; // 1 hora

    private final MusicaRepository musicas;
    private final ArtistaRepository artistas;
    private final AlbumRepository albuns;
    private final UsuarioRepository usuarios;
    private final ArmazenamentoService armazenamento;
    private final Clock clock;

    public EnvioService(MusicaRepository musicas,
                        ArtistaRepository artistas,
                        AlbumRepository albuns,
                        UsuarioRepository usuarios,
                        ArmazenamentoService armazenamento,
                        Clock clock) {
        this.musicas = musicas;
        this.artistas = artistas;
        this.albuns = albuns;
        this.usuarios = usuarios;
        this.armazenamento = armazenamento;
        this.clock = clock;
    }

    public record DadosEnvio(String titulo, String artista, String album, int duracaoSegundos) {
    }

    /**
     * Salva a música. Artista e álbum são encontrados pelo nome (sem diferenciar
     * maiúsculas) ou criados na hora. Sem álbum, a música vira um single com o próprio título.
     */
    @Transactional
    public MusicaResponse enviar(DadosEnvio dados, MultipartFile audio, MultipartFile capa, Long usuarioId) {
        String titulo = obrigatorio(dados.titulo(), "título", 200);
        String nomeArtista = obrigatorio(dados.artista(), "artista", 150);
        String tituloAlbum = dados.album() == null || dados.album().isBlank()
                ? titulo
                : obrigatorio(dados.album(), "álbum", 200);
        if (dados.duracaoSegundos() <= 0 || dados.duracaoSegundos() > DURACAO_MAXIMA) {
            throw new Excecoes.RegraNegocio("Não foi possível ler a duração do áudio. Confira se o arquivo toca normalmente.");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        Artista artista = artistas.findByNomeIgnoreCase(nomeArtista)
                .orElseGet(() -> artistas.save(new Artista(nomeArtista, agora)));
        Album album = albuns.findByArtistaIdAndTituloIgnoreCase(artista.getId(), tituloAlbum)
                .orElseGet(() -> albuns.save(new Album(tituloAlbum, artista, agora)));

        if (capa != null && !capa.isEmpty()) {
            String capaAntiga = album.getCapaArquivo();
            String novaCapa = armazenamento.salvar(capa, Tipo.CAPA);
            armazenamento.apagarSeFalhar(novaCapa, Tipo.CAPA);
            armazenamento.apagarAposConfirmar(capaAntiga, Tipo.CAPA);
            album.setCapaArquivo(novaCapa);
        }

        String arquivo = armazenamento.salvar(audio, Tipo.AUDIO);
        armazenamento.apagarSeFalhar(arquivo, Tipo.AUDIO);

        Musica musica = new Musica();
        musica.setTitulo(titulo);
        musica.setArtista(artista);
        musica.setAlbum(album);
        musica.setDuracaoSegundos(dados.duracaoSegundos());
        musica.setOrigem(Origem.UPLOAD);
        musica.setArquivo(arquivo);
        musica.setContentType(audio.getContentType());
        musica.setTamanhoBytes(audio.getSize());
        musica.setEnviadaPor(usuarios.getReferenceById(usuarioId));
        musica.setCriadaEm(agora);

        return MusicaResponse.de(musicas.save(musica));
    }

    /**
     * Só quem enviou pode excluir. Se o álbum ou o artista ficarem sem músicas,
     * também são removidos. Arquivos só somem do disco depois que o banco confirma.
     */
    @Transactional
    public void excluir(Long musicaId, Long usuarioId) {
        Musica musica = musicas.findWithDetalhesById(musicaId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Música não encontrada"));
        Long dono = musica.getEnviadaPor() == null ? null : musica.getEnviadaPor().getId();
        if (!Objects.equals(dono, usuarioId)) {
            throw new Excecoes.AcessoNegado("Só quem enviou a música pode excluí-la");
        }

        Album album = musica.getAlbum();
        Artista artista = musica.getArtista();
        if (musica.getArquivo() != null) {
            armazenamento.apagarAposConfirmar(musica.getArquivo(), Tipo.AUDIO);
        }
        musicas.delete(musica);
        musicas.flush();

        if (musicas.countByAlbumId(album.getId()) == 0) {
            armazenamento.apagarAposConfirmar(album.getCapaArquivo(), Tipo.CAPA);
            albuns.delete(album);
            albuns.flush();
        }
        if (musicas.countByArtistaId(artista.getId()) == 0) {
            artistas.delete(artista);
        }
    }

    private static String obrigatorio(String valor, String campo, int maximo) {
        String limpo = valor == null ? "" : valor.trim();
        if (limpo.isEmpty()) {
            throw new Excecoes.RegraNegocio("Preencha o campo " + campo);
        }
        if (limpo.length() > maximo) {
            throw new Excecoes.RegraNegocio("O campo " + campo + " pode ter no máximo " + maximo + " caracteres");
        }
        return limpo;
    }
}
