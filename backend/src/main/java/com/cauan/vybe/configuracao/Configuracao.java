package com.cauan.vybe.configuracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um valor de configuração salvo pela tela de Configurações (chave → valor). */
@Entity
@Table(name = "configuracao")
public class Configuracao {

    @Id
    @Column(length = 50)
    private String chave;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    public Configuracao() {
    }

    public Configuracao(String chave, String valor) {
        this.chave = chave;
        this.valor = valor;
    }

    public String getChave() { return chave; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
}
