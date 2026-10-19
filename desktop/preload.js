// Ponte mínima e segura entre o programa e as telas (só na janela do Electron, quando não há
// Edge): avisa que o VYBE está rodando como programa e deixa a página mandar mensagens
// (o que está tocando, para o Discord; pedidos de atualização). As respostas do programa
// chegam por window.__vybeDoPrograma, chamada pelo próprio programa.
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('vybeDesktop', {
  programa: true,
  plataforma: process.platform,
  enviar: (json) => ipcRenderer.send('vybe:da-pagina', String(json)),
})
