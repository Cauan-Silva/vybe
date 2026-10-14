import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../contextos/Auth.jsx'
import { useBiblioteca } from '../contextos/Biblioteca.jsx'
import Icone from './Icone.jsx'

export default function BotaoCurtir({ musica, tamanho = 18 }) {
  const { sessao } = useAuth()
  const { curtidas, alternarCurtida } = useBiblioteca()
  const navegar = useNavigate()
  const local = useLocation()
  const curtida = curtidas.has(musica.id)

  function clicar(e) {
    e.stopPropagation()
    if (!sessao) {
      navegar('/entrar', { state: { de: local.pathname, motivo: 'Entre para curtir músicas.' } })
      return
    }
    alternarCurtida(musica)
  }

  return (
    <button
      type="button"
      className={`botao-icone botao-curtir ${curtida ? 'botao-curtir--ativo' : ''}`}
      aria-pressed={curtida}
      aria-label={curtida ? `Descurtir ${musica.titulo}` : `Curtir ${musica.titulo}`}
      onClick={clicar}
    >
      <Icone nome={curtida ? 'coracaoCheio' : 'coracao'} tamanho={tamanho} />
    </button>
  )
}
