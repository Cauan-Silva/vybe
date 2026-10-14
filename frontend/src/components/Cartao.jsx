import { Link } from 'react-router-dom'
import Capa from './Capa.jsx'
import Icone from './Icone.jsx'

/** Cartão de álbum, artista ou playlist, com botão de tocar opcional. */
export default function Cartao({ para, capa, titulo, subtitulo, redonda = false, onTocar }) {
  return (
    <div className="cartao">
      <div className="cartao__imagem">
        <Capa src={capa} titulo={titulo} redonda={redonda} />
        {onTocar && (
          <button type="button" className="cartao__tocar" aria-label={`Tocar ${titulo}`} onClick={onTocar}>
            <Icone nome="play" tamanho={22} />
          </button>
        )}
      </div>
      <Link className="cartao__titulo" to={para}>
        {titulo}
      </Link>
      {subtitulo && <p className="cartao__subtitulo">{subtitulo}</p>}
    </div>
  )
}

export function Grade({ children }) {
  return <div className="grade">{children}</div>
}
