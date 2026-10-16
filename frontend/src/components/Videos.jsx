import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { carregarApi } from '../services/youtubePlayer.js'
import { duracao, numeroCompacto, tempoRelativo } from '../utils/formato.js'
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

/** De quanto em quanto tempo o progresso é salvo enquanto o vídeo toca. */
const SALVAR_A_CADA_MS = 10_000
const TOCANDO = 1
const PAUSADO = 2
const TERMINOU = 0

/**
 * Player grande com o player oficial do YouTube. Começa de onde a pessoa parou e salva
 * a posição enquanto toca, ao pausar, ao terminar e ao fechar.
 */
export function PainelVideo({ video, inicio, depois, onProgresso, onAlternarDepois, onFechar }) {
  const caixaRef = useRef(null)
  const onProgressoRef = useRef(onProgresso)
  useEffect(() => {
    onProgressoRef.current = onProgresso
  }, [onProgresso])

  useEffect(() => {
    let player = null
    let cancelado = false
    const salvar = (fim = false) => {
      if (!player?.getCurrentTime) return
      const t = fim ? player.getDuration() : player.getCurrentTime()
      if (Number.isFinite(t)) onProgressoRef.current(video, Math.floor(t))
    }

    // Entra no histórico assim que abre.
    onProgressoRef.current(video, inicio)

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
              if (e.data === PAUSADO) salvar()
              else if (e.data === TERMINOU) salvar(true)
            },
          },
        })
      })
      .catch(() => {})

    const relogio = setInterval(() => {
      if (player?.getPlayerState?.() === TOCANDO) salvar()
    }, SALVAR_A_CADA_MS)

    return () => {
      cancelado = true
      clearInterval(relogio)
      salvar()
      try {
        player?.destroy?.()
      } catch {
        // player já descartado
      }
    }
    // Recria só quando o vídeo muda; "inicio" vale para a abertura.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [video.id])

  const detalhes = [
    Number.isFinite(video.visualizacoes) ? `${numeroCompacto(video.visualizacoes)} de visualizações` : null,
    Number.isFinite(video.curtidas) ? `${numeroCompacto(video.curtidas)} curtidas` : null,
    video.publicadoEm ? `publicado ${tempoRelativo(video.publicadoEm)}` : null,
  ].filter(Boolean)

  return (
    <section className="player-video" aria-label={`Assistindo: ${video.titulo}`}>
      <div className="player-video__tela" ref={caixaRef} />
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
          <button
            type="button"
            className={`botao botao--contorno botao--pequeno ${depois ? 'botao--marcado' : ''}`}
            aria-pressed={depois}
            onClick={() => onAlternarDepois(video)}
          >
            <Icone nome={depois ? 'feito' : 'relogio'} tamanho={16} /> {depois ? 'Na lista' : 'Assistir depois'}
          </button>
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
