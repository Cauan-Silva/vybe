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

/**
 * Um vídeo do YouTube na vida de um usuário: está no "Assistir depois", foi assistido
 * (histórico) e até onde. Uma linha por usuário e vídeo.
 */
@Entity
@Table(name = "video_usuario")
public class VideoUsuario {

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

    @Column(name = "duracao_segundos", nullable = false)
    private int duracaoSegundos;

    @Column(name = "assistir_depois", nullable = false)
    private boolean assistirDepois;

    @Column(name = "salvo_em")
    private LocalDateTime salvoEm;

    /** Nulo = nunca assistido (ou histórico limpo). */
    @Column(name = "assistido_em")
    private LocalDateTime assistidoEm;

    @Column(name = "posicao_segundos", nullable = false)
    private int posicaoSegundos;

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getCanalId() { return canalId; }
    public void setCanalId(String canalId) { this.canalId = canalId; }
    public String getCapaUrl() { return capaUrl; }
    public void setCapaUrl(String capaUrl) { this.capaUrl = capaUrl; }
    public int getDuracaoSegundos() { return duracaoSegundos; }
    public void setDuracaoSegundos(int duracaoSegundos) { this.duracaoSegundos = duracaoSegundos; }
    public boolean isAssistirDepois() { return assistirDepois; }
    public void setAssistirDepois(boolean assistirDepois) { this.assistirDepois = assistirDepois; }
    public LocalDateTime getSalvoEm() { return salvoEm; }
    public void setSalvoEm(LocalDateTime salvoEm) { this.salvoEm = salvoEm; }
    public LocalDateTime getAssistidoEm() { return assistidoEm; }
    public void setAssistidoEm(LocalDateTime assistidoEm) { this.assistidoEm = assistidoEm; }
    public int getPosicaoSegundos() { return posicaoSegundos; }
    public void setPosicaoSegundos(int posicaoSegundos) { this.posicaoSegundos = posicaoSegundos; }
}
