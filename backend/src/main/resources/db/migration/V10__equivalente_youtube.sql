-- App Android: músicas do Spotify tocam pelo vídeo equivalente no YouTube (o Spotify não
-- libera o player dele em apps de celular). O vídeo achado fica guardado para não gastar
-- a cota do YouTube de novo.
ALTER TABLE musica ADD COLUMN youtube_equivalente VARCHAR(20);
