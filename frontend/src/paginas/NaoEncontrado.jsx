import { Link } from 'react-router-dom'

export default function NaoEncontrado() {
  return (
    <div className="pagina estado estado--convite">
      <h1 className="titulo-pagina">Essa página não existe</h1>
      <p>O endereço pode estar errado ou o conteúdo foi removido.</p>
      <Link to="/" className="botao">
        Voltar ao início
      </Link>
    </div>
  )
}
