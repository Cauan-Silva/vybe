/** 185 -> "3:05" */
export function duracao(segundos) {
  if (!Number.isFinite(segundos) || segundos < 0) return '0:00'
  const total = Math.floor(segundos)
  const min = Math.floor(total / 60)
  const seg = String(total % 60).padStart(2, '0')
  return `${min}:${seg}`
}

/** Soma das durações: "42 min" ou "1 h 12 min" */
export function duracaoTotal(musicas) {
  const total = musicas.reduce((soma, m) => soma + (m.duracaoSegundos ?? 0), 0)
  const horas = Math.floor(total / 3600)
  const minutos = Math.round((total % 3600) / 60)
  return horas > 0 ? `${horas} h ${minutos} min` : `${minutos} min`
}

export const plural = (n, um, varios) => `${n} ${n === 1 ? um : varios}`

/** Nome do arquivo sem extensão e com espaços: "minha_musica-01.mp3" -> "minha musica 01" */
export function tituloDoArquivo(nome) {
  return nome
    .replace(/\.[^.]+$/, '')
    .replace(/[_]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

export function saudacao(data = new Date()) {
  const hora = data.getHours()
  if (hora < 5) return 'Boa noite'
  if (hora < 12) return 'Bom dia'
  if (hora < 18) return 'Boa tarde'
  return 'Boa noite'
}

/** Embaralha 0..n-1 deixando "primeiro" na frente (para o modo aleatório). */
export function embaralhar(n, primeiro) {
  const resto = []
  for (let i = 0; i < n; i++) if (i !== primeiro) resto.push(i)
  for (let i = resto.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[resto[i], resto[j]] = [resto[j], resto[i]]
  }
  return primeiro >= 0 && primeiro < n ? [primeiro, ...resto] : resto
}

const COMPACTO = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 })
const RELATIVO = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' })

/** 1234567 vira "1,2 mi". */
export function numeroCompacto(n) {
  return Number.isFinite(n) ? COMPACTO.format(n) : ''
}

/** Data ISO vira "há 3 dias", "há 2 meses"… */
export function tempoRelativo(iso) {
  const data = iso ? new Date(iso) : null
  if (!data || Number.isNaN(data.getTime())) return ''
  const segundos = (data.getTime() - Date.now()) / 1000
  const unidades = [
    ['year', 31536000],
    ['month', 2592000],
    ['week', 604800],
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ]
  for (const [unidade, tamanho] of unidades) {
    if (Math.abs(segundos) >= tamanho) return RELATIVO.format(Math.round(segundos / tamanho), unidade)
  }
  return 'agora'
}
