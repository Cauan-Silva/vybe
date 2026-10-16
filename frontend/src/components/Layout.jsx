import { useEffect } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { useBiblioteca } from '../contextos/Biblioteca.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { usePreferencias } from '../contextos/Preferencias.jsx'
import { urlArquivo } from '../services/api.js'
import { NOME_APP } from '../config.js'
import Capa from './Capa.jsx'
import Icone from './Icone.jsx'
import PlayerBar from './PlayerBar.jsx'

export default function Layout() {
  const { alternarPlay, atual } = usePlayer()
  const { luzDinamica } = usePreferencias()
  const capaAtual = luzDinamica && atual?.capaUrl ? urlArquivo(atual.capaUrl) : null

  // Barra de espaço = tocar/pausar, a não ser que a pessoa esteja digitando ou num botão.
  useEffect(() => {
    const aoTeclar = (e) => {
      if (e.code !== 'Space') return
      const alvo = e.target
      if (alvo.closest('input, textarea, select, button, a, [contenteditable="true"]')) return
      e.preventDefault()
      alternarPlay()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [alternarPlay])

  return (
    <div className="app">
      <Lateral />
      <main className="principal" id="conteudo">
        {/* Luz dinâmica: a capa da música atual, bem desfocada, tinge o topo da página */}
        {capaAtual && (
          <div className="luz-dinamica" aria-hidden="true" style={{ backgroundImage: `url("${capaAtual}")` }} />
        )}
        <Outlet />
      </main>
      <PlayerBar />
      <NavMovel />
    </div>
  )
}

function Lateral() {
  const { sessao, usuario, sair } = useAuth()
  const { playlists, criarPlaylist } = useBiblioteca()
  const avisar = useAvisos()
  const navegar = useNavigate()

  async function novaPlaylist() {
    try {
      const nova = await criarPlaylist()
      navegar(`/playlist/${nova.id}`)
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }

  return (
    <nav className="lateral" aria-label="Navegação principal">
      <NavLink to="/" className="marca">
        <img src="/logo.png" alt="" className="marca__logo" />
        {NOME_APP}
      </NavLink>

      <ul className="lateral__menu">
        <li>
          <NavLink to="/" end>
            <Icone nome="inicio" /> Início
          </NavLink>
        </li>
        <li>
          <NavLink to="/buscar">
            <Icone nome="buscar" /> Buscar
          </NavLink>
        </li>
        <li>
          <NavLink to="/videos">
            <Icone nome="video" /> Vídeos
          </NavLink>
        </li>
        <li>
          <NavLink to="/enviar">
            <Icone nome="enviar" /> Enviar música
          </NavLink>
        </li>
        <li>
          <NavLink to="/spotify">
            <Icone nome="conectar" /> Spotify
          </NavLink>
        </li>
        <li>
          <NavLink to="/configuracoes">
            <Icone nome="engrenagem" /> Configurações
          </NavLink>
        </li>
      </ul>

      <div className="lateral__biblioteca">
        <div className="lateral__cabecalho">
          <NavLink to="/biblioteca" className="lateral__titulo">
            <Icone nome="biblioteca" /> Sua biblioteca
          </NavLink>
          {sessao && (
            <button type="button" className="botao-icone" aria-label="Nova playlist" title="Nova playlist" onClick={novaPlaylist}>
              <Icone nome="adicionar" />
            </button>
          )}
        </div>

        {sessao ? (
          <ul className="lateral__playlists">
            <li>
              <NavLink to="/curtidas" className="item-biblioteca">
                <span className="item-biblioteca__curtidas" aria-hidden="true">
                  <Icone nome="coracaoCheio" tamanho={18} />
                </span>
                <span>
                  <span className="item-biblioteca__nome">Músicas curtidas</span>
                </span>
              </NavLink>
            </li>
            {playlists.map((p) => (
              <li key={p.id}>
                <NavLink to={`/playlist/${p.id}`} className="item-biblioteca">
                  <Capa src={p.capaUrl} titulo={p.nome} className="item-biblioteca__capa" />
                  <span>
                    <span className="item-biblioteca__nome">{p.nome}</span>
                    <span className="item-biblioteca__info">
                      {p.quantidade} {p.quantidade === 1 ? 'música' : 'músicas'}
                    </span>
                  </span>
                </NavLink>
              </li>
            ))}
          </ul>
        ) : (
          <div className="lateral__convite">
            <p>Entre para curtir músicas, criar playlists e enviar suas faixas.</p>
            <NavLink to="/entrar" className="botao botao--claro">
              Entrar
            </NavLink>
          </div>
        )}
      </div>

      {sessao && (
        <div className="lateral__conta">
          <span>{usuario.nome}</span>
          <button type="button" className="link-botao" onClick={sair}>
            Sair
          </button>
        </div>
      )}
    </nav>
  )
}

function NavMovel() {
  return (
    <nav className="nav-movel" aria-label="Navegação">
      <NavLink to="/" end>
        <Icone nome="inicio" /> Início
      </NavLink>
      <NavLink to="/buscar">
        <Icone nome="buscar" /> Buscar
      </NavLink>
      <NavLink to="/videos">
        <Icone nome="video" /> Vídeos
      </NavLink>
      <NavLink to="/biblioteca">
        <Icone nome="biblioteca" /> Biblioteca
      </NavLink>
      <NavLink to="/enviar">
        <Icone nome="enviar" /> Enviar
      </NavLink>
    </nav>
  )
}
