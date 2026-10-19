import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Capa from '../components/Capa.jsx'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { useBiblioteca } from '../contextos/Biblioteca.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { EH_APP, EH_PROGRAMA } from '../config.js'
import { api } from '../services/api.js'
import { plural } from '../utils/formato.js'
import { useCarregar } from '../utils/useCarregar.js'

const PERIODOS = [
  ['short_term', 'Últimas 4 semanas'],
  ['medium_term', 'Últimos 6 meses'],
  ['long_term', 'Último ano'],
]

export default function Spotify() {
  const { sessao, chamar } = useAuth()
  const avisar = useAvisos()
  const { prepararSpotify } = usePlayer()
  const [parametros, setParametros] = useSearchParams()
  const status = useCarregar(() => (sessao ? chamar((t) => api.spotifyStatus(t)) : null), [sessao])
  const podeTocar = Boolean(status.dados?.podeTocar)

  // Com a permissão do player, já liga o dispositivo "VYBE" para o primeiro play ser imediato.
  useEffect(() => {
    if (podeTocar) prepararSpotify()
  }, [podeTocar, prepararSpotify])

  // App Android: o login do Spotify abre no navegador do celular. Ao voltar para o app,
  // confere de novo se a conta já foi conectada.
  const recarregarStatus = status.recarregar
  useEffect(() => {
    if (!EH_APP) return undefined
    const aoVoltar = () => {
      if (!document.hidden) recarregarStatus()
    }
    document.addEventListener('visibilitychange', aoVoltar)
    return () => document.removeEventListener('visibilitychange', aoVoltar)
  }, [recarregarStatus])

  // Volta do Spotify: mostra o resultado uma vez e limpa a URL.
  useEffect(() => {
    const resultado = parametros.get('resultado')
    if (!resultado) return
    if (resultado === 'conectado') avisar('Spotify conectado')
    else avisar(parametros.get('motivo') ?? 'Não foi possível conectar o Spotify.', 'erro')
    setParametros({}, { replace: true })
  }, [parametros, setParametros, avisar])

  if (!sessao) return <PrecisaEntrar texto="Entre no VYBE para conectar sua conta do Spotify." />
  if (status.carregando) return <Carregando />
  if (status.erro) return <Erro erro={status.erro} onTentar={status.recarregar} />

  const s = status.dados

  return (
    <div className="pagina">
      <h1 className="titulo-pagina">Spotify</h1>
      {!s.configurado && <NaoConfigurado />}
      {s.configurado && !s.conectado && <Conectar />}
      {s.configurado && s.conectado && (
        <Conectado nome={s.nome} podeTocar={s.podeTocar} onDesconectado={status.recarregar} />
      )}
    </div>
  )
}

function NaoConfigurado() {
  if (EH_PROGRAMA) {
    return (
      <div className="painel-info">
        <p>
          Para usar o Spotify, informe o Client ID do seu app do Spotify em{' '}
          <Link to="/configuracoes">Configurações</Link>.
        </p>
      </div>
    )
  }
  return (
    <div className="painel-info">
      <p>
        A integração com o Spotify não está configurada nesta instalação. Defina as variáveis{' '}
        <code>SPOTIFY_CLIENT_ID</code> e <code>SPOTIFY_CLIENT_SECRET</code> no servidor e reinicie a API.
      </p>
    </div>
  )
}

/** Manda a pessoa para a página de autorização do Spotify (conectar ou reconectar). */
function useConectar() {
  const { chamar } = useAuth()
  const avisar = useAvisos()
  const [indo, setIndo] = useState(false)

  async function conectar() {
    setIndo(true)
    try {
      const { url } = await chamar((t) => api.spotifyAutorizar(t))
      window.location.assign(url)
    } catch (e) {
      avisar(e.message, 'erro')
      setIndo(false)
    }
  }

  return { indo, conectar }
}

function Conectar() {
  const { indo, conectar } = useConectar()
  // O Spotify só devolve o login para 127.0.0.1; em "localhost" a sessão do VYBE não seria a mesma.
  // (No app Android o endereço interno é sempre "localhost", mas o login abre no navegador.)
  const emLocalhost = !EH_APP && window.location.hostname === 'localhost'

  return (
    <div className="painel-info">
      <h2>Conecte sua conta do Spotify</h2>
      <p>Com a conta conectada, você pode:</p>
      <ul>
        <li>ver seus artistas e músicas mais ouvidos;</li>
        <li>importar suas playlists para o VYBE;</li>
        {EH_APP ? (
          <li>ouvir as músicas das suas playlists aqui no app (elas tocam pela versão do YouTube).</li>
        ) : (
          <li>com conta Premium, ouvir as músicas do Spotify completas aqui no player do VYBE.</li>
        )}
      </ul>
      <p className="texto-apoio-claro">
        O VYBE lê suas playlists e seus mais ouvidos e controla o que toca no player. Nada é alterado nas
        suas playlists ou na sua biblioteca do Spotify.
      </p>

      {emLocalhost ? (
        <p className="alerta">
          Para conectar, abra o VYBE pelo endereço{' '}
          <a href={`http://127.0.0.1:${window.location.port}/spotify`}>127.0.0.1:{window.location.port}</a> e
          entre de novo. O Spotify não aceita “localhost”.
        </p>
      ) : (
        <>
          <button type="button" className="botao" onClick={conectar} disabled={indo}>
            <Icone nome="conectar" tamanho={18} />
            {indo ? 'Abrindo o Spotify…' : 'Conectar com o Spotify'}
          </button>
          {EH_APP && (
            <p className="texto-apoio-claro">
              O login abre no navegador do celular. Depois de autorizar, é só voltar para o app.
            </p>
          )}
        </>
      )}
    </div>
  )
}

function Conectado({ nome, podeTocar, onDesconectado }) {
  const { chamar } = useAuth()
  const avisar = useAvisos()

  async function desconectar() {
    if (!window.confirm('Desconectar sua conta do Spotify? As playlists já importadas continuam no VYBE.')) return
    try {
      await chamar((t) => api.spotifyDesconectar(t))
      avisar('Spotify desconectado')
      onDesconectado()
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }

  return (
    <>
      <div className="spotify-conta">
        <p>
          Conectado como <strong>{nome}</strong>
        </p>
        <button type="button" className="botao botao--contorno" onClick={desconectar}>
          Desconectar
        </button>
      </div>
      {!podeTocar && <LiberarPlayer />}
      <MaisOuvidos podeTocar={podeTocar} />
      <ImportarPlaylists podeTocar={podeTocar} />
    </>
  )
}

/** Conexões feitas antes do player não têm a permissão de tocar: basta autorizar de novo. */
function LiberarPlayer() {
  const { indo, conectar } = useConectar()
  return (
    <div className="painel-info painel-info--destaque">
      <h2>Ouça as músicas do Spotify aqui no VYBE</h2>
      <p>
        Com conta Premium, as músicas tocam completas no player do VYBE. Para isso, o Spotify precisa da sua
        permissão de novo (o VYBE ganhou o player depois que você conectou).
      </p>
      <button type="button" className="botao" onClick={conectar} disabled={indo}>
        <Icone nome="play" tamanho={18} />
        {indo ? 'Abrindo o Spotify…' : 'Liberar o player'}
      </button>
    </div>
  )
}

function MaisOuvidos({ podeTocar }) {
  const { chamar } = useAuth()
  const { atual, tocando, tocarLista, alternarPlay } = usePlayer()
  const [periodo, setPeriodo] = useState('short_term')
  const { dados, erro, carregando, recarregar } = useCarregar(
    () => chamar((t) => api.spotifyMaisOuvidos(periodo, t)),
    [periodo, podeTocar],
  )

  // Só as que já estão no catálogo (com a permissão do player) podem tocar.
  const tocaveis = (dados?.musicas ?? []).map((m) => m.musica).filter(Boolean)

  function tocar(musica) {
    if (atual?.id === musica.id) alternarPlay()
    else tocarLista(tocaveis, tocaveis.indexOf(musica))
  }

  return (
    <section className="secao">
      <div className="titulo-com-acao titulo-com-acao--secao">
        <h2 className="titulo-secao">Seus mais ouvidos</h2>
        <div className="alternador" role="group" aria-label="Período">
          {PERIODOS.map(([valor, rotulo]) => (
            <button key={valor} type="button" aria-pressed={periodo === valor} onClick={() => setPeriodo(valor)}>
              {rotulo}
            </button>
          ))}
        </div>
      </div>

      {carregando && <Carregando />}
      {erro && <Erro erro={erro} onTentar={recarregar} />}

      {dados && (
        <div className="mais-ouvidos">
          <div>
            <h3 className="subtitulo-secao">Artistas</h3>
            {dados.artistas.length === 0 ? (
              <p className="estado">O Spotify ainda não tem dados suficientes para este período.</p>
            ) : (
              <ol className="ranking">
                {dados.artistas.map((a, i) => (
                  <li key={`${a.nome}-${i}`}>
                    <span className="ranking__posicao">{i + 1}</span>
                    <Capa src={a.imagemUrl} titulo={a.nome} redonda className="ranking__capa" />
                    <span className="ranking__textos">
                      <span className="ranking__nome">{a.nome}</span>
                    </span>
                    <span className="ranking__acoes">
                      <Link to={`/buscar?q=${encodeURIComponent(a.nome)}`} className="link-discreto">
                        Buscar no VYBE
                      </Link>
                      {a.spotifyUrl && <LinkSpotify url={a.spotifyUrl} />}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </div>

          <div>
            <div className="titulo-com-acao titulo-com-acao--sub">
              <h3 className="subtitulo-secao">Músicas</h3>
              {tocaveis.length > 0 && (
                <button type="button" className="botao botao--pequeno" onClick={() => tocarLista(tocaveis, 0)}>
                  <Icone nome="play" tamanho={16} /> Tocar todas
                </button>
              )}
            </div>
            {dados.musicas.length === 0 ? (
              <p className="estado">O Spotify ainda não tem dados suficientes para este período.</p>
            ) : (
              <ol className="ranking">
                {dados.musicas.map((m, i) => {
                  const ehAtual = Boolean(m.musica) && atual?.id === m.musica.id
                  const estaTocando = ehAtual && tocando
                  return (
                    <li key={`${m.titulo}-${i}`} className={ehAtual ? 'ranking--atual' : undefined}>
                      {m.musica ? (
                        <button
                          type="button"
                          className="ranking__posicao ranking__tocar"
                          aria-label={estaTocando ? `Pausar ${m.titulo}` : `Tocar ${m.titulo}`}
                          title={estaTocando ? 'Pausar' : 'Tocar'}
                          onClick={() => tocar(m.musica)}
                        >
                          <span className="ranking__numero">{i + 1}</span>
                          <span className="ranking__icone">
                            <Icone nome={estaTocando ? 'pause' : 'play'} tamanho={16} />
                          </span>
                        </button>
                      ) : (
                        <span className="ranking__posicao">{i + 1}</span>
                      )}
                      <Capa src={m.capaUrl} titulo={m.titulo} className="ranking__capa" />
                      <span className="ranking__textos">
                        <span className="ranking__nome">{m.titulo}</span>
                        <span className="ranking__detalhe">{m.artistas}</span>
                      </span>
                      <span className="ranking__acoes">{m.spotifyUrl && <LinkSpotify url={m.spotifyUrl} />}</span>
                    </li>
                  )
                })}
              </ol>
            )}
          </div>
        </div>
      )}
    </section>
  )
}

function LinkSpotify({ url }) {
  return (
    <a href={url} target="_blank" rel="noreferrer" className="link-discreto link-externo">
      Abrir no Spotify <Icone nome="externo" tamanho={14} />
    </a>
  )
}

function ImportarPlaylists({ podeTocar }) {
  const { chamar } = useAuth()
  const avisar = useAvisos()
  const { recarregarPlaylists } = useBiblioteca()
  const { dados, erro, carregando, recarregar } = useCarregar(() => chamar((t) => api.spotifyPlaylists(t)), [])
  const [importando, setImportando] = useState(null)
  const [resultado, setResultado] = useState(null)

  async function importar(playlist) {
    setImportando(playlist.id)
    setResultado(null)
    try {
      const r = await chamar((t) => api.spotifyImportar(playlist.id, t))
      setResultado(r)
      if (r.playlistId) {
        recarregarPlaylists()
        avisar(`${plural(r.encontradas, 'música importada', 'músicas importadas')}`)
      }
    } catch (e) {
      avisar(e.message, 'erro')
    } finally {
      setImportando(null)
    }
  }

  const importaveis = dados?.filter((p) => p.importavel) ?? []
  const outras = dados?.filter((p) => !p.importavel) ?? []

  return (
    <section className="secao">
      <h2 className="titulo-secao">Importar playlists</h2>
      {podeTocar ? (
        <p className="texto-apoio-claro">
          As músicas vêm do próprio Spotify (até 500 por playlist) e tocam no player do VYBE com a sua conta
          Premium. A playlist importada é uma cópia: mudanças no Spotify não aparecem aqui.
        </p>
      ) : (
        <p className="texto-apoio-claro">
          Para cada música, o VYBE procura uma equivalente no catálogo e no Jamendo (até 100 músicas por playlist).
          Músicas comerciais raramente têm equivalente livre, então é normal muitas ficarem de fora. Libere o
          player acima para trazer as músicas do próprio Spotify.
        </p>
      )}

      {resultado && <ResultadoImportacao r={resultado} onFechar={() => setResultado(null)} />}

      {carregando && <Carregando />}
      {erro && <Erro erro={erro} onTentar={recarregar} />}
      {dados && dados.length === 0 && <p className="estado">Você ainda não tem playlists no Spotify.</p>}

      {importaveis.length > 0 && (
        <ul className="lista-spotify">
          {importaveis.map((p) => (
            <li key={p.id}>
              <Capa src={p.capaUrl} titulo={p.nome} className="lista-spotify__capa" />
              <span className="lista-spotify__textos">
                <span className="lista-spotify__nome">{p.nome}</span>
                <span className="lista-spotify__detalhe">{plural(p.quantidade, 'música', 'músicas')}</span>
              </span>
              <button
                type="button"
                className="botao botao--contorno"
                disabled={importando !== null}
                onClick={() => importar(p)}
              >
                {importando === p.id ? 'Importando…' : 'Importar'}
              </button>
            </li>
          ))}
        </ul>
      )}

      {outras.length > 0 && (
        <details className="outras-playlists">
          <summary>{plural(outras.length, 'playlist de outra pessoa', 'playlists de outras pessoas')} (não importáveis)</summary>
          <p className="texto-apoio-claro">
            O Spotify só permite que apps leiam playlists criadas por você ou colaborativas.
          </p>
          <ul className="lista-spotify lista-spotify--apagada">
            {outras.map((p) => (
              <li key={p.id}>
                <Capa src={p.capaUrl} titulo={p.nome} className="lista-spotify__capa" />
                <span className="lista-spotify__textos">
                  <span className="lista-spotify__nome">{p.nome}</span>
                  <span className="lista-spotify__detalhe">de {p.dono ?? 'outra pessoa'}</span>
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}

function ResultadoImportacao({ r, onFechar }) {
  return (
    <div className="resultado-importacao" role="status">
      {r.playlistId ? (
        <p>
          <strong>
            {r.viaSpotify
              ? plural(r.encontradas, 'música importada', 'músicas importadas')
              : plural(r.encontradas, 'música encontrada', 'músicas encontradas')}
          </strong>{' '}
          de {r.analisadas}.
          A playlist <Link to={`/playlist/${r.playlistId}`}>{r.nome}</Link> foi criada no VYBE.
        </p>
      ) : (
        <p>
          {r.viaSpotify
            ? 'Nenhuma música desta playlist pôde ser importada, então nenhuma playlist foi criada.'
            : `Nenhuma das ${r.analisadas} músicas tem equivalente no catálogo livre, então nenhuma playlist foi criada.`}
        </p>
      )}
      {r.naoEncontradas.length > 0 && (
        <details>
          <summary>
            {r.viaSpotify
              ? `Ver as ${r.naoEncontradas.length} que ficaram de fora`
              : `Ver as ${r.naoEncontradas.length} sem equivalente`}
          </summary>
          <ul className="nao-encontradas">
            {r.naoEncontradas.map((nome, i) => (
              <li key={`${nome}-${i}`}>{nome}</li>
            ))}
          </ul>
        </details>
      )}
      <button type="button" className="link-botao" onClick={onFechar}>
        Fechar
      </button>
    </div>
  )
}
