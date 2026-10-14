import { Link } from 'react-router-dom'
import { usePlayer } from '../contextos/Player.jsx'
import { duracao } from '../utils/formato.js'
import BotaoCurtir from './BotaoCurtir.jsx'
import Capa from './Capa.jsx'
import Icone from './Icone.jsx'
import MenuMusica from './MenuMusica.jsx'
import SeloLicenca from './SeloLicenca.jsx'

/**
 * Lista de músicas. Clicar numa linha toca a lista a partir dela;
 * clicar na música que já está tocando pausa/continua.
 */
export default function ListaMusicas({
  musicas,
  mostrarAlbum = true,
  mostrarCapa = true,
  onRemoverDaPlaylist,
  onAlterada,
}) {
  const { atual, tocando, tocarLista, alternarPlay } = usePlayer()

  if (!musicas?.length) return null

  function tocar(indice) {
    const musica = musicas[indice]
    if (atual?.id === musica.id) alternarPlay()
    else tocarLista(musicas, indice)
  }

  return (
    <ol className={`lista ${mostrarAlbum ? '' : 'lista--sem-album'}`}>
      {musicas.map((m, i) => {
        const ehAtual = atual?.id === m.id
        return (
          <li
            key={`${m.id}-${i}`}
            className={`lista__linha ${ehAtual ? 'lista__linha--atual' : ''}`}
            onDoubleClick={() => tocar(i)}
          >
            <button
              type="button"
              className="lista__tocar"
              aria-label={ehAtual && tocando ? `Pausar ${m.titulo}` : `Tocar ${m.titulo}`}
              onClick={() => tocar(i)}
            >
              <span className="lista__numero" aria-hidden="true">
                {ehAtual && tocando ? <span className="equalizador"><i /><i /><i /></span> : i + 1}
              </span>
              <span className="lista__icone" aria-hidden="true">
                <Icone nome={ehAtual && tocando ? 'pause' : 'play'} tamanho={16} />
              </span>
            </button>

            <div className="lista__titulo">
              {mostrarCapa && <Capa src={m.capaUrl} titulo={m.albumTitulo} className="lista__capa" />}
              <div>
                <p className="lista__nome">{m.titulo}</p>
                <span className="lista__linha-artista">
                  <SeloLicenca musica={m} />
                  <Link className="lista__artista" to={`/artista/${m.artistaId}`}>
                    {m.artistaNome}
                  </Link>
                </span>
              </div>
            </div>

            {mostrarAlbum && (
              <Link className="lista__album" to={`/album/${m.albumId}`}>
                {m.albumTitulo}
              </Link>
            )}

            <BotaoCurtir musica={m} />
            <span className="lista__duracao">{duracao(m.duracaoSegundos)}</span>
            <MenuMusica
              musica={m}
              onRemoverDaPlaylist={onRemoverDaPlaylist ? () => onRemoverDaPlaylist(i) : undefined}
              onExcluida={onAlterada}
            />
          </li>
        )
      })}
    </ol>
  )
}
