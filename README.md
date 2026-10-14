# VYBE

App de streaming de música feito do zero: você envia suas faixas, a API entrega o áudio em pedaços como os serviços de streaming de verdade, e o player continua tocando enquanto você navega.

> Use só músicas suas ou com licença que permita compartilhar (Creative Commons, Free Music Archive, Biblioteca de Áudio do YouTube).

## O que tem aqui

- **Streaming por partes (HTTP Range):** a música começa a tocar sem baixar o arquivo inteiro, e pular para qualquer ponto da barra busca só aquele trecho
- **Player fixo** no rodapé que não para ao trocar de página, com fila, aleatório, repetir (fila ou faixa), volume e um anel neon em volta da capa que gira e brilha enquanto toca
- **Teclas de mídia e tela de bloqueio** do celular/computador via Media Session API; barra de espaço toca/pausa
- **Envio de músicas** com barra de progresso; a duração é lida no navegador e artista/álbum são criados automaticamente (música sem álbum vira single)
- **Contas** com cadastro e login (JWT), **curtidas**, **playlists** privadas e **tocadas recentemente**
- **Busca** por título, artista e álbum
- Layout responsivo, com navegação inferior no celular

## Tecnologias

- **Back-end:** Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Security), JWT, Flyway, PostgreSQL
- **Front-end:** React 19, React Router, Vite
- **Testes:** JUnit 5, Mockito, AssertJ (18 testes, incluindo o streaming)

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

Sobe em `http://localhost:8080`; documentação em `http://localhost:8080/swagger-ui.html`. As tabelas são criadas pelo Flyway, e os arquivos enviados ficam em `backend/dados/` (fora do Git).

### 3. Front-end

Em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

Abra `http://localhost:5173`, crie uma conta e envie a primeira música.

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
├── playlist/    # playlists e suas músicas
├── seguranca/   # JWT e regras de acesso
└── usuario/     # cadastro e login

frontend/src/
├── components/  # player, fila, listas, menus, cartões
├── contextos/   # login, player, biblioteca, avisos
├── paginas/     # início, busca, artista, álbum, playlist, enviar...
├── services/    # chamadas à API
└── utils/
```

## Próximas fases

- [ ] **Jamendo:** catálogo Creative Commons misturado às músicas enviadas (o banco já tem `origem` e `url_externa`)
- [ ] **Spotify:** entrar com a conta, importar playlists e ver artistas mais ouvidos
- [ ] Guardar os arquivos num serviço de armazenamento (S3/R2) para publicar online, já que o disco de serviços gratuitos é apagado a cada deploy
