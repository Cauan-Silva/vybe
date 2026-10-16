export const NOME_APP = import.meta.env.VITE_NOME_APP ?? 'VYBE'

/** Rodando dentro do programa para Windows (o Electron expõe window.vybeDesktop). */
// No programa as telas podem abrir no Edge (modo aplicativo), onde não há window.vybeDesktop;
// por isso o build do programa também marca VITE_PROGRAMA=1.
export const EH_PROGRAMA =
  import.meta.env.VITE_PROGRAMA === '1' || (typeof window !== 'undefined' && Boolean(window.vybeDesktop?.programa))
