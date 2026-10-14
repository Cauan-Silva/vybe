-- Capa vinda de fora (Jamendo). Álbuns enviados continuam usando capa_arquivo.
ALTER TABLE album ADD COLUMN capa_url_externa VARCHAR(500);

-- Crédito obrigatório das músicas Creative Commons: licença e página original.
ALTER TABLE musica ADD COLUMN licenca_url VARCHAR(300);
ALTER TABLE musica ADD COLUMN pagina_url VARCHAR(300);

-- Cada faixa do Jamendo entra uma vez só.
CREATE UNIQUE INDEX uq_musica_externa ON musica (origem, id_externo) WHERE id_externo IS NOT NULL;
