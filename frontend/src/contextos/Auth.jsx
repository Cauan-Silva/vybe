import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { api } from '../services/api.js'

const CHAVE = 'vybe.sessao'
const AuthContexto = createContext(null)

function lerSessao() {
  try {
    const sessao = JSON.parse(localStorage.getItem(CHAVE))
    if (sessao && new Date(sessao.expiraEm) > new Date()) return sessao
  } catch {
    // armazenamento indisponível ou conteúdo inválido
  }
  return null
}

function guardar(sessao) {
  try {
    if (sessao) localStorage.setItem(CHAVE, JSON.stringify(sessao))
    else localStorage.removeItem(CHAVE)
  } catch {
    // sem armazenamento: o login vale só enquanto a aba estiver aberta
  }
}

export function AuthProvider({ children }) {
  const [sessao, setSessao] = useState(lerSessao)

  const iniciar = useCallback((nova) => {
    guardar(nova)
    setSessao(nova)
  }, [])

  const sair = useCallback(() => {
    guardar(null)
    setSessao(null)
  }, [])

  const entrar = useCallback(async (email, senha) => iniciar(await api.entrar({ email, senha })), [iniciar])

  const cadastrar = useCallback(
    async (nome, email, senha) => iniciar(await api.cadastrar({ nome, email, senha })),
    [iniciar],
  )

  /** Executa uma chamada com o token; se o servidor disser 401, encerra a sessão. */
  const chamar = useCallback(
    async (funcao) => {
      try {
        return await funcao(sessao?.token)
      } catch (e) {
        if (e.status === 401) sair()
        throw e
      }
    },
    [sessao, sair],
  )

  const valor = useMemo(
    () => ({
      sessao,
      usuario: sessao?.usuario ?? null,
      token: sessao?.token ?? null,
      entrar,
      cadastrar,
      sair,
      chamar,
    }),
    [sessao, entrar, cadastrar, sair, chamar],
  )

  return <AuthContexto.Provider value={valor}>{children}</AuthContexto.Provider>
}

export const useAuth = () => useContext(AuthContexto)
