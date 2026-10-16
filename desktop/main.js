/*
 * VYBE para Windows (Electron).
 *
 * Ao abrir: mostra a tela de abertura, liga a API Java (com o Java e o PostgreSQL que vêm
 * dentro do instalador), espera ela responder e abre as telas do VYBE, que a própria API
 * entrega em http://127.0.0.1:47823.
 *
 * As telas abrem numa janela do Microsoft Edge em modo aplicativo (sem barra de endereço nem
 * abas). Motivo: o player do Spotify exige a proteção de conteúdo Widevine, que o Edge tem e o
 * Electron comum não. O Edge roda com um perfil só do VYBE, separado do Edge da pessoa.
 * Sem Edge no computador, o VYBE usa a janela do próprio Electron (aí o Spotify não toca).
 *
 * Ao fechar a janela: o VYBE vai para a bandeja (perto do relógio) e a música continua.
 * Ao sair de verdade (menu da bandeja): pede à API para desligar com calma, para o banco
 * fechar direito, e só então encerra.
 */
const { app, BrowserWindow, Tray, Menu, shell, dialog, nativeImage } = require('electron')
const { spawn } = require('node:child_process')
const crypto = require('node:crypto')
const fs = require('node:fs')
const http = require('node:http')
const path = require('node:path')

const PORTA = 47823
const ENDERECO = `http://127.0.0.1:${PORTA}`
const ESPERA_MAXIMA_MS = 120_000 // a primeira abertura cria o banco e pode demorar
const ESPERA_DESLIGAR_MS = 15_000

// Uma janela só: abrir o atalho de novo traz a que já está aberta.
if (!app.requestSingleInstanceLock()) {
  app.quit()
}

/** Onde estão o Java, a API e as telas: dentro do instalador, ou em desktop/build durante o desenvolvimento. */
const recursos = app.isPackaged ? process.resourcesPath : path.join(__dirname, 'build')
const pastaDados = path.join(app.getPath('userData'), 'dados')
const icone = path.join(recursos, 'icon.png')

let api = null // processo Java
let edge = null // processo do Edge com as telas (modo aplicativo)
let modoEdge = false
let janela = null
let abertura = null
let bandeja = null
let saindo = false
let avisouBandeja = false
const token = crypto.randomBytes(32).toString('hex') // só este programa consegue desligar a API

// ---------- API Java ----------

/** Segredo dos logins: criado na primeira abertura e guardado na pasta de dados. */
function segredoJwt() {
  const arquivo = path.join(pastaDados, 'segredo-jwt.txt')
  try {
    const salvo = fs.readFileSync(arquivo, 'utf8').trim()
    if (salvo.length >= 32) return salvo
  } catch {
    // primeira abertura
  }
  const novo = crypto.randomBytes(48).toString('hex')
  fs.writeFileSync(arquivo, novo, { mode: 0o600 })
  return novo
}

function ligarApi() {
  fs.mkdirSync(path.join(pastaDados, 'logs'), { recursive: true })
  const log = fs.createWriteStream(path.join(pastaDados, 'logs', 'api.log'), { flags: 'w' })
  const java = path.join(recursos, 'jre', 'bin', process.platform === 'win32' ? 'java.exe' : 'java')

  // As chaves do programa vêm da tela de Configurações, e não das variáveis de quem
  // desenvolve: tiramos do ambiente tudo o que a versão web usa (ex.: SPOTIFY_CLIENT_SECRET).
  const ambiente = { ...process.env }
  for (const nome of Object.keys(ambiente)) {
    if (/^(SPOTIFY_|YOUTUBE_|JAMENDO_|DB_|SPRING_|VYBE_|JWT_)/i.test(nome) ||
        ['CORS_ORIGENS', 'FRONTEND_URL', 'ARMAZENAMENTO_PASTA', 'PORT'].includes(nome.toUpperCase())) {
      delete ambiente[nome]
    }
  }
  Object.assign(ambiente, {
    PORT: String(PORTA),
    VYBE_DADOS: pastaDados,
    VYBE_FRONTEND: path.join(recursos, 'frontend'),
    VYBE_DESKTOP_TOKEN: token,
    JWT_SEGREDO: segredoJwt(),
  })

  // Java enxuto para quem usa o VYBE jogando: pouca memória, coleta de lixo simples
  // (um app pequeno não precisa da paralela) e compilação rápida em vez de máxima.
  // O arquivo .jsa guarda classes já carregadas: da segunda abertura em diante, liga mais rápido.
  const argumentos = [
    '-Xms64m',
    '-Xmx256m',
    '-Xss512k',
    '-XX:+UseSerialGC',
    '-XX:TieredStopAtLevel=1',
    '-XX:MaxMetaspaceSize=160m',
    '-XX:ReservedCodeCacheSize=48m',
    '-XX:+AutoCreateSharedArchive',
    `-XX:SharedArchiveFile=${path.join(pastaDados, 'vybe-classes.jsa')}`,
    '-jar',
    path.join(recursos, 'vybe-api.jar'),
    '--spring.profiles.active=desktop',
  ]
  api = spawn(java, argumentos, {
    env: ambiente,
    windowsHide: true,
  })
  api.stdout.pipe(log)
  api.stderr.pipe(log)

  api.on('error', (erro) => falhar(`Não foi possível iniciar o VYBE (${erro.message}).`))
  api.on('exit', (codigo) => {
    api = null
    if (!saindo) {
      falhar(`O VYBE parou inesperadamente (código ${codigo}).`)
    }
  })
}

/** Pergunta à API se ela já está de pé (rota pública e leve). */
function apiRespondendo() {
  return new Promise((resolve) => {
    const pedido = http.get(`${ENDERECO}/api/youtube/status`, { timeout: 2000 }, (resposta) => {
      resposta.resume()
      resolve(resposta.statusCode === 200)
    })
    pedido.on('error', () => resolve(false))
    pedido.on('timeout', () => {
      pedido.destroy()
      resolve(false)
    })
  })
}

async function esperarApi() {
  const limite = Date.now() + ESPERA_MAXIMA_MS
  while (Date.now() < limite) {
    if (!api) return false // morreu durante a espera (o "exit" já avisou)
    if (await apiRespondendo()) return true
    await new Promise((r) => setTimeout(r, 500))
  }
  return false
}

/** Pede à API para desligar com calma e espera; se não sair a tempo, encerra à força. */
function desligarApi() {
  return new Promise((resolve) => {
    if (!api) return resolve()
    const processo = api
    const forcar = setTimeout(() => {
      processo.kill()
      resolve()
    }, ESPERA_DESLIGAR_MS)
    processo.once('exit', () => {
      clearTimeout(forcar)
      resolve()
    })
    const pedido = http.request(`${ENDERECO}/api/desktop/desligar`, {
      method: 'POST',
      headers: { 'X-Vybe-Token': token },
      timeout: 3000,
    })
    pedido.on('error', () => processo.kill())
    pedido.end()
  })
}

/** A porta já está ocupada por outro programa? */
function portaOcupada() {
  return new Promise((resolve) => {
    const servidor = require('node:net').createServer()
    servidor.once('error', () => resolve(true))
    servidor.once('listening', () => servidor.close(() => resolve(false)))
    servidor.listen(PORTA, '127.0.0.1')
  })
}

function falhar(mensagem) {
  if (saindo) return
  saindo = true
  api?.kill()
  const log = path.join(pastaDados, 'logs', 'api.log')
  dialog.showErrorBox(
    'VYBE',
    `${mensagem}\n\nDetalhes técnicos em:\n${log}\n\n` +
      'Se for a primeira vez, confira se o "Microsoft Visual C++ 2013 Redistributable (x64)" está instalado: ' +
      'o banco de dados embutido precisa dele.',
  )
  app.exit(1)
}

// ---------- Janelas ----------

function criarAbertura() {
  abertura = new BrowserWindow({
    width: 360,
    height: 300,
    frame: false,
    resizable: false,
    backgroundColor: '#05040d',
    icon: icone,
    show: true,
  })
  abertura.loadFile(path.join(__dirname, 'splash.html'), { query: { logo: `file://${icone.replace(/\\/g, '/')}` } })
}

function criarJanela() {
  janela = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    show: false,
    backgroundColor: '#05040d',
    icon: icone,
    title: 'VYBE',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  })
  janela.removeMenu()

  // Links externos (Spotify, YouTube, licenças) abrem no navegador, não dentro do programa.
  janela.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  // Dentro da janela só ficam o VYBE e o login do Spotify (que volta para o VYBE no fim).
  janela.webContents.on('will-navigate', (evento, url) => {
    const destino = new URL(url)
    const permitido = url.startsWith(ENDERECO) || destino.hostname === 'accounts.spotify.com'
    if (!permitido) {
      evento.preventDefault()
      if (destino.protocol === 'https:' || destino.protocol === 'http:') shell.openExternal(url)
    }
  })

  janela.once('ready-to-show', () => {
    abertura?.close()
    abertura = null
    janela.show()
  })

  // Fechar a janela só esconde: a música continua e o VYBE fica na bandeja.
  janela.on('close', (evento) => {
    if (saindo) return
    evento.preventDefault()
    janela.hide()
    if (!avisouBandeja && bandeja && process.platform === 'win32') {
      avisouBandeja = true
      bandeja.displayBalloon({
        title: 'O VYBE continua aberto',
        content: 'Ele está aqui na bandeja. Para fechar de vez, clique com o botão direito e escolha Sair.',
        iconType: 'info',
      })
    }
  })

  janela.loadURL(ENDERECO)
}

// ---------- Edge em modo aplicativo ----------

/** Caminho do msedge.exe, ou null se o Edge não estiver instalado. */
function encontrarEdge() {
  if (process.platform !== 'win32' && !process.env.VYBE_EDGE_TESTE) return null
  const bases = [process.env['ProgramFiles(x86)'], process.env.ProgramFiles, process.env.LOCALAPPDATA]
  for (const base of bases.filter(Boolean)) {
    const caminho = path.join(base, 'Microsoft', 'Edge', 'Application', 'msedge.exe')
    if (fs.existsSync(caminho)) return caminho
  }
  return null
}

function abrirNoEdge() {
  const executavel = encontrarEdge()
  const abertoEm = Date.now()
  const processo = spawn(
    executavel,
    [
      `--app=${ENDERECO}`,
      // Perfil próprio: não mistura com o Edge da pessoa e fecha junto com a janela do VYBE
      `--user-data-dir=${path.join(app.getPath('userData'), 'edge')}`,
      '--window-size=1280,820',
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-extensions',
      '--disable-sync',
      '--disable-features=msEdgeStartupBoost,msImplicitSignin,msEdgeSidebarV2',
    ],
    { windowsHide: false, detached: false },
  )
  // Se a janela já está aberta, este novo processo só entrega o pedido ao Edge do VYBE
  // e sai; seguimos acompanhando o processo principal.
  const principal = !edge
  if (principal) edge = processo
  processo.on('error', () => {
    if (!principal) return
    edge = null
    criarJanela() // não deu para abrir o Edge: usa a janela do Electron
  })
  processo.on('exit', () => {
    if (!principal) return
    if (edge === processo) edge = null
    // Fechou a janela do VYBE: sem tela a música para, então desliga tudo e libera a memória.
    // (Se o Edge saiu em menos de 3 s, ele entregou a janela a outro processo: não dá para
    // acompanhar, e o VYBE fica na bandeja para ser fechado por lá.)
    if (!saindo && Date.now() - abertoEm > 3000) app.quit()
  })
}

function mostrarJanela() {
  if (modoEdge) {
    abrirNoEdge() // abre (ou traz) a janela do VYBE no Edge
    return
  }
  if (!janela) return
  if (janela.isMinimized()) janela.restore()
  janela.show()
  janela.focus()
}

function criarBandeja() {
  const imagem = nativeImage.createFromPath(icone).resize({ width: 16, height: 16 })
  bandeja = new Tray(imagem)
  bandeja.setToolTip('VYBE')
  bandeja.setContextMenu(
    Menu.buildFromTemplate([
      { label: 'Abrir o VYBE', click: mostrarJanela },
      { type: 'separator' },
      { label: 'Sair', click: () => app.quit() },
    ]),
  )
  bandeja.on('click', mostrarJanela)
}

// ---------- Ciclo de vida ----------

app.on('second-instance', mostrarJanela)

app.whenReady().then(async () => {
  app.setAppUserModelId('com.cauan.vybe') // nome e ícone certos nas notificações do Windows
  fs.mkdirSync(pastaDados, { recursive: true })
  criarAbertura()

  if (await portaOcupada()) {
    falhar(`A porta ${PORTA} já está sendo usada por outro programa. Feche-o e abra o VYBE de novo.`)
    return
  }

  ligarApi()
  if (!(await esperarApi())) {
    if (api) falhar('O VYBE demorou demais para iniciar.')
    return
  }
  criarBandeja()
  modoEdge = Boolean(encontrarEdge())
  if (modoEdge) {
    abrirNoEdge()
    setTimeout(() => {
      abertura?.close()
      abertura = null
    }, 1500) // dá tempo da janela do Edge aparecer
  } else {
    criarJanela()
  }
})

app.on('before-quit', (evento) => {
  saindo = true
  if (edge) {
    edge.kill() // fecha a janela do Edge junto
    edge = null
  }
  if (!api) return // já desligou: pode sair
  evento.preventDefault()
  desligarApi().then(() => app.quit())
})

// Com a janela escondida na bandeja, o programa continua aberto.
app.on('window-all-closed', () => {})
