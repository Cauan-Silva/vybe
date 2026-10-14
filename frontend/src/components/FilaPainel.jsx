import { useEffect, useRef } from 'react'
import { usePlayer } from '../contextos/Player.jsx'
import { duracao } from '../utils/formato.js'
import Capa from './Capa.jsx'
import Icone from './Icone.jsx'

/** Painel com a música atual e as próximas da fila. */
export default function FilaPainel({ onFechar }) {
  const { atual, proximas, posicao, irParaPosicao } = usePlayer()
  const painel = useRef(null)

  // Foco no painel só ao abrir (não a cada troca de música).
  useEffect(() => {
    painel.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e) => e.key === 'Escape' && onFechar()
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onFechar])

  return (
    <aside className="fila" ref={painel} tabIndex={-1} aria-label="Fila de reprodução">
      <div className="fila__topo">
        <h2>Fila</h2>
        <button type="button" className="botao-icone" aria-label="Fechar fila" onClick={onFechar}>
          <Icone nome="fechar" />
        </button>
      </div>

      <h3 className="fila__secao">Tocando agora</h3>
      <ItemFila musica={atual} atual />

      <h3 className="fila__secao">A seguir</h3>
      {proximas.length === 0 ? (
        <p className="fila__vazia">Nada na fila. Use “Adicionar à fila” no menu de uma música.</p>
      ) : (
        <ol className="fila__lista">
          {proximas.map((m, i) => (
            <li key={`${m.id}-${i}`}>
              <ItemFila musica={m} onTocar={() => irParaPosicao(posicao + 1 + i)} />
            </li>
          ))}
        </ol>
      )}
    </aside>
  )
}

function ItemFila({ musica, atual = false, onTocar }) {
  const conteudo = (
    <>
      <Capa src={musica.capaUrl} titulo={musica.albumTitulo} className="fila__capa" />
      <span className="fila__textos">
        <span className={`fila__nome ${atual ? 'fila__nome--atual' : ''}`}>{musica.titulo}</span>
        <span className="fila__artista">{musica.artistaNome}</span>
      </span>
      <span className="fila__duracao">{duracao(musica.duracaoSegundos)}</span>
    </>
  )
  return onTocar ? (
    <button type="button" className="fila__item" onClick={onTocar} aria-label={`Tocar ${musica.titulo}`}>
      {conteudo}
    </button>
  ) : (
    <div className="fila__item">{conteudo}</div>
  )
}
