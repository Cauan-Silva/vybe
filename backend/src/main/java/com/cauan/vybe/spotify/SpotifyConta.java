package com.cauan.vybe.spotify;

import com.cauan.vybe.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "spotify_conta")
public class SpotifyConta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "spotify_id", nullable = false, length = 100)
    private String spotifyId;

    @Column(length = 150)
    private String nome;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    /** Quando o access token para de valer (dura cerca de 1 hora). */
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "conectado_em", nullable = false)
    private LocalDateTime conectadoEm;

    /** Permissões concedidas pela pessoa, separadas por espaço. Nulo em conexões antigas. */
    @Column(columnDefinition = "TEXT")
    private String escopos;

    /** O player só funciona se a pessoa liberou o "streaming" (conexões da fase 3 não têm). */
    public boolean podeTocar() {
        return escopos != null && java.util.Arrays.asList(escopos.split("\\s+")).contains("streaming");
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getSpotifyId() { return spotifyId; }
    public void setSpotifyId(String spotifyId) { this.spotifyId = spotifyId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public LocalDateTime getConectadoEm() { return conectadoEm; }
    public void setConectadoEm(LocalDateTime conectadoEm) { this.conectadoEm = conectadoEm; }
    public String getEscopos() { return escopos; }
    public void setEscopos(String escopos) { this.escopos = escopos; }
}
