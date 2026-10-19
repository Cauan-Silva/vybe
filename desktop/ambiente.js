/*
 * Luz ambiente em tempo real (só no programa).
 *
 * O vídeo da aba Vídeos vem num player do YouTube, e o navegador não deixa a página ler os
 * quadros dele. O programa, porém, controla a janela: de tempos em tempos ele pergunta à
 * página onde está o vídeo e se ele está tocando, recebe uma imagem pequena da janela, recorta essa área,
 * calcula a cor de cada borda (topo, base, esquerda, direita) e devolve as cores à página,
 * que pinta o brilho em volta do player.
 *
 * Com o Edge, a conversa usa o protocolo de depuração do Chromium por um canal privado
 * (--remote-debugging-pipe): só o próprio programa enxerga, nenhuma porta é aberta.
 * Com a janela do Electron (sem Edge), usa capturePage, que faz o mesmo.
 */

const LARGURA_AMOSTRA = 64 // a foto é reduzida a 64 px de largura: basta para as cores
const INTERVALO_ATIVO_MS = 125 // ~8 vezes por segundo enquanto o vídeo toca
const INTERVALO_PARADO_MS = 700 // só confere de vez em quando se voltou a tocar
const FAIXA = 0.18 // cada borda usa 18% da imagem

// Funções que a página do VYBE expõe (veja components/Videos.jsx)
const PERGUNTA = 'window.__vybeAmbiente ? JSON.stringify(window.__vybeAmbiente()) : null'
const RESPOSTA = (cores) => `window.__vybeLuz && window.__vybeLuz(${JSON.stringify(cores)})`

/**
 * Cores médias das quatro bordas de uma imagem BGRA (formato do toBitmap no Windows).
 * Devolve { topo, base, esquerda, direita } como [r, g, b] e o brilho geral (0 a 1).
 */
function coresDasBordas(pixels, largura, altura, ordem = 'bgra') {
  const [iR, iG, iB] = ordem === 'rgba' ? [0, 1, 2] : [2, 1, 0]
  const faixaX = Math.max(1, Math.round(largura * FAIXA))
  const faixaY = Math.max(1, Math.round(altura * FAIXA))

  const media = (x0, y0, x1, y1) => {
    let r = 0
    let g = 0
    let b = 0
    let n = 0
    for (let y = y0; y < y1; y++) {
      for (let x = x0; x < x1; x++) {
        const i = (y * largura + x) * 4
        r += pixels[i + iR]
        g += pixels[i + iG]
        b += pixels[i + iB]
        n++
      }
    }
    return n ? realcar([r / n, g / n, b / n]) : [0, 0, 0]
  }

  const cores = {
    topo: media(0, 0, largura, faixaY),
    base: media(0, altura - faixaY, largura, altura),
    esquerda: media(0, 0, faixaX, altura),
    direita: media(largura - faixaX, 0, largura, altura),
  }
  const todas = Object.values(cores)
  const brilho = todas.reduce((soma, [r, g, b]) => soma + (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255, 0) / 4
  return { ...cores, brilho: Number(brilho.toFixed(3)) }
}

/** Média de cores tende ao cinza: um pouco mais de saturação deixa a luz viva. */
function realcar([r, g, b], fator = 1.35) {
  const cinza = (r + g + b) / 3
  const ajustar = (v) => Math.round(Math.max(0, Math.min(255, cinza + (v - cinza) * fator)))
  return [ajustar(r), ajustar(g), ajustar(b)]
}

/**
 * Cliente mínimo do protocolo de depuração (CDP) pelo canal privado do Edge.
 * Mensagens em JSON, separadas por um caractere nulo.
 */
class CanalEdge {
  constructor(escrita, leitura) {
    this.escrita = escrita
    this.proximoId = 1
    this.pendentes = new Map()
    this.ouvintes = []
    this.sobra = ''
    leitura.on('data', (pedaco) => this.receber(pedaco))
    leitura.on('error', () => {})
    escrita.on('error', () => {})
  }

  receber(pedaco) {
    this.sobra += pedaco.toString('utf8')
    let fim
    while ((fim = this.sobra.indexOf('\0')) >= 0) {
      const texto = this.sobra.slice(0, fim)
      this.sobra = this.sobra.slice(fim + 1)
      let mensagem
      try {
        mensagem = JSON.parse(texto)
      } catch {
        continue
      }
      if (mensagem.id && this.pendentes.has(mensagem.id)) {
        const { resolve, reject } = this.pendentes.get(mensagem.id)
        this.pendentes.delete(mensagem.id)
        if (mensagem.error) reject(new Error(mensagem.error.message))
        else resolve(mensagem.result)
      } else if (mensagem.method) {
        this.ouvintes.forEach((f) => f(mensagem))
      }
    }
  }

  enviar(method, params = {}, sessionId) {
    const id = this.proximoId++
    const mensagem = { id, method, params }
    if (sessionId) mensagem.sessionId = sessionId
    return new Promise((resolve, reject) => {
      this.pendentes.set(id, { resolve, reject })
      this.escrita.write(JSON.stringify(mensagem) + '\0')
      setTimeout(() => {
        if (this.pendentes.delete(id)) reject(new Error(`Sem resposta: ${method}`))
      }, 5000)
    })
  }

  aoEvento(funcao) {
    this.ouvintes.push(funcao)
  }
}

/**
 * Liga o canal com a janela do Edge: luz ambiente e a ponte de mensagens com a página
 * (Discord, atualizações). "endereco" é o do VYBE; "decodificar" transforma o JPEG em pixels;
 * "aoMensagem" recebe o que a página manda. Devolve { desligar, paraPagina }.
 *
 * Ponte: a página chama window.vybeDesktopEnviar(json), uma função criada pelo próprio Edge
 * a pedido do programa (Runtime.addBinding); o programa responde chamando
 * window.__vybeDoPrograma(mensagem) na página.
 */
function ligarComEdge({ escrita, leitura, endereco, decodificar, registrar = () => {}, aoMensagem = () => {} }) {
  const canal = new CanalEdge(escrita, leitura)
  let sessao = null
  let ativo = true
  let anexando = false
  const pendentes = new Map() // última mensagem de cada tipo, guardada até a janela conectar

  const entregar = (mensagem) =>
    canal
      .enviar('Runtime.evaluate', {
        expression: `window.__vybeDoPrograma && window.__vybeDoPrograma(${JSON.stringify(mensagem)})`,
      }, sessao)
      .catch(() => {})

  const paraPagina = (mensagem) => {
    if (!sessao) {
      pendentes.set(mensagem.tipo, mensagem)
      return
    }
    entregar(mensagem)
  }

  const prepararPonte = async () => {
    try {
      await canal.enviar('Runtime.enable', {}, sessao)
      await canal.enviar('Runtime.addBinding', { name: 'vybeDesktopEnviar' }, sessao)
      // A página pode ter carregado antes da ponte existir: pede que mande tudo de novo.
      await canal.enviar('Runtime.evaluate', { expression: 'window.__vybeReenviar && window.__vybeReenviar()' }, sessao)
      for (const m of pendentes.values()) entregar(m)
      pendentes.clear()
      registrar('ponte: pronta')
    } catch (e) {
      registrar(`ponte: não ficou pronta (${e.message})`)
    }
  }

  const anexar = async (alvo) => {
    if (sessao || anexando || alvo.type !== 'page' || !alvo.url.startsWith(endereco)) return
    anexando = true
    try {
      const r = await canal.enviar('Target.attachToTarget', { targetId: alvo.targetId, flatten: true })
      sessao = r.sessionId
      registrar('luz ambiente: conectada à janela')
      // versões antigas deixavam a janela "encolhida"; garante o tamanho normal
      canal.enviar('Emulation.clearDeviceMetricsOverride', {}, sessao).catch(() => {})
      prepararPonte()
    } catch (e) {
      registrar(`luz ambiente: não conectou (${e.message})`)
    } finally {
      anexando = false
    }
  }

  canal.aoEvento((m) => {
    if (m.method === 'Target.targetCreated' || m.method === 'Target.targetInfoChanged') anexar(m.params.targetInfo)
    if (m.method === 'Runtime.bindingCalled' && m.sessionId === sessao && m.params.name === 'vybeDesktopEnviar') {
      try {
        aoMensagem(JSON.parse(m.params.payload))
      } catch {
        // mensagem malformada: ignora
      }
    }
    if (m.method === 'Target.detachedFromTarget' && m.params.sessionId === sessao) {
      sessao = null
      registrar('luz ambiente: janela desconectada')
    }
  })
  canal
    .enviar('Target.setDiscoverTargets', { discover: true })
    .then(() => registrar('luz ambiente: canal com o Edge aberto'))
    .catch((e) => registrar(`luz ambiente: o Edge não respondeu no canal (${e.message})`))

  // Além dos avisos do Edge, procura a janela do VYBE de tempos em tempos até achar.
  const procurar = async () => {
    if (!ativo || sessao) return
    try {
      const { targetInfos } = await canal.enviar('Target.getTargets')
      const paginas = targetInfos.filter((t) => t.type === 'page')
      if (!paginas.some((t) => t.url.startsWith(endereco))) {
        registrar(`luz ambiente: janelas vistas: ${paginas.map((t) => t.url).join(', ') || 'nenhuma'}`)
      }
      for (const alvo of paginas) await anexar(alvo)
    } catch (e) {
      registrar(`luz ambiente: erro ao procurar a janela (${e.message})`)
    }
  }
  const relogioProcura = setInterval(() => (sessao || !ativo ? null : procurar()), 3000)
  setTimeout(procurar, 1500)

  // Mostra no log os primeiros erros do ciclo (sem encher o arquivo).
  let errosMostrados = 0
  let coresEnviadas = 0
  const erro = (texto) => {
    if (errosMostrados++ < 5) registrar(`luz ambiente: ${texto}`)
  }

  const avaliar = async (expressao) => {
    const r = await canal.enviar('Runtime.evaluate', { expression: expressao, returnByValue: true }, sessao)
    return r?.result?.value
  }

  /*
   * Em vez de "tirar fotos" (Page.captureScreenshot com escala, que redimensiona a janela por
   * um instante e faz a tela piscar), o Edge manda um fluxo contínuo e pequeno da janela
   * (Page.startScreencast), que não mexe em nada na tela. Daqui recortamos só o vídeo.
   * O Edge só manda o próximo quadro depois da confirmação (ack); confirmar com atraso
   * limita o ritmo a ~8 quadros por segundo.
   */
  let transmitindo = false
  let sessaoDaTransmissao = null
  let ultimaArea = null

  const iniciarTransmissao = async () => {
    if (transmitindo) return
    transmitindo = true
    sessaoDaTransmissao = sessao
    await canal.enviar(
      'Page.startScreencast',
      { format: 'jpeg', quality: 50, maxWidth: 480, maxHeight: 300, everyNthFrame: 1 },
      sessao,
    )
  }

  const pararTransmissao = async () => {
    if (!transmitindo) return
    transmitindo = false
    const s = sessaoDaTransmissao
    sessaoDaTransmissao = null
    if (s && s === sessao) await canal.enviar('Page.stopScreencast', {}, s).catch(() => {})
  }

  canal.aoEvento(async (m) => {
    if (m.method !== 'Page.screencastFrame' || m.sessionId !== sessao) return
    const { data, metadata, sessionId: idQuadro } = m.params
    const confirmar = () =>
      canal.enviar('Page.screencastFrameAck', { sessionId: idQuadro }, sessao).catch(() => {})
    try {
      const area = ultimaArea
      if (transmitindo && area && metadata?.deviceWidth > 0 && metadata?.deviceHeight > 0) {
        // a imagem vem reduzida: a área do vídeo vai em proporção da janela (0 a 1)
        const topo = metadata.offsetTop || 0
        const recortada = decodificar(Buffer.from(data, 'base64'), {
          x: area.x / metadata.deviceWidth,
          y: (area.y + topo) / metadata.deviceHeight,
          w: area.w / metadata.deviceWidth,
          h: area.h / metadata.deviceHeight,
        })
        if (recortada) {
          const cores = coresDasBordas(recortada.pixels, recortada.largura, recortada.altura, recortada.ordem)
          await avaliar(RESPOSTA(cores))
          if (coresEnviadas++ === 0) registrar(`luz ambiente: primeiras cores enviadas ${JSON.stringify(cores)}`)
        } else {
          erro('o quadro do vídeo veio vazio')
        }
      }
    } catch (e) {
      erro(`erro no quadro (${e.message})`)
    }
    setTimeout(confirmar, INTERVALO_ATIVO_MS)
  })

  // Pergunta à página de tempos em tempos se o vídeo está tocando e onde ele está.
  const ciclo = async () => {
    if (!ativo) return
    let espera = INTERVALO_PARADO_MS
    try {
      if (!sessao) {
        transmitindo = false
        ultimaArea = null
      } else {
        const texto = await avaliar(PERGUNTA)
        const area = texto ? JSON.parse(texto) : null
        if (area?.ativo && area.w > 10 && area.h > 10) {
          ultimaArea = area
          await iniciarTransmissao()
          espera = 250 // acompanha rolagem e mudança de tamanho da janela
        } else {
          ultimaArea = null
          await pararTransmissao()
        }
      }
    } catch (e) {
      transmitindo = false
      erro(`erro no ciclo (${e.message})`) // página trocando, janela fechando... tenta de novo
    }
    if (ativo) setTimeout(ciclo, espera)
  }
  setTimeout(ciclo, 1000)

  const desligar = () => {
    ativo = false
    clearInterval(relogioProcura)
    pararTransmissao().catch(() => {})
  }
  return { desligar, paraPagina }
}

/**
 * Mesma coisa com a janela do Electron (quando não há Edge). Aqui a página fala com o
 * programa pelo preload (ipc), e o programa responde com executeJavaScript.
 */
function ligarComElectron({ webContents, registrar = () => {} }) {
  let ativo = true
  const ciclo = async () => {
    if (!ativo || webContents.isDestroyed()) return
    let espera = INTERVALO_PARADO_MS
    try {
      const texto = await webContents.executeJavaScript(PERGUNTA, true)
      const area = texto ? JSON.parse(texto) : null
      if (area?.ativo && area.w > 10 && area.h > 10) {
        const imagem = await webContents.capturePage({
          x: Math.round(area.x),
          y: Math.round(area.y),
          width: Math.round(area.w),
          height: Math.round(area.h),
        })
        const pequena = imagem.resize({ width: LARGURA_AMOSTRA })
        const { width, height } = pequena.getSize()
        const cores = coresDasBordas(pequena.toBitmap(), width, height, 'bgra')
        await webContents.executeJavaScript(RESPOSTA(cores), true)
        espera = INTERVALO_ATIVO_MS
      }
    } catch {
      // idem
    }
    if (ativo) setTimeout(ciclo, espera)
  }
  registrar('luz ambiente: usando a janela do Electron')
  setTimeout(ciclo, 1000)
  const paraPagina = (mensagem) => {
    if (webContents.isDestroyed()) return
    webContents
      .executeJavaScript(`window.__vybeDoPrograma && window.__vybeDoPrograma(${JSON.stringify(mensagem)})`, true)
      .catch(() => {})
  }
  const desligar = () => {
    ativo = false
  }
  return { desligar, paraPagina }
}

module.exports = { coresDasBordas, realcar, CanalEdge, ligarComEdge, ligarComElectron }
