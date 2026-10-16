import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { GradeVideos, PainelVideo } from '../components/Videos.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api } from '../services/api.js'
import { numeroCompacto } from '../utils/formato.js'
import { useCarregar } from '../utils/useCarregar.js'
import { inicioDe, useMeusVideos } from '../utils/useMeusVideos.js'

/** Página de um canal do YouTube: dados do canal e os vídeos mais recentes. */
export default function CanalVideos() {
  const { id } = useParams()
  const { sessao, chamar } = useAuth()
  const { pausar } = usePlayer()
  const meus = useMeusVideos()
  const [aberto, setAberto] = useState(null)
  const canal = useCarregar(() => (sessao ? chamar((t) => api.youtubeCanal(id, t)) : null), [id, sessao])

  if (!sessao) return <PrecisaEntrar texto="Entre no VYBE para ver canais do YouTube." />
  if (canal.carregando) return <Carregando />
  if (canal.erro) return <Erro erro={canal.erro} onTentar={canal.recarregar} />

  const c = canal.dados
  const detalhes = [
    Number.isFinite(c.inscritos) ? `${numeroCompacto(c.inscritos)} inscritos` : null,
    Number.isFinite(c.totalVideos) ? `${numeroCompacto(c.totalVideos)} vídeos` : null,
  ].filter(Boolean)

  function abrir(video) {
    pausar()
    setAberto(video)
    document.getElementById('conteudo')?.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <div className="pagina">
      <Link to="/videos" className="link-discreto voltar-videos">
        ← Vídeos
      </Link>

      <header className="canal-cabecalho">
        {c.avatarUrl ? (
          <img className="canal-cabecalho__avatar" src={c.avatarUrl} alt="" />
        ) : (
          <span className="canal-cabecalho__avatar canal-cabecalho__avatar--vazio">
            <Icone nome="video" tamanho={32} />
          </span>
        )}
        <div>
          <h1 className="titulo-pagina">{c.nome}</h1>
          {detalhes.length > 0 && <p className="nota-secao">{detalhes.join(' · ')}</p>}
          <a
            className="link-discreto link-externo"
            href={`https://www.youtube.com/channel/${encodeURIComponent(c.id)}`}
            target="_blank"
            rel="noreferrer"
          >
            Abrir no YouTube <Icone nome="externo" tamanho={14} />
          </a>
        </div>
      </header>

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

      <section className="secao">
        <h2 className="titulo-secao">Vídeos recentes</h2>
        {c.videos.length === 0 ? (
          <p className="estado">Este canal não tem vídeos públicos que possam ser assistidos fora do YouTube.</p>
        ) : (
          <GradeVideos videos={c.videos} meus={meus} abertoId={aberto?.id} mostrarCanal={false} onAbrir={abrir} />
        )}
      </section>
    </div>
  )
}
