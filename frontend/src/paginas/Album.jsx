import { Link, useParams } from 'react-router-dom'
import Cabecalho from '../components/Cabecalho.jsx'
import { Carregando, Erro } from '../components/Estados.jsx'
import ListaMusicas from '../components/ListaMusicas.jsx'
import { api } from '../services/api.js'
import { duracaoTotal, plural } from '../utils/formato.js'
import { useCarregar } from '../utils/useCarregar.js'
import { useTocarLista } from '../utils/useTocarLista.js'

export default function Album() {
  const { id } = useParams()
  const { dados: album, erro, carregando, recarregar } = useCarregar(() => api.album(id), [id])
  const { tocar, tocandoEsta } = useTocarLista(album?.musicas)

  if (carregando) return <Carregando />
  if (erro) return <Erro erro={erro} onTentar={recarregar} />

  const unico = album.musicas.length === 1 && album.musicas[0].titulo === album.titulo

  return (
    <div className="pagina">
      <Cabecalho
        capa={album.capaUrl}
        titulo={album.titulo}
        tipo={unico ? 'Single' : 'Álbum'}
        onTocar={album.musicas.length ? tocar : undefined}
        tocandoEsta={tocandoEsta}
      >
        <Link to={`/artista/${album.artistaId}`} className="cabecalho__artista">
          {album.artistaNome}
        </Link>
        , {plural(album.musicas.length, 'música', 'músicas')}, {duracaoTotal(album.musicas)}
      </Cabecalho>
      <ListaMusicas musicas={album.musicas} mostrarAlbum={false} mostrarCapa={false} onAlterada={recarregar} />
    </div>
  )
}
