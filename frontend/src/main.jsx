import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AuthProvider } from './contextos/Auth.jsx'
import { AvisosProvider } from './contextos/Avisos.jsx'
import { BibliotecaProvider } from './contextos/Biblioteca.jsx'
import { PlayerProvider } from './contextos/Player.jsx'
import './styles.css'
import { aplicarModoLeve, lerModoLeve } from './utils/modoLeve.js'

aplicarModoLeve(lerModoLeve())

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <AvisosProvider>
          <BibliotecaProvider>
            <PlayerProvider>
              <App />
            </PlayerProvider>
          </BibliotecaProvider>
        </AvisosProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
