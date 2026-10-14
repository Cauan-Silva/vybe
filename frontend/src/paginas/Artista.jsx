import { useParams } from 'react-router-dom'
import Cabecalho from '../components/Cabecalho.jsx'
import Cartao, { Grade } from '../components/Cartao.jsx'
import { Carregando, Erro } from '../components/Estados.jsx'
import ListaMusicas from '../components/ListaMusicas.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api } from '../services/api.js'
import { plural } from '../utils/formato.js'
import { useCarregar } from '../utils/useCarregar.js'
import { useTocarLista } from '../utils/useTocarLista.js'

export default function Artista() {
  const { id } = useParams()
  const { dados: artista, erro, carregando, recarregar } = useCarregar(() => api.artista(id), [id])
  const { tocar, tocandoEsta } = useTocarLista(artista?.musicas)
  const { tocarLista } = usePlayer()

  if (carregando) return <Carregando />
  if (erro) return <Erro erro={erro} onTentar={recarregar} />

  return (
    <div className="pagina">
      <Cabecalho
        titulo={artista.nome}
        tipo="Artista"
        redonda
        onTocar={artista.musicas.length ? tocar : undefined}
        tocandoEsta={tocandoEsta}
      >
        {plural(artista.musicas.length, 'música', 'músicas')}, {plural(artista.albuns.length, 'álbum', 'álbuns')}
      </Cabecalho>

      <section className="secao">
        <h2 className="titulo-secao">Músicas</h2>
        <ListaMusicas musicas={artista.musicas} onAlterada={recarregar} />
      </section>

      {artista.albuns.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Discografia</h2>
          <Grade>
            {artista.albuns.map((a) => (
              <Cartao
                key={a.id}
                para={`/album/${a.id}`}
                capa={a.capaUrl}
                titulo={a.titulo}
                onTocar={() => tocarLista(artista.musicas.filter((m) => m.albumId === a.id), 0)}
              />
            ))}
          </Grade>
        </section>
      )}
    </div>
  )
}
