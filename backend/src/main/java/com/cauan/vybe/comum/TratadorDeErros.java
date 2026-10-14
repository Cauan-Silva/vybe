package com.cauan.vybe.comum;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converte exceções em respostas no padrão RFC 7807 (application/problem+json). */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(Excecoes.NaoEncontrado.class)
    public ProblemDetail naoEncontrado(Excecoes.NaoEncontrado e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(Excecoes.RegraNegocio.class)
    public ProblemDetail regra(Excecoes.RegraNegocio e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível concluir", e.getMessage());
    }

    @ExceptionHandler(Excecoes.Conflito.class)
    public ProblemDetail conflito(Excecoes.Conflito e) {
        return problema(HttpStatus.CONFLICT, "Já existe", e.getMessage());
    }

    @ExceptionHandler(Excecoes.AcessoNegado.class)
    public ProblemDetail acessoNegado(Excecoes.AcessoNegado e) {
        return problema(HttpStatus.FORBIDDEN, "Sem permissão", e.getMessage());
    }

    @ExceptionHandler(Excecoes.ServicoExterno.class)
    public ProblemDetail servicoExterno(Excecoes.ServicoExterno e) {
        return problema(HttpStatus.BAD_GATEWAY, "Serviço externo indisponível", e.getMessage());
    }

    @ExceptionHandler(Excecoes.CredenciaisInvalidas.class)
    public ProblemDetail credenciais(Excecoes.CredenciaisInvalidas e) {
        return problema(HttpStatus.UNAUTHORIZED, "Login inválido", e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail arquivoGrande(MaxUploadSizeExceededException e) {
        return problema(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo grande demais",
                "O arquivo de áudio pode ter no máximo 30 MB.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Um ou mais campos estão inválidos");
        problema.setProperty("campos", campos);
        return problema;
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
