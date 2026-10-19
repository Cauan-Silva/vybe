/*
 * Conversa com o programa para Windows (Electron). Na versão web nada disso faz efeito.
 *
 * Página → programa: no Edge, window.vybeDesktopEnviar(json) é criada pelo próprio programa;
 * na janela do Electron, window.vybeDesktop.enviar(json) vem do preload.
 * Programa → página: o programa chama window.__vybeDoPrograma(mensagem), que vira o evento
 * "vybe:programa" para quem quiser ouvir (useDoPrograma).
 */
import { useEffect, useState } from 'react'

const ultimas = new Map() // última mensagem de cada tipo, para quem chegar depois
const reenvios = new Set() // funções que mandam de novo o estado atual ao programa

if (typeof window !== 'undefined') {
  window.__vybeDoPrograma = (mensagem) => {
    if (!mensagem || typeof mensagem.tipo !== 'string') return
    ultimas.set(mensagem.tipo, mensagem)
    window.dispatchEvent(new CustomEvent('vybe:programa', { detail: mensagem }))
  }
  // Chamado pelo programa quando a ponte fica pronta (a página pode ter carregado antes).
  window.__vybeReenviar = () => {
    enviarAoPrograma({ tipo: 'ola' })
    reenvios.forEach((f) => f())
  }
}

export function temPonte() {
  return typeof window.vybeDesktopEnviar === 'function' || typeof window.vybeDesktop?.enviar === 'function'
}

export function enviarAoPrograma(mensagem) {
  try {
    const json = JSON.stringify(mensagem)
    if (typeof window.vybeDesktopEnviar === 'function') window.vybeDesktopEnviar(json)
    else window.vybeDesktop?.enviar?.(json)
  } catch {
    // sem programa (versão web) ou ponte fechando
  }
}

/** Registra uma função que manda o estado atual ao programa sempre que a ponte (re)abrir. */
export function aoReabrirPonte(funcao) {
  reenvios.add(funcao)
  return () => reenvios.delete(funcao)
}

/** Última mensagem do programa de um tipo ("versao", "atualizacao", "discord-estado"...). */
export function useDoPrograma(tipo) {
  const [mensagem, setMensagem] = useState(() => ultimas.get(tipo) ?? null)
  useEffect(() => {
    const ouvir = (e) => e.detail.tipo === tipo && setMensagem(e.detail)
    window.addEventListener('vybe:programa', ouvir)
    return () => window.removeEventListener('vybe:programa', ouvir)
  }, [tipo])
  return mensagem
}

// Ao carregar a página, já se apresenta (se a ponte existir agora).
if (typeof window !== 'undefined') enviarAoPrograma({ tipo: 'ola' })
