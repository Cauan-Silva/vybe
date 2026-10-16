# VYBE

App de streaming de música feito do zero: você envia suas faixas, a API entrega o áudio em pedaços como os serviços de streaming de verdade, e o player continua tocando enquanto você navega.

> Use só músicas suas ou com licença que permita compartilhar (Creative Commons, Free Music Archive, Biblioteca de Áudio do YouTube).

## O que tem aqui

- **Streaming por partes (HTTP Range):** a música começa a tocar sem baixar o arquivo inteiro, e pular para qualquer ponto da barra busca só aquele trecho
- **Player fixo** no rodapé que não para ao trocar de página, com fila, aleatório, repetir (fila ou faixa), volume e um anel neon em volta da capa que gira e brilha enquanto toca
- **Teclas de mídia e tela de bloqueio** do celular/computador via Media Session API; barra de espaço toca/pausa
- **Envio de músicas** com barra de progresso; a duração é lida no navegador e artista/álbum são criados automaticamente (música sem álbum vira single)
- **Conexão com o Spotify (OAuth):** mostra seus artistas e músicas mais ouvidos e importa suas playlists
- **Player do Spotify (Web Playback SDK):** com conta Premium, as músicas do Spotify tocam completas no mesmo player do VYBE, com fila, aleatório, repetir e volume
- **Busca no Spotify:** qualquer música do catálogo do Spotify, não só as das suas playlists
- **YouTube (API oficial):** busca vídeos de música e toca no player oficial do YouTube, numa janelinha visível no canto da tela
- **Aba Vídeos:** qualquer vídeo do YouTube (não só música), com em alta no Brasil por categoria, página do canal, visualizações e curtidas, "Assistir depois", histórico e continuar de onde parou
- **Contas** com cadastro e login (JWT), **curtidas**, **playlists** privadas e **tocadas recentemente**
- **Busca** por título, artista e álbum, nas suas músicas e no catálogo do Jamendo
- **Catálogo Creative Commons do Jamendo** integrado: as faixas aparecem na busca e em "Em alta no Jamendo", e funcionam como as enviadas (curtir, playlist, fila), com selo e link da licença
- Layout responsivo, com navegação inferior no celular

## Tecnologias

- **Back-end:** Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Security), JWT, Flyway, PostgreSQL
- **Front-end:** React 19, React Router, Vite
- **Testes:** JUnit 5, Mockito, AssertJ (72 testes, incluindo streaming, Jamendo e a integração com o Spotify)

## Como rodar no seu computador

Pré-requisitos: JDK 21, Maven, Node.js e PostgreSQL.

### 1. Banco de dados

Crie o usuário e o banco uma vez (no PowerShell, com a senha do usuário `postgres`):

```powershell
$psql = (Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" | Select-Object -First 1).FullName
& $psql -U postgres -h localhost -c "CREATE USER vybe WITH PASSWORD 'vybe';"
& $psql -U postgres -h localhost -c "CREATE DATABASE vybe OWNER vybe;"
```

### 2. API

```bash
cd backend
mvn spring-boot:run
```

Para ligar o catálogo do Jamendo, crie um app gratuito em devportal.jamendo.com e defina o Client ID antes de rodar (sem ele, o resto funciona normalmente):

```powershell
[Environment]::SetEnvironmentVariable('JAMENDO_CLIENT_ID', 'SEU_CLIENT_ID', 'User')
```

Sobe em `http://localhost:8080`; documentação em `http://localhost:8080/swagger-ui.html`. As tabelas são criadas pelo Flyway, e os arquivos enviados ficam em `backend/dados/` (fora do Git).

### 3. Front-end

Em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

Abra `http://127.0.0.1:5173` (use 127.0.0.1, não localhost, por causa do Spotify), crie uma conta e envie a primeira música.

### Testes

```bash
cd backend
mvn test
```

## Como funciona o streaming

O elemento `<audio>` do navegador pede o arquivo com o cabeçalho `Range: bytes=0-`. Em vez de mandar tudo, a API responde **206 Partial Content** com no máximo 1 MB e o cabeçalho `Content-Range` dizendo qual trecho é. O navegador vai pedindo os próximos trechos conforme toca. Ao arrastar a barra para o meio da música, ele pede direto `Range: bytes=<posição>-`, e a API entrega só dali em diante.

O código está em [`StreamingService`](backend/src/main/java/com/cauan/vybe/arquivos/StreamingService.java) e os casos (sem Range, começo, meio, fim, trecho inválido → 416) estão cobertos em `StreamingServiceTest`.

## Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/cadastro`, `/api/auth/login` | público | Cria conta / entra (devolve o token) |
| GET | `/api/musicas` | público | Músicas adicionadas por último |
| GET | `/api/musicas/{id}/audio` | público | Áudio com suporte a Range |
| POST | `/api/musicas` | login | Envia música (multipart) |
| DELETE | `/api/musicas/{id}` | quem enviou | Exclui música |
| GET | `/api/artistas`, `/api/artistas/{id}` | público | Artistas e discografia |
| GET | `/api/albuns/{id}`, `/api/albuns/{id}/capa` | público | Álbum e capa |
| GET | `/api/busca?q=` | público | Busca músicas, artistas e álbuns |
| GET | `/api/jamendo/busca?q=`, `/api/jamendo/populares` | público | Catálogo do Jamendo (importa as faixas) |
| GET | `/api/spotify/status`, `/api/spotify/autorizar` | login | Situação da conexão / link de autorização |
| GET | `/api/spotify/callback` | público | Retorno do login do Spotify |
| GET | `/api/spotify/playlists`, `/api/spotify/mais-ouvidos?periodo=` | login | Playlists e mais ouvidos |
| POST | `/api/spotify/playlists/{id}/importar` | login | Importa uma playlist |
| GET | `/api/spotify/token` | login | Token do player do Spotify no navegador |
| GET | `/api/spotify/busca?q=` | login | Busca no catálogo do Spotify (só com o player liberado) |
| GET | `/api/youtube/status` | público | Se a busca no YouTube está configurada |
| GET | `/api/youtube/busca?q=` | login | Busca vídeos de música no YouTube (viram músicas do catálogo) |
| GET | `/api/youtube/videos?q=`, `/api/youtube/videos/em-alta?categoria=` | login | Aba Vídeos: qualquer vídeo e os em alta no Brasil (por categoria) |
| GET | `/api/youtube/canais/{id}` | login | Página do canal: dados e vídeos recentes |
| GET/PUT/DELETE | `/api/eu/videos/assistir-depois[/{videoId}]` | login | Assistir depois |
| GET/DELETE | `/api/eu/videos/historico` | login | Histórico de vídeos |
| PUT | `/api/eu/videos/progresso` | login | Salva até onde o vídeo foi assistido |
| DELETE | `/api/spotify/conexao` | login | Desconecta o Spotify |
| GET/PUT/DELETE | `/api/eu/curtidas[/{musicaId}]` | login | Curtidas |
| POST/GET | `/api/eu/reproducoes/{id}`, `/api/eu/recentes` | login | Tocadas recentemente |
| GET/POST/PUT/DELETE | `/api/playlists[/{id}]` | login (dono) | Playlists |
| POST/DELETE | `/api/playlists/{id}/musicas[/{itemId}]` | login (dono) | Músicas da playlist |

O áudio e as capas são públicos porque o elemento `<audio>` do navegador não envia o cabeçalho de login.

## Estrutura

```
backend/src/main/java/com/cauan/vybe/
├── arquivos/    # armazenamento em disco e streaming por partes
├── biblioteca/  # curtidas e tocadas recentemente
├── catalogo/    # músicas, artistas, álbuns, envio e busca
├── comum/       # erros e configuração
├── jamendo/     # cliente da API do Jamendo e importação das faixas
├── playlist/    # playlists e suas músicas
├── seguranca/   # JWT e regras de acesso
├── spotify/     # OAuth do Spotify, mais ouvidos e importação de playlists
└── usuario/     # cadastro e login

frontend/src/
├── components/  # player, fila, listas, menus, cartões
├── contextos/   # login, player, biblioteca, avisos
├── paginas/     # início, busca, artista, álbum, playlist, enviar...
├── services/    # chamadas à API
└── utils/
```

## Próximas fases

- [x] **Jamendo:** catálogo Creative Commons misturado às músicas enviadas
- [x] **Spotify:** conectar a conta, importar playlists e ver artistas e músicas mais ouvidos
- [x] **Player do Spotify:** tocar as músicas do Spotify (Premium) no player do VYBE
- [x] **Busca no Spotify e YouTube:** qualquer música do Spotify e vídeos de música do YouTube
- [ ] Guardar os arquivos num serviço de armazenamento (S3/R2) para publicar online, já que o disco de serviços gratuitos é apagado a cada deploy

## Como funciona a integração com o Jamendo

A API do VYBE chama a API pública do Jamendo (`/v3.0/tracks`) e transforma cada faixa recebida numa `Musica` com `origem = JAMENDO`, guardando o endereço do áudio, a capa, a licença e a página original. Com um id próprio, a faixa entra em curtidas, playlists, recentes e fila sem código especial. O áudio continua sendo tocado direto do servidor do Jamendo. As "Em alta" ficam em cache por uma hora para não chamar a API do Jamendo a cada visita.

## Como funciona a integração com o Spotify

1. **Conexão (OAuth 2.0, Authorization Code):** a API gera um `state` aleatório de uso único, ligado ao usuário, e devolve o link de autorização do Spotify. Depois que a pessoa autoriza, o Spotify chama `/api/spotify/callback` com um código; a API confere o `state` (proteção contra CSRF), troca o código por tokens usando o Client Secret (que nunca sai do servidor) e guarda a conta.
2. **Tokens:** o access token dura cerca de uma hora. Antes de cada chamada, a API renova o token se ele estiver para vencer. Se o Spotify recusar o token (acesso revogado), a conexão é desfeita e a pessoa conecta de novo.
3. **Importação:** com a permissão do player, as faixas da playlist (até 500) entram no catálogo com `origem = SPOTIFY` e vão para uma playlist nova. Sem ela, para cada música (até 100) a API procura uma equivalente livre comparando título e artista sem acentos, maiúsculas e complementos como "(feat. X)" ou " - Remastered", primeiro no catálogo do VYBE e depois no Jamendo.
4. **Player (Web Playback SDK):** o navegador vira um dispositivo "VYBE" na conta da pessoa. O front-end pede um access token à API (`/api/spotify/token`; o Client Secret continua só no servidor), liga o SDK e manda tocar a faixa (`spotify:track:...`) nesse dispositivo. O `PlayerProvider` tem dois "motores", o `<audio>` e o Spotify, e escolhe pela origem da música, então fila, aleatório, repetir, barra de tempo e volume são os mesmos. O VYBE guarda só os dados das faixas (título, artista, capa); o áudio nunca passa pelo servidor. As faixas do Spotify ficam fora da busca geral, porque só tocam para quem tem Premium conectado.

O player só funciona com conta Premium, no navegador do computador (o Spotify não libera o SDK no celular). Conexões feitas antes do player precisam autorizar de novo, pelo botão "Liberar o player" na página Spotify.

Regras do Spotify para apps em modo de desenvolvimento (desde fevereiro de 2026): quem cria o app precisa ter Premium, há no máximo 5 usuários, só playlists próprias ou colaborativas podem ser lidas, e o endereço de retorno precisa usar `127.0.0.1` (não `localhost`).

Variáveis da API: `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` e, se mudar o endereço, `SPOTIFY_REDIRECT_URI` e `FRONTEND_URL`.

## Como funciona a integração com o YouTube

1. **Busca (YouTube Data API v3):** a API do VYBE chama `search.list` (só vídeos da categoria Música que podem ser embutidos) e depois `videos.list` para pegar duração e miniatura. Cada vídeo vira uma `Musica` com `origem = YOUTUBE` e o endereço `youtube:ID`. Artista e título saem do título do vídeo ("Artista - Música (Official Video)") ou, sem o separador, do nome do canal.
2. **Cota:** o Google dá 10.000 unidades por dia, e cada busca custa cerca de 101, ou seja, umas 100 buscas por dia. Por isso a busca exige login, só acontece quando a pessoa aperta Enter ou o botão (não a cada letra), e os resultados ficam 6 horas em cache na memória.
3. **Player (IFrame Player API):** o vídeo toca no player oficial do YouTube, numa janelinha no canto da tela que pode ser aumentada. As regras do YouTube exigem o player visível (mínimo 200×200 px) e proíbem tocar só o áudio ou baixar o conteúdo, então o VYBE não faz nada disso. Vídeos ao vivo e os que o dono bloqueou para sites externos ficam fora dos resultados.

4. **Aba Vídeos:** busca sem o filtro de música e mostra os vídeos em alta no Brasil, também por categoria (`videos.list` com `chart=mostPopular`, que custa só 1 unidade e fica 1 hora em cache). Esses vídeos não viram músicas do catálogo: tocam no player oficial do YouTube em tamanho grande, na própria página, e a música do VYBE pausa quando um vídeo começa.
5. **Canal, Assistir depois, histórico e progresso:** a página do canal usa `channels.list` e a playlist de uploads (`playlistItems.list`), cerca de 3 unidades, com cache de 1 hora. Visualizações e curtidas vêm na mesma chamada de detalhes, sem custo extra. "Assistir depois", histórico e a posição de cada vídeo ficam numa tabela do VYBE (`video_usuario`), com uma cópia dos dados do vídeo, então as listas abrem sem gastar cota. O player salva a posição a cada 10 segundos, ao pausar e ao fechar; faltando menos de 10 segundos, o vídeo conta como terminado e recomeça do zero.

Variável da API: `YOUTUBE_API_KEY` (chave criada em console.cloud.google.com, com a YouTube Data API v3 ativada).
