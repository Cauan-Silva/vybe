import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { usePlayer, useTempo } from '../contextos/Player.jsx'
import { duracao } from '../utils/formato.js'
import BotaoCurtir from './BotaoCurtir.jsx'
import Capa from './Capa.jsx'
import FilaPainel from './FilaPainel.jsx'
import Icone from './Icone.jsx'
import SeloLicenca from './SeloLicenca.jsx'

const ROTULO_REPETIR = {
  nao: 'Repetir: desligado',
  todas: 'Repetir: a fila toda',
  uma: 'Repetir: só esta música',
}

/** Barra fixa no rodapé. O anel neon em volta da capa gira e brilha enquanto a música toca. */
export default function PlayerBar() {
  const p = usePlayer()
  const [filaAberta, setFilaAberta] = useState(false)

  if (!p.atual) {
    return (
      <footer className="player player--vazio">
        <p>Escolha uma música para começar a ouvir.</p>
      </footer>
    )
  }

  const { atual } = p

  return (
    <footer className="player" aria-label="Player">
      <div className="player__faixa">
        <div className={`anel ${p.tocando ? 'anel--tocando' : ''}`}>
          <Capa src={atual.capaUrl} titulo={atual.albumTitulo} redonda className="anel__capa" />
        </div>
        <div className="player__textos">
          <Link className="player__titulo" to={`/album/${atual.albumId}`}>
            {atual.titulo}
          </Link>
          <span className="player__linha-artista">
            <SeloLicenca musica={atual} />
            <Link className="player__artista" to={`/artista/${atual.artistaId}`}>
              {atual.artistaNome}
            </Link>
          </span>
          {p.erro && <p className="player__erro">{p.erro}</p>}
        </div>
        <BotaoCurtir musica={atual} />
      </div>

      <div className="player__centro">
        <div className="player__controles">
          <button
            type="button"
            className={`botao-icone botao-modo ${p.aleatorio ? 'botao-modo--ativo' : ''}`}
            aria-pressed={p.aleatorio}
            aria-label="Aleatório"
            title="Aleatório"
            onClick={p.alternarAleatorio}
          >
            <Icone nome="aleatorio" />
          </button>
          <button type="button" className="botao-icone" aria-label="Anterior" title="Anterior" onClick={p.anterior}>
            <Icone nome="anterior" />
          </button>
          <button
            type="button"
            className="botao-play"
            aria-label={p.tocando ? 'Pausar' : 'Tocar'}
            onClick={p.alternarPlay}
          >
            <Icone nome={p.tocando ? 'pause' : 'play'} tamanho={22} />
          </button>
          <button
            type="button"
            className="botao-icone"
            aria-label="Próxima"
            title="Próxima"
            disabled={!p.temProxima}
            onClick={() => p.proxima()}
          >
            <Icone nome="proxima" />
          </button>
          <button
            type="button"
            className={`botao-icone botao-modo ${p.repetir !== 'nao' ? 'botao-modo--ativo' : ''}`}
            aria-label={ROTULO_REPETIR[p.repetir]}
            title={ROTULO_REPETIR[p.repetir]}
            onClick={p.alternarRepetir}
          >
            <Icone nome="repetir" />
            {p.repetir === 'uma' && <span className="botao-modo__um">1</span>}
          </button>
        </div>
        <BarraProgresso />
      </div>

      <div className="player__extras">
        <BotaoTimer />
        <button
          type="button"
          className={`botao-icone botao-modo ${filaAberta ? 'botao-modo--ativo' : ''}`}
          aria-label="Fila"
          title="Fila"
          aria-expanded={filaAberta}
          onClick={() => setFilaAberta((a) => !a)}
        >
          <Icone nome="fila" />
        </button>
        <button
          type="button"
          className="botao-icone"
          aria-label={p.mudo ? 'Ativar som' : 'Silenciar'}
          onClick={() => p.setMudo(!p.mudo)}
        >
          <Icone nome={p.mudo || p.volume === 0 ? 'mudo' : 'volume'} />
        </button>
        <input
          type="range"
          className="faixa faixa--volume"
          min="0"
          max="1"
          step="0.01"
          value={p.mudo ? 0 : p.volume}
          style={{ '--p': `${(p.mudo ? 0 : p.volume) * 100}%` }}
          aria-label="Volume"
          aria-valuetext={`${Math.round((p.mudo ? 0 : p.volume) * 100)}%`}
          onChange={(e) => {
            p.setMudo(false)
            p.setVolume(Number(e.target.value))
          }}
        />
      </div>

      {filaAberta && <FilaPainel onFechar={() => setFilaAberta(false)} />}
    </footer>
  )
}

const OPCOES_TIMER = [15, 30, 45, 60, 90]

/** Quanto falta, arredondado para cima: "12 min", "1 h 05". */
function falta(fim, agora) {
  const minutos = Math.max(1, Math.ceil((fim - agora) / 60_000))
  if (minutos < 60) return `${minutos} min`
  return `${Math.floor(minutos / 60)} h ${String(minutos % 60).padStart(2, '0')}`
}

/** Timer para dormir: pausa depois de X minutos ou no fim da música atual. */
function BotaoTimer() {
  const { timer, definirTimer } = usePlayer()
  const [aberto, setAberto] = useState(false)
  const [agora, setAgora] = useState(() => Date.now())
  const raiz = useRef(null)

  // Atualiza o "falta" a cada 15 s (não precisa de mais precisão que isso).
  useEffect(() => {
    if (timer?.tipo !== 'minutos') return undefined
    setAgora(Date.now())
    const id = setInterval(() => setAgora(Date.now()), 15_000)
    return () => clearInterval(id)
  }, [timer])

  useEffect(() => {
    if (!aberto) return undefined
    const fora = (e) => !raiz.current?.contains(e.target) && setAberto(false)
    const esc = (e) => e.key === 'Escape' && setAberto(false)
    document.addEventListener('mousedown', fora)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', fora)
      document.removeEventListener('keydown', esc)
    }
  }, [aberto])

  const rotulo = !timer
    ? 'Timer para dormir'
    : timer.tipo === 'fim-da-musica'
      ? 'Timer: pausa no fim desta música'
      : `Timer: pausa em ${falta(timer.fim, agora)}`

  function escolher(opcao) {
    definirTimer(opcao)
    setAberto(false)
  }

  return (
    <div className="timer" ref={raiz}>
      <button
        type="button"
        className={`botao-icone botao-modo ${timer ? 'botao-modo--ativo' : ''}`}
        aria-label={rotulo}
        title={rotulo}
        aria-haspopup="menu"
        aria-expanded={aberto}
        onClick={() => setAberto((a) => !a)}
      >
        <Icone nome="lua" />
        {timer?.tipo === 'minutos' && <span className="timer__resta">{Math.max(1, Math.ceil((timer.fim - agora) / 60_000))}</span>}
      </button>
      {aberto && (
        <div className="menu__lista timer__menu" role="menu">
          <p className="timer__titulo">{timer ? rotulo.replace('Timer: ', '') : 'Pausar a música…'}</p>
          {OPCOES_TIMER.map((m) => (
            <button key={m} role="menuitem" onClick={() => escolher(m)}>
              Em {m < 60 ? `${m} minutos` : m === 60 ? '1 hora' : '1 h 30'}
            </button>
          ))}
          <button role="menuitem" onClick={() => escolher('fim-da-musica')}>
            No fim desta música
          </button>
          {timer && (
            <button role="menuitem" className="menu__perigo" onClick={() => escolher(null)}>
              Desligar o timer
            </button>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * Barra de progresso. Enquanto a pessoa arrasta, mostramos a posição do dedo/mouse;
 * só pedimos o trecho novo ao servidor quando ela solta.
 */
function BarraProgresso() {
  const { tempo, duracao: total, buscar } = useTempo()
  const [arrasto, setArrasto] = useState(null)
  const valor = arrasto ?? tempo
  const porcentagem = total > 0 ? (valor / total) * 100 : 0

  function confirmar() {
    if (arrasto !== null) {
      buscar(arrasto)
      setArrasto(null)
    }
  }

  return (
    <div className="progresso">
      <span className="progresso__tempo">{duracao(valor)}</span>
      <input
        type="range"
        className="faixa"
        min="0"
        max={total || 0}
        step="0.1"
        value={Math.min(valor, total || 0)}
        style={{ '--p': `${porcentagem}%` }}
        aria-label="Posição na música"
        aria-valuetext={`${duracao(valor)} de ${duracao(total)}`}
        onChange={(e) => setArrasto(Number(e.target.value))}
        onPointerUp={confirmar}
        onKeyUp={confirmar}
        onBlur={confirmar}
      />
      <span className="progresso__tempo">{duracao(total)}</span>
    </div>
  )
}
