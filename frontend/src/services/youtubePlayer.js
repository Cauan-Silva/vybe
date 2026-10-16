/*
 * Motor do YouTube para o player do VYBE.
 *
 * Usa o player oficial do YouTube (IFrame Player API). As regras do YouTube pedem que o
 * vídeo fique visível enquanto toca (no mínimo 200×200 px) e proíbem tocar só o áudio,
 * então o vídeo aparece numa janelinha no canto da tela, que pode ser aumentada.
 * Ela some quando a música seguinte não é do YouTube.
 */

const API_URL = 'https://www.youtube.com/iframe_api'

let apiPromessa = null

/** Carrega o script da API do YouTube uma vez só. */
function carregarApi() {
  if (window.YT?.Player) return Promise.resolve()
  if (!apiPromessa) {
    apiPromessa = new Promise((resolve, reject) => {
      const anterior = window.onYouTubeIframeAPIReady
      window.onYouTubeIframeAPIReady = () => {
        anterior?.()
        resolve()
      }
      const script = document.createElement('script')
      script.src = API_URL
      script.async = true
      script.onerror = () => {
        apiPromessa = null
        script.remove()
        reject(new Error('Não foi possível carregar o player do YouTube. Confira sua internet.'))
      }
      document.head.appendChild(script)
    })
  }
  return apiPromessa
}

const ERROS = {
  2: 'Este vídeo do YouTube tem um endereço inválido.',
  5: 'O navegador não conseguiu tocar este vídeo.',
  100: 'Este vídeo foi removido ou ficou privado.',
  101: 'O dono deste vídeo não permite tocar fora do YouTube.',
  150: 'O dono deste vídeo não permite tocar fora do YouTube.',
}

// Estados do player (YT.PlayerState)
const TERMINOU = 0
const TOCANDO = 1
const PAUSADO = 2

/**
 * Cria o motor. Os callbacks avisam o Player:
 * - aoMudar({ tocando, posicao, duracao }): estado novo, em segundos
 * - aoTerminar(): o vídeo chegou ao fim
 * - aoErro(mensagem)
 */
export function criarMotorYoutube({ aoMudar, aoTerminar, aoErro }) {
  let caixa = null // janelinha fixa com o vídeo
  let player = null
  let pronto = null // Promise que resolve quando o player está pronto
  let idAtual = null // vídeo que o VYBE mandou tocar (null = motor parado)
  let carregado = null // vídeo que está de fato no player

  function criarCaixa() {
    caixa = document.createElement('div')
    caixa.className = 'video-youtube video-youtube--oculto'
    caixa.setAttribute('role', 'region')
    caixa.setAttribute('aria-label', 'Vídeo do YouTube')

    const botao = document.createElement('button')
    botao.type = 'button'
    botao.className = 'video-youtube__tamanho'
    botao.textContent = 'Aumentar'
    botao.addEventListener('click', () => {
      const grande = caixa.classList.toggle('video-youtube--grande')
      botao.textContent = grande ? 'Diminuir' : 'Aumentar'
    })

    const alvo = document.createElement('div')
    caixa.append(alvo, botao)
    document.body.appendChild(caixa)
    return alvo
  }

  function estadoAtual() {
    return {
      posicao: player?.getCurrentTime?.() ?? 0,
      duracao: player?.getDuration?.() ?? 0,
    }
  }

  function aoEstado(evento) {
    if (!idAtual) return
    if (evento.data === TOCANDO) aoMudar({ tocando: true, ...estadoAtual() })
    else if (evento.data === PAUSADO) aoMudar({ tocando: false, ...estadoAtual() })
    else if (evento.data === TERMINOU) aoTerminar()
  }

  function aplicarVolume(volume) {
    if (!player?.setVolume) return
    if (volume <= 0) {
      player.mute()
    } else {
      player.unMute()
      player.setVolume(Math.round(volume * 100))
    }
  }

  /** Cria o player já com o primeiro vídeo. Chamadas seguintes devolvem a mesma espera. */
  function iniciar(videoId, volume) {
    if (pronto) return pronto
    carregado = videoId
    pronto = carregarApi().then(
      () =>
        new Promise((resolve) => {
          const alvo = criarCaixa()
          player = new window.YT.Player(alvo, {
            width: '100%',
            height: '100%',
            videoId,
            playerVars: { autoplay: 1, playsinline: 1, rel: 0 },
            events: {
              onReady: () => {
                aplicarVolume(volume)
                resolve()
              },
              onStateChange: aoEstado,
              onError: (e) => {
                if (idAtual) aoErro(ERROS[e.data] ?? 'O YouTube não conseguiu tocar este vídeo.')
              },
            },
          })
        }),
    )
    pronto.catch(() => {
      pronto = null
      carregado = null
    })
    return pronto
  }

  return {
    async tocar(videoId, volume) {
      idAtual = videoId
      caixa?.classList.remove('video-youtube--oculto')
      const primeiraVez = !pronto
      await iniciar(videoId, volume)
      caixa.classList.remove('video-youtube--oculto')
      if (idAtual !== videoId) return // a pessoa já pulou para outra
      if (!primeiraVez || carregado !== videoId) {
        player.loadVideoById(videoId)
        carregado = videoId
      }
    },
    /** Para e esconde o vídeo (quando a próxima música não é do YouTube). */
    parar() {
      if (!idAtual) return
      idAtual = null
      player?.pauseVideo?.()
      caixa?.classList.add('video-youtube--oculto')
    },
    alternar() {
      if (!player?.getPlayerState) return
      if (player.getPlayerState() === TOCANDO) player.pauseVideo()
      else player.playVideo()
    },
    buscar(segundos) {
      player?.seekTo?.(segundos, true)
    },
    volume(v) {
      aplicarVolume(v)
    },
    posicao() {
      return estadoAtual().posicao
    },
    duracao() {
      return estadoAtual().duracao
    },
  }
}
