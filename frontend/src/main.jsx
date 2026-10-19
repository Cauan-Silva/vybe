import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AuthProvider } from './contextos/Auth.jsx'
import { AvisosProvider } from './contextos/Avisos.jsx'
import { BibliotecaProvider } from './contextos/Biblioteca.jsx'
import { PerfilProvider } from './contextos/Perfil.jsx'
import { PlayerProvider } from './contextos/Player.jsx'
import { PreferenciasProvider, aplicarPreferencias, lerPreferencias } from './contextos/Preferencias.jsx'
import './styles.css'
import { aplicarModoLeve, lerModoLeve } from './utils/modoLeve.js'
import { EH_APP } from './config.js'

// No app Android, o CSS ajusta as margens da barra de status e da navegação do sistema.
if (EH_APP) document.documentElement.classList.add('app-nativo')

// Antes de desenhar, para não piscar o tema padrão
aplicarModoLeve(lerModoLeve())
aplicarPreferencias(lerPreferencias())

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <PreferenciasProvider>
      <AuthProvider>
        <PerfilProvider>
        <AvisosProvider>
          <BibliotecaProvider>
            <PlayerProvider>
              <App />
            </PlayerProvider>
          </BibliotecaProvider>
        </AvisosProvider>
        </PerfilProvider>
      </AuthProvider>
      </PreferenciasProvider>
    </BrowserRouter>
  </StrictMode>,
)
