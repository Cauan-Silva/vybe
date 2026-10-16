package com.cauan.vybe.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Desligamento pedido pelo programa (Electron) ao fechar. Desligar com calma importa:
 * fecha o banco embutido direito, em vez de deixar o PostgreSQL rodando sozinho.
 *
 * O programa gera um token aleatório a cada abertura e passa para a API; só quem tem o
 * token consegue desligar.
 */
@RestController
@Profile("desktop")
@RequestMapping("/api/desktop")
public class DesktopController {

    private static final Logger log = LoggerFactory.getLogger(DesktopController.class);

    private final ConfigurableApplicationContext contexto;
    private final byte[] token;

    public DesktopController(ConfigurableApplicationContext contexto, @Value("${vybe.desktop.token:}") String token) {
        this.contexto = contexto;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/desligar")
    public ResponseEntity<Void> desligar(@RequestHeader(value = "X-Vybe-Token", required = false) String recebido) {
        if (token.length == 0 || recebido == null
                || !MessageDigest.isEqual(token, recebido.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(403).build();
        }
        log.info("Desligando a pedido do programa");
        Thread desligar = new Thread(() -> {
            try {
                Thread.sleep(200); // deixa a resposta sair antes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(contexto, () -> 0));
        }, "vybe-desligar");
        desligar.setDaemon(false);
        desligar.start();
        return ResponseEntity.noContent().build();
    }
}
