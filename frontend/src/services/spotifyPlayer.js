/*
 * Motor do Spotify para o player do VYBE.
 *
 * Usa o Web Playback SDK (o player oficial do Spotify para sites): o navegador vira um
 * dispositivo "VYBE" na conta da pessoa, como uma caixa de som ou o celular. O áudio sai
 * direto do Spotify; o VYBE só manda tocar, pausar, pular e mudar o volume.
 *
 * Só funciona com conta Premium e no navegador do computador.
 */

const SDK_URL = 'https://sdk.scdn.co/spotify-player.js'
const API = 'https://api.spotify.com/v1'

let sdkPromessa = null

/** Carrega o script do SDK uma vez só. */
function carregarSdk() {
  if (window.Spotify?.Player) return Promise.resolve()
  if (!sdkPromessa) {
    sdkPromessa = new Promise((resolve, reject) => {
      window.onSpotifyWebPlaybackSDKReady = () => resolve()
      const script = document.createElement('script')
      script.src = SDK_URL
      script.async = true
      script.onerror = () => {
        sdkPromessa = null
        script.remove()
        reject(new Error('Não foi possível carregar o player do Spotify. Confira sua internet.'))
      }
      document.body.appendChild(script)
    })
  }
  return sdkPromessa
}

const MENSAGENS = {
  initialization_error: 'Este navegador não consegue tocar músicas do Spotify. Tente o Chrome, o Edge ou o Firefox no computador.',
  authentication_error: 'A conexão com o Spotify expirou. Conecte de novo na página Spotify.',
  account_error: 'Para tocar músicas do Spotify é preciso uma conta Premium.',
  playback_error: 'O Spotify não conseguiu tocar esta música.',
}

/**
 * Cria o motor. Os callbacks avisam o Player:
 * - obterToken(): Promise com o access token do Spotify (vem da API do VYBE)
 * - aoMudar({ tocando, posicao, duracao }): estado novo, em segundos
 * - aoTerminar(): a música chegou ao fim
 * - aoErro(mensagem)
 */
export function criarMotorSpotify({ obterToken, aoMudar, aoTerminar, aoErro }) {
  let player = null
  let deviceId = null
  let pronto = null // Promise que resolve quando o dispositivo está pronto
  let uriAtual = null // música que o VYBE mandou tocar (null = motor parado)
  let ultimo = null // último estado: { posicao, duracao, pausado, em } (ms)
  let terminou = false

  function desligar() {
    try {
      player?.disconnect()
    } catch {
      // já desconectado
    }
    player = null
    deviceId = null
    pronto = null
  }

  function ehAtual(faixa) {
    return Boolean(faixa) && (faixa.uri === uriAtual || faixa.linked_from?.uri === uriAtual)
  }

  /** Posição estimada agora, em ms (o SDK só avisa quando algo muda). */
  function estimar(estado) {
    if (!estado) return 0
    const posicao = estado.pausado ? estado.posicao : estado.posicao + (performance.now() - estado.em)
    return Math.min(posicao, estado.duracao || posicao)
  }

  function aoEstado(estado) {
    if (!estado || !uriAtual) return
    const janela = estado.track_window ?? {}
    const faixa = janela.current_track

    if (!terminou) {
      // Fim da música: ou o Spotify passou para outra (reprodução automática dele),
      // ou ela parou na posição 0 logo depois de chegar perto do fim.
      const trocou = !ehAtual(faixa) && (janela.previous_tracks ?? []).some(ehAtual)
      const parouNoFim =
        ehAtual(faixa) && estado.paused && estado.position === 0 && ultimo && ultimo.duracao > 0 &&
        estimar(ultimo) >= ultimo.duracao - 3000
      if (trocou || parouNoFim) {
        terminou = true
        if (ultimo) ultimo = { ...ultimo, pausado: true }
        aoTerminar()
        return
      }
    }

    if (!ehAtual(faixa)) return
    ultimo = { posicao: estado.position, duracao: estado.duration, pausado: estado.paused, em: performance.now() }
    aoMudar({ tocando: !estado.paused, posicao: estado.position / 1000, duracao: estado.duration / 1000 })
  }

  /** Liga o dispositivo "VYBE" (carrega o SDK e conecta). Pode ser chamado várias vezes. */
  function iniciar(volume = 0.8) {
    if (pronto) return pronto
    pronto = carregarSdk().then(
      () =>
        new Promise((resolve, reject) => {
          const p = new window.Spotify.Player({
            name: 'VYBE',
            volume,
            getOAuthToken: (devolver) => {
              obterToken()
                .then(devolver)
                .catch((e) => aoErro(e.message))
            },
          })
          player = p

          const falha = (tipo) => ({ message }) => {
            if (player !== p) return
            console.warn(`[VYBE] Spotify ${tipo}:`, message)
            if (tipo === 'playback_error') {
              aoErro(MENSAGENS.playback_error)
              return
            }
            aoErro(MENSAGENS[tipo])
            reject(new Error(MENSAGENS[tipo]))
            desligar()
          }
          p.addListener('initialization_error', falha('initialization_error'))
          p.addListener('authentication_error', falha('authentication_error'))
          p.addListener('account_error', falha('account_error'))
          p.addListener('playback_error', falha('playback_error'))
          p.addListener('ready', ({ device_id }) => {
            deviceId = device_id
            resolve()
          })
          p.addListener('not_ready', () => {
            deviceId = null
          })
          p.addListener('player_state_changed', aoEstado)

          p.connect().then((conectou) => {
            if (!conectou) {
              reject(new Error('Não foi possível ligar o player do Spotify.'))
              desligar()
            }
          })
        }),
    )
    pronto.catch(() => {
      // quem chamou trata o erro; aqui só evitamos "unhandled rejection"
    })
    return pronto
  }

  /** Manda o Spotify tocar uma faixa no dispositivo do VYBE. */
  async function tocar(uri, volume) {
    uriAtual = uri
    terminou = false
    ultimo = null
    // Precisa acontecer ainda dentro do clique, antes de qualquer espera:
    // é o que libera o som no navegador (regra de autoplay).
    player?.activateElement?.()

    await iniciar(volume)
    if (uriAtual !== uri) return // a pessoa já pulou para outra

    const enviar = async () => {
      const token = await obterToken()
      return fetch(`${API}/me/player/play?device_id=${encodeURIComponent(deviceId)}`, {
        method: 'PUT',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ uris: [uri] }),
      })
    }

    let resposta = await enviar()
    if (resposta.status === 404 || resposta.status === 502) {
      // O dispositivo às vezes ainda não "apareceu" para o Spotify. Uma nova tentativa resolve.
      await new Promise((r) => setTimeout(r, 1000))
      if (uriAtual !== uri) return
      resposta = await enviar()
    }
    if (resposta.ok) return
    if (resposta.status === 401) throw new Error(MENSAGENS.authentication_error)
    if (resposta.status === 403) throw new Error(MENSAGENS.account_error)
    if (resposta.status === 429) throw new Error('Muitos pedidos ao Spotify. Espere um pouco e tente de novo.')
    throw new Error(MENSAGENS.playback_error)
  }

  return {
    iniciar,
    tocar,
    /** Para de tocar e passa a ignorar o Spotify (quando a próxima música é do VYBE). */
    parar() {
      if (!uriAtual) return
      uriAtual = null
      ultimo = null
      player?.pause().catch(() => {})
    },
    alternar() {
      player?.activateElement?.()
      player?.togglePlay().catch(() => {})
    },
    pausar() {
      player?.pause().catch(() => {})
    },
    buscar(segundos) {
      if (!player) return
      player.seek(Math.round(segundos * 1000)).catch(() => {})
      if (ultimo) ultimo = { ...ultimo, posicao: segundos * 1000, em: performance.now() }
    },
    volume(v) {
      player?.setVolume(v).catch(() => {})
    },
    /** Posição atual em segundos. */
    posicao() {
      return estimar(ultimo) / 1000
    },
    desligar() {
      uriAtual = null
      ultimo = null
      desligar()
    },
  }
}
