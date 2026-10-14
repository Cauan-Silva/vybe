package com.cauan.vybe.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "artista")
public class Artista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public Artista() {
    }

    public Artista(String nome, LocalDateTime criadoEm) {
        this.nome = nome;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
