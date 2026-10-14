import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'

const AvisosContexto = createContext(() => {})

/** Mensagens curtas que aparecem acima do player e somem sozinhas. */
export function AvisosProvider({ children }) {
  const [aviso, setAviso] = useState(null)
  const temporizador = useRef(null)

  const avisar = useCallback((texto, tipo = 'info') => {
    clearTimeout(temporizador.current)
    setAviso({ texto, tipo, id: Date.now() })
    temporizador.current = setTimeout(() => setAviso(null), 3500)
  }, [])

  useEffect(() => () => clearTimeout(temporizador.current), [])

  return (
    <AvisosContexto.Provider value={avisar}>
      {children}
      <div className="avisos" role="status" aria-live="polite">
        {aviso && (
          <p key={aviso.id} className={`aviso-flutuante aviso-flutuante--${aviso.tipo}`}>
            {aviso.texto}
          </p>
        )}
      </div>
    </AvisosContexto.Provider>
  )
}

export const useAvisos = () => useContext(AvisosContexto)
