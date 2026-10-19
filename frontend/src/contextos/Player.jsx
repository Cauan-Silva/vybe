import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api, urlArquivo } from '../services/api.js'
import { criarMotorSpotify } from '../services/spotifyPlayer.js'
import { criarMotorYoutube } from '../services/youtubePlayer.js'
import { criarRegistroDeTempo } from '../utils/contadorTempo.js'
import { embaralhar } from '../utils/formato.js'
import { lerModoLeve } from '../utils/modoLeve.js'
import { aoReabrirPonte, enviarAoPrograma } from '../utils/programa.js'
import { useAuth } from './Auth.jsx'

/*
 * Um único elemento <audio> para o app inteiro, criado aqui e nunca desmontado:
 * por isso a música continua tocando quando você troca de página.
 *
 * A fila guarda a lista original (musicas), a ordem de reprodução (ordem, que é
 * embaralhada no modo aleatório) e a posição atual dentro dessa ordem (pos).
 *
 * Músicas do Spotify e do YouTube não usam o <audio>: quem toca é o player oficial de cada
 * um (services/spotifyPlayer.js e services/youtubePlayer.js). O "motor" diz qual dos três
 * está no comando; os controles (play, pausa, pular, volume, barra de tempo) são os mesmos.
 *
 * O tempo de reprodução fica num contexto separado: ele muda várias vezes por
 * segundo e só a barra do player precisa dele, então as listas não re-renderizam à toa.
 *
 * Também ficam aqui: o timer para dormir, a contagem do tempo ouvido (para o "Seu mês no
 * VYBE"), os controles do Windows (teclas de mídia e painel de mídia) e o aviso ao programa
 * do que está tocando (para o Discord).
 */

const PlayerContexto = createContext(null)
const TempoContexto = createContext(null)

const FILA_VAZIA = { musicas: [], ordem: [], pos: -1 }

/** Mistura uma lista (Fisher-Yates). */
function misturar(lista) {
  const copia = [...lista]
  for (let i = copia.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copia[i], copia[j]] = [copia[j], copia[i]]
  }
  return copia
}

/** Endereço completo (o painel do Windows e o Discord não entendem caminhos relativos). */
function enderecoCompleto(caminho) {
  const url = urlArquivo(caminho)
  if (!url) return null
  try {
    return new URL(url, window.location.href).href
  } catch {
    return null
  }
}

/*
 * Áudio silencioso em loop, tocado enquanto quem toca é o Spotify ou o YouTube (players que
 * ficam em outra "janela" dentro da página). Com ele, o Windows entende que a música é do
 * VYBE: teclas de mídia e o painel de mídia passam a controlar o VYBE e mostram capa e nome.
 */
function criarSilencio() {
  const taxa = 8000
  const segundos = 10
  const amostras = taxa * segundos
  const wav = new DataView(new ArrayBuffer(44 + amostras * 2))
  const texto = (pos, t) => [...t].forEach((c, i) => wav.setUint8(pos + i, c.charCodeAt(0)))
  texto(0, 'RIFF')
  wav.setUint32(4, 36 + amostras * 2, true)
  texto(8, 'WAVE')
  texto(12, 'fmt ')
  wav.setUint32(16, 16, true)
  wav.setUint16(20, 1, true) // PCM
  wav.setUint16(22, 1, true) // mono
  wav.setUint32(24, taxa, true)
  wav.setUint32(28, taxa * 2, true)
  wav.setUint16(32, 2, true)
  wav.setUint16(34, 16, true)
  texto(36, 'data')
  wav.setUint32(40, amostras * 2, true) // o resto já é zero: silêncio
  const audio = new Audio(URL.createObjectURL(new Blob([wav], { type: 'audio/wav' })))
  audio.loop = true
  return audio
}

function lerVolume() {
  try {
    const salvo = localStorage.getItem('vybe.volume')
    const v = Number(salvo)
    return salvo !== null && Number.isFinite(v) && v >= 0 && v <= 1 ? v : 0.8
  } catch {
    return 0.8
  }
}

export function PlayerProvider({ children }) {
  const { token } = useAuth()
  const audioRef = useRef(null)
  if (audioRef.current === null) audioRef.current = new Audio()

  const [fila, setFilaEstado] = useState(FILA_VAZIA)
  const [tocando, setTocando] = useState(false)
  const [tempo, setTempoEstado] = useState(0)

  /*
   * Economia de recursos: o tempo de reprodução muda várias vezes por segundo e cada mudança
   * redesenha a barra do player. Com a janela escondida (minimizada ou na bandeja) não há o
   * que redesenhar, então guardamos só o último valor; no modo leve, no máximo 1 vez por segundo.
   */
  const tempoRef = useRef(0)
  const ultimoDesenhoRef = useRef(0)
  const modoLeveRef = useRef(lerModoLeve())
  const setTempo = useCallback((segundos, forcar = false) => {
    tempoRef.current = segundos
    if (!forcar) {
      if (document.hidden) return
      const agora = performance.now()
      if (modoLeveRef.current && agora - ultimoDesenhoRef.current < 1000) return
      ultimoDesenhoRef.current = agora
    }
    setTempoEstado(segundos)
  }, [])

  useEffect(() => {
    const aoMudarModo = (e) => {
      modoLeveRef.current = Boolean(e.detail)
    }
    // Ao voltar a aparecer, mostra o tempo atual na hora.
    const aoMudarVisibilidade = () => {
      if (!document.hidden) setTempo(tempoRef.current, true)
    }
    window.addEventListener('vybe:modo-leve', aoMudarModo)
    document.addEventListener('visibilitychange', aoMudarVisibilidade)
    return () => {
      window.removeEventListener('vybe:modo-leve', aoMudarModo)
      document.removeEventListener('visibilitychange', aoMudarVisibilidade)
    }
  }, [setTempo])
  const [duracao, setDuracao] = useState(0)
  const [volume, setVolume] = useState(lerVolume)
  const [mudo, setMudo] = useState(false)
  const [aleatorio, setAleatorio] = useState(false)
  const [repetir, setRepetir] = useState('nao') // 'nao' | 'todas' | 'uma'
  const [erro, setErro] = useState(null)
  // Timer para dormir: null | { tipo: 'minutos', minutos, fim } | { tipo: 'fim-da-musica' }
  const [timer, setTimerEstado] = useState(null)
  const timerRef = useRef(null)
  const tocandoRef = useRef(false)
  const duracaoRef = useRef(0)
  const ouvindoRef = useRef(null) // registro do tempo ouvido da música atual
  const silencioRef = useRef(null)
  const presencaRef = useRef(() => {})

  // Refs espelham o estado para os eventos do <audio> sempre lerem o valor atual.
  const filaRef = useRef(FILA_VAZIA)
  const aleatorioRef = useRef(false)
  const repetirRef = useRef('nao')
  const tokenRef = useRef(token)
  useEffect(() => {
    tokenRef.current = token
  }, [token])
  const volumeRef = useRef(volume)
  const mudoRef = useRef(false)
  const proximaRef = useRef(() => {})
  const motorRef = useRef('audio') // 'audio' | 'spotify' | 'youtube'
  const fimSpotifyRef = useRef(false) // a última música do Spotify da fila acabou

  // Token do Spotify para o player, guardado até perto de vencer.
  const tokenSpotifyRef = useRef(null)
  const spotifyRef = useRef(null)
  if (spotifyRef.current === null) {
    spotifyRef.current = criarMotorSpotify({
      obterToken: async () => {
        const guardado = tokenSpotifyRef.current
        if (guardado && guardado.expira > Date.now() + 60_000) return guardado.valor
        if (!tokenRef.current) throw new Error('Entre no VYBE e conecte o Spotify para tocar esta música.')
        const r = await api.spotifyToken(tokenRef.current)
        tokenSpotifyRef.current = { valor: r.accessToken, expira: Date.now() + r.expiraEmSegundos * 1000 }
        return r.accessToken
      },
      aoMudar: ({ tocando: t, posicao, duracao: d }) => {
        if (motorRef.current !== 'spotify') return
        setTocando(t)
        setTempo(posicao)
        if (d > 0) setDuracao(d)
        if (t) setErro(null)
      },
      aoTerminar: () => {
        if (motorRef.current === 'spotify') proximaRef.current(true)
      },
      aoErro: (mensagem) => {
        if (motorRef.current !== 'spotify') return
        setErro(mensagem)
        setTocando(false)
      },
    })
  }
  const youtubeRef = useRef(null)
  if (youtubeRef.current === null) {
    youtubeRef.current = criarMotorYoutube({
      aoMudar: ({ tocando: t, posicao, duracao: d }) => {
        if (motorRef.current !== 'youtube') return
        setTocando(t)
        setTempo(posicao)
        if (d > 0) setDuracao(d)
        if (t) setErro(null)
      },
      aoTerminar: () => {
        if (motorRef.current === 'youtube') proximaRef.current(true)
      },
      aoErro: (mensagem) => {
        if (motorRef.current !== 'youtube') return
        setErro(mensagem)
        setTocando(false)
      },
    })
  }

  useEffect(() => {
    repetirRef.current = repetir
  }, [repetir])

  const setFila = useCallback((nova) => {
    filaRef.current = nova
    setFilaEstado(nova)
  }, [])

  const atual = fila.pos >= 0 ? fila.musicas[fila.ordem[fila.pos]] : null

  const carregar = useCallback((musica) => {
    const audio = audioRef.current
    const spotify = spotifyRef.current
    const youtube = youtubeRef.current
    const volumeAgora = mudoRef.current ? 0 : volumeRef.current
    setErro(null)
    setTempo(0, true)
    setDuracao(musica.duracaoSegundos ?? 0)
    fimSpotifyRef.current = false

    // Fecha a conta da música anterior e começa a desta.
    const anterior = ouvindoRef.current
    if (anterior) {
      anterior.contador.pausar()
      anterior.mandar()
    }
    const registro = criarRegistroDeTempo((id, segundos, manter) => {
      if (tokenRef.current) api.tempoReproducao(id, segundos, tokenRef.current, manter).catch(() => {})
    })
    ouvindoRef.current = registro
    if (tocandoRef.current) registro.contador.tocar()

    const novo = musica.origem === 'SPOTIFY' ? 'spotify' : musica.origem === 'YOUTUBE' ? 'youtube' : 'audio'
    const antigo = motorRef.current
    if (antigo === 'spotify' && novo !== 'spotify') spotify.parar()
    if (antigo === 'youtube' && novo !== 'youtube') youtube.parar()
    motorRef.current = novo // antes de pausar o <audio>, para o evento "pause" dele ser ignorado

    const falhou = (motor) => (e) => {
      if (motorRef.current !== motor) return
      setErro(e.message)
      setTocando(false)
    }

    if (novo === 'spotify') {
      audio.pause()
      setTocando(false)
      spotify.tocar(musica.audioUrl, volumeAgora).catch(falhou('spotify'))
    } else if (novo === 'youtube') {
      audio.pause()
      setTocando(false)
      youtube.tocar(musica.audioUrl.replace(/^youtube:/, ''), volumeAgora).catch(falhou('youtube'))
    } else {
      audio.src = urlArquivo(musica.audioUrl)
      audio.play().catch(() => {
        // O navegador pode bloquear o play automático; o botão continua funcionando.
      })
    }
    if (tokenRef.current) {
      api
        .registrarReproducao(musica.id, tokenRef.current)
        .then((r) => registro.definirId(r?.id ?? null))
        .catch(() => {})
    }
  }, [])

  const irPara = useCallback(
    (nova) => {
      setFila(nova)
      const musica = nova.musicas[nova.ordem[nova.pos]]
      if (musica) carregar(musica)
    },
    [setFila, carregar],
  )

  /** Toca uma lista a partir de uma música (clique numa linha ou no botão Tocar). */
  const tocarLista = useCallback(
    (musicas, indice = 0) => {
      if (!musicas.length) return
      const ordem = aleatorioRef.current ? embaralhar(musicas.length, indice) : musicas.map((_, i) => i)
      irPara({ musicas, ordem, pos: aleatorioRef.current ? 0 : indice })
    },
    [irPara],
  )

  const alternarPlay = useCallback(() => {
    const audio = audioRef.current
    const f = filaRef.current
    if (f.pos < 0) return
    if (motorRef.current === 'spotify') {
      // Depois do fim da fila, "tocar" recomeça a música em vez de continuar o que o Spotify puser.
      if (fimSpotifyRef.current) carregar(f.musicas[f.ordem[f.pos]])
      else spotifyRef.current.alternar()
      return
    }
    if (motorRef.current === 'youtube') {
      youtubeRef.current.alternar()
      return
    }
    if (audio.paused) audio.play().catch(() => {})
    else audio.pause()
  }, [carregar])

  /** Pausa o que estiver tocando (usado quando um vídeo começa na aba Vídeos). */
  const pausar = useCallback(() => {
    if (motorRef.current === 'spotify') spotifyRef.current.pausar()
    else if (motorRef.current === 'youtube') youtubeRef.current.pausar()
    else audioRef.current.pause()
  }, [])

  const buscar = useCallback((segundos) => {
    if (motorRef.current === 'spotify') spotifyRef.current.buscar(segundos)
    else if (motorRef.current === 'youtube') youtubeRef.current.buscar(segundos)
    else audioRef.current.currentTime = segundos
    setTempo(segundos, true)
    presencaRef.current() // o Discord e o painel do Windows acompanham o pulo
  }, [])

  /** Pula para frente (ou para trás, com número negativo) alguns segundos. */
  const avancar = useCallback(
    (delta) => {
      if (filaRef.current.pos < 0) return
      const total = duracaoRef.current
      const alvo = tempoRef.current + delta
      buscar(Math.max(0, total > 0 ? Math.min(alvo, total - 1) : alvo))
    },
    [buscar],
  )

  const ajustarVolume = useCallback((delta) => {
    setMudo(false)
    setVolume((v) => Math.round(Math.max(0, Math.min(1, v + delta)) * 100) / 100)
  }, [])

  /** Timer para dormir: minutos (número), 'fim-da-musica' ou null para desligar. */
  const definirTimer = useCallback((opcao) => {
    let novo = null
    if (opcao === 'fim-da-musica') novo = { tipo: 'fim-da-musica' }
    else if (Number(opcao) > 0) novo = { tipo: 'minutos', minutos: Number(opcao), fim: Date.now() + Number(opcao) * 60_000 }
    timerRef.current = novo
    setTimerEstado(novo)
  }, [])

  const proxima = useCallback(
    (automatico = false) => {
      const f = filaRef.current
      const audio = audioRef.current
      if (f.pos < 0) return
      const motor = motorRef.current
      // Timer "no fim da música": a música acabou, então para aqui.
      if (automatico && timerRef.current?.tipo === 'fim-da-musica') {
        timerRef.current = null
        setTimerEstado(null)
        if (motor === 'spotify') {
          spotifyRef.current.pausar()
          fimSpotifyRef.current = true
          setTempo(0, true)
        } else if (motor === 'audio') {
          audio.currentTime = 0
        }
        setTocando(false)
        return
      }
      if (automatico && repetirRef.current === 'uma') {
        if (motor !== 'audio') {
          irPara({ ...f })
        } else {
          audio.currentTime = 0
          audio.play().catch(() => {})
        }
      } else if (f.pos + 1 < f.ordem.length) {
        irPara({ ...f, pos: f.pos + 1 })
      } else if (repetirRef.current !== 'nao') {
        irPara({ ...f, pos: 0 })
      } else if (automatico) {
        // Fim da fila: para tudo (no Spotify, também a reprodução automática dele).
        if (motor === 'spotify') {
          spotifyRef.current.pausar()
          fimSpotifyRef.current = true
          setTocando(false)
          setTempo(0, true)
        } else if (motor === 'youtube') {
          setTocando(false) // o vídeo já parou sozinho no fim
        } else {
          audio.currentTime = 0 // volta ao começo da última música, pausada
        }
      }
    },
    [irPara],
  )

  const anterior = useCallback(() => {
    const f = filaRef.current
    if (f.pos < 0) return
    const motor = motorRef.current
    const posicao =
      motor === 'spotify'
        ? spotifyRef.current.posicao()
        : motor === 'youtube'
          ? youtubeRef.current.posicao()
          : audioRef.current.currentTime
    // Como nos players de verdade: depois de 3 s, "anterior" recomeça a música atual.
    if (posicao > 3 || f.pos === 0) buscar(0)
    else irPara({ ...f, pos: f.pos - 1 })
  }, [irPara, buscar])

  const irParaPosicao = useCallback((pos) => irPara({ ...filaRef.current, pos }), [irPara])

  const adicionarAFila = useCallback(
    (musica) => {
      const f = filaRef.current
      if (f.pos < 0) {
        tocarLista([musica], 0)
        return
      }
      const musicas = [...f.musicas, musica]
      setFila({ musicas, ordem: [...f.ordem, musicas.length - 1], pos: f.pos })
    },
    [tocarLista, setFila],
  )

  /*
   * Fila editável. Os índices abaixo são das "próximas" (0 = a que toca em seguida).
   * Só a ordem muda; a lista original continua guardada.
   */
  const tocarASeguir = useCallback(
    (musica) => {
      const f = filaRef.current
      if (f.pos < 0) {
        tocarLista([musica], 0)
        return
      }
      const musicas = [...f.musicas, musica]
      const ordem = [...f.ordem]
      ordem.splice(f.pos + 1, 0, musicas.length - 1)
      setFila({ musicas, ordem, pos: f.pos })
    },
    [tocarLista, setFila],
  )

  const moverNaFila = useCallback(
    (de, para) => {
      const f = filaRef.current
      const a = f.pos + 1 + de
      const b = f.pos + 1 + para
      if (f.pos < 0 || de === para || a <= f.pos || b <= f.pos || a >= f.ordem.length || b >= f.ordem.length) return
      const ordem = [...f.ordem]
      const [item] = ordem.splice(a, 1)
      ordem.splice(b, 0, item)
      setFila({ ...f, ordem })
    },
    [setFila],
  )

  const removerDaFila = useCallback(
    (indice) => {
      const f = filaRef.current
      const i = f.pos + 1 + indice
      if (f.pos < 0 || i <= f.pos || i >= f.ordem.length) return
      const ordem = [...f.ordem]
      ordem.splice(i, 1)
      setFila({ ...f, ordem })
    },
    [setFila],
  )

  /** Tira todas as próximas (a música atual continua). */
  const limparFila = useCallback(() => {
    const f = filaRef.current
    if (f.pos < 0) return
    setFila({ ...f, ordem: f.ordem.slice(0, f.pos + 1) })
  }, [setFila])

  /**
   * Liga/desliga o aleatório sem interromper a música atual nem perder as edições da fila:
   * só as próximas são misturadas (ou voltam à ordem original, continuando depois da atual).
   */
  const alternarAleatorio = useCallback(() => {
    const novo = !aleatorioRef.current
    aleatorioRef.current = novo
    setAleatorio(novo)
    const f = filaRef.current
    if (f.pos < 0) return
    const indiceAtual = f.ordem[f.pos]
    const tocadas = f.ordem.slice(0, f.pos + 1)
    const proximas = f.ordem.slice(f.pos + 1)
    let reordenadas
    if (novo) {
      reordenadas = misturar(proximas)
    } else {
      const crescente = [...proximas].sort((x, y) => x - y)
      reordenadas = [...crescente.filter((i) => i > indiceAtual), ...crescente.filter((i) => i < indiceAtual)]
    }
    setFila({ ...f, ordem: [...tocadas, ...reordenadas] })
  }, [setFila])

  const alternarRepetir = useCallback(
    () => setRepetir((r) => (r === 'nao' ? 'todas' : r === 'todas' ? 'uma' : 'nao')),
    [],
  )

  // Eventos do <audio>
  useEffect(() => {
    proximaRef.current = proxima
  }, [proxima])

  useEffect(() => {
    const audio = audioRef.current
    // Enquanto o Spotify está no comando, o <audio> fica parado e seus eventos não contam.
    const doAudio = (funcao) => (e) => motorRef.current === 'audio' && funcao(e)
    const aoAtualizarTempo = doAudio(() => setTempo(audio.currentTime))
    const aoSaberDuracao = doAudio(() => Number.isFinite(audio.duration) && setDuracao(audio.duration))
    const aoTocar = doAudio(() => setTocando(true))
    const aoPausar = doAudio(() => setTocando(false))
    const aoTerminar = doAudio(() => proximaRef.current(true))
    const aoFalhar = doAudio(() => {
      setTocando(false)
      setErro('Não foi possível tocar esta música.')
    })
    audio.addEventListener('timeupdate', aoAtualizarTempo)
    audio.addEventListener('durationchange', aoSaberDuracao)
    audio.addEventListener('play', aoTocar)
    audio.addEventListener('pause', aoPausar)
    audio.addEventListener('ended', aoTerminar)
    audio.addEventListener('error', aoFalhar)
    return () => {
      audio.removeEventListener('timeupdate', aoAtualizarTempo)
      audio.removeEventListener('durationchange', aoSaberDuracao)
      audio.removeEventListener('play', aoTocar)
      audio.removeEventListener('pause', aoPausar)
      audio.removeEventListener('ended', aoTerminar)
      audio.removeEventListener('error', aoFalhar)
    }
  }, [])

  // Spotify e YouTube não avisam o tempo continuamente: a barra é atualizada aqui enquanto toca.
  useEffect(() => {
    if (!tocando) return undefined
    const id = setInterval(() => {
      if (document.hidden) return // janela escondida: nada para atualizar na tela
      if (motorRef.current === 'spotify') setTempo(spotifyRef.current.posicao())
      if (motorRef.current === 'youtube') {
        setTempo(youtubeRef.current.posicao())
        const d = youtubeRef.current.duracao()
        if (d > 0) setDuracao(d)
      }
    }, 500)
    return () => clearInterval(id)
  }, [tocando])

  // Liga o player do Spotify já ao entrar (se a conta pode tocar), para o primeiro play
  // ser imediato. Ao sair do VYBE, desliga.
  useEffect(() => {
    tokenSpotifyRef.current = null
    const spotify = spotifyRef.current
    if (!token) {
      spotify.desligar()
      return undefined
    }
    let cancelado = false
    api
      .spotifyStatus(token)
      .then((s) => {
        if (!cancelado && s.podeTocar) spotify.iniciar(mudoRef.current ? 0 : volumeRef.current).catch(() => {})
      })
      .catch(() => {})
    return () => {
      cancelado = true
    }
  }, [token])

  /** Chamado pela página Spotify depois de conectar (ou reconectar) a conta. */
  const prepararSpotify = useCallback(() => {
    tokenSpotifyRef.current = null
    spotifyRef.current.iniciar(mudoRef.current ? 0 : volumeRef.current).catch(() => {})
  }, [])

  useEffect(() => {
    volumeRef.current = volume
    mudoRef.current = mudo
    spotifyRef.current.volume(mudo ? 0 : volume)
    youtubeRef.current.volume(mudo ? 0 : volume)
    audioRef.current.volume = volume
    audioRef.current.muted = mudo
    try {
      localStorage.setItem('vybe.volume', String(volume))
    } catch {
      // sem armazenamento: o volume volta ao padrão na próxima visita
    }
  }, [volume, mudo])

  useEffect(() => {
    duracaoRef.current = duracao
  }, [duracao])

  // Tempo ouvido: conta só enquanto toca e manda ao servidor ao pausar e a cada 30 s.
  useEffect(() => {
    tocandoRef.current = tocando
    const registro = ouvindoRef.current
    if (!registro) return undefined
    if (!tocando) {
      registro.contador.pausar()
      registro.mandar()
      return undefined
    }
    registro.contador.tocar()
    const id = setInterval(() => ouvindoRef.current?.mandar(), 30_000)
    return () => clearInterval(id)
  }, [tocando])

  // Fechando ou escondendo a janela: manda o que já foi ouvido (o pedido termina mesmo fechando).
  useEffect(() => {
    const salvar = () => ouvindoRef.current?.mandar(true)
    const aoMudarVisibilidade = () => document.hidden && salvar()
    window.addEventListener('pagehide', salvar)
    document.addEventListener('visibilitychange', aoMudarVisibilidade)
    return () => {
      window.removeEventListener('pagehide', salvar)
      document.removeEventListener('visibilitychange', aoMudarVisibilidade)
    }
  }, [])

  // Timer para dormir por minutos.
  useEffect(() => {
    if (timer?.tipo !== 'minutos') return undefined
    const id = setTimeout(() => {
      pausar()
      timerRef.current = null
      setTimerEstado(null)
    }, Math.max(0, timer.fim - Date.now()))
    return () => clearTimeout(id)
  }, [timer, pausar])

  /*
   * Controles do sistema: teclas de mídia do teclado e do fone, painel de mídia do Windows
   * (com capa, nome e barra de tempo) e a tela de bloqueio do celular.
   */
  const temSessao = typeof navigator !== 'undefined' && 'mediaSession' in navigator
  useEffect(() => {
    if (!temSessao) return
    const sessao = navigator.mediaSession
    if (!atual) {
      sessao.metadata = null
      return
    }
    const capa = enderecoCompleto(atual.capaUrl)
    sessao.metadata = new MediaMetadata({
      title: atual.titulo,
      artist: atual.artistaNome,
      album: atual.albumTitulo,
      artwork: capa ? [96, 192, 512].map((t) => ({ src: capa, sizes: `${t}x${t}` })) : [],
    })
    const acoes = {
      play: () => !tocandoRef.current && alternarPlay(),
      pause: () => tocandoRef.current && alternarPlay(),
      stop: pausar,
      nexttrack: () => proxima(),
      previoustrack: anterior,
      seekto: (d) => Number.isFinite(d.seekTime) && buscar(d.seekTime),
      seekbackward: (d) => avancar(-(d.seekOffset || 10)),
      seekforward: (d) => avancar(d.seekOffset || 10),
    }
    for (const [acao, funcao] of Object.entries(acoes)) {
      try {
        sessao.setActionHandler(acao, funcao)
      } catch {
        // ação não suportada neste navegador
      }
    }
  }, [temSessao, atual, alternarPlay, pausar, proxima, anterior, buscar, avancar])

  // Estado e posição no painel do Windows; e o aviso ao programa (Discord).
  useEffect(() => {
    const atualizar = () => {
      if (temSessao) {
        navigator.mediaSession.playbackState = atual ? (tocando ? 'playing' : 'paused') : 'none'
        try {
          if (atual && duracaoRef.current > 0) {
            navigator.mediaSession.setPositionState({
              duration: duracaoRef.current,
              playbackRate: 1,
              position: Math.min(Math.max(0, tempoRef.current), duracaoRef.current),
            })
          }
        } catch {
          // valores fora do esperado: o painel só fica sem a barra
        }
      }
      enviarAoPrograma({
        tipo: 'tocando',
        tocando: Boolean(atual) && tocando,
        titulo: atual?.titulo ?? null,
        artista: atual?.artistaNome ?? null,
        album: atual?.albumTitulo ?? null,
        capaUrl: atual ? enderecoCompleto(atual.capaUrl) : null,
        posicao: tempoRef.current,
        duracao: duracaoRef.current,
      })
    }
    presencaRef.current = atualizar
    atualizar()
    return aoReabrirPonte(atualizar)
  }, [temSessao, atual, tocando, duracao])

  // Spotify e YouTube: áudio silencioso para o Windows tratar o VYBE como quem está tocando.
  useEffect(() => {
    const precisa = tocando && motorRef.current !== 'audio'
    if (precisa) {
      if (!silencioRef.current) silencioRef.current = criarSilencio()
      silencioRef.current.play().catch(() => {})
    } else {
      silencioRef.current?.pause()
    }
  }, [tocando, atual])

  const temProxima = fila.pos >= 0 && (fila.pos + 1 < fila.ordem.length || repetir !== 'nao')
  // Memorizado: sem isso, cada atualização de tempo criaria uma lista nova e
  // faria todas as telas que usam o player renderizarem de novo.
  const proximas = useMemo(
    () => (fila.pos >= 0 ? fila.ordem.slice(fila.pos + 1).map((i) => fila.musicas[i]) : []),
    [fila],
  )

  const valor = useMemo(
    () => ({
      atual,
      tocando,
      erro,
      volume,
      setVolume,
      mudo,
      setMudo,
      aleatorio,
      repetir,
      temProxima,
      proximas,
      posicao: fila.pos,
      timer,
      tocarLista,
      alternarPlay,
      pausar,
      proxima,
      anterior,
      irParaPosicao,
      adicionarAFila,
      tocarASeguir,
      moverNaFila,
      removerDaFila,
      limparFila,
      alternarAleatorio,
      alternarRepetir,
      prepararSpotify,
      avancar,
      ajustarVolume,
      definirTimer,
    }),
    [atual, tocando, erro, volume, mudo, aleatorio, repetir, temProxima, proximas, fila.pos, timer,
      tocarLista, alternarPlay, pausar, proxima, anterior, irParaPosicao, adicionarAFila, tocarASeguir,
      moverNaFila, removerDaFila, limparFila, alternarAleatorio, alternarRepetir, prepararSpotify, avancar,
      ajustarVolume, definirTimer],
  )

  const valorTempo = useMemo(() => ({ tempo, duracao, buscar }), [tempo, duracao, buscar])

  return (
    <PlayerContexto.Provider value={valor}>
      <TempoContexto.Provider value={valorTempo}>{children}</TempoContexto.Provider>
    </PlayerContexto.Provider>
  )
}

export const usePlayer = () => useContext(PlayerContexto)
export const useTempo = () => useContext(TempoContexto)
