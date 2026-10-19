import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { useBiblioteca } from '../contextos/Biblioteca.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api } from '../services/api.js'
import { NOME_APP } from '../config.js'
import Icone from './Icone.jsx'

/**
 * Menu "⋯" de cada música: fila, playlists, navegação e, quando cabe,
 * remover da playlist ou excluir a música enviada pela própria pessoa.
 */
export default function MenuMusica({ musica, onRemoverDaPlaylist, onExcluida }) {
  const [aberto, setAberto] = useState(false)
  const [escolhendoPlaylist, setEscolhendoPlaylist] = useState(false)
  const raiz = useRef(null)
  const botao = useRef(null)
  const navegar = useNavigate()
  const { sessao, usuario, chamar } = useAuth()
  const { playlists, criarPlaylist, adicionarNaPlaylist } = useBiblioteca()
  const { adicionarAFila, tocarASeguir } = usePlayer()
  const avisar = useAvisos()

  useEffect(() => {
    if (!aberto) return
    const fecharFora = (e) => !raiz.current?.contains(e.target) && fechar()
    const fecharEsc = (e) => {
      if (e.key === 'Escape') {
        fechar()
        botao.current?.focus()
      }
    }
    document.addEventListener('mousedown', fecharFora)
    document.addEventListener('keydown', fecharEsc)
    return () => {
      document.removeEventListener('mousedown', fecharFora)
      document.removeEventListener('keydown', fecharEsc)
    }
  }, [aberto])

  function fechar() {
    setAberto(false)
    setEscolhendoPlaylist(false)
  }

  function executar(acao) {
    return () => {
      fechar()
      acao()
    }
  }

  async function novaPlaylistComMusica() {
    fechar()
    try {
      const nova = await criarPlaylist()
      await adicionarNaPlaylist(nova, musica)
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }

  async function excluir() {
    fechar()
    if (!window.confirm(`Excluir "${musica.titulo}" de ${NOME_APP}? Ela sai de todas as playlists e curtidas.`)) return
    try {
      await chamar((t) => api.excluirMusica(musica.id, t))
      avisar('Música excluída')
      onExcluida?.()
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }

  const podeExcluir = usuario && musica.enviadaPorId === usuario.id

  return (
    <div className="menu" ref={raiz}>
      <button
        ref={botao}
        type="button"
        className="botao-icone"
        aria-label={`Mais opções para ${musica.titulo}`}
        aria-haspopup="menu"
        aria-expanded={aberto}
        onClick={(e) => {
          e.stopPropagation()
          setAberto((a) => !a)
        }}
      >
        <Icone nome="mais" />
      </button>

      {aberto && (
        <div className="menu__lista" role="menu" onClick={(e) => e.stopPropagation()}>
          {!escolhendoPlaylist ? (
            <>
              <button role="menuitem" onClick={executar(() => {
                tocarASeguir(musica)
                avisar('Vai tocar em seguida')
              })}>
                Tocar a seguir
              </button>
              <button role="menuitem" onClick={executar(() => {
                adicionarAFila(musica)
                avisar('Adicionada ao fim da fila')
              })}>
                Adicionar à fila
              </button>
              {sessao && (
                <button role="menuitem" onClick={() => setEscolhendoPlaylist(true)}>
                  Adicionar à playlist…
                </button>
              )}
              <button role="menuitem" onClick={executar(() => navegar(`/artista/${musica.artistaId}`))}>
                Ir para o artista
              </button>
              <button role="menuitem" onClick={executar(() => navegar(`/album/${musica.albumId}`))}>
                Ir para o álbum
              </button>
              {onRemoverDaPlaylist && (
                <button role="menuitem" onClick={executar(onRemoverDaPlaylist)}>
                  Remover desta playlist
                </button>
              )}
              {podeExcluir && (
                <button role="menuitem" className="menu__perigo" onClick={excluir}>
                  Excluir música
                </button>
              )}
            </>
          ) : (
            <>
              <button role="menuitem" className="menu__voltar" onClick={() => setEscolhendoPlaylist(false)}>
                ← Voltar
              </button>
              <button role="menuitem" onClick={novaPlaylistComMusica}>
                <Icone nome="adicionar" tamanho={16} /> Nova playlist
              </button>
              {playlists.map((p) => (
                <button role="menuitem" key={p.id} onClick={executar(() => adicionarNaPlaylist(p, musica))}>
                  {p.nome}
                </button>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  )
}
