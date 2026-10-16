import { useEffect, useState } from 'react'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { EH_PROGRAMA } from '../config.js'
import { api } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'

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
      <div className="pagina">
        <h1 className="titulo-pagina">Configurações</h1>
        <p className="estado">Esta tela só existe no programa do VYBE para Windows.</p>
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
