const BASE = import.meta.env.VITE_API_URL ?? 'http://127.0.0.1:8080/api'

/** Endereço do servidor sem o "/api", para montar URLs de áudio e capas. */
const ORIGEM_API = BASE.replace(/\/api\/?$/, '')

/** A API devolve caminhos como "/api/musicas/1/audio"; o Jamendo (fase 2) devolve URLs completas. */
export function urlArquivo(caminho) {
  if (!caminho) return null
  return /^https?:\/\//.test(caminho) ? caminho : ORIGEM_API + caminho
}

export class ErroApi extends Error {
  constructor(mensagem, status, campos) {
    super(mensagem)
    this.status = status
    this.campos = campos ?? {}
  }
}

async function erroDaResposta(resposta) {
  let problema = {}
  try {
    problema = await resposta.json()
  } catch {
    // resposta sem corpo JSON
  }
  const primeiroCampo = problema.campos && Object.values(problema.campos)[0]
  return new ErroApi(
    primeiroCampo ?? problema.detail ?? `O servidor respondeu com erro ${resposta.status}.`,
    resposta.status,
    problema.campos,
  )
}

/** "manter" (keepalive) deixa o pedido terminar mesmo se a página estiver fechando. */
async function requisicao(caminho, { metodo = 'GET', corpo, token, manter = false } = {}) {
  const headers = {}
  if (corpo !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let resposta
  try {
    resposta = await fetch(BASE + caminho, {
      method: metodo,
      headers,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
      keepalive: manter,
    })
  } catch {
    throw new ErroApi('Sem conexão com o servidor. Confira se a API está rodando.', 0)
  }

  if (!resposta.ok) throw await erroDaResposta(resposta)
  return resposta.status === 204 ? null : resposta.json()
}

export const api = {
  // Contas
  cadastrar: (dados) => requisicao('/auth/cadastro', { metodo: 'POST', corpo: dados }),
  entrar: (dados) => requisicao('/auth/login', { metodo: 'POST', corpo: dados }),

  // Catálogo (público)
  novidades: () => requisicao('/musicas'),
  artistas: () => requisicao('/artistas'),
  artista: (id) => requisicao(`/artistas/${id}`),
  album: (id) => requisicao(`/albuns/${id}`),
  buscar: (termo) => requisicao(`/busca?q=${encodeURIComponent(termo)}`),
  excluirMusica: (id, token) => requisicao(`/musicas/${id}`, { metodo: 'DELETE', token }),

  // Jamendo (catálogo Creative Commons)
  jamendoStatus: () => requisicao('/jamendo/status'),
  jamendoBusca: (termo) => requisicao(`/jamendo/busca?q=${encodeURIComponent(termo)}`),

  // YouTube (a busca exige login, para visitantes não gastarem a cota diária)
  youtubeStatus: () => requisicao('/youtube/status'),
  youtubeBusca: (termo, token) => requisicao(`/youtube/busca?q=${encodeURIComponent(termo)}`, { token }),
  youtubeVideos: (termo, token) => requisicao(`/youtube/videos?q=${encodeURIComponent(termo)}`, { token }),
  youtubeEmAlta: (token, categoria) =>
    requisicao(`/youtube/videos/em-alta${categoria ? `?categoria=${categoria}` : ''}`, { token }),
  youtubeCanal: (id, token) => requisicao(`/youtube/canais/${encodeURIComponent(id)}`, { token }),

  // Meus vídeos: "Assistir depois", histórico e de onde continuar
  videosDepois: (token) => requisicao('/eu/videos/assistir-depois', { token }),
  salvarVideoDepois: (video, token) => requisicao('/eu/videos/assistir-depois', { metodo: 'PUT', corpo: video, token }),
  removerVideoDepois: (id, token) =>
    requisicao(`/eu/videos/assistir-depois/${encodeURIComponent(id)}`, { metodo: 'DELETE', token }),
  videosHistorico: (token) => requisicao('/eu/videos/historico', { token }),
  limparHistoricoVideos: (token) => requisicao('/eu/videos/historico', { metodo: 'DELETE', token }),
  progressoVideo: (video, posicaoSegundos, token) =>
    requisicao('/eu/videos/progresso', { metodo: 'PUT', corpo: { video, posicaoSegundos }, token }),
  // Aba Vídeos personalizada
  videosParaVoce: (token) => requisicao('/eu/videos/para-voce', { token }),
  videosInscricoes: (token) => requisicao('/eu/videos/inscricoes', { token }),
  canaisSeguidos: (token) => requisicao('/eu/videos/canais-seguidos', { token }),
  seguirCanal: (canal, token) => requisicao('/eu/videos/canais-seguidos', { metodo: 'PUT', corpo: canal, token }),
  deixarDeSeguir: (id, token) =>
    requisicao(`/eu/videos/canais-seguidos/${encodeURIComponent(id)}`, { metodo: 'DELETE', token }),
  interesses: (token) => requisicao('/eu/videos/interesses', { token }),
  adicionarInteresse: (termo, token) => requisicao('/eu/videos/interesses', { metodo: 'POST', corpo: { termo }, token }),
  removerInteresse: (id, token) => requisicao(`/eu/videos/interesses/${id}`, { metodo: 'DELETE', token }),
  videosDoInteresse: (id, token) => requisicao(`/eu/videos/interesses/${id}/videos`, { token }),
  /** Abriu um vídeo (conta para o "Seu mês"): devolve { id }. */
  registrarVisualizacao: (video, token) => requisicao('/eu/videos/visualizacoes', { metodo: 'POST', corpo: video, token }),
  tempoVisualizacao: (id, segundos, token, manter = false) =>
    requisicao(`/eu/videos/visualizacoes/${id}/tempo`, { metodo: 'PUT', corpo: { segundos }, token, manter }),
  jamendoPopulares: () => requisicao('/jamendo/populares'),

  // Biblioteca (com login)
  curtidas: (token) => requisicao('/eu/curtidas', { token }),
  idsCurtidos: (token) => requisicao('/eu/curtidas/ids', { token }),
  curtir: (id, token) => requisicao(`/eu/curtidas/${id}`, { metodo: 'PUT', token }),
  descurtir: (id, token) => requisicao(`/eu/curtidas/${id}`, { metodo: 'DELETE', token }),
  /** Devolve { id } da reprodução, usado para mandar depois quanto foi ouvido. */
  registrarReproducao: (id, token) => requisicao(`/eu/reproducoes/${id}`, { metodo: 'POST', token }),
  tempoReproducao: (id, segundos, token, manter = false) =>
    requisicao(`/eu/reproducoes/${id}/tempo`, { metodo: 'PUT', corpo: { segundos }, token, manter }),
  resumo: (mes, token) => requisicao(`/eu/resumo${mes ? `?mes=${encodeURIComponent(mes)}` : ''}`, { token }),
  recentes: (token) => requisicao('/eu/recentes', { token }),

  // Spotify (com login)
  spotifyStatus: (token) => requisicao('/spotify/status', { token }),
  spotifyAutorizar: (token) => requisicao('/spotify/autorizar', { token }),
  spotifyDesconectar: (token) => requisicao('/spotify/conexao', { metodo: 'DELETE', token }),
  spotifyPlaylists: (token) => requisicao('/spotify/playlists', { token }),
  spotifyImportar: (id, token) =>
    requisicao(`/spotify/playlists/${encodeURIComponent(id)}/importar`, { metodo: 'POST', token }),
  spotifyMaisOuvidos: (periodo, token) => requisicao(`/spotify/mais-ouvidos?periodo=${periodo}`, { token }),
  spotifyToken: (token) => requisicao('/spotify/token', { token }),
  // Configurações (só no programa para Windows)
  configuracoes: (token) => requisicao('/configuracoes', { token }),
  salvarConfiguracoes: (dados, token) => requisicao('/configuracoes', { metodo: 'PUT', corpo: dados, token }),

  spotifyBusca: (termo, token) => requisicao(`/spotify/busca?q=${encodeURIComponent(termo)}`, { token }),

  // Playlists (com login)
  playlists: (token) => requisicao('/playlists', { token }),
  playlist: (id, token) => requisicao(`/playlists/${id}`, { token }),
  criarPlaylist: (nome, token) => requisicao('/playlists', { metodo: 'POST', corpo: { nome }, token }),
  renomearPlaylist: (id, nome, token) =>
    requisicao(`/playlists/${id}`, { metodo: 'PUT', corpo: { nome }, token }),
  excluirPlaylist: (id, token) => requisicao(`/playlists/${id}`, { metodo: 'DELETE', token }),
  adicionarNaPlaylist: (id, musicaId, token) =>
    requisicao(`/playlists/${id}/musicas`, { metodo: 'POST', corpo: { musicaId }, token }),
  removerDaPlaylist: (id, itemId, token) =>
    requisicao(`/playlists/${id}/musicas/${itemId}`, { metodo: 'DELETE', token }),
}

/**
 * Envio de música. Usa XMLHttpRequest (e não fetch) porque só ele informa
 * o progresso do upload, para mostrar a barra enquanto o arquivo sobe.
 */
export function enviarMusica(formulario, token, aoProgredir) {
  return new Promise((resolver, rejeitar) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${BASE}/musicas`)
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) aoProgredir?.(e.loaded / e.total)
    }
    xhr.onload = async () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolver(JSON.parse(xhr.responseText))
      } else {
        rejeitar(await erroDaResposta(new Response(xhr.responseText, { status: xhr.status })))
      }
    }
    xhr.onerror = () => rejeitar(new ErroApi('Sem conexão com o servidor. Confira se a API está rodando.', 0))
    xhr.send(formulario)
  })
}
