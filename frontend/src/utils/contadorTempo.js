/**
 * Conta quanto tempo algo ficou de fato tocando (usado no "Seu mês no VYBE").
 * tocar() e pausar() podem ser chamados à vontade; total() devolve os segundos inteiros.
 */
export function criarContador() {
  let acumulado = 0
  let desde = null
  return {
    tocar() {
      if (desde === null) desde = performance.now()
    },
    pausar() {
      if (desde !== null) {
        acumulado += (performance.now() - desde) / 1000
        desde = null
      }
    },
    total() {
      return Math.floor(acumulado + (desde === null ? 0 : (performance.now() - desde) / 1000))
    },
  }
}

/**
 * Registro de tempo no servidor: guarda o id que o servidor devolveu e manda o total quando
 * pedido (sem repetir o mesmo valor). "enviar(id, segundos, manter)" faz o pedido.
 */
export function criarRegistroDeTempo(enviar) {
  const contador = criarContador()
  let id = null
  let enviado = 0
  return {
    contador,
    definirId(novo) {
      id = novo
    },
    mandar(manter = false) {
      const total = contador.total()
      if (!id || total === enviado) return
      enviado = total
      enviar(id, total, manter)
    },
  }
}
