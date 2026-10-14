import { useCallback, useEffect, useState } from 'react'

/**
 * Carrega dados assíncronos e guarda estado de carregando/erro.
 * Ignora respostas que chegam depois que as dependências mudaram.
 */
export function useCarregar(funcao, dependencias) {
  const [dados, setDados] = useState(null)
  const [erro, setErro] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setErro(null)
    Promise.resolve()
      .then(funcao)
      .then((r) => !cancelado && setDados(r))
      .catch((e) => !cancelado && setErro(e))
      .finally(() => !cancelado && setCarregando(false))
    return () => {
      cancelado = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencias, versao])

  const recarregar = useCallback(() => setVersao((v) => v + 1), [])

  return { dados, erro, carregando, recarregar, setDados }
}
