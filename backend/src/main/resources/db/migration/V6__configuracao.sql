-- Chaves das integrações (Spotify, YouTube, Jamendo) definidas pela tela de Configurações do programa.
-- Na versão web a tabela fica vazia e valem as variáveis de ambiente.
CREATE TABLE configuracao (
    chave VARCHAR(50) PRIMARY KEY,
    valor TEXT        NOT NULL
);
