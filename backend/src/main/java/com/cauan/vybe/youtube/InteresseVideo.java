package com.cauan.vybe.youtube;

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

/** Tema que a pessoa acompanha na aba Vídeos (ex.: "valorant"). Cada um vira uma fileira de vídeos. */
@Entity
@Table(name = "interesse_video")
public class InteresseVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 60)
    private String termo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public InteresseVideo() {
    }

    public InteresseVideo(Usuario usuario, String termo, LocalDateTime criadoEm) {
        this.usuario = usuario;
        this.termo = termo;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public String getTermo() { return termo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
