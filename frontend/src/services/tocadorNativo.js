/*
 * Ponte com o código nativo do app Android (plugin "Tocador", em mobile/android).
 *
 * No Android, o sistema suspende o app pouco depois de a tela apagar, e a música para.
 * O plugin liga um "serviço em primeiro plano" de reprodução de mídia enquanto toca: o
 * Android mantém o app vivo e mostra uma notificação com a música e os botões anterior,
 * tocar/pausar e próxima. Os botões voltam para cá como "acao".
 *
 * Na versão web e no programa para Windows nada disso existe, e as funções não fazem nada.
 */
import { Capacitor, registerPlugin } from '@capacitor/core'

const Tocador = registerPlugin('Tocador')

export function temTocadorNativo() {
  try {
    return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable('Tocador')
  } catch {
    return false
  }
}

/**
 * Mostra (ou atualiza) a notificação com a música. "fundo" diz se essa música pode seguir
 * tocando com o app em segundo plano (áudio comum sim; player do YouTube não).
 */
export function atualizarTocador({ titulo, artista, tocando, fundo = true }) {
  if (!temTocadorNativo()) return
  Tocador.atualizar({
    titulo: titulo || 'VYBE',
    artista: artista || '',
    tocando: Boolean(tocando),
    fundo: Boolean(fundo),
  }).catch(() => {})
}

/** Tira a notificação e deixa o Android suspender o app normalmente. */
export function pararTocador() {
  if (!temTocadorNativo()) return
  Tocador.parar().catch(() => {})
}

/** Botões da notificação: "alternar", "proxima" ou "anterior". Devolve a função que para de ouvir. */
export function aoComandoDoTocador(funcao) {
  if (!temTocadorNativo()) return () => {}
  const ouvinte = Tocador.addListener('acao', (evento) => funcao(evento?.acao))
  return () => {
    Promise.resolve(ouvinte).then((o) => o?.remove?.())
  }
}
