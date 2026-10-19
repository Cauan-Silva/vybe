import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { usePlayer } from '../contextos/Player.jsx'

/** Lista mostrada em Configurações (e usada como documentação dos atalhos). */
export const ATALHOS = [
  ['Espaço ou K', 'Tocar / pausar'],
  ['→ / ←', 'Avançar / voltar 5 segundos'],
  ['Shift + → / Shift + ←', 'Próxima / anterior'],
  ['↑ / ↓', 'Aumentar / diminuir o volume'],
  ['M', 'Silenciar / ativar o som'],
  ['S', 'Aleatório'],
  ['R', 'Repetir (desligado, a fila toda, só esta)'],
  ['Ctrl + K ou /', 'Buscar'],
]

const PASSO_SEGUNDOS = 5
const PASSO_VOLUME = 0.05

/** Digitando num campo, os atalhos ficam quietos (menos o Ctrl+K). */
function digitando(alvo) {
  return Boolean(alvo?.closest?.('input, textarea, select, [contenteditable="true"], [contenteditable=""]'))
}

/**
 * Atalhos de teclado do app inteiro. Enquanto o foco está num botão ou link, Espaço e
 * Enter continuam fazendo o que fazem nesses elementos.
 */
export function useAtalhos() {
  const p = usePlayer()
  const navegar = useNavigate()
  const { pathname } = useLocation()

  useEffect(() => {
    const focarBusca = () => {
      // Na página atual tem um campo de busca (Buscar, Vídeos)? Usa ele; senão vai para Buscar.
      const campo = document.querySelector('#conteudo input[type="search"]')
      if (campo) {
        campo.focus()
        campo.select()
      } else {
        navegar('/buscar') // o campo de lá já abre com o foco
      }
    }

    const aoTeclar = (e) => {
      if (e.defaultPrevented || e.isComposing) return
      const tecla = e.key
      const ctrl = e.ctrlKey || e.metaKey

      if (ctrl && !e.altKey && !e.shiftKey && tecla.toLowerCase() === 'k') {
        e.preventDefault()
        focarBusca()
        return
      }
      if (ctrl || e.altKey || digitando(e.target)) return

      const emControle = Boolean(e.target?.closest?.('button, a, [role="menuitem"], summary'))
      let feito = true
      switch (tecla) {
        case ' ':
          if (emControle) return // Espaço num botão é clique nele
          p.alternarPlay()
          break
        case 'k':
        case 'K':
          p.alternarPlay()
          break
        case 'ArrowRight':
          if (e.shiftKey) p.proxima()
          else p.avancar(PASSO_SEGUNDOS)
          break
        case 'ArrowLeft':
          if (e.shiftKey) p.anterior()
          else p.avancar(-PASSO_SEGUNDOS)
          break
        case 'ArrowUp':
          p.ajustarVolume(PASSO_VOLUME)
          break
        case 'ArrowDown':
          p.ajustarVolume(-PASSO_VOLUME)
          break
        case 'm':
        case 'M':
          p.setMudo(!p.mudo)
          break
        case 's':
        case 'S':
          p.alternarAleatorio()
          break
        case 'r':
        case 'R':
          p.alternarRepetir()
          break
        case '/':
          focarBusca()
          break
        default:
          feito = false
      }
      if (feito) e.preventDefault()
    }

    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [p, navegar, pathname])
}
