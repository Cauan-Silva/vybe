import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { definirModoLeve, lerModoLeve } from '../utils/modoLeve.js'
import { aoReabrirPonte, enviarAoPrograma } from '../utils/programa.js'

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
  { id: 'personalizado', nome: 'Personalizado', cores: null }, // cores vêm de "personalizado" abaixo
]

/**
 * Tema personalizado: cada cor é um ponto na roda de cores. "destaque", "secundaria" e "fundo"
 * são a matiz (0 a 360, o ângulo na roda); "satDestaque", "satSecundaria" e "saturacao" (a do
 * fundo) vão de 0 (centro da roda: cinza, sem cor) a 100 (borda: a cor mais viva).
 */
export const PERSONALIZADO_PADRAO = {
  destaque: 190,
  satDestaque: 95,
  secundaria: 265,
  satSecundaria: 80,
  fundo: 250,
  saturacao: 45,
}

/** Lê um tema personalizado salvo; os de antes da roda (sem as saturações das cores) ganham as padrão. */
export function lerPersonalizado(p) {
  if (!p || typeof p !== 'object') return null
  const ok = (v, max) => Number.isFinite(v) && v >= 0 && v <= max
  if (!ok(p.destaque, 360) || !ok(p.secundaria, 360) || !ok(p.fundo, 360) || !ok(p.saturacao, 100)) return null
  return {
    destaque: p.destaque,
    satDestaque: ok(p.satDestaque, 100) ? p.satDestaque : PERSONALIZADO_PADRAO.satDestaque,
    secundaria: p.secundaria,
    satSecundaria: ok(p.satSecundaria, 100) ? p.satSecundaria : PERSONALIZADO_PADRAO.satSecundaria,
    fundo: p.fundo,
    saturacao: p.saturacao,
  }
}

const hsl = (h, s, l, a) => `hsl(${Math.round(h)} ${Math.round(s)}% ${l}%${a !== undefined ? ` / ${a}` : ''})`

/**
 * Variáveis do tema personalizado, no mesmo formato dos temas prontos (styles.css).
 * O destaque fica sempre claro (com texto escuro por cima) e o fundo sempre escuro,
 * então qualquer combinação de matizes continua legível.
 */
export function coresPersonalizadas({
  destaque,
  satDestaque = PERSONALIZADO_PADRAO.satDestaque,
  secundaria,
  satSecundaria = PERSONALIZADO_PADRAO.satSecundaria,
  fundo,
  saturacao,
}) {
  const limite = (v) => Math.max(0, Math.min(100, v))
  const sf = limite(saturacao) / 100
  const sd = limite(satDestaque)
  const ss = limite(satSecundaria)
  return {
    '--fundo': hsl(fundo, 60 * sf, 4),
    '--painel': hsl(fundo, 50 * sf, 8),
    '--painel-2': hsl(fundo, 45 * sf, 11.5),
    '--painel-3': hsl(fundo, 42 * sf, 17),
    '--linha': hsl(fundo, 38 * sf, 24),
    '--topo': hsl(fundo, 70 * sf, 13),
    '--texto': hsl(fundo, 40 * sf, 95),
    '--texto-suave': hsl(fundo, 30 * sf + 8, 72),
    '--texto-fraco': hsl(fundo, 20 * sf + 6, 50),
    // No centro da roda (saturação 0) as cores viram cinzas: o destaque é um cinza claro.
    '--rotulo': hsl(destaque, sd, sd < 15 ? 68 : 60),
    '--rotulo-escuro': hsl(destaque, sd * 0.9, 8),
    '--violeta': hsl(secundaria, ss, ss < 15 ? 62 : 56),
    '--brilho': hsl(destaque, sd, 60, 0.45),
    '--grafico-musica': hsl(destaque, sd * 0.8, sd < 15 ? 52 : 42),
    '--grafico-video': hsl(secundaria, ss * 0.88, sd < 15 && ss < 15 ? 76 : 60),
  }
}

/** As cores do cartão de prévia do tema personalizado. */
export function amostraPersonalizada(p) {
  const c = coresPersonalizadas(p)
  return [c['--fundo'], c['--rotulo'], c['--violeta']]
}

const PADRAO = {
  tema: 'neon',
  texto: 'normal', // 'normal' | 'grande'
  luzDinamica: true,
  luzAmbiente: true,
  intensidadeAmbiente: 'media', // 'suave' | 'media' | 'forte'
  tamanhoAmbiente: 'medio', // 'pequeno' | 'medio' | 'grande'
  ambienteTempoReal: true, // só tem efeito no programa
  personalizado: PERSONALIZADO_PADRAO,
  discordAtivo: false, // programa: mostrar no Discord o que está tocando
  discordClientId: '', // ID do aplicativo criado em discord.com/developers
}

/** ID de aplicativo do Discord: só números (o Discord usa 17 a 20 dígitos). */
export const ID_DISCORD = /^\d{15,25}$/

const VALIDOS = {
  tema: TEMAS.map((t) => t.id),
  texto: ['normal', 'grande'],
  luzDinamica: [true, false],
  luzAmbiente: [true, false],
  intensidadeAmbiente: ['suave', 'media', 'forte'],
  tamanhoAmbiente: ['pequeno', 'medio', 'grande'],
  ambienteTempoReal: [true, false],
  discordAtivo: [true, false],
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
  const personalizado = lerPersonalizado(salvas.personalizado)
  if (personalizado) resultado.personalizado = personalizado
  if (typeof salvas.discordClientId === 'string' && ID_DISCORD.test(salvas.discordClientId)) {
    resultado.discordClientId = salvas.discordClientId
  }
  return resultado
}

/** Aplica no <html>: o CSS faz o resto (temas em [data-tema], texto em .texto-grande). */
export function aplicarPreferencias(p) {
  const raiz = document.documentElement
  raiz.dataset.tema = p.tema
  raiz.classList.toggle('texto-grande', p.texto === 'grande')
  raiz.style.colorScheme = p.tema === 'claro' ? 'light' : 'dark'
  // Tema personalizado: as variáveis vão direto no <html>; nos outros temas, saem.
  const cores = coresPersonalizadas(p.personalizado ?? PERSONALIZADO_PADRAO)
  for (const [nome, valor] of Object.entries(cores)) {
    if (p.tema === 'personalizado') raiz.style.setProperty(nome, valor)
    else raiz.style.removeProperty(nome)
  }
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

  /** Volta a aparência ao padrão (o Discord não é aparência: fica como está). */
  const restaurar = useCallback(() => {
    setPreferencias((atuais) => {
      const novas = { ...PADRAO, discordAtivo: atuais.discordAtivo, discordClientId: atuais.discordClientId }
      try {
        localStorage.setItem(CHAVE, JSON.stringify(novas))
      } catch {
        // sem armazenamento
      }
      aplicarPreferencias(novas)
      return novas
    })
    setModoLeve(false)
    definirModoLeve(false)
  }, [])

  // Programa: avisa como o Discord deve ficar (e de novo sempre que a ponte reabrir).
  const { discordAtivo, discordClientId } = preferencias
  useEffect(() => {
    const mandar = () => enviarAoPrograma({ tipo: 'discord', ativo: discordAtivo, clientId: discordClientId })
    mandar()
    return aoReabrirPonte(mandar)
  }, [discordAtivo, discordClientId])

  const valor = useMemo(
    () => ({ ...preferencias, modoLeve, alterar, alterarModoLeve, restaurar }),
    [preferencias, modoLeve, alterar, alterarModoLeve, restaurar],
  )

  return <PreferenciasContexto.Provider value={valor}>{children}</PreferenciasContexto.Provider>
}

export const usePreferencias = () => useContext(PreferenciasContexto)
