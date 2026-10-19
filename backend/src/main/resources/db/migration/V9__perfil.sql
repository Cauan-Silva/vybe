-- Perfil: foto, banner e uma frase curta. As imagens ficam na pasta de armazenamento do VYBE.
ALTER TABLE usuario ADD COLUMN foto_arquivo VARCHAR(100);
ALTER TABLE usuario ADD COLUMN banner_arquivo VARCHAR(100);
ALTER TABLE usuario ADD COLUMN bio VARCHAR(160);
