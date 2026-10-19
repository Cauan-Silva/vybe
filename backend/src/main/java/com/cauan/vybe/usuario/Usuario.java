package com.cauan.vybe.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** Só o hash BCrypt, nunca a senha. */
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "foto_arquivo", length = 100)
    private String fotoArquivo;

    @Column(name = "banner_arquivo", length = 100)
    private String bannerArquivo;

    @Column(length = 160)
    private String bio;

    public Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash, LocalDateTime criadoEm) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setNome(String nome) { this.nome = nome; }
    public String getFotoArquivo() { return fotoArquivo; }
    public void setFotoArquivo(String fotoArquivo) { this.fotoArquivo = fotoArquivo; }
    public String getBannerArquivo() { return bannerArquivo; }
    public void setBannerArquivo(String bannerArquivo) { this.bannerArquivo = bannerArquivo; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    /** Endereço da foto (o "v" muda a cada troca, para o navegador não mostrar a antiga). */
    public String fotoUrl() {
        return fotoArquivo == null ? null : "/api/usuarios/" + id + "/foto?v=" + fotoArquivo.substring(0, Math.min(8, fotoArquivo.length()));
    }

    public String bannerUrl() {
        return bannerArquivo == null ? null : "/api/usuarios/" + id + "/banner?v=" + bannerArquivo.substring(0, Math.min(8, bannerArquivo.length()));
    }
}
