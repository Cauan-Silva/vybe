import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../contextos/Auth.jsx'
import { NOME_APP } from '../config.js'

export default function Entrar() {
  const { sessao, entrar, cadastrar } = useAuth()
  const local = useLocation()
  const navegar = useNavigate()
  const [modo, setModo] = useState('entrar')
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState(null)

  const destino = local.state?.de ?? '/'
  if (sessao && !enviando) return <Navigate to={destino} replace />

  async function enviar(e) {
    e.preventDefault()
    setEnviando(true)
    setErro(null)
    try {
      if (modo === 'entrar') await entrar(email.trim(), senha)
      else await cadastrar(nome.trim(), email.trim(), senha)
      navegar(destino, { replace: true })
    } catch (falha) {
      setErro(falha.message)
      setEnviando(false)
    }
  }

  return (
    <div className="pagina pagina--estreita">
      <h1 className="titulo-pagina">{modo === 'entrar' ? 'Entrar' : `Criar conta em ${NOME_APP}`}</h1>
      {local.state?.motivo && <p className="texto-apoio">{local.state.motivo}</p>}

      <div className="alternador" role="group" aria-label="Escolha">
        <button type="button" aria-pressed={modo === 'entrar'} onClick={() => setModo('entrar')}>
          Já tenho conta
        </button>
        <button type="button" aria-pressed={modo === 'cadastrar'} onClick={() => setModo('cadastrar')}>
          Criar conta
        </button>
      </div>

      {erro && (
        <p className="alerta" role="alert">
          {erro}
        </p>
      )}

      <form className="formulario" onSubmit={enviar}>
        {modo === 'cadastrar' && (
          <label className="campo">
            <span>Nome</span>
            <input required maxLength={100} autoComplete="name" value={nome} onChange={(e) => setNome(e.target.value)} />
          </label>
        )}
        <label className="campo">
          <span>E-mail</span>
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label className="campo">
          <span>Senha {modo === 'cadastrar' && <span className="campo__opcional">(mínimo 8 caracteres)</span>}</span>
          <input
            type="password"
            required
            minLength={modo === 'cadastrar' ? 8 : undefined}
            autoComplete={modo === 'entrar' ? 'current-password' : 'new-password'}
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
          />
        </label>
        <button type="submit" className="botao" disabled={enviando}>
          {enviando ? 'Aguarde…' : modo === 'entrar' ? 'Entrar' : 'Criar conta'}
        </button>
      </form>
    </div>
  )
}
