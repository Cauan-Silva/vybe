import Capa from './Capa.jsx'
import Icone from './Icone.jsx'

/** Topo das páginas de álbum, artista e playlist. */
export default function Cabecalho({ capa, titulo, redonda, tipo, children, onTocar, tocandoEsta, acoes }) {
  return (
    <header className="cabecalho">
      <Capa src={capa} titulo={titulo} redonda={redonda} className="cabecalho__capa" />
      <div className="cabecalho__textos">
        <p className="cabecalho__tipo">{tipo}</p>
        <h1 className="cabecalho__titulo">{titulo}</h1>
        <div className="cabecalho__info">{children}</div>
        <div className="cabecalho__acoes">
          {onTocar && (
            <button
              type="button"
              className="botao-play botao-play--grande"
              aria-label={tocandoEsta ? 'Pausar' : `Tocar ${titulo}`}
              onClick={onTocar}
            >
              <Icone nome={tocandoEsta ? 'pause' : 'play'} tamanho={26} />
            </button>
          )}
          {acoes}
        </div>
      </div>
    </header>
  )
}
