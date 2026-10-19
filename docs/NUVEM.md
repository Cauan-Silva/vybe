# VYBE online: Render, Neon e Cloudflare R2

O app Android não tem servidor dentro dele: ele conversa com a API do VYBE publicada na internet. Tudo aqui usa planos gratuitos.

| Parte | Serviço | Plano gratuito |
|---|---|---|
| API (Spring Boot em Docker) | [Render](https://render.com) | 750 h/mês; **dorme após 15 min sem uso** e leva cerca de 1 min para acordar |
| Banco PostgreSQL | [Neon](https://neon.tech) | 0,5 GB, não expira |
| Áudios e imagens enviados | [Cloudflare R2](https://www.cloudflare.com/developer-platform/r2/) | 10 GB, sem custo de download |

Por que o R2: o disco dos serviços gratuitos é apagado a cada deploy, então as músicas enviadas sumiriam. Com o perfil `nuvem`, a API grava os arquivos no R2 e, na hora de tocar, manda o navegador baixar direto de lá (resposta 302). A API não gasta banda com áudio.

Faça na ordem abaixo. **Nenhuma senha ou chave vai para o Git**: elas ficam só no painel do Render.

## 1. Banco no Neon

1. Crie a conta e um projeto. Região: **AWS US East 1 (N. Virginia)**, a mesma do Render.
2. Em **Connect**, copie os dados da conexão. O Neon mostra algo como
   `postgresql://neondb_owner:SENHA@ep-nome-123.us-east-1.aws.neon.tech/neondb?sslmode=require`.
3. Separe em três partes para o Render:
   - `DB_URL`: `jdbc:postgresql://ep-nome-123.us-east-1.aws.neon.tech/neondb?sslmode=require` (troque `postgresql://usuário:senha@` por `jdbc:postgresql://`)
   - `DB_USER`: `neondb_owner`
   - `DB_PASSWORD`: a senha

As tabelas são criadas sozinhas pelo Flyway no primeiro start.

## 2. Arquivos no Cloudflare R2

1. No painel da Cloudflare, abra **R2 Object Storage**. Para ativar, a Cloudflare pede um meio de pagamento, mas não cobra nada dentro do plano gratuito.
2. **Create bucket** com o nome `vybe`. Esse nome é o `S3_BUCKET`.
3. **Manage API tokens → Create API token**: permissão **Object Read & Write**, só para o bucket `vybe`. Guarde:
   - **Access Key ID** → `S3_CHAVE`
   - **Secret Access Key** → `S3_SEGREDO` (aparece uma vez só)
   - o endpoint `https://<ID-da-conta>.r2.cloudflarestorage.com` → `S3_ENDPOINT`
4. Opcional: no bucket, **Settings → Public Development URL → Enable**. O endereço `https://pub-....r2.dev` vai em `S3_URL_PUBLICA`. Sem ele, a API gera links temporários (assinados, válidos por 1 hora) para cada arquivo, o que também funciona.

## 3. API no Render

1. Crie a conta no Render entrando com o GitHub.
2. **New → Blueprint**, escolha o repositório `vybe`. O Render lê o `render.yaml` da raiz e cria o serviço `vybe-api`.
3. Preencha as variáveis que ele pedir:

| Variável | Valor |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | do passo 1 |
| `S3_ENDPOINT`, `S3_BUCKET`, `S3_CHAVE`, `S3_SEGREDO` | do passo 2 |
| `S3_URL_PUBLICA` | do passo 2 (ou deixe vazio) |
| `FRONTEND_URL` | `https://vybe-api.onrender.com/api/spotify/app` (troque pelo endereço do seu serviço) |
| `YOUTUBE_API_KEY` | a chave do YouTube (passo 4) |
| `JAMENDO_CLIENT_ID` | opcional, de [devportal.jamendo.com](https://devportal.jamendo.com) |
| `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `SPOTIFY_REDIRECT_URI` | opcionais (passo 5) |

`JWT_SEGREDO` é gerado pelo próprio Render, e `CORS_ORIGENS` já vem liberando o app Android (`https://localhost`).

4. O primeiro deploy compila a API dentro do Docker (alguns minutos). Para conferir, abra `https://vybe-api.onrender.com/api/youtube/status`: deve aparecer um JSON.
5. O endereço da API **com `/api` no fim** é o que o app usa: guarde para a variável `VYBE_API_URL` do GitHub ([docs/APP.md](APP.md)).

A cada push na `main`, o Render publica a API de novo sozinho.

## 4. Chave do YouTube

No [Google Cloud Console](https://console.cloud.google.com/apis/credentials), crie uma chave de API com **YouTube Data API v3** ativada. Em **Restrições da API**, deixe só a YouTube Data API v3. Não use restrição por site: quem chama o YouTube é o servidor, não o navegador.

A cota gratuita é de 10.000 unidades por dia. No app, cada música do Spotify procura o vídeo equivalente uma única vez (uma busca, 100 unidades); depois, o resultado fica salvo no banco.

## 5. Spotify (opcional)

No [painel do Spotify](https://developer.spotify.com/dashboard), no seu app:

1. Em **Redirect URIs**, acrescente `https://vybe-api.onrender.com/api/spotify/callback` (o mesmo valor de `SPOTIFY_REDIRECT_URI`).
2. Em modo de desenvolvimento, só as contas adicionadas em **User Management** conseguem conectar.

No celular, "Conectar com o Spotify" abre o login no navegador. Depois de autorizar, a página `/api/spotify/app/spotify` avisa que já dá para voltar ao app, e a tela do Spotify confere a conexão sozinha. As playlists importadas tocam pelo vídeo equivalente do YouTube.

## Como rodar o perfil da nuvem no seu computador

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "nuvem"
$env:DB_URL = "jdbc:postgresql://localhost:5432/vybe"
# ... e as variáveis S3_* do R2
mvn spring-boot:run
```

Sem `ARMAZENAMENTO_TIPO=s3` (fora do perfil `nuvem`), a API continua gravando no disco, como sempre.
