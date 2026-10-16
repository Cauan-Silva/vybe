/**
 * Crédito da origem da música, ao lado do nome do artista:
 * - Jamendo: selo "CC" que leva à licença Creative Commons (dar o crédito é condição de uso).
 * - Spotify: selo que leva à faixa no Spotify (as regras do Spotify pedem o link de volta).
 * - YouTube: selo que leva ao vídeo no YouTube.
 */
export default function SeloLicenca({ musica }) {
  if (musica.origem === 'SPOTIFY') return <SeloSpotify musica={musica} />
  if (musica.origem === 'YOUTUBE') return <SeloYoutube musica={musica} />
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

function SeloSpotify({ musica }) {
  const titulo = 'Do Spotify (toca com conta Premium conectada)'
  const marca = (
    <svg viewBox="0 0 24 24" width="10" height="10" aria-hidden="true" fill="currentColor">
      <path d="M12 1.5a10.5 10.5 0 1 0 0 21 10.5 10.5 0 0 0 0-21Zm4.8 15.15a.66.66 0 0 1-.9.22c-2.47-1.5-5.58-1.85-9.24-1.01a.66.66 0 1 1-.3-1.28c4-.92 7.44-.52 10.22 1.17.31.19.41.6.22.9Zm1.28-2.85a.82.82 0 0 1-1.13.27c-2.83-1.74-7.14-2.24-10.48-1.23a.82.82 0 1 1-.48-1.57c3.82-1.16 8.57-.6 11.82 1.4.39.24.51.75.27 1.13Zm.11-2.97C14.8 8.82 9.2 8.63 5.96 9.62a.99.99 0 1 1-.57-1.89c3.72-1.13 9.9-.91 13.8 1.4a.99.99 0 0 1-1 1.7Z" />
    </svg>
  )
  return musica.paginaUrl ? (
    <a
      className="selo-spotify"
      href={musica.paginaUrl}
      target="_blank"
      rel="noreferrer"
      title={titulo}
      aria-label={`${titulo} (abre no Spotify)`}
      onClick={(e) => e.stopPropagation()}
    >
      {marca}
    </a>
  ) : (
    <span className="selo-spotify" title={titulo}>
      {marca}
    </span>
  )
}

function SeloYoutube({ musica }) {
  const titulo = 'Vídeo do YouTube'
  const marca = (
    <svg viewBox="0 0 24 24" width="9" height="9" aria-hidden="true" fill="currentColor">
      <path d="M8 5.5v13l11-6.5z" />
    </svg>
  )
  return musica.paginaUrl ? (
    <a
      className="selo-youtube"
      href={musica.paginaUrl}
      target="_blank"
      rel="noreferrer"
      title={titulo}
      aria-label={`${titulo} (abre no YouTube)`}
      onClick={(e) => e.stopPropagation()}
    >
      {marca}
    </a>
  ) : (
    <span className="selo-youtube" title={titulo}>
      {marca}
    </span>
  )
}
