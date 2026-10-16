import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { definirModoLeve, lerModoLeve } from '../utils/modoLeve.js'

/*
 * Preferências de aparência: tema, tamanho do texto, Luz dinâmica (fundo tingido pela capa
 * da música) e Luz ambiente (brilho em volta do vídeo). Ficam só neste computador
 * (localStorage), por isso tudo dentro de try/catch e sempre com valores padrão.
 */

const CHAVE = 'vybe.preferencias'

export const TEMAS = [
  { id: 'neon', nome: 'Neon', cores: ['#05040d', '#19e0ff', '#7b2ff2'] },
  { id: 'aurora', nome: 'Aurora', cores: ['#030b0a', '#2ef2a6', '#0f9d8a'] },
  { id: 'oceano', nome: 'Oceano', cores: ['#030712', '#4cc3ff', '#2f5bf2'] },
  { id: 'por-do-sol', nome: 'Pôr do sol', cores: ['#0d0507', '#ffb347', '#ff4f81'] },
  { id: 'claro', nome: 'Claro', cores: ['#eef0f8', '#0874b0', '#6b2fe0'] },
]

const PADRAO = {
  tema: 'neon',
  texto: 'normal', // 'normal' | 'grande'
  luzDinamica: true,
  luzAmbiente: true,
  intensidadeAmbiente: 'media', // 'suave' | 'media' | 'forte'
  tamanhoAmbiente: 'medio', // 'pequeno' | 'medio' | 'grande'
  ambienteTempoReal: true, // só tem efeito no programa
}

const VALIDOS = {
  tema: TEMAS.map((t) => t.id),
  texto: ['normal', 'grande'],
  luzDinamica: [true, false],
  luzAmbiente: [true, false],
  intensidadeAmbiente: ['suave', 'media', 'forte'],
  tamanhoAmbiente: ['pequeno', 'medio', 'grande'],
  ambienteTempoReal: [true, false],
}

export function lerPreferencias() {
  let salvas = {}
  try {
    salvas = JSON.parse(localStorage.getItem(CHAVE)) ?? {}
  } catch {
    salvas = {}
  }
  const resultado = { ...PADRAO }
  for (const [chave, valores] of Object.entries(VALIDOS)) {
    if (valores.includes(salvas[chave])) resultado[chave] = salvas[chave]
  }
  return resultado
}

/** Aplica no <html>: o CSS faz o resto (temas em [data-tema], texto em .texto-grande). */
export function aplicarPreferencias(p) {
  const raiz = document.documentElement
  raiz.dataset.tema = p.tema
  raiz.classList.toggle('texto-grande', p.texto === 'grande')
  raiz.style.colorScheme = p.tema === 'claro' ? 'light' : 'dark'
}

const PreferenciasContexto = createContext(null)

export function PreferenciasProvider({ children }) {
  const [preferencias, setPreferencias] = useState(lerPreferencias)
  const [modoLeve, setModoLeve] = useState(lerModoLeve)

  const alterar = useCallback((chave, valor) => {
    setPreferencias((atuais) => {
      const novas = { ...atuais, [chave]: valor }
      try {
        localStorage.setItem(CHAVE, JSON.stringify(novas))
      } catch {
        // sem armazenamento: vale só até fechar
      }
      aplicarPreferencias(novas)
      return novas
    })
  }, [])

  const alterarModoLeve = useCallback((ligado) => {
    setModoLeve(ligado)
    definirModoLeve(ligado)
  }, [])

  const restaurar = useCallback(() => {
    try {
      localStorage.removeItem(CHAVE)
    } catch {
      // nada a apagar
    }
    setPreferencias(PADRAO)
    aplicarPreferencias(PADRAO)
    setModoLeve(false)
    definirModoLeve(false)
  }, [])

  const valor = useMemo(
    () => ({ ...preferencias, modoLeve, alterar, alterarModoLeve, restaurar }),
    [preferencias, modoLeve, alterar, alterarModoLeve, restaurar],
  )

  return <PreferenciasContexto.Provider value={valor}>{children}</PreferenciasContexto.Provider>
}

export const usePreferencias = () => useContext(PreferenciasContexto)
