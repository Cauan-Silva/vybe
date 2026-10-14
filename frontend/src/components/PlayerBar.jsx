import { useState } from 'react'
import { Link } from 'react-router-dom'
import { usePlayer, useTempo } from '../contextos/Player.jsx'
import { duracao } from '../utils/formato.js'
import BotaoCurtir from './BotaoCurtir.jsx'
import Capa from './Capa.jsx'
import FilaPainel from './FilaPainel.jsx'
import Icone from './Icone.jsx'

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
          <Link className="player__artista" to={`/artista/${atual.artistaId}`}>
            {atual.artistaNome}
          </Link>
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
