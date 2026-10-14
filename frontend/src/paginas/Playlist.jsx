import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import Cabecalho from '../components/Cabecalho.jsx'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import ListaMusicas from '../components/ListaMusicas.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { useBiblioteca } from '../contextos/Biblioteca.jsx'
import { api } from '../services/api.js'
import { duracaoTotal, plural } from '../utils/formato.js'
import { useCarregar } from '../utils/useCarregar.js'
import { useTocarLista } from '../utils/useTocarLista.js'

export default function Playlist() {
  const { id } = useParams()
  const { sessao, chamar } = useAuth()
  const { recarregarPlaylists } = useBiblioteca()
  const avisar = useAvisos()
  const navegar = useNavigate()
  const [editando, setEditando] = useState(false)
  const [nome, setNome] = useState('')

  const { dados: playlist, erro, carregando, recarregar, setDados } = useCarregar(
    () => (sessao ? chamar((t) => api.playlist(id, t)) : null),
    [id, sessao],
  )
  const musicas = playlist?.itens.map((i) => i.musica) ?? []
  const { tocar, tocandoEsta } = useTocarLista(musicas)

  if (!sessao) return <PrecisaEntrar texto="Entre para ver suas playlists." />
  if (carregando) return <Carregando />
  if (erro) return <Erro erro={erro} onTentar={recarregar} />

  async function salvarNome(e) {
    e.preventDefault()
    const limpo = nome.trim()
    if (!limpo || limpo === playlist.nome) {
      setEditando(false)
      return
    }
    try {
      await chamar((t) => api.renomearPlaylist(playlist.id, limpo, t))
      setDados({ ...playlist, nome: limpo })
      recarregarPlaylists()
      setEditando(false)
    } catch (erroNome) {
      avisar(erroNome.message, 'erro')
    }
  }

  async function remover(indice) {
    const item = playlist.itens[indice]
    try {
      setDados(await chamar((t) => api.removerDaPlaylist(playlist.id, item.itemId, t)))
      recarregarPlaylists()
      avisar(`Removida de ${playlist.nome}`)
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }

  async function excluir() {
    if (!window.confirm(`Excluir a playlist "${playlist.nome}"? As músicas continuam no catálogo.`)) return
    try {
      await chamar((t) => api.excluirPlaylist(playlist.id, t))
      await recarregarPlaylists()
      avisar('Playlist excluída')
      navegar('/biblioteca')
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }

  return (
    <div className="pagina">
      <Cabecalho
        capa={musicas[0]?.capaUrl}
        titulo={playlist.nome}
        tipo="Playlist"
        onTocar={musicas.length ? tocar : undefined}
        tocandoEsta={tocandoEsta}
        acoes={
          <>
            <button
              type="button"
              className="botao botao--contorno"
              onClick={() => {
                setNome(playlist.nome)
                setEditando(true)
              }}
            >
              Renomear
            </button>
            <button type="button" className="botao botao--contorno" onClick={excluir}>
              Excluir playlist
            </button>
          </>
        }
      >
        {plural(musicas.length, 'música', 'músicas')}
        {musicas.length > 0 && `, ${duracaoTotal(musicas)}`}
      </Cabecalho>

      {editando && (
        <form className="renomear" onSubmit={salvarNome}>
          <label className="campo">
            <span>Nome da playlist</span>
            <input autoFocus maxLength={100} value={nome} onChange={(e) => setNome(e.target.value)} />
          </label>
          <button type="submit" className="botao">
            Salvar nome
          </button>
          <button type="button" className="botao botao--contorno" onClick={() => setEditando(false)}>
            Cancelar
          </button>
        </form>
      )}

      {musicas.length === 0 ? (
        <p className="estado">
          Playlist vazia. Use o menu “⋯” de qualquer música e escolha “Adicionar à playlist”.
        </p>
      ) : (
        <ListaMusicas musicas={musicas} onRemoverDaPlaylist={remover} onAlterada={recarregar} />
      )}
    </div>
  )
}
