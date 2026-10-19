# App Android

O app Android é o mesmo front-end React dentro de um WebView ([Capacitor 8](https://capacitorjs.com)), com um plugin nativo em Java para a música continuar com a tela apagada. Ele usa a API hospedada ([docs/NUVEM.md](NUVEM.md)).

Não precisa instalar o Android Studio: o GitHub Actions gera, assina e anexa o APK a cada release (`.github/workflows/android.yml`).

## Como o build funciona

1. `npm run app:build`: gera as telas no modo app (`.env.app` marca `VITE_APP=1`), apontando para a API em `VYBE_API_URL`.
2. `npx cap add android`: cria o projeto Android do zero (a pasta `frontend/android` não fica no Git).
3. `node mobile/preparar-android.mjs 0.3.0`: copia o código nativo e os ícones de `frontend/mobile/android`, acrescenta ao `AndroidManifest.xml` as permissões e o serviço de mídia e grava a versão.
4. `./gradlew assembleRelease`, depois `zipalign` e `apksigner` com a chave guardada nos segredos do GitHub.
5. O `VYBE-0.3.0.apk` vai para o release.

### O código nativo (`frontend/mobile/android/java/com/cauan/vybe`)

| Arquivo | Função |
|---|---|
| `MainActivity.java` | Registra o plugin, pede a permissão de notificações (Android 13+) e mantém o WebView rodando quando o app vai para segundo plano com música tocando |
| `TocadorPlugin.java` | Ponte com o JavaScript (`services/tocadorNativo.js`): `atualizar`, `parar` e o evento `acao` dos botões |
| `TocadorService.java` | Serviço em primeiro plano de mídia: notificação com anterior/tocar/próxima, sessão de mídia (tela de bloqueio e fone Bluetooth) e travas de CPU e Wi-Fi enquanto toca |

## Configurar uma vez

### 1. Criar a chave de assinatura (só para o APK final)

Todo APK precisa ser assinado, e **as atualizações precisam da mesma chave**: se ela se perder, quem já instalou terá de desinstalar o app (e perder os dados locais) para instalar a versão nova. Guarde o arquivo e a senha num lugar seguro, fora do repositório.

No PowerShell (o `keytool` vem com o Java 21 que você já usa no Maven):

```powershell
mkdir "$HOME\Documents\chaves" -Force
cd "$HOME\Documents\chaves"
keytool -genkeypair -v -keystore vybe.keystore -alias vybe -keyalg RSA -keysize 2048 -validity 10000
```

Ele pede uma senha e alguns dados (nome, cidade...). A senha da chave é a mesma do arquivo.

Copie o arquivo em base64 para a área de transferência:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Documents\chaves\vybe.keystore")) | Set-Clipboard
```

### 2. Segredos e variável no GitHub

No repositório: **Settings → Secrets and variables → Actions**.

Aba **Secrets → New repository secret**:

| Nome | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | cole o que o comando acima copiou |
| `ANDROID_KEYSTORE_PASSWORD` | a senha da chave |
| `ANDROID_KEY_ALIAS` | `vybe` |
| `ANDROID_KEY_PASSWORD` | a mesma senha |

Aba **Variables → New repository variable**:

| Nome | Valor |
|---|---|
| `VYBE_API_URL` | o endereço da API **terminando em `/api`**, ex.: `https://vybe-api.onrender.com/api` |

## Gerar o APK

### Testar antes de publicar (não precisa da chave)

1. Com a API já no ar ([docs/NUVEM.md](NUVEM.md)) e a variável `VYBE_API_URL` criada, abra **Actions → App Android → Run workflow**.
2. Deixe **tipo = teste**, informe a versão (ex.: `0.3.0`) e clique em **Run workflow**.
3. Em uns 10 minutos, abra a execução e baixe **VYBE-0.3.0-teste-apk** em **Artifacts**, no fim da página. É um .zip com o `VYBE-0.3.0-teste.apk` dentro.
4. Mande o .apk para o celular (cabo, Google Drive ou WhatsApp para você mesmo) e instale.

O APK de teste aparece como **VYBE Teste** e é um app separado do final: pode ficar instalado junto, sem conflito, e é só desinstalar quando terminar.

### Publicar

Ao publicar um release (tag `v0.3.0`, por exemplo), o workflow roda sozinho com **tipo = final**: o APK é assinado com a sua chave e, em uns 10 minutos, o `VYBE-0.3.0.apk` aparece junto dos arquivos do release.

## Instalar no celular

1. No celular, abra a página do release e baixe o `.apk`.
2. Abra o arquivo. Na primeira vez o Android pede para **permitir a instalação de apps desta fonte** (o navegador).
3. Se o Play Protect avisar que o app é desconhecido (ele não veio da Play Store), toque em **Mais detalhes → Instalar mesmo assim**.
4. Ao abrir, permita as notificações: é por elas que aparecem os controles da música.

Para atualizar, baixe o APK novo e instale por cima. As músicas, playlists e o perfil ficam no servidor, então nada se perde.

## O que muda no celular

- **Tocando agora:** toque na música na barra do player para abrir a tela cheia com capa, tempo, todos os controles, timer e fila. O "voltar" do Android fecha.
- **Tela apagada:** músicas enviadas e do Jamendo continuam tocando com a tela apagada e passam para a próxima da fila sozinhas.
- **Spotify:** o celular não tem o player do Spotify (ele exige a proteção Widevine do navegador de computador). Ao tocar uma música do Spotify, a API procura o mesmo vídeo no YouTube, comparando título, artista e duração e evitando versões ao vivo, karaokê e covers, e ele toca no app. O resultado fica salvo para as próximas vezes.
- **YouTube só com o app aberto:** as regras do YouTube não permitem o player dele tocando em segundo plano. Ao sair do app ou apagar a tela, músicas do YouTube (e do Spotify, que tocam por ele) pausam; na volta, é só apertar o play.
- **Primeira abertura do dia:** a API gratuita do Render dorme após 15 minutos sem uso; o primeiro acesso pode levar cerca de 1 minuto.
- **Conta separada:** o app usa o servidor online; o programa para Windows guarda tudo no próprio computador. São contas diferentes.
