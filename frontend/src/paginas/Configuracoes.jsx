import { useEffect, useState } from 'react'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { EH_PROGRAMA } from '../config.js'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'
import {
  amostraPersonalizada,
  coresPersonalizadas,
  ID_DISCORD,
  PERSONALIZADO_PADRAO,
  TEMAS,
  usePreferencias,
} from '../contextos/Preferencias.jsx'
import RodaDeCores from '../components/RodaDeCores.jsx'
import { enviarAoPrograma, useDoPrograma } from '../utils/programa.js'
import { ATALHOS } from '../utils/useAtalhos.js'

/** Aparência e desempenho: valem no programa e na versão web (ficam guardados neste computador). */
function Aparencia() {
  const p = usePreferencias()

  return (
    <>
      <section className="config__grupo">
        <h2 className="config__titulo">Tema</h2>
        <div className="temas" role="group" aria-label="Tema">
          {TEMAS.map((t) => (
            <button
              key={t.id}
              type="button"
              className="tema"
              aria-pressed={p.tema === t.id}
              onClick={() => p.alterar('tema', t.id)}
            >
              <span className="tema__amostra" aria-hidden="true">
                {(t.cores ?? amostraPersonalizada(p.personalizado)).map((cor, i) => (
                  <span key={i} style={{ background: cor }} />
                ))}
              </span>
              {t.nome}
            </button>
          ))}
        </div>

        {p.tema === 'personalizado' && <EditorDeCores />}

        <div className="config__linha">
          <span>
            <strong>Tamanho do texto</strong>
          </span>
          <div className="alternador" role="group" aria-label="Tamanho do texto">
            <button type="button" aria-pressed={p.texto === 'normal'} onClick={() => p.alterar('texto', 'normal')}>
              Normal
            </button>
            <button type="button" aria-pressed={p.texto === 'grande'} onClick={() => p.alterar('texto', 'grande')}>
              Grande
            </button>
          </div>
        </div>
      </section>

      <section className="config__grupo">
        <h2 className="config__titulo">Efeitos de luz</h2>
        <label className="config__chave">
          <input type="checkbox" checked={p.luzDinamica} onChange={(e) => p.alterar('luzDinamica', e.target.checked)} />
          <span>
            <strong>Luz dinâmica</strong>
            <span className="texto-apoio-claro">
              O topo das páginas ganha as cores da capa da música que está tocando.
            </span>
          </span>
        </label>

        <label className="config__chave">
          <input type="checkbox" checked={p.luzAmbiente} onChange={(e) => p.alterar('luzAmbiente', e.target.checked)} />
          <span>
            <strong>Luz ambiente nos vídeos</strong>
            <span className="texto-apoio-claro">
              Um brilho com as cores do vídeo envolve o player na aba Vídeos, como uma luz atrás da TV.
            </span>
          </span>
        </label>
        {p.luzAmbiente && (
          <label className="config__chave config__sub">
            <input
              type="checkbox"
              checked={p.ambienteTempoReal}
              onChange={(e) => p.alterar('ambienteTempoReal', e.target.checked)}
            />
            <span>
              <strong>Acompanhar o vídeo em tempo real</strong>
              <span className="texto-apoio-claro">
                A luz muda de cor junto com as cenas. Só funciona no programa para Windows e usa um pouco mais de
                processador enquanto o vídeo toca; desligado (ou na versão web), usa as cores da miniatura.
              </span>
            </span>
          </label>
        )}
        {p.luzAmbiente && (
          <div className="config__linha">
            <span>Tamanho do brilho</span>
            <div className="alternador" role="group" aria-label="Tamanho do brilho">
              {[
                ['pequeno', 'Pequeno'],
                ['medio', 'Médio'],
                ['grande', 'Grande'],
              ].map(([valor, nome]) => (
                <button
                  key={valor}
                  type="button"
                  aria-pressed={p.tamanhoAmbiente === valor}
                  onClick={() => p.alterar('tamanhoAmbiente', valor)}
                >
                  {nome}
                </button>
              ))}
            </div>
          </div>
        )}
        {p.luzAmbiente && (
          <div className="config__linha">
            <span>Intensidade da luz ambiente</span>
            <div className="alternador" role="group" aria-label="Intensidade da luz ambiente">
              {[
                ['suave', 'Suave'],
                ['media', 'Média'],
                ['forte', 'Forte'],
              ].map(([valor, nome]) => (
                <button
                  key={valor}
                  type="button"
                  aria-pressed={p.intensidadeAmbiente === valor}
                  onClick={() => p.alterar('intensidadeAmbiente', valor)}
                >
                  {nome}
                </button>
              ))}
            </div>
          </div>
        )}
      </section>

      <section className="config__grupo">
        <h2 className="config__titulo">Desempenho</h2>
        <label className="config__chave">
          <input type="checkbox" checked={p.modoLeve} onChange={(e) => p.alterarModoLeve(e.target.checked)} />
          <span>
            <strong>Modo leve</strong>
            <span className="texto-apoio-claro">
              Desliga animações, brilhos e os efeitos de luz e atualiza a barra de tempo só uma vez por segundo. Bom
              para usar o VYBE enquanto joga. Com a janela minimizada, o VYBE já economiza sozinho.
            </span>
          </span>
        </label>
        <button type="button" className="link-botao config__restaurar" onClick={p.restaurar}>
          Restaurar a aparência padrão
        </button>
      </section>
    </>
  )
}

/** As três cores do tema personalizado, cada uma um marcador na roda. */
const ALVOS = [
  { id: 'destaque', sat: 'satDestaque', letra: 'D', nome: 'Cor de destaque', dica: 'Botões, item ativo, barra de progresso', amostra: '--rotulo' },
  { id: 'secundaria', sat: 'satSecundaria', letra: 'S', nome: 'Cor secundária', dica: 'Brilhos, degradês e detalhes', amostra: '--violeta' },
  { id: 'fundo', sat: 'saturacao', letra: 'F', nome: 'Tom do fundo', dica: 'A cor puxada pelo fundo e pelos painéis', amostra: '--painel-3' },
]

/** Tema personalizado: a roda muda o VYBE na hora, como uma prévia ao vivo. */
function EditorDeCores() {
  const p = usePreferencias()
  const c = p.personalizado
  const [ativo, setAtivo] = useState('destaque')
  const cores = coresPersonalizadas(c)
  const alvo = ALVOS.find((a) => a.id === ativo)

  const mudar = (id, h, s) => {
    const a = ALVOS.find((x) => x.id === id)
    p.alterar('personalizado', { ...c, [a.id]: h, [a.sat]: s })
  }
  const aleatorio = () => {
    const destaque = Math.floor(Math.random() * 360)
    p.alterar('personalizado', {
      destaque,
      satDestaque: 60 + Math.floor(Math.random() * 41),
      secundaria: (destaque + 60 + Math.floor(Math.random() * 180)) % 360,
      satSecundaria: 50 + Math.floor(Math.random() * 51),
      fundo: Math.floor(Math.random() * 360),
      saturacao: 15 + Math.floor(Math.random() * 60),
    })
  }
  const cinzas = () => p.alterar('personalizado', { ...c, satDestaque: 0, satSecundaria: 0, saturacao: 0 })

  return (
    <div className="editor-cores">
      <RodaDeCores
        rotulo="Roda de cores do tema personalizado"
        ativo={ativo}
        onAtivar={setAtivo}
        onMudar={mudar}
        pontos={ALVOS.map((a) => ({ id: a.id, letra: a.letra, nome: a.nome, h: c[a.id], s: c[a.sat] }))}
      />
      <div className="editor-cores__lado">
        <div className="editor-cores__alvos" role="group" aria-label="Qual cor mexer na roda">
          {ALVOS.map((a) => (
            <button
              key={a.id}
              type="button"
              className="editor-cores__alvo"
              aria-pressed={ativo === a.id}
              onClick={() => setAtivo(a.id)}
            >
              <span className="editor-cores__amostra" style={{ background: cores[a.amostra] }} aria-hidden="true">
                {a.letra}
              </span>
              <span className="editor-cores__rotulo">
                <strong>{a.nome}</strong>
                <span className="texto-apoio-claro">{a.dica}</span>
              </span>
            </button>
          ))}
        </div>
        <p className="editor-cores__valores" aria-live="polite">
          <strong>{alvo.nome}</strong>: matiz {c[alvo.id]}° · saturação {c[alvo.sat]}%
          {c[alvo.sat] < 10 && <span className="editor-cores__cinza"> (cinza)</span>}
        </p>
        <p className="texto-apoio-claro">
          Arraste o marcador: girando muda a cor; para o centro, ela perde a cor até virar cinza (acromática).
        </p>
        <div className="editor-cores__acoes">
          <button type="button" className="botao botao--contorno botao--pequeno" onClick={aleatorio}>
            Cores aleatórias
          </button>
          <button type="button" className="botao botao--contorno botao--pequeno" onClick={cinzas}>
            Tons de cinza
          </button>
          <button type="button" className="link-botao" onClick={() => p.alterar('personalizado', PERSONALIZADO_PADRAO)}>
            Voltar ao padrão
          </button>
        </div>
      </div>
    </div>
  )
}

function Atalhos() {
  return (
    <section className="config__grupo">
      <h2 className="config__titulo">Atalhos de teclado</h2>
      <p className="texto-apoio-claro">Funcionam em qualquer tela, menos enquanto você digita num campo.</p>
      <dl className="atalhos">
        {ATALHOS.map(([teclas, acao]) => (
          <div key={teclas} className="atalhos__linha">
            <dt>
              {teclas.split(' ').map((parte, i, todas) =>
                parte === '+' || parte === 'ou' || (parte === '/' && i < todas.length - 1) ? (
                  <span key={i} className="atalhos__sep">
                    {parte}
                  </span>
                ) : (
                  <kbd key={i}>{parte}</kbd>
                ),
              )}
            </dt>
            <dd>{acao}</dd>
          </div>
        ))}
      </dl>
      <p className="texto-apoio-claro">
        As teclas de mídia do teclado e do fone (tocar, pausar, próxima) e o painel de mídia do Windows também
        controlam o VYBE, mesmo com outro programa ou jogo em primeiro plano.
      </p>
    </section>
  )
}

const TEXTO_DISCORD = {
  desligado: 'Desligado.',
  procurando: 'Procurando o Discord…',
  conectado: 'Conectado ao Discord.',
  erro: 'Não foi possível conectar.',
}

/** Programa: mostrar no perfil do Discord o que está tocando. */
function SecaoDiscord() {
  const p = usePreferencias()
  const estado = useDoPrograma('discord-estado')
  const [id, setId] = useState(p.discordClientId)
  const idLimpo = id.trim()
  const valido = ID_DISCORD.test(idLimpo)
  const mudou = idLimpo !== p.discordClientId

  return (
    <section className="config__grupo">
      <h2 className="config__titulo">Discord</h2>
      <label className="config__chave">
        <input type="checkbox" checked={p.discordAtivo} onChange={(e) => p.alterar('discordAtivo', e.target.checked)} />
        <span>
          <strong>Mostrar no Discord o que estou ouvindo</strong>
          <span className="texto-apoio-claro">
            Aparece no seu perfil “Ouvindo VYBE” com a música, o artista, a capa e o tempo. Some quando você pausa.
          </span>
        </span>
      </label>
      <ol className="config__passos">
        <li>
          Em{' '}
          <a href="https://discord.com/developers/applications" target="_blank" rel="noreferrer">
            discord.com/developers/applications
          </a>
          , clique em <strong>New Application</strong> e dê o nome <strong>VYBE</strong> (é o nome que aparece no perfil).
        </li>
        <li>
          Copie o <strong>Application ID</strong> e cole aqui.
        </li>
        <li>
          Deixe o Discord para computador aberto, com <strong>Privacidade da atividade → Compartilhar minha atividade</strong>{' '}
          ligado.
        </li>
      </ol>
      <div className="config__copiar">
        <label className="campo campo--linha">
          <span>ID do aplicativo</span>
          <input
            value={id}
            onChange={(e) => setId(e.target.value)}
            placeholder="Só números, ex.: 1234567890123456789"
            inputMode="numeric"
            spellCheck="false"
            autoComplete="off"
          />
        </label>
        <button
          type="button"
          className="botao botao--contorno botao--pequeno"
          disabled={!mudou || (idLimpo !== '' && !valido)}
          onClick={() => p.alterar('discordClientId', idLimpo)}
        >
          Salvar ID
        </button>
      </div>
      {idLimpo !== '' && !valido && <p className="campo__erro">O ID tem só números (entre 15 e 25).</p>}
      {p.discordAtivo && estado && (
        <p className={`config__estado config__estado--${estado.estado}`} role="status">
          {TEXTO_DISCORD[estado.estado] ?? estado.estado}
          {estado.detalhe && estado.estado !== 'conectado' ? ` ${estado.detalhe}` : ''}
          {estado.estado === 'conectado' && estado.detalhe ? ` (${estado.detalhe})` : ''}
        </p>
      )}
    </section>
  )
}

/** Programa: versão e atualização automática pelo GitHub. */
function SecaoAtualizacoes() {
  const versao = useDoPrograma('versao')
  const a = useDoPrograma('atualizacao')
  const estado = a?.estado ?? 'parado'
  const textos = {
    parado: 'O VYBE procura versões novas sozinho, algumas vezes por dia.',
    procurando: 'Procurando versões novas…',
    'em-dia': 'Você está com a versão mais recente.',
    baixando: `Baixando a versão ${a?.versao ?? 'nova'}… ${a?.progresso ?? 0}%`,
    pronta: `A versão ${a?.versao} está pronta. Ela será instalada quando você fechar o VYBE.`,
    erro: a?.mensagem ?? 'Não foi possível procurar atualizações agora.',
    indisponivel: 'A atualização automática só funciona no programa instalado.',
  }
  return (
    <section className="config__grupo">
      <h2 className="config__titulo">Atualizações</h2>
      <p>
        Versão instalada: <strong>{versao?.versao ?? a?.versaoAtual ?? '…'}</strong>
      </p>
      <p className="texto-apoio-claro" role="status">
        {textos[estado] ?? ''}
      </p>
      <div className="config__botoes">
        {estado === 'pronta' ? (
          <button type="button" className="botao botao--pequeno" onClick={() => enviarAoPrograma({ tipo: 'atualizacao', acao: 'instalar' })}>
            Reiniciar e atualizar
          </button>
        ) : (
          <button
            type="button"
            className="botao botao--contorno botao--pequeno"
            disabled={['procurando', 'baixando', 'indisponivel'].includes(estado)}
            onClick={() => enviarAoPrograma({ tipo: 'atualizacao', acao: 'procurar' })}
          >
            Procurar agora
          </button>
        )}
      </div>
    </section>
  )
}

/*
 * Configurações do programa: cada pessoa informa as próprias chaves das integrações.
 * Só existe no programa (Windows); na versão web as chaves ficam no servidor.
 */
export default function Configuracoes() {
  const { sessao, chamar } = useAuth()
  const avisar = useAvisos()
  const atual = useCarregar(() => (sessao && EH_PROGRAMA ? chamar((t) => api.configuracoes(t)) : null), [sessao])
  const [spotify, setSpotify] = useState('')
  const [jamendo, setJamendo] = useState('')
  const [youtube, setYoutube] = useState('') // vazio = manter a chave atual
  const [salvando, setSalvando] = useState(false)

  useEffect(() => {
    if (!atual.dados) return
    setSpotify(atual.dados.spotifyClientId ?? '')
    setJamendo(atual.dados.jamendoClientId ?? '')
  }, [atual.dados])

  if (!EH_PROGRAMA) {
    return (
      <div className="pagina pagina--estreita">
        <h1 className="titulo-pagina">Configurações</h1>
        <div className="config">
          <Aparencia />
          <Atalhos />
        </div>
        <p className="texto-apoio-claro">As chaves das integrações só podem ser trocadas no programa para Windows.</p>
      </div>
    )
  }
  if (!sessao) return <PrecisaEntrar texto="Entre no VYBE para configurar as integrações." />
  if (atual.carregando) return <Carregando />
  if (atual.erro) return <Erro erro={atual.erro} onTentar={atual.recarregar} />

  const c = atual.dados

  async function salvar(alteracao, mensagem) {
    setSalvando(true)
    try {
      const novo = await chamar((t) => api.salvarConfiguracoes(alteracao, t))
      atual.setDados(novo)
      setYoutube('')
      avisar(mensagem)
    } catch (e) {
      avisar(e.message, 'erro')
    } finally {
      setSalvando(false)
    }
  }

  function aoEnviar(e) {
    e.preventDefault()
    salvar(
      {
        spotifyClientId: spotify.trim(),
        jamendoClientId: jamendo.trim(),
        youtubeApiKey: youtube.trim() ? youtube.trim() : null,
      },
      'Configurações salvas',
    )
  }

  async function copiar(texto) {
    try {
      await navigator.clipboard.writeText(texto)
      avisar('Endereço copiado')
    } catch {
      avisar('Não foi possível copiar; selecione e copie à mão.', 'erro')
    }
  }

  return (
    <div className="pagina pagina--estreita">
      <h1 className="titulo-pagina">Configurações</h1>
      <p className="texto-apoio-claro">
        O VYBE usa contas gratuitas de desenvolvedor de cada serviço. As chaves ficam só neste computador. Todas são
        opcionais: sem elas, a parte correspondente fica escondida e o resto funciona normalmente.
      </p>

      <div className="config">
        <Aparencia />
        <Atalhos />
        <SecaoDiscord />
        <SecaoAtualizacoes />
      </div>

      <form className="config" onSubmit={aoEnviar}>
        <fieldset className="config__grupo">
          <legend>Spotify</legend>
          <p>
            Para ver seus mais ouvidos, importar playlists e buscar no Spotify. Crie um app em{' '}
            <a href="https://developer.spotify.com/dashboard" target="_blank" rel="noreferrer">
              developer.spotify.com/dashboard
            </a>
            , marque <strong>Web API</strong> e cadastre este endereço em <strong>Redirect URIs</strong>:
          </p>
          <div className="config__copiar">
            <code>{c.spotifyRedirectUri}</code>
            <button type="button" className="botao botao--contorno botao--pequeno" onClick={() => copiar(c.spotifyRedirectUri)}>
              Copiar
            </button>
          </div>
          <p className="texto-apoio-claro">
            O programa não usa o Client Secret: o login é feito com PKCE, o jeito recomendado para programas.
          </p>
          <label className="campo">
            <span>Client ID</span>
            <input
              value={spotify}
              onChange={(e) => setSpotify(e.target.value)}
              placeholder="32 letras e números"
              spellCheck="false"
              autoComplete="off"
            />
          </label>
        </fieldset>

        <fieldset className="config__grupo">
          <legend>YouTube</legend>
          <p>
            Para buscar músicas no YouTube e usar a aba Vídeos. Crie uma chave de API em{' '}
            <a href="https://console.cloud.google.com/apis/credentials" target="_blank" rel="noreferrer">
              console.cloud.google.com/apis/credentials
            </a>{' '}
            e ative a <strong>YouTube Data API v3</strong> no mesmo projeto.
          </p>
          <label className="campo">
            <span>Chave de API {c.youtubeConfigurado && <em>(configurada, termina em {c.youtubeFinal})</em>}</span>
            <input
              type="password"
              value={youtube}
              onChange={(e) => setYoutube(e.target.value)}
              placeholder={c.youtubeConfigurado ? 'Deixe em branco para manter a atual' : 'Começa com AIza'}
              spellCheck="false"
              autoComplete="off"
            />
          </label>
          {c.youtubeConfigurado && (
            <button
              type="button"
              className="link-botao"
              disabled={salvando}
              onClick={() => salvar({ youtubeApiKey: '' }, 'Chave do YouTube removida')}
            >
              Remover a chave do YouTube
            </button>
          )}
        </fieldset>

        <fieldset className="config__grupo">
          <legend>Jamendo</legend>
          <p>
            Para o catálogo de músicas livres (Creative Commons). Crie um app em{' '}
            <a href="https://devportal.jamendo.com" target="_blank" rel="noreferrer">
              devportal.jamendo.com
            </a>{' '}
            e copie o Client ID.
          </p>
          <label className="campo">
            <span>Client ID</span>
            <input value={jamendo} onChange={(e) => setJamendo(e.target.value)} spellCheck="false" autoComplete="off" />
          </label>
        </fieldset>

        <button type="submit" className="botao" disabled={salvando}>
          <Icone nome="feito" tamanho={18} />
          {salvando ? 'Salvando…' : 'Salvar'}
        </button>
      </form>
    </div>
  )
}
