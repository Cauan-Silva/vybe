package com.cauan.vybe.spotify;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * App Android: o login do Spotify abre no navegador do celular. Depois de autorizar, o Spotify
 * volta para a API (callback), que manda o navegador para FRONTEND_URL + "/spotify". Na versão
 * hospedada, FRONTEND_URL aponta para /api/spotify/app, e esta página avisa que já dá para
 * voltar ao app (lá, a tela do Spotify confere a conexão de novo sozinha).
 */
@RestController
public class SpotifyRetornoApp {

    @GetMapping(value = "/api/spotify/app/spotify", produces = MediaType.TEXT_HTML_VALUE)
    public String retorno(@RequestParam(value = "resultado", required = false) String resultado,
                          @RequestParam(value = "motivo", required = false) String motivo) {
        return pagina(resultado, motivo);
    }

    static String pagina(String resultado, String motivo) {
        boolean deuCerto = "conectado".equals(resultado);
        String titulo = deuCerto ? "Spotify conectado" : "Não foi possível conectar o Spotify";
        String texto = deuCerto
                ? "Pronto! Pode fechar esta página e voltar para o app VYBE."
                : HtmlUtils.htmlEscape(motivo == null || motivo.isBlank() ? "Tente de novo pelo app." : motivo);
        return """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>VYBE</title>
                  <style>
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 24px;
                           box-sizing: border-box; background: #05040d; color: #f2f0ff;
                           font: 16px/1.5 system-ui, sans-serif; text-align: center; }
                    h1 { font-size: 1.5rem; margin: 0 0 8px; }
                    p { margin: 0; color: #b9b4d6; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>%s</h1>
                    <p>%s</p>
                  </main>
                </body>
                </html>
                """.formatted(titulo, texto);
    }
}
