import Cabecalho from '../components/Cabecalho.jsx'
import { Carregando, Erro, PrecisaEntrar } from '../components/Estados.jsx'
import ListaMusicas from '../components/ListaMusicas.jsx'
import { useAuth } from '../contextos/Auth.jsx'
import { useBiblioteca } from '../contextos/Biblioteca.jsx'
import { api } from '../services/api.js'
import { duracaoTotal, plural } from '../utils/formato.js'
import { useCarregar } from '../utils/useCarregar.js'
import { useTocarLista } from '../utils/useTocarLista.js'

export default function Curtidas() {
  const { sessao, chamar } = useAuth()
  const { curtidas } = useBiblioteca()
  const { dados, erro, carregando, recarregar } = useCarregar(
    () => (sessao ? chamar((t) => api.curtidas(t)) : []),
    [sessao],
  )
  // Descurtir nesta página tira a música da lista na hora.
  const musicas = (dados ?? []).filter((m) => curtidas.has(m.id))
  const { tocar, tocandoEsta } = useTocarLista(musicas)

  if (!sessao) return <PrecisaEntrar texto="Entre para guardar as músicas que você curtir." />
  if (carregando) return <Carregando />
  if (erro) return <Erro erro={erro} onTentar={recarregar} />

  return (
    <div className="pagina">
      <Cabecalho
        titulo="Músicas curtidas"
        tipo="Playlist"
        capa={null}
        onTocar={musicas.length ? tocar : undefined}
        tocandoEsta={tocandoEsta}
      >
        {plural(musicas.length, 'música', 'músicas')}
        {musicas.length > 0 && `, ${duracaoTotal(musicas)}`}
      </Cabecalho>
      {musicas.length === 0 ? (
        <p className="estado">Toque no coração de uma música para ela aparecer aqui.</p>
      ) : (
        <ListaMusicas musicas={musicas} onAlterada={recarregar} />
      )}
    </div>
  )
}
