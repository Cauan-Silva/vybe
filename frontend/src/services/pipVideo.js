/*
 * Janela flutuante (Picture-in-Picture) dos vídeos.
 *
 * O botão de PiP comum do navegador não alcança o vídeo: ele está dentro do iframe do
 * YouTube, que é de outro site. Por isso usamos a "Document Picture-in-Picture" (Edge e
 * Chrome): uma janelinha que fica sempre por cima das outras. Dentro dela vai a página
 * /pip-video.html do próprio VYBE com um player oficial do YouTube novo, começando do ponto em
 * que o vídeo estava. (O player não pode ficar direto na janelinha: ela é uma página em branco,
 * e o YouTube recusa players sem um endereço de origem, com o "Erro 153".) Ao fechar a
 * janelinha, quem abriu recebe o tempo atual e continua dali.
 *
 * A janela fica aqui, fora das páginas do React: dá para navegar pelo VYBE com ela aberta.
 * Ela também salva o progresso e conta o tempo assistido enquanto estiver aberta.
 */

const TOCANDO = 1
const SALVAR_A_CADA_MS = 10_000

let aberta = null // { janela, player, video, ... }

export function pipDisponivel() {
  return typeof window !== 'undefined' && 'documentPictureInPicture' in window
}

/** Há uma janela flutuante aberta (de qualquer vídeo, ou do vídeo informado)? */
export function pipAberto(videoId) {
  return Boolean(aberta) && (!videoId || aberta.video.id === videoId)
}

function tempoDe(estado) {
  try {
    const t = estado.quadro?.contentWindow?.vybePlayer?.getCurrentTime?.()
    return Number.isFinite(t) ? t : estado.inicio
  } catch {
    return estado.inicio
  }
}

/**
 * Abre a janela flutuante. Precisa ser chamada direto no clique (o navegador exige).
 * - inicio: segundo de onde começar
 * - registro: contador do tempo assistido (utils/contadorTempo.js), ou null
 * - aoProgresso(segundos): salvar a posição
 * - aoFechar(segundos, tocava): a janelinha fechou
 */
export async function abrirPip({ video, inicio = 0, registro = null, aoProgresso, aoFechar }) {
  fecharPip()
  const janela = await window.documentPictureInPicture.requestWindow({ width: 480, height: 270 })
  const doc = janela.document
  doc.title = video.titulo
  const estilo = doc.createElement('style')
  estilo.textContent = `
    html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
    #tela, #tela iframe { width: 100%; height: 100%; border: 0; display: block; }
  `
  doc.head.append(estilo)
  const tela = doc.createElement('div')
  tela.id = 'tela'
  doc.body.append(tela)

  const estado = { janela, video, inicio, quadro: null, tocando: false, relogio: null }
  aberta = estado

  const salvar = () => aoProgresso?.(Math.floor(tempoDe(estado)))

  janela.addEventListener(
    'pagehide',
    () => {
      clearInterval(estado.relogio)
      const tempo = tempoDe(estado)
      const tocava = estado.tocando
      if (aberta === estado) aberta = null
      registro?.contador.pausar()
      registro?.mandar()
      aoProgresso?.(Math.floor(tempo))
      aoFechar?.(tempo, tocava)
    },
    { once: true },
  )

  // A página do player avisa cada mudança (tocando, pausado, terminou).
  janela.__vybePipEstado = (codigo) => {
    estado.tocando = codigo === TOCANDO
    if (estado.tocando) {
      registro?.contador.tocar()
    } else {
      registro?.contador.pausar()
      salvar()
    }
  }

  const endereco = new URL('/pip-video.html', window.location.origin)
  endereco.searchParams.set('v', video.id)
  endereco.searchParams.set('t', String(Math.floor(inicio)))
  const quadro = doc.createElement('iframe')
  quadro.src = endereco.href
  quadro.allow = 'autoplay; encrypted-media; picture-in-picture; fullscreen'
  quadro.title = video.titulo
  tela.append(quadro)
  estado.quadro = quadro

  estado.relogio = setInterval(() => {
    if (!estado.tocando) return
    salvar()
    registro?.mandar()
  }, SALVAR_A_CADA_MS)
}

export function fecharPip() {
  if (!aberta) return
  try {
    aberta.janela.close() // dispara o "pagehide" acima
  } catch {
    aberta = null
  }
}
