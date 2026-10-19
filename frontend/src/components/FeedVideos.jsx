import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'
import { Carregando, Erro } from './Estados.jsx'
import Icone from './Icone.jsx'
import { GradeVideos } from './Videos.jsx'

/*
 * Seções personalizadas da aba Vídeos:
 * - "Para você": vídeos novos dos canais que você mais assistiu nos últimos 30 dias;
 * - "Inscrições": vídeos novos dos canais que você segue;
 * - "Seus interesses": vídeos recentes sobre até 3 temas que você escolhe.
 */

const MAXIMO_INTERESSES = 3

/** "A", "A e B", "A, B e C" */
function juntarNomes(nomes) {
  if (nomes.length <= 1) return nomes[0] ?? ''
  return `${nomes.slice(0, -1).join(', ')} e ${nomes.at(-1)}`
}

export function SecaoParaVoce({ meus, abertoId, onAbrir }) {
  const { sessao, chamar } = useAuth()
  const feed = useCarregar(() => (sessao ? chamar((t) => api.videosParaVoce(t)) : null), [sessao])
  const videos = feed.dados?.videos ?? []
  if (feed.carregando || feed.erro || videos.length === 0) return null

  const nomes = feed.dados.canais.map((c) => c.nome).filter(Boolean)
  return (
    <section className="secao">
      <div className="titulo-com-nota">
        <h2 className="titulo-secao">Para você</h2>
        <p className="nota-secao">Porque você assiste {juntarNomes(nomes.slice(0, 3))}</p>
      </div>
      <GradeVideos videos={videos} meus={meus} abertoId={abertoId} onAbrir={onAbrir} />
    </section>
  )
}

export function SecaoInscricoes({ meus, abertoId, onAbrir }) {
  const { sessao, chamar } = useAuth()
  // recarrega quando a pessoa segue ou deixa de seguir alguém
  const chave = meus.seguidos.map((c) => c.id).join(',')
  const feed = useCarregar(
    () => (sessao && chave ? chamar((t) => api.videosInscricoes(t)) : null),
    [sessao, chave],
  )
  if (meus.seguidos.length === 0) return null
  const videos = feed.dados?.videos ?? []

  return (
    <section className="secao">
      <div className="titulo-com-nota">
        <h2 className="titulo-secao">Inscrições</h2>
        <p className="nota-secao">
          {meus.seguidos.length === 1 ? '1 canal' : `${meus.seguidos.length} canais`} que você segue
        </p>
      </div>
      <ul className="canais-seguidos" aria-label="Canais que você segue">
        {meus.seguidos.map((c) => (
          <li key={c.id}>
            <Link to={`/videos/canal/${c.id}`} className="canal-seguido" title={c.nome}>
              {c.avatarUrl ? (
                <img src={c.avatarUrl} alt="" loading="lazy" />
              ) : (
                <span className="canal-seguido__inicial" aria-hidden="true">
                  {c.nome.charAt(0).toUpperCase()}
                </span>
              )}
              <span className="canal-seguido__nome">{c.nome}</span>
            </Link>
          </li>
        ))}
      </ul>
      {feed.carregando && <Carregando texto="Buscando os vídeos novos…" />}
      {feed.erro && <Erro erro={feed.erro} onTentar={feed.recarregar} />}
      {!feed.carregando && !feed.erro && videos.length === 0 && (
        <p className="estado">Nenhum vídeo novo dos canais que você segue.</p>
      )}
      {videos.length > 0 && <GradeVideos videos={videos} meus={meus} abertoId={abertoId} onAbrir={onAbrir} />}
    </section>
  )
}

export function SecaoInteresses({ meus, abertoId, onAbrir }) {
  const { sessao, chamar } = useAuth()
  const avisar = useAvisos()
  const lista = useCarregar(() => (sessao ? chamar((t) => api.interesses(t)) : []), [sessao])
  const [texto, setTexto] = useState('')
  const [salvando, setSalvando] = useState(false)
  const interesses = lista.dados ?? []
  const cheio = interesses.length >= MAXIMO_INTERESSES

  async function adicionar(e) {
    e.preventDefault()
    const termo = texto.trim()
    if (termo.length < 2) return
    setSalvando(true)
    try {
      const novo = await chamar((t) => api.adicionarInteresse(termo, t))
      lista.setDados((atuais) => [...(atuais ?? []).filter((i) => i.id !== novo.id), novo])
      setTexto('')
    } catch (erro) {
      avisar(erro.message, 'erro')
    } finally {
      setSalvando(false)
    }
  }

  async function remover(interesse) {
    try {
      await chamar((t) => api.removerInteresse(interesse.id, t))
      lista.setDados((atuais) => (atuais ?? []).filter((i) => i.id !== interesse.id))
    } catch (erro) {
      avisar(erro.message, 'erro')
    }
  }

  if (lista.carregando && !lista.dados) return null

  return (
    <>
      <section className="secao interesses">
        <div className="titulo-com-nota">
          <h2 className="titulo-secao">Seus interesses</h2>
          <p className="nota-secao">Vídeos recentes sobre o que você acompanha</p>
        </div>
        <div className="interesses__barra">
          {interesses.length > 0 && (
            <ul className="chips" aria-label="Seus interesses">
              {interesses.map((i) => (
                <li key={i.id}>
                  <span className="chip chip--interesse">
                    <a href={`#interesse-${i.id}`}>{i.termo}</a>
                    <button type="button" aria-label={`Remover o interesse ${i.termo}`} onClick={() => remover(i)}>
                      <Icone nome="fechar" tamanho={14} />
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          )}
          {!cheio && (
            <form className="interesses__form" onSubmit={adicionar}>
              <label className="visualmente-oculto" htmlFor="novo-interesse">
                Novo interesse
              </label>
              <input
                id="novo-interesse"
                value={texto}
                maxLength={60}
                onChange={(e) => setTexto(e.target.value)}
                placeholder={interesses.length ? 'Outro tema…' : 'Ex.: valorant, VCT, valorant clips'}
                autoComplete="off"
              />
              <button type="submit" className="botao botao--pequeno" disabled={salvando || texto.trim().length < 2}>
                <Icone nome="adicionar" tamanho={16} /> Acompanhar
              </button>
            </form>
          )}
        </div>
        <p className="texto-apoio-claro interesses__dica">
          {cheio
            ? `Você já acompanha ${MAXIMO_INTERESSES} temas. Remova um para trocar.`
            : `Até ${MAXIMO_INTERESSES} temas. Cada um vira uma fileira com os vídeos mais relevantes dos últimos 30 dias.`}
        </p>
      </section>

      {interesses.map((i) => (
        <FileiraInteresse key={i.id} interesse={i} meus={meus} abertoId={abertoId} onAbrir={onAbrir} />
      ))}
    </>
  )
}

function FileiraInteresse({ interesse, meus, abertoId, onAbrir }) {
  const { chamar } = useAuth()
  const videos = useCarregar(() => chamar((t) => api.videosDoInteresse(interesse.id, t)), [interesse.id])
  return (
    <section className="secao" id={`interesse-${interesse.id}`}>
      <div className="titulo-com-nota">
        <h2 className="titulo-secao">Sobre “{interesse.termo}”</h2>
        <p className="nota-secao">Últimos 30 dias · atualiza a cada 6 horas</p>
      </div>
      {videos.carregando && <Carregando texto="Buscando no YouTube…" />}
      {videos.erro && <Erro erro={videos.erro} onTentar={videos.recarregar} />}
      {!videos.carregando && !videos.erro && (videos.dados ?? []).length === 0 && (
        <p className="estado">Nenhum vídeo recente sobre esse tema.</p>
      )}
      {(videos.dados ?? []).length > 0 && (
        <GradeVideos videos={videos.dados} meus={meus} abertoId={abertoId} onAbrir={onAbrir} />
      )}
    </section>
  )
}
