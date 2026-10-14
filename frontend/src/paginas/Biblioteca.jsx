import { Link, useNavigate } from 'react-router-dom'
import Cartao, { Grade } from '../components/Cartao.jsx'
import { PrecisaEntrar } from '../components/Estados.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useAvisos } from '../contextos/Avisos.jsx'
import { useBiblioteca } from '../contextos/Biblioteca.jsx'
import { plural } from '../utils/formato.js'

export default function Biblioteca() {
  const { sessao, usuario, sair } = useAuth()
  const { curtidas, playlists, criarPlaylist } = useBiblioteca()
  const avisar = useAvisos()
  const navegar = useNavigate()

  if (!sessao) return <PrecisaEntrar texto="Entre para ver suas curtidas e playlists." />

  async function novaPlaylist() {
    try {
      const nova = await criarPlaylist()
      navegar(`/playlist/${nova.id}`)
    } catch (e) {
      avisar(e.message, 'erro')
    }
  }

  return (
    <div className="pagina">
      <div className="titulo-com-acao">
        <h1 className="titulo-pagina">Sua biblioteca</h1>
        <button type="button" className="botao" onClick={novaPlaylist}>
          Nova playlist
        </button>
      </div>

      <Grade>
        <Cartao
          para="/curtidas"
          titulo="Músicas curtidas"
          subtitulo={plural(curtidas.size, 'música', 'músicas')}
        />
        {playlists.map((p) => (
          <Cartao
            key={p.id}
            para={`/playlist/${p.id}`}
            capa={p.capaUrl}
            titulo={p.nome}
            subtitulo={plural(p.quantidade, 'música', 'músicas')}
          />
        ))}
      </Grade>

      <p className="conta-movel">
        <Link to="/spotify">Conectar o Spotify</Link>
      </p>
      <p className="conta-movel">
        Conectado como {usuario.nome}.{' '}
        <button type="button" className="link-botao" onClick={sair}>
          Sair
        </button>
      </p>
    </div>
  )
}
