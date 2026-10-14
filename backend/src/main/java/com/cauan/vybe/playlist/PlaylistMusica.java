package com.cauan.vybe.playlist;

import com.cauan.vybe.catalogo.Musica;
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

/** Uma música dentro de uma playlist, com sua posição. */
@Entity
@Table(name = "playlist_musica")
public class PlaylistMusica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "musica_id", nullable = false)
    private Musica musica;

    @Column(nullable = false)
    private int posicao;

    @Column(name = "adicionada_em", nullable = false)
    private LocalDateTime adicionadaEm;

    public PlaylistMusica() {
    }

    public PlaylistMusica(Playlist playlist, Musica musica, int posicao, LocalDateTime adicionadaEm) {
        this.playlist = playlist;
        this.musica = musica;
        this.posicao = posicao;
        this.adicionadaEm = adicionadaEm;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Playlist getPlaylist() { return playlist; }
    public Musica getMusica() { return musica; }
    public int getPosicao() { return posicao; }
    public LocalDateTime getAdicionadaEm() { return adicionadaEm; }
}
