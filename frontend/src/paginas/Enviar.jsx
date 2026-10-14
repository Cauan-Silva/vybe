import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PrecisaEntrar } from '../components/Estados.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { api, enviarMusica } from '../services/api.js'
import { duracao, tituloDoArquivo } from '../utils/formato.js'

const LIMITE_MB = 30

/** O navegador lê a duração do áudio antes do envio, sem tocar nada. */
function lerDuracao(arquivo) {
  return new Promise((resolver, rejeitar) => {
    const url = URL.createObjectURL(arquivo)
    const audio = new Audio()
    audio.preload = 'metadata'
    audio.onloadedmetadata = () => {
      URL.revokeObjectURL(url)
      Number.isFinite(audio.duration) && audio.duration > 0 ? resolver(Math.round(audio.duration)) : rejeitar()
    }
    audio.onerror = () => {
      URL.revokeObjectURL(url)
      rejeitar()
    }
    audio.src = url
  })
}

export default function Enviar() {
  const { sessao, chamar } = useAuth()
  const avisar = useAvisos()
  const navegar = useNavigate()

  const [arquivo, setArquivo] = useState(null)
  const [segundos, setSegundos] = useState(null)
  const [titulo, setTitulo] = useState('')
  const [artista, setArtista] = useState('')
  const [album, setAlbum] = useState('')
  const [capa, setCapa] = useState(null)
  const [previaCapa, setPreviaCapa] = useState(null)
  const [artistas, setArtistas] = useState([])
  const [progresso, setProgresso] = useState(null)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    api.artistas().then(setArtistas).catch(() => {})
  }, [])

  useEffect(() => {
    if (!capa) {
      setPreviaCapa(null)
      return
    }
    const url = URL.createObjectURL(capa)
    setPreviaCapa(url)
    return () => URL.revokeObjectURL(url)
  }, [capa])

  if (!sessao) return <PrecisaEntrar texto="Entre para enviar músicas." />

  async function escolherArquivo(e) {
    const escolhido = e.target.files[0]
    setErro(null)
    setSegundos(null)
    if (!escolhido) {
      setArquivo(null)
      return
    }
    if (escolhido.size > LIMITE_MB * 1024 * 1024) {
      setArquivo(null)
      setErro(`O arquivo tem mais de ${LIMITE_MB} MB. Escolha um arquivo menor.`)
      return
    }
    setArquivo(escolhido)
    if (!titulo) setTitulo(tituloDoArquivo(escolhido.name))
    try {
      setSegundos(await lerDuracao(escolhido))
    } catch {
      setErro('Não foi possível ler este áudio. Confira se é um MP3, WAV, OGG, M4A ou FLAC que toca normalmente.')
    }
  }

  async function enviar(e) {
    e.preventDefault()
    if (!arquivo || !segundos) return
    const formulario = new FormData()
    formulario.append('arquivo', arquivo)
    if (capa) formulario.append('capa', capa)
    formulario.append('titulo', titulo.trim())
    formulario.append('artista', artista.trim())
    if (album.trim()) formulario.append('album', album.trim())
    formulario.append('duracaoSegundos', String(segundos))

    setErro(null)
    setProgresso(0)
    try {
      const musica = await chamar((t) => enviarMusica(formulario, t, setProgresso))
      avisar(`"${musica.titulo}" foi enviada`)
      navegar(`/album/${musica.albumId}`)
    } catch (falha) {
      setErro(falha.message)
      setProgresso(null)
    }
  }

  const enviando = progresso !== null
  const pronto = arquivo && segundos && titulo.trim() && artista.trim()

  return (
    <div className="pagina pagina--estreita">
      <h1 className="titulo-pagina">Enviar música</h1>
      <p className="texto-apoio">
        Envie só faixas suas ou com licença que permita compartilhar, como Creative Commons.
      </p>

      {erro && (
        <p className="alerta" role="alert">
          {erro}
        </p>
      )}

      <form className="formulario" onSubmit={enviar}>
        <label className="seletor-arquivo">
          <input type="file" accept="audio/*" onChange={escolherArquivo} disabled={enviando} />
          <span className="seletor-arquivo__titulo">{arquivo ? arquivo.name : 'Escolher arquivo de áudio'}</span>
          <span className="seletor-arquivo__detalhe">
            {arquivo
              ? `${(arquivo.size / 1024 / 1024).toFixed(1)} MB${segundos ? `, ${duracao(segundos)}` : ''}`
              : `MP3, WAV, OGG, M4A ou FLAC, até ${LIMITE_MB} MB`}
          </span>
        </label>

        <label className="campo">
          <span>Título</span>
          <input required maxLength={200} value={titulo} onChange={(e) => setTitulo(e.target.value)} />
        </label>

        <label className="campo">
          <span>Artista</span>
          <input
            required
            maxLength={150}
            list="lista-artistas"
            value={artista}
            onChange={(e) => setArtista(e.target.value)}
          />
          <datalist id="lista-artistas">
            {artistas.map((a) => (
              <option key={a.id} value={a.nome} />
            ))}
          </datalist>
        </label>

        <label className="campo">
          <span>
            Álbum <span className="campo__opcional">(opcional; sem álbum, a música vira um single)</span>
          </span>
          <input maxLength={200} value={album} onChange={(e) => setAlbum(e.target.value)} />
        </label>

        <div className="campo-capa">
          {previaCapa ? (
            <img src={previaCapa} alt="Prévia da capa" className="campo-capa__previa" />
          ) : (
            <div className="campo-capa__previa campo-capa__previa--vazia" aria-hidden="true" />
          )}
          <label className="campo">
            <span>
              Capa <span className="campo__opcional">(opcional, JPG, PNG ou WebP até 5 MB)</span>
            </span>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={enviando}
              onChange={(e) => setCapa(e.target.files[0] ?? null)}
            />
          </label>
        </div>

        {enviando ? (
          <div className="envio-progresso" role="progressbar" aria-valuenow={Math.round(progresso * 100)} aria-valuemin={0} aria-valuemax={100} aria-label="Enviando">
            <div className="envio-progresso__barra" style={{ width: `${progresso * 100}%` }} />
            <span>{progresso < 1 ? `Enviando… ${Math.round(progresso * 100)}%` : 'Salvando…'}</span>
          </div>
        ) : (
          <button type="submit" className="botao" disabled={!pronto}>
            Enviar música
          </button>
        )}
      </form>
    </div>
  )
}
