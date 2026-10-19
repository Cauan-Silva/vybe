/*
 * Discord Rich Presence: mostra no perfil do Discord a música que está tocando no VYBE.
 *
 * O Discord aberto no computador escuta num canal local (no Windows, o "named pipe"
 * \\?\pipe\discord-ipc-0, até o 9). A conversa é simples: cada mensagem tem 8 bytes de
 * cabeçalho (tipo e tamanho, em little-endian) seguidos de um JSON. Primeiro nos
 * apresentamos com o ID do aplicativo criado no portal de desenvolvedores do Discord;
 * depois mandamos SET_ACTIVITY a cada troca de música. Nada sai do computador por aqui:
 * quem fala com os servidores é o próprio Discord.
 *
 * Sem Discord aberto, tenta de novo de tempos em tempos, sem incomodar.
 */
const net = require('node:net')
const path = require('node:path')
const crypto = require('node:crypto')

const OP_APRESENTACAO = 0
const OP_MENSAGEM = 1
const OP_FECHAR = 2
const OP_PING = 3
const OP_PONG = 4

const TENTAR_DE_NOVO_MS = 15_000
// O Discord aceita poucas atualizações por vez (cerca de 5 a cada 20 s): juntamos as rápidas.
const INTERVALO_MINIMO_MS = 4_000
const TIPO_OUVINDO = 2

function caminhosDoCanal() {
  if (process.env.VYBE_DISCORD_IPC) return [process.env.VYBE_DISCORD_IPC] // testes
  const lista = []
  for (let i = 0; i < 10; i++) {
    if (process.platform === 'win32') {
      lista.push(`\\\\?\\pipe\\discord-ipc-${i}`)
    } else {
      const base = process.env.XDG_RUNTIME_DIR || process.env.TMPDIR || '/tmp'
      lista.push(path.join(base, `discord-ipc-${i}`))
    }
  }
  return lista
}

function quadro(op, dados) {
  const json = Buffer.from(JSON.stringify(dados), 'utf8')
  const cabecalho = Buffer.alloc(8)
  cabecalho.writeInt32LE(op, 0)
  cabecalho.writeInt32LE(json.length, 4)
  return Buffer.concat([cabecalho, json])
}

/** O Discord exige textos de 2 a 128 caracteres. */
function texto(valor, reserva) {
  let t = String(valor ?? '').trim()
  if (!t) t = reserva
  if (t.length < 2) t = `${t} `
  return t.length > 128 ? `${t.slice(0, 127)}…` : t
}

/** Monta a atividade do Discord a partir do que o VYBE está tocando (null = limpar). */
function montarAtividade(agora, relogio = Date.now) {
  if (!agora || !agora.tocando || !agora.titulo) return null
  const atividade = {
    type: TIPO_OUVINDO,
    details: texto(agora.titulo, 'Música'),
    state: texto(agora.artista, 'Artista desconhecido'),
    instance: false,
    assets: { large_text: texto(agora.album || 'VYBE', 'VYBE') },
  }
  // Capa: o Discord aceita endereço https público (até 256 caracteres)
  if (typeof agora.capaUrl === 'string' && /^https:\/\//.test(agora.capaUrl) && agora.capaUrl.length <= 256) {
    atividade.assets.large_image = agora.capaUrl
  }
  const duracao = Number(agora.duracao) || 0
  const posicao = Math.max(0, Number(agora.posicao) || 0)
  if (duracao > 0) {
    const inicio = relogio() - Math.round(posicao * 1000)
    atividade.timestamps = { start: inicio, end: inicio + Math.round(duracao * 1000) }
  }
  return atividade
}

class PresencaDiscord {
  /**
   * aoEstado(estado, detalhe): 'desligado' | 'procurando' | 'conectado' | 'erro'.
   */
  constructor({ registrar = () => {}, aoEstado = () => {} } = {}) {
    this.registrar = registrar
    this.aoEstado = aoEstado
    this.ativo = false
    this.clientId = ''
    this.soquete = null
    this.conectado = false
    this.sobra = Buffer.alloc(0)
    this.agora = null
    this.ultimoEnvio = 0
    this.envioAgendado = null
    this.reconexao = null
    this.estado = 'desligado'
  }

  mudarEstado(estado, detalhe) {
    if (this.estado === estado && !detalhe) return
    this.estado = estado
    this.aoEstado(estado, detalhe)
  }

  /** Liga/desliga e troca o ID do aplicativo. */
  configurar({ ativo, clientId }) {
    const id = /^\d{15,25}$/.test(String(clientId ?? '')) ? String(clientId) : ''
    const mudou = Boolean(ativo) !== this.ativo || id !== this.clientId
    this.ativo = Boolean(ativo)
    this.clientId = id
    if (!mudou) {
      this.aoEstado(this.estado) // devolve o estado atual para a tela
      return
    }
    this.desconectar()
    if (!this.ativo) {
      this.mudarEstado('desligado')
    } else if (!this.clientId) {
      this.mudarEstado('erro', 'Informe o ID do aplicativo do Discord.')
    } else {
      this.conectar()
    }
  }

  /** O VYBE avisou o que está tocando agora. */
  atualizar(agora) {
    this.agora = agora
    this.agendarEnvio()
  }

  conectar(indice = 0) {
    clearTimeout(this.reconexao)
    if (!this.ativo || !this.clientId) return
    const caminhos = caminhosDoCanal()
    if (indice >= caminhos.length) {
      this.mudarEstado('procurando', 'Discord não encontrado. Ele está aberto?')
      this.reconexao = setTimeout(() => this.conectar(), TENTAR_DE_NOVO_MS)
      return
    }
    if (indice === 0 && this.estado !== 'procurando') this.mudarEstado('procurando')
    const soquete = net.createConnection(caminhos[indice])
    let abriu = false
    soquete.once('connect', () => {
      abriu = true
      this.soquete = soquete
      this.sobra = Buffer.alloc(0)
      soquete.write(quadro(OP_APRESENTACAO, { v: 1, client_id: this.clientId }))
    })
    soquete.on('data', (pedaco) => this.receber(pedaco))
    soquete.on('error', () => {
      if (!abriu) this.conectar(indice + 1) // esse canal não existe: tenta o próximo
    })
    soquete.on('close', () => {
      if (!abriu || this.soquete !== soquete) return
      this.soquete = null
      this.conectado = false
      if (this.ativo) {
        this.mudarEstado('procurando', 'O Discord foi fechado. Tentando de novo…')
        this.reconexao = setTimeout(() => this.conectar(), TENTAR_DE_NOVO_MS)
      }
    })
  }

  desconectar() {
    clearTimeout(this.reconexao)
    clearTimeout(this.envioAgendado)
    this.envioAgendado = null
    this.conectado = false
    const s = this.soquete
    this.soquete = null
    if (s) {
      try {
        s.write(quadro(OP_MENSAGEM, this.mensagemAtividade(null)))
      } catch {
        // canal já fechado
      }
      s.end()
      s.destroy()
    }
  }

  receber(pedaco) {
    this.sobra = Buffer.concat([this.sobra, pedaco])
    while (this.sobra.length >= 8) {
      const op = this.sobra.readInt32LE(0)
      const tamanho = this.sobra.readInt32LE(4)
      if (this.sobra.length < 8 + tamanho) return
      let dados = null
      try {
        dados = JSON.parse(this.sobra.subarray(8, 8 + tamanho).toString('utf8'))
      } catch {
        dados = null
      }
      this.sobra = this.sobra.subarray(8 + tamanho)
      this.tratar(op, dados)
    }
  }

  tratar(op, dados) {
    if (op === OP_PING) {
      this.soquete?.write(quadro(OP_PONG, dados ?? {}))
    } else if (op === OP_FECHAR) {
      // ID errado ou aplicativo apagado: não adianta insistir
      const motivo = dados?.message || 'O Discord recusou a conexão.'
      this.registrar(`discord: fechou a conexão (${dados?.code ?? '?'}: ${motivo})`)
      this.ativo = false
      this.desconectar()
      this.mudarEstado('erro', `O Discord recusou: ${motivo}. Confira o ID do aplicativo.`)
    } else if (op === OP_MENSAGEM && dados) {
      if (dados.evt === 'READY') {
        this.conectado = true
        this.registrar(`discord: conectado como ${dados.data?.user?.username ?? '?'}`)
        this.mudarEstado('conectado', dados.data?.user?.username)
        this.ultimoEnvio = 0
        if (this.agora) this.agendarEnvio() // ainda não sabe o que toca: espera o primeiro aviso
      } else if (dados.evt === 'ERROR') {
        this.registrar(`discord: erro ${dados.data?.code}: ${dados.data?.message}`)
      }
    }
  }

  mensagemAtividade(atividade) {
    return { cmd: 'SET_ACTIVITY', args: { pid: process.pid, activity: atividade }, nonce: crypto.randomUUID() }
  }

  agendarEnvio() {
    if (!this.conectado || this.envioAgendado) return
    const espera = Math.max(0, this.ultimoEnvio + INTERVALO_MINIMO_MS - Date.now())
    this.envioAgendado = setTimeout(() => {
      this.envioAgendado = null
      if (!this.conectado || !this.soquete) return
      this.ultimoEnvio = Date.now()
      this.soquete.write(quadro(OP_MENSAGEM, this.mensagemAtividade(montarAtividade(this.agora))))
    }, espera)
  }

  encerrar() {
    this.ativo = false
    this.desconectar()
  }
}

module.exports = { PresencaDiscord, montarAtividade, quadro }
