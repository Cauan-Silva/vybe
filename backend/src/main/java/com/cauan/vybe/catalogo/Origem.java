package com.cauan.vybe.catalogo;

/** De onde vem o áudio da música. */
public enum Origem {
    /** Arquivo enviado por um usuário e servido pela própria API. */
    UPLOAD,
    /** Catálogo Creative Commons do Jamendo (fase 2), tocado a partir do servidor deles. */
    JAMENDO,
    /**
     * Faixa do Spotify (fase 4). O VYBE guarda só os dados (título, artista, capa e o
     * endereço spotify:track:...); o áudio é tocado pelo player oficial do Spotify no navegador,
     * e só para quem tem conta Premium conectada.
     */
    SPOTIFY,
    /**
     * Vídeo do YouTube (fase 5). Guardamos os dados e o endereço youtube:ID; quem toca é o
     * player oficial do YouTube, embutido e visível na tela (exigência das regras do YouTube).
     */
    YOUTUBE
}
