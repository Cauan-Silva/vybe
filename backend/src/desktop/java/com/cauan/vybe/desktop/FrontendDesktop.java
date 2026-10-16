package com.cauan.vybe.desktop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;

/**
 * No programa, a própria API entrega as telas (o React já compilado), no mesmo endereço
 * 127.0.0.1. Assim a janela do programa e a API ficam na mesma origem.
 *
 * Endereços que não são arquivos (ex.: /playlist/3) devolvem o index.html, porque quem
 * cuida dessas rotas é o React Router. Endereços /api que não existem continuam dando 404.
 */
@Configuration
@Profile("desktop")
public class FrontendDesktop implements WebMvcConfigurer {

    private final Path pasta;

    public FrontendDesktop(@Value("${vybe.desktop.frontend}") String pasta) {
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
    }

    /**
     * O endereço raiz ("/") precisa de uma regra própria: o tratador de arquivos do Spring
     * ignora caminhos vazios e devolveria 404 em vez do index.html.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        registro.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/**")
                .addResourceLocations(pasta.toUri().toString())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String caminho, Resource local) throws IOException {
                        if (caminho.startsWith("api/")) {
                            return null;
                        }
                        Resource arquivo = local.createRelative(caminho);
                        if (arquivo.exists() && arquivo.isReadable()) {
                            return arquivo;
                        }
                        return new FileSystemResource(pasta.resolve("index.html"));
                    }
                });
    }
}