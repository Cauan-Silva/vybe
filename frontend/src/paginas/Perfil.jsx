import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Avatar from '../components/Avatar.jsx'
import Capa from '../components/Capa.jsx'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import Icone from '../components/Icone.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { usePerfil } from '../contextos/Perfil.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api, enviarImagemPerfil, urlArquivo } from '../services/api.js'
import { useCarregar } from '../utils/useCarregar.js'
import { tempoLegivel } from './SeuMes.jsx'

/*
 * Página do perfil: banner, foto, nome e frase, com as músicas e os artistas mais ouvidos
 * (do mês ou de sempre). "Editar perfil" troca foto, banner, nome e frase.
 */

const MESES = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })
const LIMITE_IMAGEM = 5 * 1024 * 1024
const TAMANHO_BIO = 160

function membroDesde(data) {
  if (!data) return null
  const [ano, mes] = data.split('-').map(Number)
  return MESES.format(new Date(ano, mes - 1, 1))
}

const vezes = (n) => (n === 1 ? '1 vez' : `${n} vezes`)

export default function Perfil() {
  const { sessao, chamar } = useAuth()
  const { perfil } = usePerfil()
  const [periodo, setPeriodo] = useState('mes')
  const [editando, setEditando] = useState(false)
  const mais = useCarregar(() => (sessao ? chamar((t) => api.maisOuvidas(periodo, t)) : null), [sessao, periodo])

  if (!sessao) return <PrecisaEntrar texto="Entre no VYBE para ver o seu perfil." />
  if (!perfil) return <Carregando />

  const desde = membroDesde(perfil.membroDesde)
  const banner = perfil.bannerUrl ? { backgroundImage: `url("${urlArquivo(perfil.bannerUrl)}")` } : undefined

  return (
    <div className="pagina perfil">
      <header className="perfil__topo">
        <div className={`perfil__banner ${banner ? '' : 'perfil__banner--vazio'}`} style={banner} aria-hidden="true" />
        <div className="perfil__identidade">
          <Avatar nome={perfil.nome} fotoUrl={perfil.fotoUrl} tamanho={136} className="perfil__avatar" />
          <div className="perfil__textos">
            <p className="perfil__chapeu">Perfil</p>
            <h1 className="titulo-pagina perfil__nome">{perfil.nome}</h1>
            {perfil.bio && <p className="perfil__bio">{perfil.bio}</p>}
            <p className="nota-secao">
              {desde && <>No VYBE desde {desde} · </>}
              <Link to="/seu-mes" className="link-discreto">
                Ver o seu mês
              </Link>
              {' · '}
              <Link to="/enviar" className="link-discreto">
                Enviar música
              </Link>
            </p>
          </div>
          <button
            type="button"
            className="botao botao--contorno botao--pequeno perfil__editar"
            aria-expanded={editando}
            onClick={() => setEditando((e) => !e)}
          >
            <Icone nome="engrenagem" tamanho={16} /> {editando ? 'Fechar edição' : 'Editar perfil'}
          </button>
        </div>
      </header>

      {editando && <EditarPerfil onFechar={() => setEditando(false)} />}

      <div className="perfil__cabecalho-secao">
        <h2 className="titulo-secao">Mais ouvidas</h2>
        <div className="alternador" role="group" aria-label="Período">
          <button type="button" aria-pressed={periodo === 'mes'} onClick={() => setPeriodo('mes')}>
            Este mês
          </button>
          <button type="button" aria-pressed={periodo === 'sempre'} onClick={() => setPeriodo('sempre')}>
            Sempre
          </button>
        </div>
      </div>

      {mais.carregando && !mais.dados && <Carregando />}
      {mais.erro && <Erro erro={mais.erro} onTentar={mais.recarregar} />}
      {mais.dados && mais.dados.musicas.length === 0 && (
        <div className="painel-info">
          <p>
            {periodo === 'mes'
              ? 'Nada ouvido este mês ainda. Cada música entra aqui a partir de 30 segundos.'
              : 'Nada ouvido ainda. Cada música entra aqui a partir de 30 segundos.'}
          </p>
        </div>
      )}
      {mais.dados && mais.dados.musicas.length > 0 && (
        <div className="perfil__grade">
          <MusicasMaisOuvidas itens={mais.dados.musicas} />
          <ArtistasMaisOuvidos itens={mais.dados.artistas} />
        </div>
      )}
    </div>
  )
}

function MusicasMaisOuvidas({ itens }) {
  const { tocarLista } = usePlayer()
  const lista = itens.map((i) => i.musica)
  return (
    <section className="secao top" aria-labelledby="perfil-musicas">
      <div className="top__cabecalho">
        <h3 className="titulo-secao" id="perfil-musicas">
          Músicas
        </h3>
        <button type="button" className="botao botao--pequeno" onClick={() => tocarLista(lista, 0)}>
          <Icone nome="play" tamanho={16} /> Tocar
        </button>
      </div>
      <ol className="top__lista">
        {itens.map((item, i) => (
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

function ArtistasMaisOuvidos({ itens }) {
  return (
    <section className="secao top" aria-labelledby="perfil-artistas">
      <h3 className="titulo-secao" id="perfil-artistas">
        Artistas
      </h3>
      <ol className="perfil__artistas">
        {itens.map((a, i) => (
          <li key={a.id}>
            <Link to={`/artista/${a.id}`} className="perfil__artista">
              <span className="perfil__artista-capa">
                <Capa src={a.capaUrl} titulo={a.nome} redonda />
                <span className="perfil__artista-posicao">{i + 1}</span>
              </span>
              <span className="perfil__artista-nome">{a.nome}</span>
              <span className="top__apoio">{tempoLegivel(a.segundos)}</span>
            </Link>
          </li>
        ))}
      </ol>
    </section>
  )
}

/** Foto e banner são enviados na hora em que a pessoa escolhe; nome e frase, no "Salvar". */
function EditarPerfil({ onFechar }) {
  const { chamar } = useAuth()
  const { perfil, setPerfil } = usePerfil()
  const avisar = useAvisos()
  const [nome, setNome] = useState(perfil.nome)
  const [bio, setBio] = useState(perfil.bio ?? '')
  const [enviando, setEnviando] = useState(null) // 'foto' | 'banner' | 'dados'
  const entradaFoto = useRef(null)
  const entradaBanner = useRef(null)

  async function trocarImagem(tipo, arquivo) {
    if (!arquivo) return
    if (!/^image\/(jpeg|png|webp)$/.test(arquivo.type)) {
      avisar('Use uma imagem JPG, PNG ou WebP.', 'erro')
      return
    }
    if (arquivo.size > LIMITE_IMAGEM) {
      avisar('A imagem pode ter no máximo 5 MB.', 'erro')
      return
    }
    setEnviando(tipo)
    try {
      setPerfil(await chamar((t) => enviarImagemPerfil(tipo, arquivo, t)))
      avisar(tipo === 'foto' ? 'Foto atualizada' : 'Banner atualizado')
    } catch (e) {
      avisar(e.message, 'erro')
    } finally {
      setEnviando(null)
    }
  }

  async function removerImagem(tipo) {
    setEnviando(tipo)
    try {
      setPerfil(await chamar((t) => api.removerImagemPerfil(tipo, t)))
    } catch (e) {
      avisar(e.message, 'erro')
    } finally {
      setEnviando(null)
    }
  }

  async function salvar(e) {
    e.preventDefault()
    setEnviando('dados')
    try {
      setPerfil(await chamar((t) => api.atualizarPerfil({ nome: nome.trim(), bio: bio.trim() }, t)))
      avisar('Perfil salvo')
      onFechar()
    } catch (erro) {
      avisar(erro.message, 'erro')
    } finally {
      setEnviando(null)
    }
  }

  const banner = perfil.bannerUrl ? { backgroundImage: `url("${urlArquivo(perfil.bannerUrl)}")` } : undefined

  return (
    <form className="editar-perfil" onSubmit={salvar}>
      <div className="editar-perfil__imagens">
        <div className="editar-perfil__bloco">
          <span className="editar-perfil__titulo">Foto</span>
          <Avatar nome={perfil.nome} fotoUrl={perfil.fotoUrl} tamanho={88} />
          <div className="editar-perfil__botoes">
            <button
              type="button"
              className="botao botao--contorno botao--pequeno"
              disabled={enviando !== null}
              onClick={() => entradaFoto.current?.click()}
            >
              {enviando === 'foto' ? 'Enviando…' : 'Trocar foto'}
            </button>
            {perfil.fotoUrl && (
              <button type="button" className="link-botao" disabled={enviando !== null} onClick={() => removerImagem('foto')}>
                Remover
              </button>
            )}
          </div>
          <input
            ref={entradaFoto}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            hidden
            onChange={(e) => {
              trocarImagem('foto', e.target.files?.[0])
              e.target.value = ''
            }}
          />
        </div>

        <div className="editar-perfil__bloco editar-perfil__bloco--banner">
          <span className="editar-perfil__titulo">Banner</span>
          <div className={`editar-perfil__banner ${banner ? '' : 'perfil__banner--vazio'}`} style={banner} />
          <div className="editar-perfil__botoes">
            <button
              type="button"
              className="botao botao--contorno botao--pequeno"
              disabled={enviando !== null}
              onClick={() => entradaBanner.current?.click()}
            >
              {enviando === 'banner' ? 'Enviando…' : 'Trocar banner'}
            </button>
            {perfil.bannerUrl && (
              <button type="button" className="link-botao" disabled={enviando !== null} onClick={() => removerImagem('banner')}>
                Remover
              </button>
            )}
            <span className="texto-apoio-claro">Ideal: 1500 × 500, até 5 MB</span>
          </div>
          <input
            ref={entradaBanner}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            hidden
            onChange={(e) => {
              trocarImagem('banner', e.target.files?.[0])
              e.target.value = ''
            }}
          />
        </div>
      </div>

      <label className="campo">
        <span>Nome</span>
        <input value={nome} onChange={(e) => setNome(e.target.value)} minLength={2} maxLength={100} required />
      </label>
      <label className="campo">
        <span>
          Frase <span className="campo__opcional">(opcional, {bio.length}/{TAMANHO_BIO})</span>
        </span>
        <textarea
          value={bio}
          rows={2}
          maxLength={TAMANHO_BIO}
          placeholder="Ex.: Valorant, rap e lo-fi para estudar"
          onChange={(e) => setBio(e.target.value)}
        />
      </label>
      <div className="editar-perfil__acoes">
        <button type="submit" className="botao" disabled={enviando !== null || nome.trim().length < 2}>
          <Icone nome="feito" tamanho={18} /> {enviando === 'dados' ? 'Salvando…' : 'Salvar'}
        </button>
        <button type="button" className="link-botao" onClick={onFechar}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
