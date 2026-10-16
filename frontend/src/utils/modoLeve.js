/*
 * Modo leve: desliga animações, brilhos e transições, e atualiza a barra de tempo
 * com menos frequência. Pensado para quem usa o VYBE enquanto joga.
 * A escolha fica só neste computador (localStorage), por isso tudo dentro de try/catch.
 */
const CHAVE = 'vybe.modoLeve'

export function lerModoLeve() {
  try {
    return localStorage.getItem(CHAVE) === '1'
  } catch {
    return false
  }
}

/** Liga ou desliga, guarda a escolha e avisa quem estiver ouvindo (ex.: o player). */
export function definirModoLeve(ligado) {
  try {
    localStorage.setItem(CHAVE, ligado ? '1' : '0')
  } catch {
    // sem armazenamento: vale só até fechar
  }
  aplicarModoLeve(ligado)
  window.dispatchEvent(new CustomEvent('vybe:modo-leve', { detail: ligado }))
}

export function aplicarModoLeve(ligado) {
  document.documentElement.classList.toggle('modo-leve', ligado)
}
