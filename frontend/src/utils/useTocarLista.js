import { usePlayer } from '../contextos/Player.jsx'

/**
 * Botão grande de tocar das páginas: se a música atual é desta lista,
 * pausa/continua; senão começa a lista do início.
 */
export function useTocarLista(musicas) {
  const { atual, tocando, tocarLista, alternarPlay } = usePlayer()
  const lista = musicas ?? []
  const contemAtual = Boolean(atual) && lista.some((m) => m.id === atual.id)

  return {
    tocandoEsta: contemAtual && tocando,
    tocar: () => (contemAtual ? alternarPlay() : tocarLista(lista, 0)),
  }
}
