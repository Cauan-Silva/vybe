import { useEffect, useRef, useState } from 'react'
import { usePlayer } from '../contextos/Player.jsx'
import { duracao } from '../utils/formato.js'
import Capa from './Capa.jsx'
import Icone from './Icone.jsx'

/**
 * Painel com a música atual e as próximas da fila. A fila é editável: arrastar para
 * reordenar (ou usar os botões de subir e descer, que funcionam também no teclado e no
 * celular), tirar uma música e limpar tudo.
 */
export default function FilaPainel({ onFechar }) {
  const { atual, proximas, posicao, irParaPosicao, moverNaFila, removerDaFila, limparFila } = usePlayer()
  const painel = useRef(null)
  const [arrastando, setArrastando] = useState(null) // índice que está sendo arrastado
  const [alvo, setAlvo] = useState(null) // onde ele vai cair

  // Foco no painel só ao abrir (não a cada troca de música).
  useEffect(() => {
    painel.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e) => e.key === 'Escape' && onFechar()
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onFechar])

  function soltar() {
    if (arrastando !== null && alvo !== null) moverNaFila(arrastando, alvo)
    setArrastando(null)
    setAlvo(null)
  }

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

      <div className="fila__secao fila__secao--com-acao">
        <h3>A seguir</h3>
        {proximas.length > 0 && (
          <button type="button" className="link-botao" onClick={limparFila}>
            Limpar fila
          </button>
        )}
      </div>
      {proximas.length === 0 ? (
        <p className="fila__vazia">Nada na fila. Use “Tocar a seguir” ou “Adicionar à fila” no menu de uma música.</p>
      ) : (
        <ol className="fila__lista">
          {proximas.map((m, i) => (
            <li
              key={`${m.id}-${posicao + 1 + i}`}
              className={[
                'fila__linha',
                arrastando === i ? 'fila__linha--arrastando' : '',
                alvo === i && arrastando !== null && arrastando !== i
                  ? arrastando < i ? 'fila__linha--alvo-baixo' : 'fila__linha--alvo-cima'
                  : '',
              ].join(' ')}
              draggable
              onDragStart={(e) => {
                setArrastando(i)
                e.dataTransfer.effectAllowed = 'move'
                e.dataTransfer.setData('text/plain', String(i)) // o Firefox só arrasta com algum dado
              }}
              onDragOver={(e) => {
                if (arrastando === null) return
                e.preventDefault()
                e.dataTransfer.dropEffect = 'move'
                if (alvo !== i) setAlvo(i)
              }}
              onDrop={(e) => {
                e.preventDefault()
                soltar()
              }}
              onDragEnd={() => {
                setArrastando(null)
                setAlvo(null)
              }}
            >
              <span className="fila__alca" aria-hidden="true" title="Arraste para mudar a ordem">
                <Icone nome="arrastar" tamanho={16} />
              </span>
              <ItemFila musica={m} onTocar={() => irParaPosicao(posicao + 1 + i)} />
              <span className="fila__acoes">
                <button
                  type="button"
                  className="botao-icone botao-icone--pequeno"
                  aria-label={`Subir ${m.titulo}`}
                  title="Subir"
                  disabled={i === 0}
                  onClick={() => moverNaFila(i, i - 1)}
                >
                  <Icone nome="subir" tamanho={16} />
                </button>
                <button
                  type="button"
                  className="botao-icone botao-icone--pequeno"
                  aria-label={`Descer ${m.titulo}`}
                  title="Descer"
                  disabled={i === proximas.length - 1}
                  onClick={() => moverNaFila(i, i + 1)}
                >
                  <Icone nome="descer" tamanho={16} />
                </button>
                <button
                  type="button"
                  className="botao-icone botao-icone--pequeno"
                  aria-label={`Tirar ${m.titulo} da fila`}
                  title="Tirar da fila"
                  onClick={() => removerDaFila(i)}
                >
                  <Icone nome="fechar" tamanho={16} />
                </button>
              </span>
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
