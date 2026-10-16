import { useState } from 'react'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { GradeVideos, PainelVideo } from '../components/Videos.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { Link } from 'react-router-dom'
import { EH_PROGRAMA } from '../config.js'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'
import { inicioDe, useMeusVideos } from '../utils/useMeusVideos.js'

/*
 * Aba Vídeos: qualquer vídeo do YouTube (não só música).
 *
 * Os vídeos não entram no catálogo de músicas: tocam no player oficial do YouTube aqui
 * mesmo, em tamanho grande. Ao começar um vídeo, a música do VYBE é pausada.
 * A busca só acontece com Enter, porque cada busca gasta cerca de 1% da cota diária;
 * categorias, canais, "Assistir depois" e histórico quase não gastam nada.
 */

/** Categorias do YouTube com lista de em alta no Brasil (o número é o id da categoria). */
const CATEGORIAS = [
  [null, 'Tudo'],
  [10, 'Música'],
  [20, 'Games'],
  [17, 'Esportes'],
  [24, 'Entretenimento'],
  [23, 'Comédia'],
  [25, 'Notícias'],
  [28, 'Ciência e tecnologia'],
  [1, 'Filmes e animação'],
]

const HISTORICO_RESUMIDO = 8

export default function Videos() {
  const { sessao, chamar } = useAuth()
  const { pausar } = usePlayer()
  const meus = useMeusVideos()
  const [texto, setTexto] = useState('')
  const [termo, setTermo] = useState('') // o que foi de fato buscado
  const [categoria, setCategoria] = useState(null)
  const [aberto, setAberto] = useState(null)
  const [historicoInteiro, setHistoricoInteiro] = useState(false)

  const status = useCarregar(() => api.youtubeStatus().catch(() => ({ configurado: false })), [])
  const configurado = Boolean(status.dados?.configurado)
  const podeBuscar = Boolean(sessao) && configurado

  const lista = useCarregar(() => {
    if (!podeBuscar) return []
    return chamar((t) => (termo ? api.youtubeVideos(termo, t) : api.youtubeEmAlta(t, categoria)))
  }, [termo, categoria, podeBuscar])

  if (!sessao) return <PrecisaEntrar texto="Entre no VYBE para assistir vídeos do YouTube." />
  if (status.carregando) return <Carregando />
  if (!configurado) {
    return (
      <div className="pagina">
        <h1 className="titulo-pagina">Vídeos</h1>
        <div className="painel-info">
          {EH_PROGRAMA ? (
            <p>
              Para assistir vídeos, informe uma chave da API do YouTube em <Link to="/configuracoes">Configurações</Link>.
            </p>
          ) : (
            <p>
              A integração com o YouTube não está configurada nesta instalação. Defina a variável{' '}
              <code>YOUTUBE_API_KEY</code> no servidor e reinicie a API.
            </p>
          )}
        </div>
      </div>
    )
  }

  function aoEnviar(e) {
    e.preventDefault()
    setTermo(texto.trim())
  }

  function abrir(video) {
    pausar()
    setAberto(video)
    document.getElementById('conteudo')?.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const videos = lista.dados ?? []
  // Começados e não terminados (a posição salva fica entre o começo e o fim).
  const continuar = meus.historico.filter((v) => inicioDe(v) > 0)
  const historicoVisivel = historicoInteiro ? meus.historico : meus.historico.slice(0, HISTORICO_RESUMIDO)
  const nomeCategoria = CATEGORIAS.find(([id]) => id === categoria)?.[1]
  const grade = (lista) => (
    <GradeVideos videos={lista} meus={meus} abertoId={aberto?.id} onAbrir={abrir} />
  )

  return (
    <div className="pagina">
      <h1 className="titulo-pagina">Vídeos</h1>

      <form role="search" onSubmit={aoEnviar}>
        <label className="busca">
          <Icone nome="buscar" />
          <span className="visualmente-oculto">Buscar vídeos no YouTube</span>
          <input
            type="search"
            placeholder="Buscar vídeos no YouTube e apertar Enter"
            value={texto}
            onChange={(e) => {
              setTexto(e.target.value)
              if (!e.target.value.trim()) setTermo('') // limpou a busca: volta para os em alta
            }}
          />
        </label>
      </form>

      {aberto && (
        <PainelVideo
          video={aberto}
          inicio={inicioDe(meus.progresso.get(aberto.id))}
          depois={meus.idsDepois.has(aberto.id)}
          onProgresso={meus.registrarProgresso}
          onAlternarDepois={meus.alternarDepois}
          onFechar={() => setAberto(null)}
        />
      )}

      {!termo && continuar.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Continuar assistindo</h2>
          {grade(continuar.slice(0, 4))}
        </section>
      )}

      {!termo && meus.depois.length > 0 && (
        <section className="secao">
          <div className="titulo-com-nota">
            <h2 className="titulo-secao">Assistir depois</h2>
            <p className="nota-secao">{meus.depois.length === 1 ? '1 vídeo' : `${meus.depois.length} vídeos`}</p>
          </div>
          {grade(meus.depois)}
        </section>
      )}

      <section className="secao">
        {termo ? (
          <h2 className="titulo-secao">Resultados para “{termo}”</h2>
        ) : (
          <>
            <div className="titulo-com-nota">
              <h2 className="titulo-secao">Em alta no Brasil</h2>
              <p className="nota-secao">Atualizado a cada hora</p>
            </div>
            <div className="chips" role="group" aria-label="Categoria">
              {CATEGORIAS.map(([id, nome]) => (
                <button
                  key={nome}
                  type="button"
                  className="chip"
                  aria-pressed={categoria === id}
                  onClick={() => setCategoria(id)}
                >
                  {nome}
                </button>
              ))}
            </div>
          </>
        )}

        {lista.carregando && <Carregando texto={termo ? 'Buscando no YouTube…' : 'Carregando…'} />}
        {lista.erro && <Erro erro={lista.erro} onTentar={lista.recarregar} />}
        {!lista.carregando && !lista.erro && videos.length === 0 && (
          <p className="estado">
            {termo
              ? 'Nenhum vídeo encontrado.'
              : categoria
                ? `Nenhum vídeo em alta em ${nomeCategoria} agora. Tente outra categoria.`
                : 'Nenhum vídeo em alta agora.'}
          </p>
        )}
        {!lista.carregando && videos.length > 0 && grade(videos)}
      </section>

      {!termo && meus.historico.length > 0 && (
        <section className="secao">
          <div className="titulo-com-acao titulo-com-acao--secao">
            <h2 className="titulo-secao">Assistidos recentemente</h2>
            <button
              type="button"
              className="link-botao"
              onClick={() => {
                if (window.confirm('Limpar o histórico de vídeos? Os vídeos em "Assistir depois" continuam lá.')) {
                  meus.limparHistorico()
                }
              }}
            >
              Limpar histórico
            </button>
          </div>
          {grade(historicoVisivel)}
          {meus.historico.length > HISTORICO_RESUMIDO && (
            <button type="button" className="botao botao--contorno botao--pequeno" onClick={() => setHistoricoInteiro((v) => !v)}>
              {historicoInteiro ? 'Mostrar menos' : `Ver todos (${meus.historico.length})`}
            </button>
          )}
        </section>
      )}
    </div>
  )
}
