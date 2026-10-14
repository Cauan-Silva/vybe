package com.cauan.vybe.catalogo;

/** De onde vem o áudio da música. */
public enum Origem {
    /** Arquivo enviado por um usuário e servido pela própria API. */
    UPLOAD,
    /** Catálogo Creative Commons do Jamendo (fase 2), tocado a partir do servidor deles. */
    JAMENDO
}
