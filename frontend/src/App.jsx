import { useEffect } from 'react'
import { Route, Routes, useLocation } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import Inicio from './paginas/Inicio.jsx'
import Buscar from './paginas/Buscar.jsx'
import Artista from './paginas/Artista.jsx'
import Album from './paginas/Album.jsx'
import Playlist from './paginas/Playlist.jsx'
import Curtidas from './paginas/Curtidas.jsx'
import Biblioteca from './paginas/Biblioteca.jsx'
import Enviar from './paginas/Enviar.jsx'
import Entrar from './paginas/Entrar.jsx'
import Spotify from './paginas/Spotify.jsx'
import Videos from './paginas/Videos.jsx'
import CanalVideos from './paginas/CanalVideos.jsx'
import NaoEncontrado from './paginas/NaoEncontrado.jsx'

export default function App() {
  const { pathname } = useLocation()

  // Ao trocar de página, volta ao topo da área de conteúdo.
  useEffect(() => {
    document.getElementById('conteudo')?.scrollTo(0, 0)
  }, [pathname])

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Inicio />} />
        <Route path="buscar" element={<Buscar />} />
        <Route path="artista/:id" element={<Artista />} />
        <Route path="album/:id" element={<Album />} />
        <Route path="playlist/:id" element={<Playlist />} />
        <Route path="curtidas" element={<Curtidas />} />
        <Route path="biblioteca" element={<Biblioteca />} />
        <Route path="enviar" element={<Enviar />} />
        <Route path="entrar" element={<Entrar />} />
        <Route path="spotify" element={<Spotify />} />
        <Route path="videos" element={<Videos />} />
        <Route path="videos/canal/:id" element={<CanalVideos />} />
        <Route path="*" element={<NaoEncontrado />} />
      </Route>
    </Routes>
  )
}
