import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api, urlArquivo } from '../services/api.js'
import { embaralhar } from '../utils/formato.js'
import { useAuth } from './Auth.jsx'

/*
 * Um único elemento <audio> para o app inteiro, criado aqui e nunca desmontado:
 * por isso a música continua tocando quando você troca de página.
 *
 * A fila guarda a lista original (musicas), a ordem de reprodução (ordem, que é
 * embaralhada no modo aleatório) e a posição atual dentro dessa ordem (pos).
 *
 * O tempo de reprodução fica num contexto separado: ele muda várias vezes por
 * segundo e só a barra do player precisa dele, então as listas não re-renderizam à toa.
 */

const PlayerContexto = createContext(null)
const TempoContexto = createContext(null)

const FILA_VAZIA = { musicas: [], ordem: [], pos: -1 }

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
  const [tempo, setTempo] = useState(0)
  const [duracao, setDuracao] = useState(0)
  const [volume, setVolume] = useState(lerVolume)
  const [mudo, setMudo] = useState(false)
  const [aleatorio, setAleatorio] = useState(false)
  const [repetir, setRepetir] = useState('nao') // 'nao' | 'todas' | 'uma'
  const [erro, setErro] = useState(null)

  // Refs espelham o estado para os eventos do <audio> sempre lerem o valor atual.
  const filaRef = useRef(FILA_VAZIA)
  const aleatorioRef = useRef(false)
  const repetirRef = useRef('nao')
  const tokenRef = useRef(token)
  useEffect(() => {
    tokenRef.current = token
  }, [token])
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
    setErro(null)
    setTempo(0)
    setDuracao(musica.duracaoSegundos ?? 0)
    audio.src = urlArquivo(musica.audioUrl)
    audio.play().catch(() => {
      // O navegador pode bloquear o play automático; o botão continua funcionando.
    })
    if (tokenRef.current) api.registrarReproducao(musica.id, tokenRef.current).catch(() => {})
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
    if (filaRef.current.pos < 0) return
    if (audio.paused) audio.play().catch(() => {})
    else audio.pause()
  }, [])

  const proxima = useCallback(
    (automatico = false) => {
      const f = filaRef.current
      const audio = audioRef.current
      if (f.pos < 0) return
      if (automatico && repetirRef.current === 'uma') {
        audio.currentTime = 0
        audio.play().catch(() => {})
      } else if (f.pos + 1 < f.ordem.length) {
        irPara({ ...f, pos: f.pos + 1 })
      } else if (repetirRef.current !== 'nao') {
        irPara({ ...f, pos: 0 })
      } else if (automatico) {
        audio.currentTime = 0 // fim da fila: volta ao começo da última música, pausada
      }
    },
    [irPara],
  )

  const anterior = useCallback(() => {
    const f = filaRef.current
    const audio = audioRef.current
    if (f.pos < 0) return
    // Como nos players de verdade: depois de 3 s, "anterior" recomeça a música atual.
    if (audio.currentTime > 3 || f.pos === 0) audio.currentTime = 0
    else irPara({ ...f, pos: f.pos - 1 })
  }, [irPara])

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

  /** Liga/desliga o aleatório sem interromper a música atual. */
  const alternarAleatorio = useCallback(() => {
    const novo = !aleatorioRef.current
    aleatorioRef.current = novo
    setAleatorio(novo)
    const f = filaRef.current
    if (f.pos < 0) return
    const indiceAtual = f.ordem[f.pos]
    setFila({
      ...f,
      ordem: novo ? embaralhar(f.musicas.length, indiceAtual) : f.musicas.map((_, i) => i),
      pos: novo ? 0 : indiceAtual,
    })
  }, [setFila])

  const alternarRepetir = useCallback(
    () => setRepetir((r) => (r === 'nao' ? 'todas' : r === 'todas' ? 'uma' : 'nao')),
    [],
  )

  const buscar = useCallback((segundos) => {
    audioRef.current.currentTime = segundos
    setTempo(segundos)
  }, [])

  // Eventos do <audio>
  const proximaRef = useRef(proxima)
  useEffect(() => {
    proximaRef.current = proxima
  }, [proxima])

  useEffect(() => {
    const audio = audioRef.current
    const aoAtualizarTempo = () => setTempo(audio.currentTime)
    const aoSaberDuracao = () => Number.isFinite(audio.duration) && setDuracao(audio.duration)
    const aoTocar = () => setTocando(true)
    const aoPausar = () => setTocando(false)
    const aoTerminar = () => proximaRef.current(true)
    const aoFalhar = () => {
      setTocando(false)
      setErro('Não foi possível tocar esta música.')
    }
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

  useEffect(() => {
    audioRef.current.volume = volume
    audioRef.current.muted = mudo
    try {
      localStorage.setItem('vybe.volume', String(volume))
    } catch {
      // sem armazenamento: o volume volta ao padrão na próxima visita
    }
  }, [volume, mudo])

  // Controles do sistema (teclas de mídia, tela de bloqueio do celular)
  useEffect(() => {
    if (!('mediaSession' in navigator) || !atual) return
    const capa = urlArquivo(atual.capaUrl)
    navigator.mediaSession.metadata = new MediaMetadata({
      title: atual.titulo,
      artist: atual.artistaNome,
      album: atual.albumTitulo,
      artwork: capa ? [{ src: capa, sizes: '512x512' }] : [],
    })
    navigator.mediaSession.setActionHandler('play', alternarPlay)
    navigator.mediaSession.setActionHandler('pause', alternarPlay)
    navigator.mediaSession.setActionHandler('nexttrack', () => proxima())
    navigator.mediaSession.setActionHandler('previoustrack', anterior)
  }, [atual, alternarPlay, proxima, anterior])

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
      tocarLista,
      alternarPlay,
      proxima,
      anterior,
      irParaPosicao,
      adicionarAFila,
      alternarAleatorio,
      alternarRepetir,
    }),
    [atual, tocando, erro, volume, mudo, aleatorio, repetir, temProxima, proximas, fila.pos,
      tocarLista, alternarPlay, proxima, anterior, irParaPosicao, adicionarAFila, alternarAleatorio,
      alternarRepetir],
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
