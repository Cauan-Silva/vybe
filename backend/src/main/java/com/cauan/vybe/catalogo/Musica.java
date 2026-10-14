package com.cauan.vybe.catalogo;

import com.cauan.vybe.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "musica")
public class Musica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artista_id", nullable = false)
    private Artista artista;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(name = "duracao_segundos", nullable = false)
    private int duracaoSegundos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origem origem;

    /** UPLOAD: nome do arquivo na pasta de armazenamento. */
    @Column(length = 100)
    private String arquivo;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    /** JAMENDO (fase 2): endereço do áudio no servidor externo. */
    @Column(name = "url_externa", length = 500)
    private String urlExterna;

    @Column(name = "id_externo", length = 50)
    private String idExterno;

    /** JAMENDO: link da licença Creative Commons da faixa. */
    @Column(name = "licenca_url", length = 300)
    private String licencaUrl;

    /** JAMENDO: página da faixa no site de origem. */
    @Column(name = "pagina_url", length = 300)
    private String paginaUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enviada_por")
    private Usuario enviadaPor;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    /** Endereço que o player usa para tocar a música. */
    public String audioUrl() {
        return origem == Origem.UPLOAD ? "/api/musicas/" + id + "/audio" : urlExterna;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public Artista getArtista() { return artista; }
    public void setArtista(Artista artista) { this.artista = artista; }
    public Album getAlbum() { return album; }
    public void setAlbum(Album album) { this.album = album; }
    public int getDuracaoSegundos() { return duracaoSegundos; }
    public void setDuracaoSegundos(int duracaoSegundos) { this.duracaoSegundos = duracaoSegundos; }
    public Origem getOrigem() { return origem; }
    public void setOrigem(Origem origem) { this.origem = origem; }
    public String getArquivo() { return arquivo; }
    public void setArquivo(String arquivo) { this.arquivo = arquivo; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getTamanhoBytes() { return tamanhoBytes; }
    public void setTamanhoBytes(Long tamanhoBytes) { this.tamanhoBytes = tamanhoBytes; }
    public String getUrlExterna() { return urlExterna; }
    public void setUrlExterna(String urlExterna) { this.urlExterna = urlExterna; }
    public String getIdExterno() { return idExterno; }
    public void setIdExterno(String idExterno) { this.idExterno = idExterno; }
    public String getLicencaUrl() { return licencaUrl; }
    public void setLicencaUrl(String licencaUrl) { this.licencaUrl = licencaUrl; }
    public String getPaginaUrl() { return paginaUrl; }
    public void setPaginaUrl(String paginaUrl) { this.paginaUrl = paginaUrl; }
    public Usuario getEnviadaPor() { return enviadaPor; }
    public void setEnviadaPor(Usuario enviadaPor) { this.enviadaPor = enviadaPor; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
}
