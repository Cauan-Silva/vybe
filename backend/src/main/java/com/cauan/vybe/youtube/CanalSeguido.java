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

/** Canal do YouTube que a pessoa segue no VYBE (seção "Inscrições" da aba Vídeos). */
@Entity
@Table(name = "canal_seguido")
public class CanalSeguido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "canal_id", nullable = false, length = 40)
    private String canalId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "seguido_em", nullable = false)
    private LocalDateTime seguidoEm;

    public CanalSeguido() {
    }

    public CanalSeguido(Usuario usuario, String canalId, String nome, String avatarUrl, LocalDateTime seguidoEm) {
        this.usuario = usuario;
        this.canalId = canalId;
        this.nome = nome;
        this.avatarUrl = avatarUrl;
        this.seguidoEm = seguidoEm;
    }

    public Long getId() { return id; }
    public String getCanalId() { return canalId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public LocalDateTime getSeguidoEm() { return seguidoEm; }
}
