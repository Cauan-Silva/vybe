import { useEffect, useRef } from 'react'

/*
 * Roda de cores: a matiz é o ângulo (vermelho no alto, girando no sentido do relógio) e a
 * saturação é a distância do centro. No centro ficam os cinzas (cores acromáticas, sem
 * matiz nenhuma); na borda, as cores mais vivas.
 *
 * Cada cor do tema é um marcador. Arrastar (ou tocar) move o marcador ativo; tocar perto de
 * outro marcador passa a mexer nele. Pelo teclado: setas ←/→ giram a matiz, ↑/↓ mudam a
 * saturação (com Shift, passos maiores).
 */

const RESOLUCAO = 320 // pixels do desenho (o CSS ajusta o tamanho na tela)
const CLARIDADE_DA_RODA = 0.55
const ALCANCE_DO_MARCADOR = 0.12 // fração do raio: tocar a essa distância pega o marcador

function hslParaRgb(h, s, l) {
  const k = (n) => (n + h / 30) % 12
  const a = s * Math.min(l, 1 - l)
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)))
  return [Math.round(f(0) * 255), Math.round(f(8) * 255), Math.round(f(4) * 255)]
}

function desenhar(canvas) {
  const n = canvas.width
  const contexto = canvas.getContext('2d')
  if (!contexto) return
  const imagem = contexto.createImageData(n, n)
  const raio = n / 2
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      const dx = x + 0.5 - raio
      const dy = y + 0.5 - raio
      const distancia = Math.hypot(dx, dy)
      const borda = Math.max(0, Math.min(1, raio - distancia)) // contorno suave
      if (borda === 0) continue
      const matiz = (Math.atan2(dx, -dy) * 180) / Math.PI
      const [r, g, b] = hslParaRgb((matiz + 360) % 360, Math.min(1, distancia / raio), CLARIDADE_DA_RODA)
      const i = (y * n + x) * 4
      imagem.data[i] = r
      imagem.data[i + 1] = g
      imagem.data[i + 2] = b
      imagem.data[i + 3] = Math.round(borda * 255)
    }
  }
  contexto.putImageData(imagem, 0, 0)
}

/** Posição (0 a 1 em x e y) de uma cor na roda. */
function posicao(h, s) {
  const angulo = (h * Math.PI) / 180
  const d = (s / 100) * 0.5
  return { x: 0.5 + Math.sin(angulo) * d, y: 0.5 - Math.cos(angulo) * d }
}

/** Cor (matiz e saturação) do ponto x, y (0 a 1) da roda. */
function corDoPonto(x, y) {
  const dx = x - 0.5
  const dy = y - 0.5
  const h = Math.round(((Math.atan2(dx, -dy) * 180) / Math.PI + 360) % 360)
  const s = Math.round(Math.min(1, Math.hypot(dx, dy) / 0.5) * 100)
  return { h, s }
}

const rodar = (h, passo) => (((h + passo) % 360) + 360) % 360
const limitar = (s) => Math.max(0, Math.min(100, s))

/**
 * pontos: [{ id, nome, h, s }]; ativo: id do marcador que se mexe;
 * onAtivar(id); onMudar(id, h, s).
 */
export default function RodaDeCores({ pontos, ativo, onAtivar, onMudar, rotulo = 'Roda de cores' }) {
  const canvasRef = useRef(null)
  const rodaRef = useRef(null)
  const arrastandoRef = useRef(null)
  const marcadoresRef = useRef({})

  useEffect(() => {
    if (canvasRef.current) desenhar(canvasRef.current)
  }, [])

  function pontoDoEvento(e) {
    const caixa = rodaRef.current.getBoundingClientRect()
    return { x: (e.clientX - caixa.left) / caixa.width, y: (e.clientY - caixa.top) / caixa.height }
  }

  function aoPressionar(e) {
    if (e.button !== undefined && e.button !== 0) return
    const { x, y } = pontoDoEvento(e)
    if (Math.hypot(x - 0.5, y - 0.5) > 0.53) return // fora da roda
    e.preventDefault()
    // Tocou perto de um marcador? Passa a mexer nele (o mais perto, se houver mais de um).
    let alvo = ativo
    let menor = ALCANCE_DO_MARCADOR / 2
    for (const p of pontos) {
      const c = posicao(p.h, p.s)
      const d = Math.hypot(c.x - x, c.y - y)
      if (d < menor) {
        menor = d
        alvo = p.id
      }
    }
    if (alvo !== ativo) onAtivar(alvo)
    arrastandoRef.current = alvo
    rodaRef.current.setPointerCapture?.(e.pointerId)
    // Tocou num marcador: só seleciona (para não pular de lugar). Fora deles: move até ali.
    if (menor === ALCANCE_DO_MARCADOR / 2) {
      const { h, s } = corDoPonto(x, y)
      onMudar(alvo, h, s)
    }
    marcadoresRef.current[alvo]?.focus({ preventScroll: true })
  }

  function aoMover(e) {
    const alvo = arrastandoRef.current
    if (alvo === null) return
    const { x, y } = pontoDoEvento(e)
    const { h, s } = corDoPonto(x, y)
    onMudar(alvo, h, s)
  }

  function aoSoltar(e) {
    arrastandoRef.current = null
    rodaRef.current?.releasePointerCapture?.(e.pointerId)
  }

  function aoTeclar(e, p) {
    const passo = e.shiftKey ? 15 : 3
    const mudancas = {
      ArrowRight: [rodar(p.h, passo), p.s],
      ArrowLeft: [rodar(p.h, -passo), p.s],
      ArrowUp: [p.h, limitar(p.s + passo)],
      ArrowDown: [p.h, limitar(p.s - passo)],
      Home: [p.h, 0],
      End: [p.h, 100],
    }
    const nova = mudancas[e.key]
    if (!nova) return
    e.preventDefault()
    onMudar(p.id, nova[0], nova[1])
  }

  return (
    <div
      ref={rodaRef}
      className="roda-cores"
      role="group"
      aria-label={rotulo}
      onPointerDown={aoPressionar}
      onPointerMove={aoMover}
      onPointerUp={aoSoltar}
      onPointerCancel={aoSoltar}
    >
      <canvas ref={canvasRef} className="roda-cores__desenho" width={RESOLUCAO} height={RESOLUCAO} aria-hidden="true" />
      {pontos.map((p) => {
        const c = posicao(p.h, p.s)
        return (
          <button
            key={p.id}
            ref={(el) => {
              marcadoresRef.current[p.id] = el
            }}
            type="button"
            className={`roda-cores__marcador ${p.id === ativo ? 'roda-cores__marcador--ativo' : ''}`}
            style={{
              left: `${c.x * 100}%`,
              top: `${c.y * 100}%`,
              background: `hsl(${p.h} ${p.s}% ${CLARIDADE_DA_RODA * 100}%)`,
            }}
            aria-label={`${p.nome}: matiz ${p.h} graus, saturação ${p.s}%. Setas para ajustar.`}
            aria-pressed={p.id === ativo}
            onFocus={() => p.id !== ativo && onAtivar(p.id)}
            onKeyDown={(e) => aoTeclar(e, p)}
          >
            <span aria-hidden="true">{p.letra}</span>
          </button>
        )
      })}
    </div>
  )
}
