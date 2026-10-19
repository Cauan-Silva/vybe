package com.cauan.vybe.biblioteca;

import com.cauan.vybe.catalogo.Musica;
import com.cauan.vybe.usuario.Usuario;
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

/** Cada vez que alguém logado começa a ouvir uma música. */
@Entity
@Table(name = "reproducao")
public class Reproducao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "musica_id", nullable = false)
    private Musica musica;

    @Column(name = "tocada_em", nullable = false)
    private LocalDateTime tocadaEm;

    /** Quanto foi de fato ouvido; nulo nas reproduções de antes desta coluna existir. */
    @Column(name = "segundos_ouvidos")
    private Integer segundosOuvidos;

    public Reproducao() {
    }

    public Reproducao(Usuario usuario, Musica musica, LocalDateTime tocadaEm) {
        this.usuario = usuario;
        this.musica = musica;
        this.tocadaEm = tocadaEm;
        this.segundosOuvidos = 0;
    }

    public Long getId() { return id; }
    public Musica getMusica() { return musica; }
    public LocalDateTime getTocadaEm() { return tocadaEm; }
    public Integer getSegundosOuvidos() { return segundosOuvidos; }
    public void setSegundosOuvidos(Integer segundosOuvidos) { this.segundosOuvidos = segundosOuvidos; }
}
