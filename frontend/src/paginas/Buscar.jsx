import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Cartao, { Grade } from '../components/Cartao.jsx'
import { Carregando, Erro } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import ListaMusicas from '../components/ListaMusicas.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'

export default function Buscar() {
  const [parametros, setParametros] = useSearchParams()
  const termoUrl = parametros.get('q') ?? ''
  const [texto, setTexto] = useState(termoUrl)
  const { tocarLista } = usePlayer()

  // Espera a pessoa parar de digitar (300 ms) antes de buscar; o termo fica na URL.
  useEffect(() => {
    const espera = setTimeout(() => {
      const limpo = texto.trim()
      if (limpo !== termoUrl) setParametros(limpo ? { q: limpo } : {}, { replace: true })
    }, 300)
    return () => clearTimeout(espera)
  }, [texto, termoUrl, setParametros])

  const resultado = useCarregar(() => (termoUrl ? api.buscar(termoUrl) : null), [termoUrl])
  // Busca no Jamendo em paralelo. Um erro lá não atrapalha os resultados daqui.
  const jamendo = useCarregar(() => (termoUrl ? api.jamendoBusca(termoUrl) : []), [termoUrl])
  const artistas = useCarregar(() => api.artistas(), [])

  const r = resultado.dados
  const doJamendo = jamendo.dados ?? []
  const vazio =
    r && !r.musicas.length && !r.artistas.length && !r.albuns.length && !jamendo.carregando && !doJamendo.length

  return (
    <div className="pagina">
      <h1 className="visualmente-oculto">Buscar</h1>
      <label className="busca">
        <Icone nome="buscar" />
        <span className="visualmente-oculto">Buscar músicas, artistas ou álbuns</span>
        <input
          type="search"
          autoFocus
          placeholder="O que você quer ouvir?"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
        />
      </label>

      {!termoUrl && artistas.dados?.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Navegue pelos artistas</h2>
          <Grade>
            {artistas.dados.map((a) => (
              <Cartao key={a.id} para={`/artista/${a.id}`} titulo={a.nome} subtitulo="Artista" redonda />
            ))}
          </Grade>
        </section>
      )}

      {termoUrl && resultado.carregando && <Carregando texto="Buscando…" />}
      {resultado.erro && <Erro erro={resultado.erro} onTentar={resultado.recarregar} />}
      {vazio && <p className="estado">Nada encontrado para “{termoUrl}”. Tente outro nome.</p>}

      {r?.musicas.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Músicas</h2>
          <ListaMusicas musicas={r.musicas} onAlterada={resultado.recarregar} />
        </section>
      )}

      {termoUrl && (doJamendo.length > 0 || jamendo.erro) && (
        <section className="secao">
          <div className="titulo-com-nota">
            <h2 className="titulo-secao">No Jamendo</h2>
            <p className="nota-secao">Músicas livres, sob licença Creative Commons</p>
          </div>
          {jamendo.erro ? (
            <p className="estado">{jamendo.erro.message}</p>
          ) : (
            <ListaMusicas musicas={doJamendo} />
          )}
        </section>
      )}

      {r?.artistas.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Artistas</h2>
          <Grade>
            {r.artistas.map((a) => (
              <Cartao key={a.id} para={`/artista/${a.id}`} titulo={a.nome} subtitulo="Artista" redonda />
            ))}
          </Grade>
        </section>
      )}

      {r?.albuns.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Álbuns</h2>
          <Grade>
            {r.albuns.map((a) => (
              <Cartao
                key={a.id}
                para={`/album/${a.id}`}
                capa={a.capaUrl}
                titulo={a.titulo}
                subtitulo={a.artistaNome}
                onTocar={async () => tocarLista((await api.album(a.id)).musicas, 0)}
              />
            ))}
          </Grade>
        </section>
      )}
    </div>
  )
}
