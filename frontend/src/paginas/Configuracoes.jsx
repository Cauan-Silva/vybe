import { useEffect, useState } from 'react'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { EH_PROGRAMA } from '../config.js'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'
import { TEMAS, usePreferencias } from '../contextos/Preferencias.jsx'

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
                {t.cores.map((cor) => (
                  <span key={cor} style={{ background: cor }} />
                ))}
              </span>
              {t.nome}
            </button>
          ))}
        </div>

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
