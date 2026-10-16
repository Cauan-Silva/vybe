package com.cauan.vybe.spotify;

import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.CatalogoService;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.comum.Excecoes;
import com.cauan.vybe.jamendo.JamendoDtos;
import com.cauan.vybe.jamendo.JamendoService;
import com.cauan.vybe.playlist.PlaylistDtos.PlaylistResumo;
import com.cauan.vybe.playlist.PlaylistService;
import com.cauan.vybe.spotify.SpotifyApi.Faixa;
import com.cauan.vybe.spotify.SpotifyApi.Imagem;
import com.cauan.vybe.spotify.SpotifyApi.Perfil;
import com.cauan.vybe.spotify.SpotifyApi.Token;
import com.cauan.vybe.spotify.SpotifyCliente.TokenRecusado;
import com.cauan.vybe.spotify.SpotifyDtos.ArtistaOuvido;
import com.cauan.vybe.spotify.SpotifyDtos.Importacao;
import com.cauan.vybe.spotify.SpotifyDtos.MaisOuvidos;
import com.cauan.vybe.spotify.SpotifyDtos.MusicaOuvida;
import com.cauan.vybe.spotify.SpotifyDtos.PlaylistSpotify;
import com.cauan.vybe.spotify.SpotifyDtos.Status;
import com.cauan.vybe.spotify.SpotifyDtos.TokenPlayer;
import com.cauan.vybe.spotify.SpotifyDtos.UrlAutorizacao;
import com.cauan.vybe.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Integração com o Spotify: conectar a conta (OAuth), mais ouvidos, importação de playlists
 * e o token do player (Web Playback SDK) que toca as faixas no navegador.
 *
 * Os métodos não são @Transactional de ponta a ponta de propósito: eles fazem chamadas
 * HTTP ao Spotify e ao Jamendo, e não queremos segurar uma conexão do banco esperando a rede.
 * Cada gravação usa a transação do próprio repositório ou serviço chamado.
 */
@Service
public class SpotifyService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyService.class);
    /** Procurando equivalentes livres: cada faixa pode custar uma busca no Jamendo. */
    static final int LIMITE_IMPORTACAO = 100;
    /** Trazendo as faixas do próprio Spotify: não há busca extra, então cabe mais. */
    static final int LIMITE_IMPORTACAO_SPOTIFY = 500;
    private static final Set<String> PERIODOS = Set.of("short_term", "medium_term", "long_term");

    private final SpotifyCliente cliente;
    private final SpotifyContaRepository contas;
    private final SpotifyEstados estados;
    private final UsuarioRepository usuarios;
    private final CatalogoService catalogo;
    private final JamendoService jamendo;
    private final PlaylistService playlists;
    private final SpotifyCatalogo catalogoSpotify;
    private final Clock clock;
    private final String frontendUrl;

    public SpotifyService(SpotifyCliente cliente,
                          SpotifyContaRepository contas,
                          SpotifyEstados estados,
                          UsuarioRepository usuarios,
                          CatalogoService catalogo,
                          JamendoService jamendo,
                          PlaylistService playlists,
                          SpotifyCatalogo catalogoSpotify,
                          Clock clock,
                          @Value("${vybe.frontend-url}") String frontendUrl) {
        this.cliente = cliente;
        this.contas = contas;
        this.estados = estados;
        this.usuarios = usuarios;
        this.catalogo = catalogo;
        this.jamendo = jamendo;
        this.playlists = playlists;
        this.catalogoSpotify = catalogoSpotify;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    // ---------- Conexão ----------

    public Status status(Long usuarioId) {
        if (!cliente.configurado()) {
            return new Status(false, false, null, false);
        }
        return contas.findByUsuarioId(usuarioId)
                .map(c -> new Status(true, true, c.getNome(), c.podeTocar()))
                .orElse(new Status(true, false, null, false));
    }

    public UrlAutorizacao autorizar(Long usuarioId) {
        if (!cliente.configurado()) {
            throw new Excecoes.RegraNegocio("A integração com o Spotify não está configurada no servidor.");
        }
        return new UrlAutorizacao(cliente.urlAutorizacao(estados.gerar(usuarioId)));
    }

    /**
     * Callback do OAuth. Troca o código pelos tokens, guarda a conta e devolve
     * o endereço da página do VYBE para onde o navegador deve voltar.
     */
    public URI concluir(String codigo, String estado, String erro) {
        Optional<Long> usuarioId = estados.consumir(estado);
        if (erro != null) {
            return voltar("erro", "access_denied".equals(erro)
                    ? "Você cancelou a conexão com o Spotify."
                    : "O Spotify recusou a conexão.");
        }
        if (usuarioId.isEmpty() || codigo == null || codigo.isBlank()) {
            return voltar("erro", "O pedido de conexão expirou. Tente de novo.");
        }
        try {
            Token token = cliente.trocarCodigo(codigo);
            Perfil perfil = cliente.perfil(token.accessToken());
            salvarConta(usuarioId.get(), token, perfil);
            return voltar("conectado", null);
        } catch (RuntimeException e) {
            log.warn("Falha ao concluir a conexão com o Spotify: {}", e.getMessage());
            return voltar("erro", "Não foi possível concluir a conexão com o Spotify.");
        }
    }

    @Transactional
    public void desconectar(Long usuarioId) {
        contas.deleteByUsuarioId(usuarioId);
    }

    // ---------- Leitura ----------

    public List<PlaylistSpotify> playlists(Long usuarioId) {
        SpotifyConta conta = obterConta(usuarioId);
        return comToken(usuarioId, cliente::playlists).stream()
                .map(p -> new PlaylistSpotify(
                        p.id(),
                        p.name(),
                        primeiraImagem(p.images()),
                        p.quantidade(),
                        p.owner() == null ? null : p.owner().nome(),
                        p.collaborative() || (p.owner() != null && conta.getSpotifyId().equals(p.owner().id()))))
                .toList();
    }

    /**
     * Top 10 artistas e músicas. Se a conta pode tocar, as músicas também entram no catálogo
     * (origem SPOTIFY) e vêm com o id do VYBE, prontas para o player.
     */
    public MaisOuvidos maisOuvidos(Long usuarioId, String periodo) {
        String intervalo = PERIODOS.contains(periodo) ? periodo : "medium_term";
        boolean podeTocar = obterConta(usuarioId).podeTocar();
        List<ArtistaOuvido> artistas = comToken(usuarioId, t -> cliente.artistasMaisOuvidos(t, intervalo)).stream()
                .map(a -> new ArtistaOuvido(a.name(), primeiraImagem(a.images()), a.links() == null ? null : a.links().spotify()))
                .toList();
        List<Faixa> faixas = comToken(usuarioId, t -> cliente.musicasMaisOuvidas(t, intervalo));
        List<MusicaResponse> noCatalogo = podeTocar ? catalogoSpotify.salvar(faixas) : null;

        List<MusicaOuvida> musicas = new ArrayList<>();
        for (int i = 0; i < faixas.size(); i++) {
            Faixa f = faixas.get(i);
            musicas.add(new MusicaOuvida(
                    f.name(),
                    String.join(", ", nomesDosArtistas(f)),
                    f.album() == null ? null : primeiraImagem(f.album().images()),
                    f.links() == null ? null : f.links().spotify(),
                    noCatalogo == null ? null : noCatalogo.get(i)));
        }
        return new MaisOuvidos(artistas, musicas);
    }

    /**
     * Busca qualquer música no Spotify. Só para quem pode tocar (Premium com a permissão do
     * player): os resultados entram no catálogo e voltam prontos para o player.
     * Para os outros, devolve vazio, e a tela simplesmente não mostra a seção.
     */
    public List<MusicaResponse> buscar(Long usuarioId, String termo) {
        String limpo = termo == null ? "" : termo.trim();
        if (limpo.isEmpty() || !cliente.configurado()) {
            return List.of();
        }
        Optional<SpotifyConta> conta = contas.findByUsuarioId(usuarioId);
        if (conta.isEmpty() || !conta.get().podeTocar()) {
            return List.of();
        }
        List<Faixa> faixas = comToken(usuarioId, t -> cliente.buscarFaixas(t, cortar(limpo, 100)));
        return catalogoSpotify.salvar(faixas).stream().filter(java.util.Objects::nonNull).toList();
    }

    // ---------- Player ----------

    /**
     * Access token para o player do Spotify no navegador. O Web Playback SDK exige um token
     * da própria pessoa; o Client Secret continua só no servidor.
     */
    public TokenPlayer tokenDoPlayer(Long usuarioId) {
        SpotifyConta conta = obterConta(usuarioId);
        if (!conta.podeTocar()) {
            throw new Excecoes.RegraNegocio(
                    "Para tocar músicas do Spotify, desconecte e conecte sua conta de novo na página Spotify.");
        }
        String token;
        try {
            token = tokenValido(conta); // renova se preciso e atualiza conta.expiraEm
        } catch (TokenRecusado e) {
            contas.delete(conta);
            throw new Excecoes.RegraNegocio("A conexão com o Spotify expirou. Conecte de novo.");
        }
        long segundos = Duration.between(LocalDateTime.now(clock), conta.getExpiraEm()).getSeconds();
        return new TokenPlayer(token, (int) Math.max(0, segundos));
    }

    // ---------- Importação ----------

    /**
     * Cria uma playlist no VYBE a partir de uma playlist do Spotify.
     *
     * Se a conta pode tocar (Premium com a permissão do player), as próprias faixas do Spotify
     * entram no catálogo e na playlist. Senão, procura equivalentes livres: primeiro nas músicas
     * já existentes, depois no Jamendo. Se nada entrar, nenhuma playlist é criada.
     */
    public Importacao importar(Long usuarioId, String playlistSpotifyId) {
        PlaylistSpotify origem = playlists(usuarioId).stream()
                .filter(p -> p.id().equals(playlistSpotifyId))
                .findFirst()
                .orElseThrow(() -> new Excecoes.NaoEncontrado("Playlist do Spotify não encontrada"));
        if (!origem.importavel()) {
            throw new Excecoes.RegraNegocio(
                    "O Spotify só permite ler playlists criadas por você ou colaborativas.");
        }

        boolean viaSpotify = obterConta(usuarioId).podeTocar();
        int limite = viaSpotify ? LIMITE_IMPORTACAO_SPOTIFY : LIMITE_IMPORTACAO;
        List<Faixa> faixas = comToken(usuarioId, t -> cliente.faixasDaPlaylist(t, playlistSpotifyId, limite));

        // Com o player, as faixas do Spotify entram direto (uma transação para todas).
        List<MusicaResponse> doSpotify = viaSpotify ? catalogoSpotify.salvar(faixas) : null;

        String nome = cortar(origem.nome() + " (Spotify)", 100);
        PlaylistResumo criada = playlists.criar(usuarioId, nome);
        Set<Long> adicionadas = new HashSet<>();
        List<String> naoEncontradas = new ArrayList<>();

        for (int i = 0; i < faixas.size(); i++) {
            Faixa faixa = faixas.get(i);
            List<String> artistas = nomesDosArtistas(faixa);
            Long musicaId = viaSpotify
                    ? (doSpotify.get(i) == null ? null : doSpotify.get(i).id())
                    : procurarEquivalente(faixa.name(), artistas);
            if (musicaId == null) {
                naoEncontradas.add((artistas.isEmpty() ? "?" : artistas.get(0)) + " – " + faixa.name());
            } else if (adicionadas.add(musicaId)) {
                playlists.adicionar(usuarioId, criada.id(), musicaId);
            }
        }

        if (adicionadas.isEmpty()) {
            playlists.excluir(usuarioId, criada.id());
            return new Importacao(null, nome, faixas.size(), 0, naoEncontradas, viaSpotify);
        }
        return new Importacao(criada.id(), nome, faixas.size(), adicionadas.size(), naoEncontradas, viaSpotify);
    }

    /** Id da música equivalente no VYBE, ou null. */
    Long procurarEquivalente(String titulo, List<String> artistas) {
        if (titulo == null || artistas.isEmpty()) {
            return null;
        }
        String termo = titulo.replaceAll("\\(.*?\\)|\\[.*?]", " ").replaceAll("\\s+-\\s+.*$", "").trim();
        if (termo.isEmpty()) {
            termo = titulo;
        }

        // 1. No catálogo (músicas enviadas e do Jamendo que já passaram por aqui).
        //    Faixas do Spotify não servem: quem cai neste caminho não pode tocá-las.
        for (MusicaResponse m : catalogo.buscar(termo).musicas()) {
            if (m.origem() != Origem.SPOTIFY && Correspondencia.mesmaMusica(m.titulo(), m.artistaNome(), titulo, artistas)) {
                return m.id();
            }
        }

        // 2. No Jamendo, por artista + título
        if (jamendo.disponivel()) {
            try {
                for (JamendoDtos.Faixa f : jamendo.procurar(artistas.get(0) + " " + termo)) {
                    if (Correspondencia.mesmaMusica(f.name(), f.artista(), titulo, artistas)) {
                        MusicaResponse importada = jamendo.importarUma(f);
                        if (importada != null) {
                            return importada.id();
                        }
                    }
                }
            } catch (Excecoes.ServicoExterno e) {
                log.debug("Jamendo indisponível durante a importação: {}", e.getMessage());
            }
        }
        return null;
    }

    // ---------- Tokens ----------

    /**
     * Executa uma chamada com um access token válido (renovando se estiver para vencer).
     * Se o Spotify recusar o token, a conexão é desfeita e a pessoa precisa conectar de novo.
     */
    private <T> T comToken(Long usuarioId, Function<String, T> chamada) {
        SpotifyConta conta = obterConta(usuarioId);
        try {
            return chamada.apply(tokenValido(conta));
        } catch (TokenRecusado e) {
            contas.delete(conta);
            throw new Excecoes.RegraNegocio("A conexão com o Spotify expirou. Conecte de novo.");
        }
    }

    private String tokenValido(SpotifyConta conta) {
        LocalDateTime agora = LocalDateTime.now(clock);
        if (conta.getExpiraEm().isAfter(agora.plusSeconds(60))) {
            return conta.getAccessToken();
        }
        Token novo = cliente.renovar(conta.getRefreshToken());
        conta.setAccessToken(novo.accessToken());
        if (novo.refreshToken() != null && !novo.refreshToken().isBlank()) {
            conta.setRefreshToken(novo.refreshToken()); // o Spotify às vezes manda um novo
        }
        conta.setExpiraEm(agora.plusSeconds(novo.expiraEmSegundos()));
        if (novo.escopos() != null && !novo.escopos().isBlank()) {
            conta.setEscopos(novo.escopos());
        }
        contas.save(conta);
        return novo.accessToken();
    }

    private void salvarConta(Long usuarioId, Token token, Perfil perfil) {
        LocalDateTime agora = LocalDateTime.now(clock);
        SpotifyConta conta = contas.findByUsuarioId(usuarioId).orElseGet(SpotifyConta::new);
        if (conta.getUsuario() == null) {
            conta.setUsuario(usuarios.getReferenceById(usuarioId));
        }
        conta.setSpotifyId(perfil.id());
        conta.setNome(perfil.nome() == null || perfil.nome().isBlank() ? perfil.id() : cortar(perfil.nome(), 150));
        conta.setAccessToken(token.accessToken());
        conta.setRefreshToken(token.refreshToken());
        conta.setExpiraEm(agora.plusSeconds(token.expiraEmSegundos()));
        conta.setConectadoEm(agora);
        conta.setEscopos(token.escopos());
        contas.save(conta);
    }

    private SpotifyConta obterConta(Long usuarioId) {
        return contas.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new Excecoes.RegraNegocio("Conecte sua conta do Spotify primeiro."));
    }

    // ---------- Utilidades ----------

    private URI voltar(String resultado, String motivo) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/spotify")
                .queryParam("resultado", resultado);
        if (motivo != null) {
            url.queryParam("motivo", motivo);
        }
        return url.encode().build().toUri();
    }

    private static List<String> nomesDosArtistas(Faixa faixa) {
        if (faixa.artists() == null) return List.of();
        return faixa.artists().stream()
                .filter(a -> a != null && a.name() != null)
                .map(SpotifyApi.ArtistaSimples::name)
                .toList();
    }

    private static String primeiraImagem(List<Imagem> imagens) {
        return imagens == null || imagens.isEmpty() ? null : imagens.get(0).url();
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
