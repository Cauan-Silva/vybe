/**
 * Crédito das músicas do Jamendo: um selo "CC" que leva à licença Creative Commons.
 * Dar o crédito é uma condição para usar essas faixas.
 */
export default function SeloLicenca({ musica }) {
  if (musica.origem !== 'JAMENDO') return null
  const titulo = 'Do Jamendo, sob licença Creative Commons'
  return musica.licencaUrl ? (
    <a
      className="selo-cc"
      href={musica.licencaUrl}
      target="_blank"
      rel="noreferrer"
      title={titulo}
      aria-label={`${titulo} (abre a licença)`}
      onClick={(e) => e.stopPropagation()}
    >
      CC
    </a>
  ) : (
    <span className="selo-cc" title={titulo}>
      CC
    </span>
  )
}
