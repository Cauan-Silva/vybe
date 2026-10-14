package com.cauan.vybe.comum;

/** Exceções de negócio, cada uma mapeada para um status HTTP em TratadorDeErros. */
public final class Excecoes {

    private Excecoes() {
    }

    /** 404 */
    public static class NaoEncontrado extends RuntimeException {
        public NaoEncontrado(String mensagem) {
            super(mensagem);
        }
    }

    /** 422: pedido bem formado, mas que quebra uma regra (formato de arquivo, nome vazio...). */
    public static class RegraNegocio extends RuntimeException {
        public RegraNegocio(String mensagem) {
            super(mensagem);
        }
    }

    /** 409: já existe (e-mail cadastrado, música repetida na playlist). */
    public static class Conflito extends RuntimeException {
        public Conflito(String mensagem) {
            super(mensagem);
        }
    }

    /** 403: logado, mas sem permissão (ex.: excluir música enviada por outra pessoa). */
    public static class AcessoNegado extends RuntimeException {
        public AcessoNegado(String mensagem) {
            super(mensagem);
        }
    }

    /** 401: e-mail ou senha incorretos. Não diz qual dos dois, de propósito. */
    public static class CredenciaisInvalidas extends RuntimeException {
        public CredenciaisInvalidas() {
            super("E-mail ou senha incorretos");
        }
    }
}
