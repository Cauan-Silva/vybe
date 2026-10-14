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

@Entity
@Table(name = "curtida")
public class Curtida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "musica_id", nullable = false)
    private Musica musica;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    public Curtida() {
    }

    public Curtida(Usuario usuario, Musica musica, LocalDateTime criadaEm) {
        this.usuario = usuario;
        this.musica = musica;
        this.criadaEm = criadaEm;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Musica getMusica() { return musica; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
}
