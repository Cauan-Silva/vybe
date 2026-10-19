import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { api } from '../services/api.js'

/** Só os campos que o servidor guarda (cópia dos dados do vídeo). */
export function dadosDoVideo(v) {
  return {
    id: v.id,
    titulo: v.titulo,
    canal: v.canal,
    canalId: v.canalId,
    capaUrl: v.capaUrl,
    duracaoSegundos: v.duracaoSegundos ?? 0,
  }
}

/** Faltando menos que isso para o fim, o vídeo recomeça do zero (igual ao servidor). */
const MARGEM_FIM = 10

/** De onde continuar: a posição salva, ou 0 se terminou, é ao vivo ou nunca foi visto. */
export function inicioDe(salvo) {
  if (!salvo || !salvo.duracaoSegundos) return 0
  return salvo.posicaoSegundos < salvo.duracaoSegundos - MARGEM_FIM ? salvo.posicaoSegundos : 0
}

/**
 * "Assistir depois", histórico e progresso dos vídeos do usuário logado.
 * Usado pela aba Vídeos e pela página do canal.
 */
export function useMeusVideos() {
  const { sessao, chamar } = useAuth()
  const avisar = useAvisos()
  const [depois, setDepois] = useState([])
  const [historico, setHistorico] = useState([])
  const [seguidos, setSeguidos] = useState([])

  useEffect(() => {
    if (!sessao) return
    let cancelado = false
    Promise.all([
      chamar((t) => api.videosDepois(t)),
      chamar((t) => api.videosHistorico(t)),
      chamar((t) => api.canaisSeguidos(t)).catch(() => []),
    ])
      .then(([d, h, s]) => {
        if (cancelado) return
        setDepois(d)
        setHistorico(h)
        setSeguidos(s)
      })
      .catch(() => {})
    return () => {
      cancelado = true
    }
  }, [sessao, chamar])

  const idsDepois = useMemo(() => new Set(depois.map((v) => v.id)), [depois])
  const idsSeguidos = useMemo(() => new Set(seguidos.map((c) => c.id)), [seguidos])

  /** Seguir / deixar de seguir. canal = { id, nome, avatarUrl }. */
  const alternarSeguir = useCallback(
    async (canal) => {
      if (!canal?.id) return
      try {
        if (idsSeguidos.has(canal.id)) {
          await chamar((t) => api.deixarDeSeguir(canal.id, t))
          setSeguidos((lista) => lista.filter((c) => c.id !== canal.id))
          avisar(`Você deixou de seguir ${canal.nome}`)
        } else {
          const salvo = await chamar((t) => api.seguirCanal(canal, t))
          setSeguidos((lista) => [salvo, ...lista.filter((c) => c.id !== salvo.id)])
          avisar(`Seguindo ${salvo.nome}: os vídeos novos aparecem em Inscrições`)
        }
      } catch (e) {
        avisar(e.message, 'erro')
      }
    },
    [idsSeguidos, chamar, avisar],
  )
  const progresso = useMemo(() => new Map(historico.map((v) => [v.id, v])), [historico])

  const alternarDepois = useCallback(
    async (video) => {
      try {
        if (idsDepois.has(video.id)) {
          await chamar((t) => api.removerVideoDepois(video.id, t))
          setDepois((lista) => lista.filter((v) => v.id !== video.id))
          avisar('Removido de Assistir depois')
        } else {
          const salvo = await chamar((t) => api.salvarVideoDepois(dadosDoVideo(video), t))
          setDepois((lista) => [salvo, ...lista.filter((v) => v.id !== salvo.id)])
          avisar('Salvo em Assistir depois')
        }
      } catch (e) {
        avisar(e.message, 'erro')
      }
    },
    [idsDepois, chamar, avisar],
  )

  /** Chamado pelo player; erros são ignorados (é só o progresso). */
  const registrarProgresso = useCallback(
    async (video, posicaoSegundos) => {
      try {
        const salvo = await chamar((t) => api.progressoVideo(dadosDoVideo(video), posicaoSegundos, t))
        setHistorico((lista) => [salvo, ...lista.filter((v) => v.id !== salvo.id)].slice(0, 30))
        setDepois((lista) => lista.map((v) => (v.id === salvo.id ? salvo : v)))
      } catch {
        // sem conexão: tenta de novo no próximo salvamento
      }
    },
    [chamar],
  )

  const limparHistorico = useCallback(async () => {
    try {
      await chamar((t) => api.limparHistoricoVideos(t))
      setHistorico([])
      setDepois((lista) => lista.map((v) => ({ ...v, assistidoEm: null, posicaoSegundos: 0 })))
      avisar('Histórico limpo')
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }, [chamar, avisar])

  return {
    depois,
    historico,
    seguidos,
    idsDepois,
    idsSeguidos,
    progresso,
    alternarDepois,
    alternarSeguir,
    registrarProgresso,
    limparHistorico,
  }
}
