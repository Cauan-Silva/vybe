/*
 * VYBE para Windows (Electron).
 *
 * Ao abrir: mostra a tela de abertura, liga a API Java (com o Java e o PostgreSQL que vêm
 * dentro do instalador), espera ela responder e abre a janela com as telas do VYBE, que a
 * própria API entrega em http://127.0.0.1:47823.
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

  api = spawn(java, ['-Xmx512m', '-jar', path.join(recursos, 'vybe-api.jar'), '--spring.profiles.active=desktop'], {
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

function mostrarJanela() {
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
  criarJanela()
})

app.on('before-quit', (evento) => {
  if (!api) return // já desligou: pode sair
  evento.preventDefault()
  saindo = true
  desligarApi().then(() => app.quit())
})

// Com a janela escondida na bandeja, o programa continua aberto.
app.on('window-all-closed', () => {})
