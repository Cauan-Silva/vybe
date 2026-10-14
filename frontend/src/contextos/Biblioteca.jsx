import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../services/api.js'
import { useAuth } from './Auth.jsx'
import { useAvisos } from './Avisos.jsx'

const BibliotecaContexto = createContext(null)

/**
 * Curtidas e playlists do usuário logado, compartilhadas por todas as telas:
 * o coração fica igual em qualquer lista e a barra lateral vê playlists novas na hora.
 */
export function BibliotecaProvider({ children }) {
  const { token, chamar } = useAuth()
  const avisar = useAvisos()
  const [curtidas, setCurtidas] = useState(() => new Set())
  const [playlists, setPlaylists] = useState([])

  const recarregarPlaylists = useCallback(() => {
    if (!token) return Promise.resolve()
    return chamar((t) => api.playlists(t)).then(setPlaylists).catch(() => {})
  }, [token, chamar])

  useEffect(() => {
    if (!token) {
      setCurtidas(new Set())
      setPlaylists([])
      return
    }
    chamar((t) => api.idsCurtidos(t))
      .then((ids) => setCurtidas(new Set(ids)))
      .catch(() => {})
    recarregarPlaylists()
  }, [token, chamar, recarregarPlaylists])

  /** Atualiza a tela na hora e desfaz se o servidor recusar. */
  const alternarCurtida = useCallback(
    async (musica) => {
      const curtida = curtidas.has(musica.id)
      const alterar = (adicionar) =>
        setCurtidas((atual) => {
          const novo = new Set(atual)
          if (adicionar) novo.add(musica.id)
          else novo.delete(musica.id)
          return novo
        })

      alterar(!curtida)
      try {
        await chamar((t) => (curtida ? api.descurtir(musica.id, t) : api.curtir(musica.id, t)))
        avisar(curtida ? 'Removida das curtidas' : 'Adicionada às curtidas')
      } catch (e) {
        alterar(curtida)
        avisar(e.message, 'erro')
      }
    },
    [curtidas, chamar, avisar],
  )

  const criarPlaylist = useCallback(async () => {
    const nome = `Minha playlist nº ${playlists.length + 1}`
    const nova = await chamar((t) => api.criarPlaylist(nome, t))
    await recarregarPlaylists()
    return nova
  }, [playlists.length, chamar, recarregarPlaylists])

  const adicionarNaPlaylist = useCallback(
    async (playlist, musica) => {
      try {
        await chamar((t) => api.adicionarNaPlaylist(playlist.id, musica.id, t))
        avisar(`Adicionada a ${playlist.nome}`)
        recarregarPlaylists()
      } catch (e) {
        avisar(e.message, 'erro')
      }
    },
    [chamar, avisar, recarregarPlaylists],
  )

  const valor = useMemo(
    () => ({ curtidas, playlists, alternarCurtida, criarPlaylist, adicionarNaPlaylist, recarregarPlaylists }),
    [curtidas, playlists, alternarCurtida, criarPlaylist, adicionarNaPlaylist, recarregarPlaylists],
  )

  return <BibliotecaContexto.Provider value={valor}>{children}</BibliotecaContexto.Provider>
}

export const useBiblioteca = () => useContext(BibliotecaContexto)
