package com.cauan.vybe.spotify;

import com.cauan.vybe.catalogo.CatalogoDtos.BuscaResponse;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.CatalogoService;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.jamendo.JamendoDtos;
import com.cauan.vybe.jamendo.JamendoService;
import com.cauan.vybe.playlist.PlaylistDtos.PlaylistResumo;
import com.cauan.vybe.playlist.PlaylistService;
import com.cauan.vybe.spotify.SpotifyApi.ArtistaSimples;
import com.cauan.vybe.spotify.SpotifyApi.Dono;
import com.cauan.vybe.spotify.SpotifyApi.Faixa;
import com.cauan.vybe.spotify.SpotifyApi.Playlist;
import com.cauan.vybe.spotify.SpotifyApi.Token;
import com.cauan.vybe.spotify.SpotifyDtos.Importacao;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotifyServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 14, 12, 0);

    @Mock
    private SpotifyCliente cliente;
    @Mock
    private SpotifyContaRepository contas;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private CatalogoService catalogo;
    @Mock
    private JamendoService jamendo;
    @Mock
    private PlaylistService playlists;
    @Mock
    private SpotifyCatalogo catalogoSpotify;

    private SpotifyService service;
    private SpotifyEstados estados;
    private SpotifyConta conta;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        estados = new SpotifyEstados(relogio);
        service = new SpotifyService(cliente, contas, estados, usuarios, catalogo, jamendo, playlists, catalogoSpotify, relogio,
                "http://127.0.0.1:5173");

        conta = new SpotifyConta();
        conta.setSpotifyId("eu-no-spotify");
        conta.setNome("Cauan");
        conta.setAccessToken("token-valido");
        conta.setRefreshToken("refresh");
        conta.setExpiraEm(AGORA.plusMinutes(30));
        lenient().when(contas.findByUsuarioId(1L)).thenReturn(Optional.of(conta));
    }

    private static Faixa faixa(String titulo, String artista) {
        return new Faixa("id-" + titulo, titulo, "track", 200_000, List.of(new ArtistaSimples(artista)), null, null);
    }

    private static MusicaResponse musica(long id, String titulo, String artista) {
        return new MusicaResponse(id, titulo, 1L, artista, 1L, titulo, null, 200, Origem.UPLOAD,
                "/api/musicas/" + id + "/audio", null, null, null);
    }

    private static MusicaResponse doSpotify(long id, String titulo, String artista) {
        return new MusicaResponse(id, titulo, 1L, artista, 1L, titulo, null, 200, Origem.SPOTIFY,
                "spotify:track:id-" + titulo, null, null, null);
    }

    private void contaComPlayer() {
        conta.setEscopos("playlist-read-private user-top-read streaming user-read-email user-read-private");
    }

    private void playlistPropria() {
        when(cliente.playlists("token-valido")).thenReturn(List.of(
                new Playlist("pl1", "Para estudar", List.of(), new Dono("eu-no-spotify", "Cauan"), false, null, null)));
        when(playlists.criar(eq(1L), anyString())).thenReturn(new PlaylistResumo(50L, "Para estudar (Spotify)", 0, null));
    }

    // ---------- Conexão ----------

    @Test
    void callbackComEstadoDesconhecidoVoltaComErro() {
        URI destino = service.concluir("codigo", "estado-falso", null);

        assertThat(destino.toString()).startsWith("http://127.0.0.1:5173/spotify?resultado=erro");
        verify(cliente, never()).trocarCodigo(anyString(), anyString());
    }

    @Test
    void callbackCanceladoPelaPessoaVoltaComErro() {
        String estado = estados.gerar(1L);

        URI destino = service.concluir(null, estado, "access_denied");

        assertThat(destino.getQuery()).contains("resultado=erro").contains("cancelou");
    }

    @Test
    void callbackValidoGuardaAContaEVoltaConectado() {
        String estado = estados.gerar(1L);
        when(contas.findByUsuarioId(1L)).thenReturn(Optional.empty());
        when(cliente.trocarCodigo(eq("codigo"), anyString())).thenReturn(new Token("novo-access", "novo-refresh", 3600));
        when(cliente.perfil("novo-access")).thenReturn(new SpotifyApi.Perfil("spotify-id", "Cauan"));

        URI destino = service.concluir("codigo", estado, null);

        assertThat(destino.toString()).isEqualTo("http://127.0.0.1:5173/spotify?resultado=conectado");
        verify(contas).save(any(SpotifyConta.class));
        // o mesmo estado não pode ser usado de novo
        assertThat(estados.consumir(estado)).isEmpty();
    }

    @Test
    void renovaOTokenQuandoEstaPertoDeVencer() {
        conta.setExpiraEm(AGORA.plusSeconds(30));
        when(cliente.renovar("refresh")).thenReturn(new Token("access-renovado", null, 3600));
        when(cliente.playlists("access-renovado")).thenReturn(List.of());

        service.playlists(1L);

        assertThat(conta.getAccessToken()).isEqualTo("access-renovado");
        assertThat(conta.getRefreshToken()).isEqualTo("refresh"); // sem refresh novo, mantém o antigo
        assertThat(conta.getExpiraEm()).isEqualTo(AGORA.plusSeconds(3600));
        verify(contas).save(conta);
    }

    @Test
    void tokenRecusadoDesfazAConexao() {
        when(cliente.playlists("token-valido")).thenThrow(new SpotifyCliente.TokenRecusado());

        assertThatThrownBy(() -> service.playlists(1L))
                .isInstanceOf(Excecoes.RegraNegocio.class)
                .hasMessageContaining("Conecte de novo");
        verify(contas).delete(conta);
    }

    // ---------- Importação ----------

    @Test
    void importaUsandoOCatalogoEDepoisOJamendo() {
        playlistPropria();
        when(cliente.faixasDaPlaylist("token-valido", "pl1", SpotifyService.LIMITE_IMPORTACAO)).thenReturn(List.of(
                faixa("Noite Clara", "Banda Local"),
                faixa("Mar Aberto (Remastered)", "Artista Livre"),
                faixa("Hit Comercial", "Estrela Pop")));

        // "Noite Clara" já existe no catálogo
        when(catalogo.buscar(anyString())).thenReturn(new BuscaResponse(List.of(), List.of(), List.of()));
        when(catalogo.buscar("Noite Clara")).thenReturn(
                new BuscaResponse(List.of(musica(7L, "Noite Clara", "Banda Local")), List.of(), List.of()));

        // "Mar Aberto" só existe no Jamendo
        when(jamendo.disponivel()).thenReturn(true);
        when(jamendo.procurar(anyString())).thenReturn(List.of());
        JamendoDtos.Faixa doJamendo = new JamendoDtos.Faixa("j1", "Mar Aberto", 180, "Artista Livre", "", null,
                null, "https://audio/j1.mp3", null, null);
        when(jamendo.procurar("Artista Livre Mar Aberto")).thenReturn(List.of(doJamendo));
        when(jamendo.importarUma(doJamendo)).thenReturn(musica(8L, "Mar Aberto", "Artista Livre"));

        Importacao resultado = service.importar(1L, "pl1");

        assertThat(resultado.playlistId()).isEqualTo(50L);
        assertThat(resultado.analisadas()).isEqualTo(3);
        assertThat(resultado.encontradas()).isEqualTo(2);
        assertThat(resultado.naoEncontradas()).containsExactly("Estrela Pop – Hit Comercial");
        verify(playlists).adicionar(1L, 50L, 7L);
        verify(playlists).adicionar(1L, 50L, 8L);
    }

    @Test
    void semNenhumaEquivalenteNaoDeixaPlaylistVazia() {
        playlistPropria();
        when(cliente.faixasDaPlaylist("token-valido", "pl1", SpotifyService.LIMITE_IMPORTACAO))
                .thenReturn(List.of(faixa("Hit Comercial", "Estrela Pop")));
        when(catalogo.buscar(anyString())).thenReturn(new BuscaResponse(List.of(), List.of(), List.of()));
        when(jamendo.disponivel()).thenReturn(false);

        Importacao resultado = service.importar(1L, "pl1");

        assertThat(resultado.playlistId()).isNull();
        assertThat(resultado.encontradas()).isZero();
        verify(playlists).excluir(1L, 50L);
    }

    @Test
    void recusaPlaylistDeOutraPessoa() {
        when(cliente.playlists("token-valido")).thenReturn(List.of(
                new Playlist("pl2", "Top Brasil", List.of(), new Dono("spotify", "Spotify"), false, null, null)));

        assertThatThrownBy(() -> service.importar(1L, "pl2"))
                .isInstanceOf(Excecoes.RegraNegocio.class)
                .hasMessageContaining("criadas por você");
        verify(playlists, never()).criar(any(), any());
    }

    // ---------- Player (fase 4) ----------

    @Test
    void statusInformaSeAContaPodeTocar() {
        when(cliente.configurado()).thenReturn(true);

        assertThat(service.status(1L).podeTocar()).isFalse(); // conexão antiga, sem "streaming"

        contaComPlayer();
        assertThat(service.status(1L).podeTocar()).isTrue();
    }

    @Test
    void callbackGuardaAsPermissoesConcedidas() {
        String estado = estados.gerar(1L);
        when(contas.findByUsuarioId(1L)).thenReturn(Optional.empty());
        when(cliente.trocarCodigo(eq("codigo"), anyString()))
                .thenReturn(new Token("novo-access", "novo-refresh", 3600, "streaming user-top-read"));
        when(cliente.perfil("novo-access")).thenReturn(new SpotifyApi.Perfil("spotify-id", "Cauan"));

        service.concluir("codigo", estado, null);

        verify(contas).save(org.mockito.ArgumentMatchers.argThat(SpotifyConta::podeTocar));
    }

    @Test
    void tokenDoPlayerExigeAPermissaoDeStreaming() {
        assertThatThrownBy(() -> service.tokenDoPlayer(1L))
                .isInstanceOf(Excecoes.RegraNegocio.class)
                .hasMessageContaining("conecte sua conta de novo");
    }

    @Test
    void tokenDoPlayerDevolveOTokenEOTempoRestante() {
        contaComPlayer();

        SpotifyDtos.TokenPlayer token = service.tokenDoPlayer(1L);

        assertThat(token.accessToken()).isEqualTo("token-valido");
        assertThat(token.expiraEmSegundos()).isEqualTo(30 * 60);
    }

    @Test
    void comPlayerImportaAsPropriasFaixasDoSpotify() {
        contaComPlayer();
        playlistPropria();
        List<Faixa> faixas = List.of(
                faixa("Joseph", "Falling In Reverse"),
                faixa("Arquivo local", "Alguém"),
                faixa("Given Up", "Linkin Park"));
        when(cliente.faixasDaPlaylist("token-valido", "pl1", SpotifyService.LIMITE_IMPORTACAO_SPOTIFY))
                .thenReturn(faixas);
        when(catalogoSpotify.salvar(faixas)).thenReturn(java.util.Arrays.asList(
                doSpotify(20L, "Joseph", "Falling In Reverse"),
                null, // faixa que não serve (ex.: arquivo local)
                doSpotify(21L, "Given Up", "Linkin Park")));

        Importacao resultado = service.importar(1L, "pl1");

        assertThat(resultado.viaSpotify()).isTrue();
        assertThat(resultado.encontradas()).isEqualTo(2);
        assertThat(resultado.naoEncontradas()).containsExactly("Alguém – Arquivo local");
        verify(playlists).adicionar(1L, 50L, 20L);
        verify(playlists).adicionar(1L, 50L, 21L);
        verify(jamendo, never()).procurar(anyString());
    }

    @Test
    void maisOuvidosComPlayerTrazAsMusicasProntasParaTocar() {
        contaComPlayer();
        List<Faixa> top = List.of(faixa("Joseph", "Falling In Reverse"));
        when(cliente.artistasMaisOuvidos("token-valido", "short_term")).thenReturn(List.of());
        when(cliente.musicasMaisOuvidas("token-valido", "short_term")).thenReturn(top);
        when(catalogoSpotify.salvar(top)).thenReturn(List.of(doSpotify(20L, "Joseph", "Falling In Reverse")));

        SpotifyDtos.MaisOuvidos resultado = service.maisOuvidos(1L, "short_term");

        assertThat(resultado.musicas()).hasSize(1);
        assertThat(resultado.musicas().get(0).musica().id()).isEqualTo(20L);
    }

    @Test
    void maisOuvidosSemPlayerNaoGravaNada() {
        when(cliente.artistasMaisOuvidos("token-valido", "medium_term")).thenReturn(List.of());
        when(cliente.musicasMaisOuvidas("token-valido", "medium_term"))
                .thenReturn(List.of(faixa("Joseph", "Falling In Reverse")));

        SpotifyDtos.MaisOuvidos resultado = service.maisOuvidos(1L, "periodo-invalido");

        assertThat(resultado.musicas().get(0).musica()).isNull();
        verify(catalogoSpotify, never()).salvar(any());
    }

    // ---------- Busca (fase 5) ----------

    @Test
    void buscaNoSpotifyDevolveMusicasProntasParaTocar() {
        contaComPlayer();
        when(cliente.configurado()).thenReturn(true);
        List<Faixa> achadas = List.of(faixa("Numb", "Linkin Park"), faixa("Sem id", "X"));
        when(cliente.buscarFaixas("token-valido", "numb")).thenReturn(achadas);
        when(catalogoSpotify.salvar(achadas)).thenReturn(java.util.Arrays.asList(doSpotify(40L, "Numb", "Linkin Park"), null));

        List<MusicaResponse> resultado = service.buscar(1L, "  numb ");

        assertThat(resultado).extracting(MusicaResponse::id).containsExactly(40L);
    }

    @Test
    void buscaSemPermissaoDoPlayerNaoChamaOSpotify() {
        when(cliente.configurado()).thenReturn(true);

        assertThat(service.buscar(1L, "numb")).isEmpty();
        verify(cliente, never()).buscarFaixas(anyString(), anyString());
    }
}
