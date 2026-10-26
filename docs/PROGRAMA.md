# VYBE para Windows (programa instalável)

O VYBE também é um programa para Windows: um instalador `VYBE-Setup.exe` que qualquer pessoa
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

## Luz ambiente em tempo real

Na aba Vídeos, o brilho em volta do player acompanha as cores do vídeo. Como o vídeo vem num player do
YouTube (outro site), a página não consegue ler os quadros; quem faz isso é o programa (`desktop/ambiente.js`):

1. pergunta à página onde está o vídeo e se ele está tocando (`window.__vybeAmbiente`);
2. recebe do Edge um fluxo pequeno da janela (`Page.startScreencast`, que não mexe no tamanho da tela),
   recorta a área do vídeo e reduz a 64 px de largura, cerca de 8 vezes por segundo;
3. calcula a cor média de cada borda (topo, base, esquerda, direita), com um pouco mais de saturação;
4. devolve as cores à página (`window.__vybeLuz`), que pinta quatro manchas desfocadas com transição suave.

Com o Edge, a conversa usa o protocolo de depuração do Chromium por um canal privado
(`--remote-debugging-pipe`, sem abrir porta nenhuma); com a janela do Electron, usa `capturePage`.
Nada é capturado com o vídeo pausado, a janela minimizada ou o player fora da tela, e o Modo leve desliga
tudo. Na versão web, a luz usa as cores da miniatura do vídeo.

## Conversa entre o programa e a página (ponte)

O mesmo canal privado com o Edge serve de ponte: o programa pede ao Edge para criar na página a função
`window.vybeDesktopEnviar(json)` (`Runtime.addBinding`), e a página usa ela para mandar mensagens; o
programa responde chamando `window.__vybeDoPrograma(mensagem)`. Na janela do Electron, a página manda pelo
preload (`window.vybeDesktop.enviar`). Mensagens: o que está tocando (`tocando`), a configuração do
Discord (`discord`), pedidos de atualização (`atualizacao`) e, do programa para a página, a versão, o estado
da atualização e o do Discord. O lado da página fica em `frontend/src/utils/programa.js`.

## Discord ("Ouvindo VYBE")

`desktop/discord.js` fala direto com o Discord aberto no computador, pelo canal local dele
(`\\?\pipe\discord-ipc-0`): apresenta-se com o ID do aplicativo e manda `SET_ACTIVITY` com música,
artista, capa (quando é um endereço https) e o tempo. Nada sai do computador por aqui. Ao pausar, a
atividade some; ao fechar o VYBE, também. As atualizações são juntadas (no máximo uma a cada 4 s), porque o
Discord limita quantas aceita.

Cada pessoa cria o próprio aplicativo em discord.com/developers/applications, com o nome **VYBE**
(é o nome que aparece no perfil), e cola o **Application ID** em Configurações → Discord.

## Atualização automática

O programa usa o `electron-updater` com as Releases do GitHub (`Cauan-Silva/vybe`). Uns 20 segundos depois
de abrir, e de 6 em 6 horas, ele procura uma versão nova; achando, baixa em segundo plano e mostra o aviso
**"Reiniciar e atualizar"**. Se a pessoa não clicar, a versão nova é instalada quando ela fechar o VYBE.
Só funciona no programa instalado (com `npm start` aparece como indisponível).

Para publicar uma versão nova:

1. Aumente `version` em `desktop/package.json` (ex.: `0.3.0` → `0.4.0`) e faça o push.
2. No GitHub, crie a Release com a tag `v0.4.0` (a mesma versão, com `v` na frente) e publique.
3. Pronto: o GitHub Actions gera o instalador no Windows (`.github/workflows/programa.yml`) e anexa ao
   release os três arquivos: `VYBE-Setup-0.4.0.exe`, `VYBE-Setup-0.4.0.exe.blockmap` e `latest.yml`
   (o que os programas instalados leem para saber que há versão nova). O workflow do Android anexa o APK.
   Se a tag não bater com a versão do `package.json`, o workflow para com um aviso.

Para gerar o instalador no seu computador, sem publicar, veja [Gerar o instalador](#gerar-o-instalador).

A 0.1.0 não tinha atualização automática: quem está nela instala a 0.2.0 à mão uma vez, e daí em diante
as versões chegam sozinhas.

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
- **Janela flutuante dos vídeos:** usa a Document Picture-in-Picture do Edge/Chrome. Fica por cima de
  janelas comuns e de jogos em modo janela/sem bordas; jogos em tela cheia exclusiva desenham por cima
  de tudo.
- **Instalador sem assinatura digital:** o Windows mostra o aviso do SmartScreen na primeira instalação
  ("Mais informações → Executar assim mesmo"). A atualização automática funciona mesmo assim.
- **Visual C++ 2013:** o PostgreSQL embutido pode precisar do "Microsoft Visual C++ 2013 Redistributable
  (x64)". Se o programa não abrir na primeira vez, a mensagem de erro avisa.

## Gerar o instalador

Pré-requisitos no computador de quem gera: **JDK 21** (com `JAVA_HOME` configurado), **Maven** e **Node.js**.

```powershell
cd "C:\Users\MAIS INTERNET\Documents\vybe"
powershell -ExecutionPolicy Bypass -File desktop\build.ps1
```

O script compila a API com o perfil `desktop`, monta o React apontando para `/api`, gera o Java enxuto com
`jlink`, copia o ícone e chama o `electron-builder`. O instalador sai em `desktop\dist\VYBE-Setup-<versão>.exe`,
junto com o `.blockmap` e o `latest.yml` usados pela atualização automática.

Para abrir o programa sem gerar o instalador (desenvolvimento):

```powershell
powershell -ExecutionPolicy Bypass -File desktop\build.ps1 -SoPreparar
cd desktop
npm start
```

O log da API fica em `%APPDATA%\VYBE\dados\logs\api.log`.
