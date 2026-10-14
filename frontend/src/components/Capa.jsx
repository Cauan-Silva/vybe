import { useEffect, useState } from 'react'
import { urlArquivo } from '../services/api.js'

// Pares de cores para capas geradas quando não há imagem.
// Pares de cores neon, na família do logo.
const PARES = [
  ['#19e0ff', '#7b2ff2'],
  ['#ff5fa2', '#7b2ff2'],
  ['#19e0ff', '#0b8f7a'],
  ['#b14bff', '#2b1a7a'],
  ['#ff8a5c', '#b14bff'],
  ['#4f7bff', '#19e0ff'],
]

function hash(texto) {
  let h = 0
  for (let i = 0; i < texto.length; i++) h = (h * 31 + texto.charCodeAt(i)) | 0
  return Math.abs(h)
}

/**
 * Capa do álbum, ou uma capa gerada (degradê + inicial) quando não há imagem.
 * A mesma palavra sempre gera as mesmas cores.
 */
export default function Capa({ src, titulo = '', redonda = false, className = '' }) {
  const url = urlArquivo(src)
  const [falhou, setFalhou] = useState(false)
  useEffect(() => setFalhou(false), [url])
  const classes = `capa ${redonda ? 'capa--redonda' : ''} ${className}`

  if (url && !falhou) {
    return <img className={classes} src={url} alt="" loading="lazy" onError={() => setFalhou(true)} />
  }

  const [a, b] = PARES[hash(titulo) % PARES.length]
  return (
    <div className={`${classes} capa--gerada`} style={{ '--capa-a': a, '--capa-b': b }} aria-hidden="true">
      <span>{titulo.trim().charAt(0).toUpperCase() || '♪'}</span>
    </div>
  )
}
