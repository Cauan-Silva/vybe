-- Fase 4: player do Spotify.
-- Guarda as permissões que a pessoa concedeu; o player só funciona se "streaming" estiver entre elas.
-- Contas conectadas antes desta versão ficam sem escopos e precisam conectar de novo para tocar.
ALTER TABLE spotify_conta ADD COLUMN escopos TEXT;
