import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../contextos/Auth.jsx'
import { usePreferencias } from '../contextos/Preferencias.jsx'
import { api } from '../services/api.js'
import { abrirPip, fecharPip, pipAberto, pipDisponivel } from '../services/pipVideo.js'
import { carregarApi } from '../services/youtubePlayer.js'
import { criarRegistroDeTempo } from '../utils/contadorTempo.js'
import { duracao, numeroCompacto, tempoRelativo } from '../utils/formato.js'
import { dadosDoVideo } from '../utils/useMeusVideos.js'
import Icone from './Icone.jsx'

/* Peças da aba Vídeos e da página do canal: cartão, grade e o player grande. */

/** "1,2 mi de visualizações · há 3 dias" (o que existir). */
function linhaDeDetalhes(v) {
  const partes = []
  if (Number.isFinite(v.visualizacoes)) partes.push(`${numeroCompacto(v.visualizacoes)} de visualizações`)
  if (v.publicadoEm) partes.push(tempoRelativo(v.publicadoEm))
  return partes.join(' · ')
}

export function CartaoVideo({ video, salvo, depois, aberto, mostrarCanal = true, onAbrir, onAlternarDepois }) {
  const fracao =
    salvo && video.duracaoSegundos > 0 ? Math.min(1, (salvo.posicaoSegundos ?? 0) / video.duracaoSegundos) : 0
  const detalhes = linhaDeDetalhes(video)

  return (
    <article className={`cartao-video ${aberto ? 'cartao-video--aberto' : ''}`}>
      <div className="cartao-video__topo">
        <button
          type="button"
          className="cartao-video__miniatura"
          onClick={() => onAbrir(video)}
          aria-label={`Assistir ${video.titulo}`}
        >
          {video.capaUrl ? <img src={video.capaUrl} alt="" loading="lazy" /> : <Icone nome="video" tamanho={32} />}
          <span className={`cartao-video__duracao ${video.duracaoSegundos ? '' : 'cartao-video__duracao--vivo'}`}>
            {video.duracaoSegundos ? duracao(video.duracaoSegundos) : 'AO VIVO'}
          </span>
          {fracao > 0 && (
            <span className="cartao-video__progresso" aria-hidden="true">
              <span style={{ width: `${fracao * 100}%` }} />
            </span>
          )}
        </button>
        <button
          type="button"
          className={`cartao-video__depois ${depois ? 'cartao-video__depois--ativo' : ''}`}
          aria-pressed={depois}
          aria-label={depois ? 'Remover de Assistir depois' : 'Assistir depois'}
          title={depois ? 'Remover de Assistir depois' : 'Assistir depois'}
          onClick={() => onAlternarDepois(video)}
        >
          <Icone nome={depois ? 'feito' : 'relogio'} tamanho={18} />
        </button>
      </div>
      <button type="button" className="cartao-video__titulo" onClick={() => onAbrir(video)}>
        {video.titulo}
      </button>
      {mostrarCanal &&
        (video.canalId ? (
          <Link to={`/videos/canal/${video.canalId}`} className="cartao-video__canal">
            {video.canal}
          </Link>
        ) : (
          <span className="cartao-video__canal">{video.canal}</span>
        ))}
      {detalhes && <span className="cartao-video__meta">{detalhes}</span>}
    </article>
  )
}

/** Grade de cartões, já ligada ao "Assistir depois" e ao progresso. */
export function GradeVideos({ videos, meus, abertoId, mostrarCanal, onAbrir }) {
  return (
    <ul className="grade-videos">
      {videos.map((v) => (
        <li key={v.id}>
          <CartaoVideo
            video={v}
            salvo={meus.progresso.get(v.id)}
            depois={meus.idsDepois.has(v.id)}
            aberto={abertoId === v.id}
            mostrarCanal={mostrarCanal}
            onAbrir={onAbrir}
            onAlternarDepois={meus.alternarDepois}
          />
        </li>
      ))}
    </ul>
  )
}

/** Seguir um canal: os vídeos novos dele aparecem em "Inscrições" na aba Vídeos. */
export function BotaoSeguir({ seguindo, onClick, pequeno = true }) {
  return (
    <button
      type="button"
      className={`botao ${seguindo ? 'botao--contorno botao--marcado' : ''} ${pequeno ? 'botao--pequeno' : ''}`}
      aria-pressed={seguindo}
      onClick={onClick}
    >
      <Icone nome={seguindo ? 'feito' : 'adicionar'} tamanho={16} /> {seguindo ? 'Seguindo' : 'Seguir'}
    </button>
  )
}

/** De quanto em quanto tempo o progresso é salvo enquanto o vídeo toca. */
const SALVAR_A_CADA_MS = 10_000
const TOCANDO = 1
const PAUSADO = 2
const TERMINOU = 0

/**
 * Player grande com o player oficial do YouTube. Começa de onde a pessoa parou e salva
 * a posição enquanto toca, ao pausar, ao terminar e ao fechar. Pode ir para a janela
 * flutuante (Picture-in-Picture) e voltar do mesmo ponto.
 */
export function PainelVideo({
  video,
  inicio,
  depois,
  seguindo = false,
  onProgresso,
  onAlternarDepois,
  onAlternarSeguir,
  onFechar,
}) {
  const caixaRef = useRef(null)
  const palcoRef = useRef(null)
  const tocandoRef = useRef(false)
  const playerRef = useRef(null)
  const registroRef = useRef(null) // tempo assistido (para o "Seu mês")
  const montadoRef = useRef(true)
  const videoIdRef = useRef(video.id)
  const [emPip, setEmPip] = useState(false)
  const { chamar } = useAuth()
  const chamarRef = useRef(chamar)
  useEffect(() => {
    chamarRef.current = chamar
  }, [chamar])
  useEffect(() => {
    montadoRef.current = true
    return () => {
      montadoRef.current = false
    }
  }, [])
  const { luzAmbiente, intensidadeAmbiente, tamanhoAmbiente, ambienteTempoReal, modoLeve } = usePreferencias()
  // "viva": o programa está mandando as cores do vídeo em tempo real
  const [viva, setViva] = useState(false)
  const vivaRef = useRef(false)
  const onProgressoRef = useRef(onProgresso)
  useEffect(() => {
    onProgressoRef.current = onProgresso
  }, [onProgresso])

  useEffect(() => {
    let player = null
    let cancelado = false
    videoIdRef.current = video.id
    const salvar = (fim = false) => {
      if (!player?.getCurrentTime) return
      const t = fim ? player.getDuration() : player.getCurrentTime()
      if (Number.isFinite(t)) onProgressoRef.current(video, Math.floor(t))
    }

    // Outro vídeo na janela flutuante: fecha antes de abrir este.
    if (pipAberto()) fecharPip()
    setEmPip(false)

    // Entra no histórico assim que abre.
    onProgressoRef.current(video, inicio)
    tocandoRef.current = false
    vivaRef.current = false
    setViva(false)

    // Conta para o "Seu mês no VYBE": registra a visualização e manda o tempo assistido.
    const registro = criarRegistroDeTempo((id, segundos, manter) => {
      chamarRef.current((t) => api.tempoVisualizacao(id, segundos, t, manter)).catch(() => {})
    })
    registroRef.current = registro
    chamarRef
      .current((t) => api.registrarVisualizacao(dadosDoVideo(video), t))
      .then((r) => registro.definirId(r?.id ?? null))
      .catch(() => {})

    carregarApi()
      .then(() => {
        if (cancelado || !caixaRef.current) return
        // O YouTube troca o elemento pelo iframe; criamos um filho para o React não perder o dele.
        const alvo = document.createElement('div')
        caixaRef.current.replaceChildren(alvo)
        player = new window.YT.Player(alvo, {
          width: '100%',
          height: '100%',
          videoId: video.id,
          host: 'https://www.youtube-nocookie.com',
          playerVars: { autoplay: 1, rel: 0, playsinline: 1, start: Math.floor(inicio) },
          events: {
            onStateChange: (e) => {
              tocandoRef.current = e.data === TOCANDO
              if (e.data === TOCANDO) registro.contador.tocar()
              else registro.contador.pausar()
              if (e.data === PAUSADO) salvar()
              else if (e.data === TERMINOU) salvar(true)
              if (e.data !== TOCANDO) registro.mandar()
            },
          },
        })
        playerRef.current = player
      })
      .catch(() => {})

    const relogio = setInterval(() => {
      if (player?.getPlayerState?.() === TOCANDO) {
        salvar()
        registro.mandar()
      }
    }, SALVAR_A_CADA_MS)
    const aoEsconder = () => document.hidden && registro.mandar(true)
    document.addEventListener('visibilitychange', aoEsconder)

    return () => {
      cancelado = true
      clearInterval(relogio)
      document.removeEventListener('visibilitychange', aoEsconder)
      // Se o vídeo está na janela flutuante, é ela quem salva e conta o tempo daqui em diante.
      if (!pipAberto(video.id)) {
        salvar()
        registro.contador.pausar()
        registro.mandar()
      }
      playerRef.current = null
      try {
        player?.destroy?.()
      } catch {
        // player já descartado
      }
    }
    // Recria só quando o vídeo muda; "inicio" vale para a abertura.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video.id])

  /*
   * Luz ambiente em tempo real (só no programa): o programa pergunta onde está o vídeo e se
   * está tocando (__vybeAmbiente), fotografa essa área e devolve as cores das bordas (__vybeLuz).
   * Na versão web ninguém chama essas funções, e fica a luz com as cores da miniatura.
   */
  useEffect(() => {
    if (!luzAmbiente || !ambienteTempoReal || modoLeve) return undefined
    window.__vybeAmbiente = () => {
      const tela = caixaRef.current
      if (!tela || document.hidden || !tocandoRef.current) return { ativo: false }
      const r = tela.getBoundingClientRect()
      const naTela = r.bottom > 0 && r.top < window.innerHeight
      return { ativo: naTela, x: r.left, y: Math.max(0, r.top), w: r.width, h: Math.min(r.height, r.bottom) }
    }
    window.__vybeLuz = (c) => {
      const palco = palcoRef.current
      if (!palco) return
      const cor = ([r, g, b]) => `rgb(${r} ${g} ${b})`
      palco.style.setProperty('--luz-topo', cor(c.topo))
      palco.style.setProperty('--luz-base', cor(c.base))
      palco.style.setProperty('--luz-esquerda', cor(c.esquerda))
      palco.style.setProperty('--luz-direita', cor(c.direita))
      if (!vivaRef.current) {
        vivaRef.current = true
        setViva(true)
      }
    }
    return () => {
      delete window.__vybeAmbiente
      delete window.__vybeLuz
    }
  }, [luzAmbiente, ambienteTempoReal, modoLeve])

  /** Leva o vídeo para a janela flutuante, do ponto em que está. */
  async function abrirJanelaFlutuante() {
    const player = playerRef.current
    let tempo = inicio
    let tocava = true
    try {
      tempo = player?.getCurrentTime?.() ?? inicio
      tocava = player?.getPlayerState?.() === TOCANDO
    } catch {
      // player ainda carregando: começa do início salvo
    }
    const idDoVideo = video.id
    const aberta = abrirPip({
      video,
      inicio: tempo,
      registro: registroRef.current,
      aoProgresso: (segundos) => onProgressoRef.current(video, segundos),
      aoFechar: (segundos, tocavaNaJanela) => {
        // Voltou: continua no player grande, se ele ainda for deste vídeo.
        if (!montadoRef.current || videoIdRef.current !== idDoVideo) return
        setEmPip(false)
        try {
          playerRef.current?.seekTo?.(segundos, true)
          if (tocavaNaJanela) playerRef.current?.playVideo?.()
        } catch {
          // player sendo recriado
        }
      },
    })
    try {
      player?.pauseVideo?.()
    } catch {
      // nada a pausar
    }
    setEmPip(true)
    try {
      await aberta
    } catch {
      // o navegador não deixou abrir: continua aqui mesmo
      setEmPip(false)
      if (tocava) playerRef.current?.playVideo?.()
    }
  }

  const detalhes = [
    Number.isFinite(video.visualizacoes) ? `${numeroCompacto(video.visualizacoes)} de visualizações` : null,
    Number.isFinite(video.curtidas) ? `${numeroCompacto(video.curtidas)} curtidas` : null,
    video.publicadoEm ? `publicado ${tempoRelativo(video.publicadoEm)}` : null,
  ].filter(Boolean)

  return (
    <section className="player-video" aria-label={`Assistindo: ${video.titulo}`}>
      <div className="player-video__palco" ref={palcoRef}>
        {/* Luz que acompanha o vídeo: uma mancha de cor para cada borda, bem desfocada */}
        {luzAmbiente && viva && (
          <div className={`luz-viva luz-viva--${intensidadeAmbiente} luz-viva--${tamanhoAmbiente}`} aria-hidden="true">
            <span className="luz-viva__topo" />
            <span className="luz-viva__base" />
            <span className="luz-viva__esquerda" />
            <span className="luz-viva__direita" />
          </div>
        )}
        {/*
          Sem o programa (ou até chegarem as primeiras cores), a luz usa as cores da miniatura:
          o vídeo vem num iframe do YouTube, que a página não consegue ler quadro a quadro.
        */}
        {luzAmbiente && !viva && video.capaUrl && (
          <div
            className={`luz-ambiente luz-ambiente--${intensidadeAmbiente}`}
            aria-hidden="true"
            style={{ backgroundImage: `url("${video.capaUrl}")` }}
          />
        )}
        <div className="player-video__tela" ref={caixaRef} />
        {emPip && (
          <div className="player-video__pip">
            <Icone nome="janelaFlutuante" tamanho={32} />
            <p>Tocando na janela flutuante</p>
            <button type="button" className="botao botao--contorno botao--pequeno" onClick={fecharPip}>
              Trazer de volta
            </button>
          </div>
        )}
      </div>
      <div className="player-video__info">
        <div>
          <p className="player-video__titulo">{video.titulo}</p>
          {video.canalId ? (
            <Link to={`/videos/canal/${video.canalId}`} className="player-video__canal">
              {video.canal}
            </Link>
          ) : (
            <p className="player-video__canal">{video.canal}</p>
          )}
          {detalhes.length > 0 && <p className="player-video__detalhes">{detalhes.join(' · ')}</p>}
        </div>
        <div className="player-video__acoes">
          {video.canalId && onAlternarSeguir && (
            <BotaoSeguir
              seguindo={seguindo}
              onClick={() => onAlternarSeguir({ id: video.canalId, nome: video.canal || 'Canal', avatarUrl: null })}
            />
          )}
          <button
            type="button"
            className={`botao botao--contorno botao--pequeno ${depois ? 'botao--marcado' : ''}`}
            aria-pressed={depois}
            onClick={() => onAlternarDepois(video)}
          >
            <Icone nome={depois ? 'feito' : 'relogio'} tamanho={16} /> {depois ? 'Na lista' : 'Assistir depois'}
          </button>
          {pipDisponivel() && !emPip && (
            <button
              type="button"
              className="botao botao--contorno botao--pequeno"
              title="Assistir numa janelinha que fica por cima de tudo (até de jogos em janela)"
              onClick={abrirJanelaFlutuante}
            >
              <Icone nome="janelaFlutuante" tamanho={16} /> Janela flutuante
            </button>
          )}
          <a
            className="link-discreto link-externo"
            href={`https://www.youtube.com/watch?v=${encodeURIComponent(video.id)}`}
            target="_blank"
            rel="noreferrer"
          >
            Abrir no YouTube <Icone nome="externo" tamanho={14} />
          </a>
          <button type="button" className="botao botao--contorno botao--pequeno" onClick={onFechar}>
            <Icone nome="fechar" tamanho={16} /> Fechar
          </button>
        </div>
      </div>
    </section>
  )
}
