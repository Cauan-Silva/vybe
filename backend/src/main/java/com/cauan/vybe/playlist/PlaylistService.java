package com.cauan.vybe.playlist;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.playlist.PlaylistDtos.ItemPlaylist;
import com.cauan.vybe.playlist.PlaylistDtos.PlaylistDetalhe;
import com.cauan.vybe.playlist.PlaylistDtos.PlaylistResumo;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Playlists são privadas: cada pessoa só vê e altera as suas. */
@Service
public class PlaylistService {

    private final PlaylistRepository playlists;
    private final PlaylistMusicaRepository itens;
    private final MusicaRepository musicas;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public PlaylistService(PlaylistRepository playlists,
                           PlaylistMusicaRepository itens,
                           MusicaRepository musicas,
                           UsuarioRepository usuarios,
                           Clock clock) {
        this.playlists = playlists;
        this.itens = itens;
        this.musicas = musicas;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlaylistResumo> listar(Long usuarioId) {
        return playlists.findByDonoIdOrderByAtualizadaEmDesc(usuarioId).stream()
                .map(this::resumo)
                .toList();
    }

    @Transactional
    public PlaylistResumo criar(Long usuarioId, String nome) {
        Playlist playlist = playlists.save(
                new Playlist(nome.trim(), usuarios.getReferenceById(usuarioId), LocalDateTime.now(clock)));
        return resumo(playlist);
    }

    @Transactional(readOnly = true)
    public PlaylistDetalhe detalhe(Long usuarioId, Long playlistId) {
        return detalheDe(obter(usuarioId, playlistId));
    }

    @Transactional
    public PlaylistResumo renomear(Long usuarioId, Long playlistId, String nome) {
        Playlist playlist = obter(usuarioId, playlistId);
        playlist.setNome(nome.trim());
        playlist.setAtualizadaEm(LocalDateTime.now(clock));
        return resumo(playlist);
    }

    @Transactional
    public void excluir(Long usuarioId, Long playlistId) {
        playlists.delete(obter(usuarioId, playlistId));
    }

    /** Adiciona no fim. A mesma música não entra duas vezes. */
    @Transactional
    public PlaylistDetalhe adicionar(Long usuarioId, Long playlistId, Long musicaId) {
        Playlist playlist = obter(usuarioId, playlistId);
        if (itens.existsByPlaylistIdAndMusicaId(playlistId, musicaId)) {
            throw new Excecoes.Conflito("Essa música já está em " + playlist.getNome());
        }
        Musica musica = musicas.findById(musicaId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Música não encontrada"));

        LocalDateTime agora = LocalDateTime.now(clock);
        itens.save(new PlaylistMusica(playlist, musica, itens.maiorPosicao(playlistId) + 1, agora));
        playlist.setAtualizadaEm(agora);
        return detalheDe(playlist);
    }

    @Transactional
    public PlaylistDetalhe remover(Long usuarioId, Long playlistId, Long itemId) {
        Playlist playlist = obter(usuarioId, playlistId);
        PlaylistMusica item = itens.findByIdAndPlaylistId(itemId, playlistId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Essa música não está na playlist"));
        itens.delete(item);
        itens.flush();
        playlist.setAtualizadaEm(LocalDateTime.now(clock));
        return detalheDe(playlist);
    }

    private Playlist obter(Long usuarioId, Long playlistId) {
        return playlists.findByIdAndDonoId(playlistId, usuarioId)
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Playlist não encontrada"));
    }

    private PlaylistResumo resumo(Playlist p) {
        String capa = itens.findFirstByPlaylistIdOrderByPosicaoAsc(p.getId())
                .map(i -> i.getMusica().getAlbum().capaUrl())
                .orElse(null);
        return new PlaylistResumo(p.getId(), p.getNome(), itens.countByPlaylistId(p.getId()), capa);
    }

    private PlaylistDetalhe detalheDe(Playlist p) {
        List<ItemPlaylist> lista = itens.findByPlaylistIdOrderByPosicaoAsc(p.getId()).stream()
                .map(i -> new ItemPlaylist(i.getId(), MusicaResponse.de(i.getMusica())))
                .toList();
        return new PlaylistDetalhe(p.getId(), p.getNome(), lista);
    }
}
