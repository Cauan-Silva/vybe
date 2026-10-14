package com.cauan.vybe.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Toda música pertence a um álbum. Músicas avulsas viram um "single" com o próprio título. */
@Entity
@Table(name = "album")
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artista_id", nullable = false)
    private Artista artista;

    /** Nome do arquivo da capa na pasta de armazenamento (opcional). */
    @Column(name = "capa_arquivo", length = 100)
    private String capaArquivo;

    /** Capa hospedada fora (Jamendo). Usada quando não há capa enviada. */
    @Column(name = "capa_url_externa", length = 500)
    private String capaUrlExterna;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public Album() {
    }

    public Album(String titulo, Artista artista, LocalDateTime criadoEm) {
        this.titulo = titulo;
        this.artista = artista;
        this.criadoEm = criadoEm;
    }

    /**
     * Endereço da capa, ou null se o álbum não tem capa. O "v" muda quando a capa
     * é trocada, para o navegador não mostrar a antiga do cache.
     */
    public String capaUrl() {
        if (capaArquivo == null) {
            return capaUrlExterna;
        }
        return "/api/albuns/" + id + "/capa?v=" + capaArquivo.substring(0, Math.min(8, capaArquivo.length()));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public Artista getArtista() { return artista; }
    public String getCapaArquivo() { return capaArquivo; }
    public void setCapaArquivo(String capaArquivo) { this.capaArquivo = capaArquivo; }
    public String getCapaUrlExterna() { return capaUrlExterna; }
    public void setCapaUrlExterna(String capaUrlExterna) { this.capaUrlExterna = capaUrlExterna; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
