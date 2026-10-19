import { urlArquivo } from '../services/api.js'

/** Foto de perfil, ou a inicial do nome num círculo com as cores do tema. */
export default function Avatar({ nome, fotoUrl, tamanho = 32, className = '' }) {
  const estilo = { width: tamanho, height: tamanho, fontSize: tamanho * 0.42 }
  if (fotoUrl) {
    return <img className={`avatar ${className}`} src={urlArquivo(fotoUrl)} alt="" style={estilo} />
  }
  return (
    <span className={`avatar avatar--inicial ${className}`} style={estilo} aria-hidden="true">
      {(nome ?? '?').trim().charAt(0).toUpperCase() || '?'}
    </span>
  )
}
