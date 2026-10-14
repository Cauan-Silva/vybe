package com.cauan.vybe.jamendo;

import com.cauan.vybe.catalogo.Album;
import com.cauan.vybe.catalogo.AlbumRepository;
import com.cauan.vybe.catalogo.Artista;
import com.cauan.vybe.catalogo.ArtistaRepository;
import com.cauan.vybe.catalogo.CatalogoDtos.MusicaResponse;
import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.catalogo.MusicaRepository;
import com.cauan.vybe.catalogo.Origem;
import com.cauan.vybe.jamendo.JamendoDtos.Faixa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Traz faixas do Jamendo para o catálogo.
 *
 * Cada faixa que aparece num resultado vira (ou atualiza) uma Musica com origem JAMENDO.
 * Assim ela ganha um id nosso e funciona como qualquer outra: curtir, playlist,
 * recentes e fila. O áudio continua vindo do servidor do Jamendo.
 */
@Service
public class JamendoService {

    private static final Duration VALIDADE_POPULARES = Duration.ofHours(1);
    private static final int TAMANHO_MAXIMO_URL = 500;

    private final JamendoCliente cliente;
    private final MusicaRepository musicas;
    private final ArtistaRepository artistas;
    private final AlbumRepository albuns;
    private final Clock clock;

    // Cache simples das populares: evita chamar o Jamendo a cada visita à tela inicial.
    private volatile List<MusicaResponse> popularesEmCache = List.of();
    private volatile Instant popularesAtualizadasEm = Instant.EPOCH;

    public JamendoService(JamendoCliente cliente,
                          MusicaRepository musicas,
                          ArtistaRepository artistas,
                          AlbumRepository albuns,
                          Clock clock) {
        this.cliente = cliente;
        this.musicas = musicas;
        this.artistas = artistas;
        this.albuns = albuns;
        this.clock = clock;
    }

    public boolean disponivel() {
        return cliente.configurado();
    }

    @Transactional
    public List<MusicaResponse> buscar(String termo) {
        String limpo = termo == null ? "" : termo.trim();
        if (!disponivel() || limpo.isEmpty()) {
            return List.of();
        }
        return importar(cliente.buscar(limpo));
    }

    @Transactional
    public List<MusicaResponse> populares() {
        if (!disponivel()) {
            return List.of();
        }
        Instant agora = clock.instant();
        if (popularesEmCache.isEmpty() || agora.isAfter(popularesAtualizadasEm.plus(VALIDADE_POPULARES))) {
            popularesEmCache = importar(cliente.populares());
            popularesAtualizadasEm = agora;
        }
        return popularesEmCache;
    }

    /** Cria ou atualiza as músicas. Faixas sem áudio ou sem duração são ignoradas. */
    List<MusicaResponse> importar(List<Faixa> faixas) {
        LocalDateTime agora = LocalDateTime.now(clock);
        List<MusicaResponse> resultado = new ArrayList<>();

        for (Faixa faixa : faixas) {
            if (faixa.id() == null || vazio(faixa.audio()) || faixa.duration() <= 0 || vazio(faixa.name())
                    || vazio(faixa.artista()) || faixa.audio().length() > TAMANHO_MAXIMO_URL) {
                continue;
            }

            String titulo = cortar(faixa.name().trim(), 200);
            String nomeArtista = cortar(faixa.artista().trim(), 150);
            // Single: o Jamendo manda o álbum vazio; usamos o título, como nos envios.
            String tituloAlbum = vazio(faixa.album()) ? titulo : cortar(faixa.album().trim(), 200);

            Artista artista = artistas.findByNomeIgnoreCase(nomeArtista)
                    .orElseGet(() -> artistas.save(new Artista(nomeArtista, agora)));
            Album album = albuns.findByArtistaIdAndTituloIgnoreCase(artista.getId(), tituloAlbum)
                    .orElseGet(() -> albuns.save(new Album(tituloAlbum, artista, agora)));
            String capa = vazio(faixa.capaAlbum()) ? faixa.image() : faixa.capaAlbum();
            if (album.getCapaArquivo() == null && !vazio(capa) && capa.length() <= TAMANHO_MAXIMO_URL) {
                album.setCapaUrlExterna(capa);
            }

            Musica musica = musicas.findByOrigemAndIdExterno(Origem.JAMENDO, faixa.id()).orElseGet(() -> {
                Musica nova = new Musica();
                nova.setOrigem(Origem.JAMENDO);
                nova.setIdExterno(faixa.id());
                nova.setCriadaEm(agora);
                return nova;
            });
            musica.setTitulo(titulo);
            musica.setArtista(artista);
            musica.setAlbum(album);
            musica.setDuracaoSegundos(faixa.duration());
            musica.setUrlExterna(faixa.audio()); // atualizado sempre, caso o endereço mude
            musica.setLicencaUrl(cortarOuNulo(faixa.licencaUrl(), 300));
            musica.setPaginaUrl(cortarOuNulo(faixa.paginaUrl(), 300));

            resultado.add(MusicaResponse.de(musicas.save(musica)));
        }
        return resultado;
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    /** Links longos demais não cabem na coluna; melhor sem link do que cortado. */
    private static String cortarOuNulo(String url, int maximo) {
        return vazio(url) || url.length() > maximo ? null : url;
    }
}
