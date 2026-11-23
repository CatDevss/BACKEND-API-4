-- BE-15: apoio às consultas de imóveis do CAR.
-- Todas as consultas filtram por cam_id; o Oracle não cria índice para chave estrangeira.
CREATE INDEX IF NOT EXISTS idx_feicao_cam_id ON feicao (cam_id);