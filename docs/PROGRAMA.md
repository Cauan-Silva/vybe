# VYBE para Windows (programa instalável)

O VYBE também é um programa para Windows: um instalador `VYBE Setup.exe` que qualquer pessoa
baixa e usa, sem instalar Java, PostgreSQL ou Node e sem abrir terminal.

## Como funciona por dentro

```
VYBE.exe (Electron)
 ├─ abre a tela de abertura
 ├─ liga a API: jre\bin\java.exe -jar vybe-api.jar --spring.profiles.active=desktop
 │    ├─ PostgreSQL embutido (dados em %APPDATA%\VYBE\dados\banco)
 │    ├─ entrega as telas (React compilado) em http://127.0.0.1:47823
 │    └─ só aceita conexões do próprio computador
 ├─ espera a API responder e abre as telas numa janela do Edge em modo aplicativo
 │    (sem barra de endereço nem abas, com um perfil só do VYBE)
 ├─ fechar a janela → desliga tudo (a música para junto com a tela)
 └─ Sair (bandeja) → fecha a janela, pede à API para desligar com calma (fecha o banco) e encerra
```

- **Mesmo código da versão web.** O perfil `desktop` do Spring (`application-desktop.yml`) e o perfil
  `desktop` do Maven (código em `backend/src/desktop/java`) só acrescentam o banco embutido, a entrega das
  telas e o desligamento. As migrações do Flyway são as mesmas.
- **Java enxuto.** O `jlink` gera um Java só com os módulos que a API usa, que vai dentro do instalador.
- **Dados do usuário** ficam em `%APPDATA%\VYBE\dados`: banco, músicas enviadas, capas e logs. Desinstalar
  o programa não apaga essa pasta.
- **Segurança.** O segredo dos logins é gerado na primeira abertura. O desligamento exige um token aleatório,
  criado a cada abertura. Links externos abrem no navegador, e só o VYBE e o login do Spotify abrem dentro
  da janela.

## Desempenho (para quem usa jogando)

- **Java enxuto:** a API roda com no máximo 256 MB de memória, coleta de lixo simples (`SerialGC`) e
  compilação rápida (`TieredStopAtLevel=1`). Um arquivo de classes (`vybe-classes.jsa`, CDS) é criado na
  primeira abertura e deixa as seguintes mais rápidas.
- **Só o necessário:** a API cria cada parte quando é usada pela primeira vez (`lazy-initialization`), o
  Tomcat usa poucas threads e o PostgreSQL embutido roda com configuração leve (32 MB de cache).
- **Janela escondida não gasta:** minimizado ou na bandeja, o VYBE para de redesenhar a barra de tempo; a
  música continua tocando normalmente.
- **Modo leve** (em Configurações): desliga animações, brilhos e transições e atualiza a barra de tempo
  uma vez por segundo.

## Chaves das integrações

No programa, cada pessoa usa as próprias chaves, informadas em **Configurações** (o item aparece no menu
só no programa). Todas são opcionais.

| Integração | Onde criar | O que informar |
|---|---|---|
| Spotify | developer.spotify.com/dashboard | Client ID. Cadastrar `http://127.0.0.1:47823/api/spotify/callback` em Redirect URIs |
| YouTube | console.cloud.google.com/apis/credentials | Chave de API, com a YouTube Data API v3 ativada |
| Jamendo | devportal.jamendo.com | Client ID |

**Spotify sem Client Secret.** Um secret dentro do instalador poderia ser extraído por qualquer pessoa.
Por isso o programa loga com **PKCE** (RFC 7636): a cada login a API gera um código aleatório, manda o
hash dele ao Spotify e, na volta, prova que é a mesma API mostrando o código original. A versão web
continua usando o secret, que fica só no servidor.

## Limitações conhecidas

- **Por que o Edge.** O player do Spotify (Web Playback SDK) exige a proteção de conteúdo Widevine, que o
  Edge tem e o Electron comum não. O Edge já vem em todo Windows 10 e 11 e consome parecido com o
  Electron (os dois são Chromium). Se o Edge não for encontrado, o VYBE abre na janela do Electron e o
  Spotify não toca: as músicas do Spotify continuam aparecendo e o selo abre o app oficial.
- **Modo de desenvolvimento do Spotify:** cada app do Spotify aceita até 5 usuários cadastrados no painel.
  Por isso cada pessoa cria o próprio app.
- **Visual C++ 2013:** o PostgreSQL embutido pode precisar do "Microsoft Visual C++ 2013 Redistributable
  (x64)". Se o programa não abrir na primeira vez, a mensagem de erro avisa.

## Gerar o instalador

Pré-requisitos no computador de quem gera: **JDK 21** (com `JAVA_HOME` configurado), **Maven** e **Node.js**.

```powershell
cd "C:\Users\MAIS INTERNET\Documents\vybe"
powershell -ExecutionPolicy Bypass -File desktop\build.ps1
```

O script compila a API com o perfil `desktop`, monta o React apontando para `/api`, gera o Java enxuto com
`jlink`, copia o ícone e chama o `electron-builder`. O instalador sai em `desktop\dist\VYBE Setup 0.1.0.exe`.

Para abrir o programa sem gerar o instalador (desenvolvimento):

```powershell
powershell -ExecutionPolicy Bypass -File desktop\build.ps1 -SoPreparar
cd desktop
npm start
```

O log da API fica em `%APPDATA%\VYBE\dados\logs\api.log`.
