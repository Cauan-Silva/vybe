package com.cauan.vybe.arquivos;

import com.cauan.vybe.comum.Excecoes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arquivos num armazenamento compatível com S3 (pensado para o Cloudflare R2, que tem 10 GB
 * grátis e não cobra pela saída de dados). Ligado com vybe.armazenamento.tipo=s3.
 *
 * O navegador busca os arquivos direto no R2: a API só responde com um redirecionamento.
 * Assim o áudio não passa pelo servidor (que no plano grátis tem pouca banda) e o R2 cuida
 * dos pedaços (HTTP Range) sozinho.
 * - Com vybe.armazenamento.s3.url-publica (bucket público do R2), o endereço é fixo;
 * - sem ela, a API gera um endereço assinado que vale 1 hora, reaproveitado por 50 minutos
 *   para o navegador conseguir usar o cache das capas.
 */
@Component
@ConditionalOnProperty(name = "vybe.armazenamento.tipo", havingValue = "s3")
public class DestinoS3 implements Destino {

    static final Duration VALIDADE_ASSINATURA = Duration.ofHours(1);
    static final Duration REAPROVEITAR_POR = Duration.ofMinutes(50);
    private static final int MAXIMO_NO_CACHE = 2000;

    private record Assinado(URI endereco, Instant ate) {
    }

    private final String bucket;
    private final String urlPublica;
    private final S3Client cliente;
    private final S3Presigner assinador;
    private final Clock clock;
    private final Map<String, Assinado> assinados = new ConcurrentHashMap<>();

    public DestinoS3(@Value("${vybe.armazenamento.s3.endpoint}") String endpoint,
                     @Value("${vybe.armazenamento.s3.regiao:auto}") String regiao,
                     @Value("${vybe.armazenamento.s3.bucket}") String bucket,
                     @Value("${vybe.armazenamento.s3.chave}") String chave,
                     @Value("${vybe.armazenamento.s3.segredo}") String segredo,
                     @Value("${vybe.armazenamento.s3.url-publica:}") String urlPublica,
                     Clock clock) {
        if (endpoint.isBlank() || bucket.isBlank() || chave.isBlank() || segredo.isBlank()) {
            throw new IllegalStateException("Armazenamento S3 ligado, mas faltam S3_ENDPOINT, S3_BUCKET, S3_CHAVE ou S3_SEGREDO.");
        }
        this.bucket = bucket;
        this.urlPublica = urlPublica.isBlank() ? null : urlPublica.replaceAll("/+$", "");
        this.clock = clock;
        var credenciais = StaticCredentialsProvider.create(AwsBasicCredentials.create(chave, segredo));
        // O R2 usa endereços no formato endpoint/bucket/chave (path-style).
        var configuracao = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.cliente = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(regiao))
                .credentialsProvider(credenciais)
                .serviceConfiguration(configuracao)
                .build();
        this.assinador = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(regiao))
                .credentialsProvider(credenciais)
                .serviceConfiguration(configuracao)
                .build();
    }

    @Override
    public void gravar(String chave, InputStream conteudo, long tamanho, String tipoConteudo) {
        cliente.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(chave)
                        .contentType(tipoConteudo)
                        .cacheControl("public, max-age=31536000, immutable") // o nome muda a cada envio
                        .build(),
                RequestBody.fromInputStream(conteudo, tamanho));
    }

    @Override
    public Resource ler(String chave) {
        // Nunca deveria ser chamado: os controllers usam o endereço direto.
        throw new Excecoes.NaoEncontrado("Arquivo disponível só pelo endereço direto");
    }

    @Override
    public Optional<URI> enderecoDireto(String chave) {
        if (urlPublica != null) {
            return Optional.of(publico(urlPublica, chave));
        }
        Instant agora = clock.instant();
        Assinado guardado = assinados.get(chave);
        if (guardado != null && agora.isBefore(guardado.ate())) {
            return Optional.of(guardado.endereco());
        }
        if (assinados.size() >= MAXIMO_NO_CACHE) {
            assinados.clear();
        }
        try {
            URI endereco = assinador.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(VALIDADE_ASSINATURA)
                            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(chave).build())
                            .build())
                    .url().toURI();
            assinados.put(chave, new Assinado(endereco, agora.plus(REAPROVEITAR_POR)));
            return Optional.of(endereco);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Endereço assinado inválido", e);
        }
    }

    @Override
    public void apagar(String chave) {
        assinados.remove(chave);
        cliente.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(chave).build());
    }

    /** "https://pub-xxx.r2.dev" + "audio/abc.mp3" (as chaves só têm letras, números, "-", "." e "/"). */
    static URI publico(String base, String chave) {
        return URI.create(base + "/" + chave);
    }
}
