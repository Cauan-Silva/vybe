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

/** Cada vez que um vídeo é aberto, com quanto foi assistido. Usado no "Seu mês no VYBE". */
@Entity
@Table(name = "visualizacao_video")
public class VisualizacaoVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "video_id", nullable = false, length = 20)
    private String videoId;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(length = 150)
    private String canal;

    @Column(name = "canal_id", length = 40)
    private String canalId;

    @Column(name = "capa_url", length = 500)
    private String capaUrl;

    @Column(name = "assistido_em", nullable = false)
    private LocalDateTime assistidoEm;

    @Column(name = "segundos_assistidos", nullable = false)
    private int segundosAssistidos;

    public VisualizacaoVideo() {
    }

    public VisualizacaoVideo(Usuario usuario, String videoId, String titulo, String canal, String canalId,
                             String capaUrl, LocalDateTime assistidoEm) {
        this.usuario = usuario;
        this.videoId = videoId;
        this.titulo = titulo;
        this.canal = canal;
        this.canalId = canalId;
        this.capaUrl = capaUrl;
        this.assistidoEm = assistidoEm;
    }

    public Long getId() { return id; }
    public String getVideoId() { return videoId; }
    public String getTitulo() { return titulo; }
    public String getCanal() { return canal; }
    public String getCanalId() { return canalId; }
    public String getCapaUrl() { return capaUrl; }
    public LocalDateTime getAssistidoEm() { return assistidoEm; }
    public int getSegundosAssistidos() { return segundosAssistidos; }
    public void setSegundosAssistidos(int segundosAssistidos) { this.segundosAssistidos = segundosAssistidos; }
}
