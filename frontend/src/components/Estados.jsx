import { Link, useLocation } from 'react-router-dom'

export function Carregando({ texto = 'Carregando…' }) {
  return <p className="estado">{texto}</p>
}

export function Erro({ erro, onTentar }) {
  return (
    <div className="estado estado--erro" role="alert">
      <p>{erro?.message ?? 'Algo deu errado.'}</p>
      {onTentar && (
        <button type="button" className="botao botao--claro" onClick={onTentar}>
          Tentar de novo
        </button>
      )}
    </div>
  )
}

/** Tela para quem precisa entrar antes de usar a página. */
export function PrecisaEntrar({ texto }) {
  const local = useLocation()
  return (
    <div className="estado estado--convite">
      <p>{texto}</p>
      <Link className="botao" to="/entrar" state={{ de: local.pathname }}>
        Entrar ou criar conta
      </Link>
    </div>
  )
}
