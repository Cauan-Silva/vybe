import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Cartao, { Grade } from '../components/Cartao.jsx'
import { Carregando, Erro } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import ListaMusicas from '../components/ListaMusicas.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'

export default function Buscar() {
  const [parametros, setParametros] = useSearchParams()
  const termoUrl = parametros.get('q') ?? ''
  const [texto, setTexto] = useState(termoUrl)
  const { tocarLista } = usePlayer()
  const { sessao, chamar } = useAuth()
  // Termo que a pessoa pediu para buscar no YouTube (Enter ou botão). Cada busca lá gasta
  // cerca de 1% da cota diária, então não buscamos a cada letra digitada.
  const [pedidoYoutube, setPedidoYoutube] = useState(null)

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

  // Spotify: só para quem conectou com a permissão do player (Premium).
  const statusSpotify = useCarregar(() => (sessao ? chamar((t) => api.spotifyStatus(t)) : null), [sessao])
  const usaSpotify = Boolean(statusSpotify.dados?.podeTocar)
  const spotify = useCarregar(
    () => (termoUrl && usaSpotify ? chamar((t) => api.spotifyBusca(termoUrl, t)) : []),
    [termoUrl, usaSpotify],
  )

  // YouTube: precisa da chave no servidor e de login.
  const statusYoutube = useCarregar(() => api.youtubeStatus().catch(() => ({ configurado: false })), [])
  const usaYoutube = Boolean(sessao && statusYoutube.dados?.configurado)
  const buscouYoutube = usaYoutube && Boolean(termoUrl) && pedidoYoutube === termoUrl
  const youtube = useCarregar(
    () => (buscouYoutube ? chamar((t) => api.youtubeBusca(termoUrl, t)) : []),
    [termoUrl, buscouYoutube],
  )

  function aoEnviar(e) {
    e.preventDefault()
    const limpo = texto.trim()
    if (!limpo) return
    if (limpo !== termoUrl) setParametros({ q: limpo }, { replace: true })
    setPedidoYoutube(limpo)
  }

  const r = resultado.dados
  const doJamendo = jamendo.dados ?? []
  const doSpotify = spotify.dados ?? []
  const doYoutube = youtube.dados ?? []
  const vazio =
    r && !r.musicas.length && !r.artistas.length && !r.albuns.length && !jamendo.carregando && !doJamendo.length &&
    !spotify.carregando && !doSpotify.length && !usaYoutube

  return (
    <div className="pagina">
      <h1 className="visualmente-oculto">Buscar</h1>
      <form role="search" onSubmit={aoEnviar}>
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
      </form>

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

      {termoUrl && usaSpotify && (doSpotify.length > 0 || spotify.erro) && (
        <section className="secao">
          <div className="titulo-com-nota">
            <h2 className="titulo-secao">No Spotify</h2>
            <p className="nota-secao">Tocam completas com a sua conta Premium</p>
          </div>
          {spotify.erro ? <p className="estado">{spotify.erro.message}</p> : <ListaMusicas musicas={doSpotify} />}
        </section>
      )}

      {termoUrl && usaYoutube && (
        <section className="secao">
          <div className="titulo-com-nota">
            <h2 className="titulo-secao">No YouTube</h2>
            <p className="nota-secao">O vídeo aparece no canto da tela enquanto toca</p>
          </div>
          {!buscouYoutube && (
            <button type="button" className="botao botao--contorno" onClick={() => setPedidoYoutube(termoUrl)}>
              <Icone nome="buscar" tamanho={16} /> Buscar “{termoUrl}” no YouTube
            </button>
          )}
          {buscouYoutube && youtube.carregando && <Carregando texto="Buscando no YouTube…" />}
          {buscouYoutube && youtube.erro && <p className="estado">{youtube.erro.message}</p>}
          {buscouYoutube && !youtube.carregando && !youtube.erro && doYoutube.length === 0 && (
            <p className="estado">Nenhum vídeo de música encontrado.</p>
          )}
          {buscouYoutube && doYoutube.length > 0 && <ListaMusicas musicas={doYoutube} />}
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
