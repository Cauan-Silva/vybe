import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Capa from '../components/Capa.jsx'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'

/*
 * "Seu mês no VYBE": músicas, artistas, vídeos e canais mais ouvidos/assistidos no mês,
 * o tempo total e o ritmo de cada dia. Uma música ou vídeo só conta a partir de 30 segundos.
 */

const NOME_MES = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })
const NOME_SEMANA = new Intl.DateTimeFormat('pt-BR', { weekday: 'long' })
const NOME_DIA_CURTO = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' })

function data(mes, dia = 1) {
  const [ano, m] = mes.split('-').map(Number)
  return new Date(ano, m - 1, dia)
}

/** "2026-10-09" → Date local (sem fuso, para não cair no dia anterior). */
function diaDe(texto) {
  const [ano, mes, dia] = texto.split('-').map(Number)
  return new Date(ano, mes - 1, dia)
}

const maiuscula = (t) => t.charAt(0).toUpperCase() + t.slice(1)

/** 42 s → "menos de 1 min"; 2520 s → "42 min"; 11 520 s → "3 h 12 min". */
export function tempoLegivel(segundos) {
  const minutos = Math.floor(segundos / 60)
  if (minutos < 1) return segundos > 0 ? 'menos de 1 min' : '0 min'
  if (minutos < 60) return `${minutos} min`
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return resto ? `${horas} h ${resto} min` : `${horas} h`
}

const vezes = (n) => (n === 1 ? '1 vez' : `${n} vezes`)

export default function SeuMes() {
  const { sessao, chamar } = useAuth()
  const [mes, setMes] = useState(null) // null = mês atual
  const resumo = useCarregar(() => (sessao ? chamar((t) => api.resumo(mes, t)) : null), [sessao, mes])

  if (!sessao) return <PrecisaEntrar texto="Entre no VYBE para ver o resumo do seu mês." />
  if (resumo.carregando && !resumo.dados) return <Carregando />
  if (resumo.erro) return <Erro erro={resumo.erro} onTentar={resumo.recarregar} />

  const r = resumo.dados
  const totalSegundos = r.segundosMusica + r.segundosVideo
  const vazio = r.reproducoes === 0 && r.videosAssistidos === 0

  return (
    <div className="pagina seu-mes">
      <header className="seu-mes__topo">
        <p className="seu-mes__chapeu">Seu mês no VYBE</p>
        <div className="seu-mes__navegacao">
          <button
            type="button"
            className="botao-icone"
            aria-label="Mês anterior"
            title="Mês anterior"
            disabled={!r.mesAnterior}
            onClick={() => setMes(r.mesAnterior)}
          >
            <Icone nome="esquerda" />
          </button>
          <h1 className="titulo-pagina seu-mes__mes">{maiuscula(NOME_MES.format(data(r.mes)))}</h1>
          <button
            type="button"
            className="botao-icone"
            aria-label="Próximo mês"
            title="Próximo mês"
            disabled={!r.mesSeguinte}
            onClick={() => setMes(r.mesSeguinte)}
          >
            <Icone nome="direita" />
          </button>
        </div>
      </header>

      {vazio ? (
        <div className="painel-info seu-mes__vazio">
          <Icone nome="estrela" tamanho={28} />
          <p>
            Ainda não há o que contar neste mês. Ouça músicas e assista vídeos: cada um entra no resumo a partir de 30
            segundos.
          </p>
        </div>
      ) : (
        <>
          <section className="seu-mes__numeros" aria-label="Números do mês">
            <div className="numero numero--destaque">
              <span className="numero__rotulo">Tempo no VYBE</span>
              <span className="numero__valor">{tempoLegivel(totalSegundos)}</span>
              <span className="numero__apoio">
                {tempoLegivel(r.segundosMusica)} de música · {tempoLegivel(r.segundosVideo)} de vídeos
              </span>
            </div>
            <div className="numero">
              <span className="numero__rotulo">Músicas</span>
              <span className="numero__valor">{r.reproducoes}</span>
              <span className="numero__apoio">
                {r.musicasDiferentes} diferentes, de {r.artistasDiferentes}{' '}
                {r.artistasDiferentes === 1 ? 'artista' : 'artistas'}
              </span>
            </div>
            <div className="numero">
              <span className="numero__rotulo">Vídeos</span>
              <span className="numero__valor">{r.videosAssistidos}</span>
              <span className="numero__apoio">contam a partir de 30 s</span>
            </div>
            <div className="numero">
              <span className="numero__rotulo">Seu horário</span>
              <span className="numero__valor">{r.horaFavorita !== null ? `${r.horaFavorita}h` : '–'}</span>
              <span className="numero__apoio">quando você mais ouve e assiste</span>
            </div>
            <div className="numero">
              <span className="numero__rotulo">Dia mais animado</span>
              <span className="numero__valor numero__valor--texto">
                {r.diaFavorito ? NOME_DIA_CURTO.format(diaDe(r.diaFavorito)).replace('.', '') : '–'}
              </span>
              <span className="numero__apoio">
                {r.diaFavorito ? maiuscula(NOME_SEMANA.format(diaDe(r.diaFavorito))) : ''}
              </span>
            </div>
          </section>

          <RitmoDoMes mes={r.mes} dias={r.dias} />

          <div className="seu-mes__grade">
            {r.musicas.length > 0 && <TopMusicas musicas={r.musicas} />}
            {r.artistas.length > 0 && <TopArtistas artistas={r.artistas} />}
            {r.videos.length > 0 && <TopVideos videos={r.videos} />}
            {r.canais.length > 0 && <TopCanais canais={r.canais} />}
          </div>
        </>
      )}
    </div>
  )
}

/**
 * Barras empilhadas por dia: música embaixo, vídeo em cima. Passar o mouse (ou o foco do
 * teclado) numa barra mostra os valores; a tabela logo abaixo traz os mesmos números.
 */
function RitmoDoMes({ mes, dias }) {
  const [destaque, setDestaque] = useState(null)
  const maximo = Math.max(...dias.map((d) => d.segundosMusica + d.segundosVideo), 0)
  // Topo do eixo arredondado para um número de minutos "redondo"
  const maxMin = Math.max(1, Math.ceil(maximo / 60))
  const passo = maxMin <= 10 ? 5 : maxMin <= 60 ? 15 : maxMin <= 180 ? 30 : 60
  const topo = Math.ceil(maxMin / passo) * passo
  const marcas = [topo, topo / 2, 0]
  const nomeDia = (d) => NOME_DIA_CURTO.format(data(mes, d.dia)).replace('.', '')
  const d = destaque !== null ? dias[destaque] : null

  return (
    <section className="secao ritmo" aria-labelledby="ritmo-titulo">
      <div className="ritmo__cabecalho">
        <h2 className="titulo-secao" id="ritmo-titulo">
          Seu ritmo no mês
        </h2>
        <ul className="ritmo__legenda" aria-label="Legenda">
          <li>
            <span className="ritmo__amostra ritmo__amostra--musica" aria-hidden="true" /> Música
          </li>
          <li>
            <span className="ritmo__amostra ritmo__amostra--video" aria-hidden="true" /> Vídeos
          </li>
        </ul>
      </div>

      <div className="ritmo__area">
        <div className="ritmo__eixo" aria-hidden="true">
          {marcas.map((m) => (
            <span key={m}>{m} min</span>
          ))}
        </div>
        <div className="ritmo__grafico" onMouseLeave={() => setDestaque(null)}>
          <div className="ritmo__grade" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
          {dias.map((dia, i) => {
            const musica = dia.segundosMusica / 60 / topo
            const video = dia.segundosVideo / 60 / topo
            const total = dia.segundosMusica + dia.segundosVideo
            return (
              <div
                key={dia.dia}
                className={`ritmo__coluna ${destaque === i ? 'ritmo__coluna--ativa' : ''}`}
                tabIndex={0}
                aria-label={`${nomeDia(dia)}: ${tempoLegivel(dia.segundosMusica)} de música, ${tempoLegivel(dia.segundosVideo)} de vídeos`}
                onMouseEnter={() => setDestaque(i)}
                onFocus={() => setDestaque(i)}
                onBlur={() => setDestaque(null)}
              >
                <div className="ritmo__pilha">
                  {total > 0 && (
                    <>
                      {dia.segundosVideo > 0 && (
                        <span className="ritmo__barra ritmo__barra--video" style={{ height: `${video * 100}%` }} />
                      )}
                      {dia.segundosMusica > 0 && (
                        <span className="ritmo__barra ritmo__barra--musica" style={{ height: `${musica * 100}%` }} />
                      )}
                    </>
                  )}
                </div>
                <span className="ritmo__dia" aria-hidden="true">
                  {dia.dia === 1 || dia.dia % 5 === 0 ? dia.dia : ''}
                </span>
              </div>
            )
          })}
          {d && (
            <div
              className="ritmo__dica"
              role="presentation"
              style={{ left: `${((destaque + 0.5) / dias.length) * 100}%` }}
            >
              <strong>{nomeDia(d)}</strong>
              <span>
                <span className="ritmo__amostra ritmo__amostra--musica" aria-hidden="true" /> Música{' '}
                <b>{tempoLegivel(d.segundosMusica)}</b>
              </span>
              <span>
                <span className="ritmo__amostra ritmo__amostra--video" aria-hidden="true" /> Vídeos{' '}
                <b>{tempoLegivel(d.segundosVideo)}</b>
              </span>
            </div>
          )}
        </div>
      </div>

      <details className="ritmo__tabela">
        <summary>Ver como tabela</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">Dia</th>
              <th scope="col">Música</th>
              <th scope="col">Vídeos</th>
            </tr>
          </thead>
          <tbody>
            {dias
              .filter((x) => x.segundosMusica + x.segundosVideo > 0)
              .map((x) => (
                <tr key={x.dia}>
                  <th scope="row">{nomeDia(x)}</th>
                  <td>{tempoLegivel(x.segundosMusica)}</td>
                  <td>{tempoLegivel(x.segundosVideo)}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </details>
    </section>
  )
}

function TopMusicas({ musicas }) {
  const { tocarLista } = usePlayer()
  const lista = musicas.map((m) => m.musica)
  return (
    <section className="secao top" aria-labelledby="top-musicas">
      <div className="top__cabecalho">
        <h2 className="titulo-secao" id="top-musicas">
          Músicas mais ouvidas
        </h2>
        <button type="button" className="botao botao--pequeno" onClick={() => tocarLista(lista, 0)}>
          <Icone nome="play" tamanho={16} /> Tocar
        </button>
      </div>
      <ol className="top__lista">
        {musicas.map((item, i) => (
          <li key={item.musica.id}>
            <button type="button" className="top__item" onClick={() => tocarLista(lista, i)}>
              <span className="top__posicao">{i + 1}</span>
              <Capa src={item.musica.capaUrl} titulo={item.musica.albumTitulo} className="top__capa" />
              <span className="top__textos">
                <span className="top__nome">{item.musica.titulo}</span>
                <span className="top__apoio">{item.musica.artistaNome}</span>
              </span>
              <span className="top__numero">
                {vezes(item.vezes)}
                <small>{tempoLegivel(item.segundos)}</small>
              </span>
            </button>
          </li>
        ))}
      </ol>
    </section>
  )
}

function TopArtistas({ artistas }) {
  return (
    <section className="secao top" aria-labelledby="top-artistas">
      <h2 className="titulo-secao" id="top-artistas">
        Artistas do mês
      </h2>
      <ol className="top__lista">
        {artistas.map((a, i) => (
          <li key={a.id}>
            <Link to={`/artista/${a.id}`} className="top__item">
              <span className="top__posicao">{i + 1}</span>
              <Capa src={a.capaUrl} titulo={a.nome} redonda className="top__capa" />
              <span className="top__textos">
                <span className="top__nome">{a.nome}</span>
                <span className="top__apoio">{vezes(a.vezes)}</span>
              </span>
              <span className="top__numero">{tempoLegivel(a.segundos)}</span>
            </Link>
          </li>
        ))}
      </ol>
    </section>
  )
}

function TopVideos({ videos }) {
  const navegar = useNavigate()
  return (
    <section className="secao top" aria-labelledby="top-videos">
      <h2 className="titulo-secao" id="top-videos">
        Vídeos mais assistidos
      </h2>
      <ol className="top__lista">
        {videos.map((v, i) => (
          <li key={v.id}>
            <button
              type="button"
              className="top__item"
              onClick={() =>
                navegar('/videos', {
                  state: { abrir: { id: v.id, titulo: v.titulo, canal: v.canal, canalId: v.canalId, capaUrl: v.capaUrl } },
                })
              }
            >
              <span className="top__posicao">{i + 1}</span>
              <span className="top__miniatura">{v.capaUrl ? <img src={v.capaUrl} alt="" loading="lazy" /> : null}</span>
              <span className="top__textos">
                <span className="top__nome">{v.titulo}</span>
                <span className="top__apoio">{v.canal}</span>
              </span>
              <span className="top__numero">
                {vezes(v.vezes)}
                <small>{tempoLegivel(v.segundos)}</small>
              </span>
            </button>
          </li>
        ))}
      </ol>
    </section>
  )
}

function TopCanais({ canais }) {
  return (
    <section className="secao top" aria-labelledby="top-canais">
      <h2 className="titulo-secao" id="top-canais">
        Canais do mês
      </h2>
      <ol className="top__lista">
        {canais.map((c, i) => {
          const conteudo = (
            <>
              <span className="top__posicao">{i + 1}</span>
              <span className="top__textos">
                <span className="top__nome">{c.nome}</span>
                <span className="top__apoio">{c.vezes === 1 ? '1 vídeo' : `${c.vezes} vídeos`}</span>
              </span>
              <span className="top__numero">{tempoLegivel(c.segundos)}</span>
            </>
          )
          return (
            <li key={c.nome}>
              {c.canalId ? (
                <Link to={`/videos/canal/${c.canalId}`} className="top__item">
                  {conteudo}
                </Link>
              ) : (
                <div className="top__item">{conteudo}</div>
              )}
            </li>
          )
        })}
      </ol>
    </section>
  )
}
