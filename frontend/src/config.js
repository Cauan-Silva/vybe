export const NOME_APP = import.meta.env.VITE_NOME_APP ?? 'VYBE'

/** Rodando dentro do programa para Windows (o Electron expõe window.vybeDesktop). */
export const EH_PROGRAMA = typeof window !== 'undefined' && Boolean(window.vybeDesktop?.programa)
