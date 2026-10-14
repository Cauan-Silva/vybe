const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

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

async function requisicao(caminho, { metodo = 'GET', corpo, token } = {}) {
  const headers = {}
  if (corpo !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let resposta
  try {
    resposta = await fetch(BASE + caminho, {
      method: metodo,
      headers,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
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

  // Biblioteca (com login)
  curtidas: (token) => requisicao('/eu/curtidas', { token }),
  idsCurtidos: (token) => requisicao('/eu/curtidas/ids', { token }),
  curtir: (id, token) => requisicao(`/eu/curtidas/${id}`, { metodo: 'PUT', token }),
  descurtir: (id, token) => requisicao(`/eu/curtidas/${id}`, { metodo: 'DELETE', token }),
  registrarReproducao: (id, token) => requisicao(`/eu/reproducoes/${id}`, { metodo: 'POST', token }),
  recentes: (token) => requisicao('/eu/recentes', { token }),

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
