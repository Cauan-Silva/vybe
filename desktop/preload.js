// Ponte mínima e segura entre o programa e as telas: só avisa que o VYBE está rodando
// como programa (para a tela de Configurações aparecer e as mensagens se ajustarem).
const { contextBridge } = require('electron')

contextBridge.exposeInMainWorld('vybeDesktop', {
  programa: true,
  plataforma: process.platform,
})
