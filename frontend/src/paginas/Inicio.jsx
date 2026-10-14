import { Link } from 'react-router-dom'
import Cartao, { Grade } from '../components/Cartao.jsx'
import { Carregando, Erro } from '../components/Estados.jsx'
import ListaMusicas from '../components/ListaMusicas.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { usePlayer } from '../contextos/Player.jsx'
import { api } from '../services/api.js'
import { saudacao } from '../utils/formato.js'
import { useCarregar } from '../utils/useCarregar.js'

export default function Inicio() {
  const { usuario, token, chamar } = useAuth()
  const { tocarLista } = usePlayer()

  const catalogo = useCarregar(() => Promise.all([api.novidades(), api.artistas()]), [])
  const recentes = useCarregar(() => (token ? chamar((t) => api.recentes(t)) : []), [token])

  const [novidades, artistas] = catalogo.dados ?? [[], []]
  const primeiroNome = usuario?.nome.split(' ')[0]

  return (
    <div className="pagina">
      <h1 className="titulo-pagina">
        {saudacao()}
        {primeiroNome ? `, ${primeiroNome}` : ''}
      </h1>

      {catalogo.carregando && <Carregando />}
      {catalogo.erro && <Erro erro={catalogo.erro} onTentar={catalogo.recarregar} />}

      {!catalogo.carregando && !catalogo.erro && novidades.length === 0 && (
        <div className="boas-vindas">
          <h2>O catálogo ainda está vazio</h2>
          <p>
            Envie a primeira música para começar. Use faixas suas ou com licença livre, como as do Free Music
            Archive ou da Biblioteca de Áudio do YouTube.
          </p>
          <Link to="/enviar" className="botao">
            Enviar música
          </Link>
        </div>
      )}

      {recentes.dados?.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Tocadas recentemente</h2>
          <Grade>
            {recentes.dados.slice(0, 6).map((m, i) => (
              <Cartao
                key={m.id}
                para={`/album/${m.albumId}`}
                capa={m.capaUrl}
                titulo={m.titulo}
                subtitulo={m.artistaNome}
                onTocar={() => tocarLista(recentes.dados, i)}
              />
            ))}
          </Grade>
        </section>
      )}

      {novidades.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Chegaram agora</h2>
          <ListaMusicas musicas={novidades} onAlterada={catalogo.recarregar} />
        </section>
      )}

      {artistas.length > 0 && (
        <section className="secao">
          <h2 className="titulo-secao">Artistas</h2>
          <Grade>
            {artistas.map((a) => (
              <Cartao key={a.id} para={`/artista/${a.id}`} titulo={a.nome} subtitulo="Artista" redonda />
            ))}
          </Grade>
        </section>
      )}
    </div>
  )
}
