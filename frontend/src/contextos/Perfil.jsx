import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../services/api.js'
import { useAuth } from './Auth.jsx'

/*
 * Perfil do usuário logado (foto, banner, frase), carregado uma vez e compartilhado:
 * a barra lateral mostra a foto, e a página do perfil edita.
 */
const PerfilContexto = createContext(null)

export function PerfilProvider({ children }) {
  const { sessao, chamar, atualizarUsuario } = useAuth()
  const [perfil, setPerfilEstado] = useState(null)
  const idUsuario = sessao?.usuario?.id

  useEffect(() => {
    if (!idUsuario) {
      setPerfilEstado(null)
      return undefined
    }
    let cancelado = false
    chamar((t) => api.perfil(t))
      .then((p) => !cancelado && setPerfilEstado(p))
      .catch(() => {})
    return () => {
      cancelado = true
    }
    // recarrega só quando troca de conta
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idUsuario])

  /** Guarda o perfil novo (vindo da API) e mantém o nome da sessão igual. */
  const setPerfil = useCallback(
    (novo) => {
      setPerfilEstado(novo)
      if (novo?.nome) atualizarUsuario({ nome: novo.nome })
    },
    [atualizarUsuario],
  )

  const valor = useMemo(() => ({ perfil, setPerfil }), [perfil, setPerfil])
  return <PerfilContexto.Provider value={valor}>{children}</PerfilContexto.Provider>
}

export const usePerfil = () => useContext(PerfilContexto)
