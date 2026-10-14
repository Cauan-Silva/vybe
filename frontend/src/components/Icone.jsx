// Ícones desenhados em SVG (24×24). "cheio" = preenchido; o resto é traço.
const ICONES = {
  play: { cheio: true, d: 'M8 5.6v12.8a1 1 0 0 0 1.5.86l10.2-6.4a1 1 0 0 0 0-1.72L9.5 4.74A1 1 0 0 0 8 5.6z' },
  pause: { cheio: true, d: 'M6.5 5h3.75v14H6.5zM13.75 5h3.75v14h-3.75z' },
  proxima: { cheio: true, d: 'M5 6.3v11.4a.8.8 0 0 0 1.24.67L14 13.2v4.3h2.5v-11H14v4.3L6.24 5.63A.8.8 0 0 0 5 6.3z' },
  anterior: { cheio: true, d: 'M19 6.3v11.4a.8.8 0 0 1-1.24.67L10 13.2v4.3H7.5v-11H10v4.3l7.76-5.17A.8.8 0 0 1 19 6.3z' },
  aleatorio: { d: 'M16 4h4v4M4 19 20 4M20 16v4h-4M14.5 14.5 20 20M4 5l5 5' },
  repetir: { d: 'M17 3l3 3-3 3M4 11V9a3 3 0 0 1 3-3h13M7 21l-3-3 3-3M20 13v2a3 3 0 0 1-3 3H4' },
  coracao: {
    d: 'M12 20s-7.2-4.4-9.1-8.8C1.6 8.1 3.6 4.6 7 4.6c2 0 3.5 1.1 5 2.9 1.5-1.8 3-2.9 5-2.9 3.4 0 5.4 3.5 4.1 6.6C19.2 15.6 12 20 12 20z',
  },
  coracaoCheio: {
    cheio: true,
    d: 'M12 20s-7.2-4.4-9.1-8.8C1.6 8.1 3.6 4.6 7 4.6c2 0 3.5 1.1 5 2.9 1.5-1.8 3-2.9 5-2.9 3.4 0 5.4 3.5 4.1 6.6C19.2 15.6 12 20 12 20z',
  },
  mais: { cheio: true, d: 'M5 10.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z' },
  volume: { d: 'M4 9.5v5h3.5L12 18.5v-13L7.5 9.5H4zM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11' },
  mudo: { d: 'M4 9.5v5h3.5L12 18.5v-13L7.5 9.5H4zM16 9.5l5 5M21 9.5l-5 5' },
  fila: { d: 'M4 6h13M4 11h13M4 16h8M16 14v6l5-3-5-3z' },
  inicio: { d: 'M3.5 11 12 3.5l8.5 7.5M5.5 9.5V20h4.5v-5.5h4V20h4.5V9.5' },
  buscar: { d: 'M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM15.5 15.5 20 20' },
  biblioteca: { d: 'M5 4v16M9.5 4v16M14 5l5 15' },
  enviar: { d: 'M12 15.5V4M7 9l5-5 5 5M4.5 14.5V19a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-4.5' },
  adicionar: { d: 'M12 5v14M5 12h14' },
  fechar: { d: 'M6 6l12 12M18 6 6 18' },
  disco: { d: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM12 9.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z' },
}

export default function Icone({ nome, tamanho = 20, ...resto }) {
  const icone = ICONES[nome]
  if (!icone) return null
  return (
    <svg
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      aria-hidden="true"
      focusable="false"
      fill={icone.cheio ? 'currentColor' : 'none'}
      stroke={icone.cheio ? 'none' : 'currentColor'}
      strokeWidth={icone.cheio ? undefined : 2}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...resto}
    >
      <path d={icone.d} />
    </svg>
  )
}
