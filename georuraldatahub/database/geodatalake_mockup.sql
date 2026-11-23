-- Cadastros iniciais do GeoRural DataHub.

-- 1. USUARIOS

INSERT INTO usuario (usr_nome, usr_email, usr_tipo, usr_status)
VALUES (
    'Administrador GeoRural',
    'admin@georuraldatahub.com.br',
    'ADMIN',
    1
);

INSERT INTO usuario (usr_nome, usr_email, usr_tipo, usr_status)
VALUES (
    'Operador de Dados',
    'operador@georuraldatahub.com.br',
    'OPERADOR',
    1
);

INSERT INTO usuario (usr_nome, usr_email, usr_tipo, usr_status)
VALUES (
    'Analista Ambiental',
    'analista@georuraldatahub.com.br',
    'ANALISTA',
    1
);

INSERT INTO usuario (usr_nome, usr_email, usr_tipo, usr_status)
VALUES (
    'Gestor Ambiental',
    'gestor@georuraldatahub.com.br',
    'GESTOR',
    1
);

INSERT INTO usuario (usr_nome, usr_email, usr_tipo, usr_status)
VALUES (
    'Auditor de Dados',
    'auditor@georuraldatahub.com.br',
    'AUDITOR',
    1
);



-- 2. FONTES DE DADOS

-- 2.1. Cadastro Ambiental Rural (CAR)
-- Camadas: perímetro do imóvel, APP e Reserva Legal
-- Formatos: SHP, GPKG e GeoJSON
-- Indicadores: IAPP, ICV, IRL e demais indicadores
-- que dependem do perímetro rural.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'CAR',
    'https://www.car.gov.br/',
    'Cadastro Ambiental Rural. Fonte das geometrias dos '
    || 'imóveis rurais, das Areas de Preservacao Permanente '
    || '(APP) declaradas e das Reservas Legais declaradas. '
    || 'Abrangencia nacional. Formatos previstos: SHP, '
    || 'GeoPackage e GeoJSON.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.2. Instituto Brasileiro de Geografia e Estatistica (IBGE)
-- Camadas: biomas e malhas municipais
-- Formato: SHP
-- Indicadores: IRL e agregações municipais.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'IBGE',
    'https://www.ibge.gov.br/geociencias/',
    'Instituto Brasileiro de Geografia e Estatistica. '
    || 'Fonte oficial dos limites de biomas brasileiros '
    || 'e das malhas territoriais municipais. '
    || 'Abrangencia nacional. Formato previsto: SHP. '
    || 'Utilizada na contextualizacao espacial dos dados '
    || 'e na agregacao dos indicadores por municipio.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.3. Instituto Chico Mendes de Conservacao da Biodiversidade
-- Camada: unidades de conservacao federais e areas protegidas
-- Formato: SHP
-- Indicador: ISAP.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'ICMBio',
    'https://www.gov.br/icmbio/pt-br',
    'Instituto Chico Mendes de Conservacao da Biodiversidade. '
    || 'Fonte de dados geoespaciais sobre unidades de '
    || 'conservacao federais e areas protegidas. '
    || 'Abrangencia nacional. Formato previsto: SHP. '
    || 'Utilizada no calculo do indicador ISAP e como '
    || 'referencia para dados de embargos ambientais.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.4. Ministerio do Meio Ambiente e Mudanca do Clima (MMA)
-- Camada: areas protegidas e informacoes territoriais ambientais
-- Formato: SHP
-- Indicador: ISAP.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'MMA',
    'https://www.gov.br/mma/pt-br',
    'Ministerio do Meio Ambiente e Mudanca do Clima. '
    || 'Fonte de referencias geoespaciais ambientais e '
    || 'territoriais, incluindo areas protegidas. '
    || 'Abrangencia nacional. Formato previsto: SHP. '
    || 'Utilizada como fonte complementar no calculo '
    || 'do indicador ISAP.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.5. Fundacao Nacional dos Povos Indigenas (FUNAI)
-- Camada: terras indigenas
-- Formato: SHP
-- Indicador: ISAP.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'FUNAI',
    'https://www.gov.br/funai/pt-br',
    'Fundacao Nacional dos Povos Indigenas. '
    || 'Fonte de informacoes territoriais e geoespaciais '
    || 'relacionadas as terras indigenas. '
    || 'Abrangencia nacional. Formato previsto: SHP. '
    || 'Utilizada como referencia territorial no calculo '
    || 'do indicador ISAP.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.6. Instituto Nacional de Colonizacao e Reforma Agraria (INCRA)
-- Camada: territorios de assentamentos e outras areas de interesse
-- Formato: SHP
-- Indicador: ISAP.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'INCRA',
    'https://www.gov.br/incra/pt-br',
    'Instituto Nacional de Colonizacao e Reforma Agraria. '
    || 'Fonte de informacoes territoriais relacionadas '
    || 'a assentamentos e areas de interesse fundiario. '
    || 'Abrangencia nacional. Formato previsto: SHP. '
    || 'Utilizada como referencia territorial complementar '
    || 'no calculo do indicador ISAP.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.7. Servico Florestal Brasileiro (SFB)
-- Camada: dados territoriais e florestais
-- Formato: SHP
-- Indicador: ISAP, conforme a camada utilizada.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'SFB',
    'https://www.gov.br/florestal/pt-br',
    'Servico Florestal Brasileiro. Fonte complementar de '
    || 'informacoes geoespaciais florestais e territoriais. '
    || 'Abrangencia nacional. Formato previsto: SHP. '
    || 'Pode fornecer referencias para areas de interesse '
    || 'ambiental e territorial, conforme a camada utilizada.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.8. Instituto Brasileiro do Meio Ambiente e dos Recursos
-- Naturais Renovaveis (IBAMA)
-- Camada: embargos ambientais
-- Formatos: SHP e CSV
-- Indicador: IAE.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'IBAMA',
    'https://www.gov.br/ibama/pt-br',
    'Instituto Brasileiro do Meio Ambiente e dos Recursos '
    || 'Naturais Renovaveis. Fonte de dados sobre embargos '
    || 'ambientais. Abrangencia nacional. Formatos previstos: '
    || 'SHP e CSV. Utilizada no calculo do indicador IAE, '
    || 'mediante verificacao da intersecao espacial ou '
    || 'associacao dos registros aos imoveis rurais.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.9. Instituto Nacional de Pesquisas Espaciais (INPE)
-- Programa PRODES
-- Camada: desmatamento
-- Formatos: SHP e GeoJSON
-- Indicador: IDesmat.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'INPE - PRODES',
    'https://www.gov.br/inpe/pt-br',
    'Instituto Nacional de Pesquisas Espaciais - Programa '
    || 'de Monitoramento do Desmatamento na Amazonia Legal '
    || 'por Satelite (PRODES) e dados relacionados de '
    || 'monitoramento do desmatamento. Abrangencia conforme '
    || 'o bioma e o produto disponibilizado. Periodicidade '
    || 'conforme a publicacao oficial. Formatos previstos: '
    || 'SHP e GeoJSON. Utilizada no indicador IDesmat.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2.10. INPE - Programa Queimadas
-- Camada: focos de calor
-- Formatos: CSV com latitude/longitude e SHP de pontos
-- Indicador: IFC.

INSERT INTO fonte (
    fon_nome,
    fon_url,
    fon_descricao,
    usr_id
)
SELECT
    'INPE - Queimadas',
    'https://terrabrasilis.dpi.inpe.br/queimadas/portal/',
    'Instituto Nacional de Pesquisas Espaciais - Programa '
    || 'Queimadas. Fonte de dados sobre focos de calor, '
    || 'com atualizacao conforme a disponibilizacao dos '
    || 'produtos oficiais. Abrangencia nacional. Formatos '
    || 'previstos: CSV com latitude e longitude e SHP de '
    || 'pontos. Utilizada no calculo do indicador IFC.',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';



-- 3. CONJUNTOS

-- 1. Imóveis rurais - CAR
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Imóveis Rurais - CAR',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 2. Áreas de Preservação Permanente - CAR
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Áreas de Preservação Permanente - CAR',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 3. Reservas Legais - CAR
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Reservas Legais - CAR',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 4. Vegetação nativa
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Vegetação Nativa',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 5. Biomas brasileiros - IBGE
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Biomas Brasileiros - IBGE',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 6. Áreas protegidas e territórios especiais
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Áreas Protegidas e Territórios Especiais',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 7. Embargos ambientais
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Embargos Ambientais',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 8. Desmatamento - INPE/PRODES
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Desmatamento - INPE/PRODES',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 9. Focos de calor - INPE/Queimadas
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Focos de Calor - INPE/Queimadas',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 10. Malha municipal - IBGE
INSERT INTO conjunto (con_nome, usr_id)
SELECT
    'Malha Municipal - IBGE',
    usr_id
FROM usuario
WHERE usr_email = 'admin@georuraldatahub.com.br';


-- 4. DICIONARIOS

-- 4.1. INPE - QUEIMADAS
INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'INPE Queimadas - CSV Original',
    'Dicionario para validacao dos campos do CSV original '
    || 'de focos de calor do INPE.',
    1,
    '[
      {"name":"DataHora","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"Satelite","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"Pais","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"Estado","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"Municipio","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"Bioma","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"DiaSemChuva","required":false,"type":"INTEGER","domain":[],"unique":false},
      {"name":"Precipitacao","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"RiscoFogo","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"FRP","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"Latitude","required":true,"type":"LATITUDE","domain":[],"unique":false},
      {"name":"Longitude","required":true,"type":"LONGITUDE","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'INPE - Queimadas';


-- 4.2. IBGE - MALHA MUNICIPAL DO PARANA
INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'IBGE - Malha Municipal do Parana',
    'Dicionario para validacao dos atributos das malhas '
    || 'municipais do Parana em GeoJSON.',
    1,
    '[
      {"name":"GEOCODIGO","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"NOME","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"UF","required":true,"type":"STRING","domain":["PR"],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'IBGE';

-- 4.3. - INPE Queimadas - Dados Normalizados
INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'INPE Queimadas - Dados Normalizados',
    'Dicionario para validacao dos dados normalizados de focos de calor do INPE.',
    2,
    '[
      {"name":"id","required":true,"type":"STRING","domain":[],"unique":true},
      {"name":"lat","required":true,"type":"LATITUDE","domain":[],"unique":false},
      {"name":"lon","required":true,"type":"LONGITUDE","domain":[],"unique":false},
      {"name":"data_hora_gmt","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"satelite","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"municipio","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"estado","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"pais","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"municipio_id","required":false,"type":"INTEGER","domain":[],"unique":false},
      {"name":"estado_id","required":true,"type":"INTEGER","domain":[41],"unique":false},
      {"name":"pais_id","required":false,"type":"INTEGER","domain":[],"unique":false},
      {"name":"numero_dias_sem_chuva","required":false,"type":"INTEGER","domain":[],"unique":false},
      {"name":"precipitacao","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"risco_fogo","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"bioma","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"frp","required":false,"type":"NUMBER","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'INPE - Queimadas';


-- 4.4. - IBGE - Biomas do Parana
INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'IBGE - Biomas do Parana',
    'Dicionario para validacao dos limites dos biomas no Parana em GeoJSON.',
    2,
    '[
      {"name":"CD_BIOMA","required":true,"type":"STRING","domain":["1","2","3","4","5","6"],"unique":false},
      {"name":"NM_BIOMA","required":true,"type":"STRING","domain":["Amazônia","Caatinga","Cerrado","Mata Atlântica","Pampa","Pantanal"],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'IBGE';


-- 4.5. IBGE - MALHA MUNICIPAL PR - API 2022

INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'IBGE - Malha municipal PR - API 2022',
    'Malha simplificada da API IBGE, edicao 2022. '
    || 'Codigo municipal textual. Nao usar para a saida '
    || 'completa do ZIP de 2024.',
    3,
    '[
      {"name":"codarea","required":true,"type":"STRING","domain":[],"unique":true}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'IBGE';


-- 4.6. IBGE - MALHA MUNICIPAL PR - ZIP 2024

INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'IBGE - Malha municipal PR - 2024',
    'Atributos do DBF oficial PR_Municipios_2024 e alias '
    || 'codarea adicionado pelo conector. Recorte do Parana. '
    || 'Campos de concentracao urbana podem estar vazios.',
    4,
    '[
      {"name":"codarea","required":true,"type":"STRING","domain":[],"unique":true},
      {"name":"CD_MUN","required":true,"type":"STRING","domain":[],"unique":true},
      {"name":"NM_MUN","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_UF","required":true,"type":"STRING","domain":["41"],"unique":false},
      {"name":"SIGLA_UF","required":true,"type":"STRING","domain":["PR"],"unique":false},
      {"name":"NM_UF","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"AREA_KM2","required":true,"type":"NUMBER","domain":[],"unique":false},
      {"name":"CD_RGI","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_RGI","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_RGINT","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_RGINT","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_REGIA","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_REGIA","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"SIGLA_RG","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_CONCU","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_CONCU","required":false,"type":"STRING","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'IBGE';


-- 4.7. IBGE - BIOMAS PR - 2019

INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'IBGE - Biomas PR - 2019',
    'DBF lm_bioma_250, edicao 2019, recortado pelo '
    || 'conector ao Parana. CD_Bioma e numerico nesta edicao.',
    5,
    '[
      {"name":"Bioma","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_Bioma","required":true,"type":"INTEGER","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'IBGE';

-- 4.8. IBGE - BIOMAS PR - 2025

INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'IBGE - Biomas PR - 2025',
    'DBF lml_bioma_e250k_v20250911_A, edicao 2025, '
    || 'recortado ao Parana. CD_BIOMA e textual nesta edicao.',
    6,
    '[
      {"name":"CD_BIOMA","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_BIOMA","required":true,"type":"STRING","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'IBGE';


-- 4.9. INPE - PRODES PR - MATA ATLANTICA

INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'INPE - PRODES PR - Mata Atlantica',
    'Incrementos anuais WFS prodes-mata-atlantica-nb:'
    || 'yearly_deforestation. Campos verificados em '
    || '2026-10-24. Datas mantidas como texto para aceitar '
    || 'o formato publicado.',
    1,
    '[
      {"name":"uuid","required":true,"type":"STRING","domain":[],"unique":true},
      {"name":"year","required":true,"type":"INTEGER","domain":[],"unique":false},
      {"name":"state","required":true,"type":"STRING","domain":["PR","PARANA","PARANÁ"],"unique":false},
      {"name":"fid","required":false,"type":"INTEGER","domain":[],"unique":false},
      {"name":"def_cloud","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"julian_day","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"scene_id","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"area_km","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"path_row","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"main_class","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"class_name","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"image_date","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"source","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"satellite","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"sensor","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"publish_year","required":false,"type":"STRING","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'INPE - PRODES';


-- 4.10. INPE - PRODES PR - CERRADO

INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'INPE - PRODES PR - Cerrado',
    'Incrementos anuais WFS prodes-cerrado-nb:'
    || 'yearly_deforestation. Inclui update_at e pub_date '
    || 'da publicacao atualizada. Campos verificados em '
    || '2026-10-24.',
    2,
    '[
      {"name":"uuid","required":true,"type":"STRING","domain":[],"unique":true},
      {"name":"year","required":true,"type":"INTEGER","domain":[],"unique":false},
      {"name":"state","required":true,"type":"STRING","domain":["PR","PARANA","PARANÁ"],"unique":false},
      {"name":"fid","required":false,"type":"INTEGER","domain":[],"unique":false},
      {"name":"def_cloud","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"julian_day","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"scene_id","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"area_km","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"path_row","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"main_class","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"class_name","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"image_date","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"source","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"satellite","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"sensor","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"publish_year","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"update_at","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"pub_date","required":false,"type":"STRING","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'INPE - PRODES';


-- 4.11. INPE - FOCOS DIARIOS PR

INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'INPE - Focos diarios PR',
    'CSV diario Brasil, filtrado pelo conector para '
    || 'estado_id=41. Cabecalho verificado em '
    || 'focos_diario_br_20261021.csv. Datas e nomes '
    || 'permanecem textuais; indicadores ausentes sao opcionais.',
    3,
    '[
      {"name":"id","required":true,"type":"STRING","domain":[],"unique":true},
      {"name":"lat","required":true,"type":"LATITUDE","domain":[],"unique":false},
      {"name":"lon","required":true,"type":"LONGITUDE","domain":[],"unique":false},
      {"name":"data_hora_gmt","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"satelite","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"estado_id","required":true,"type":"INTEGER","domain":["41"],"unique":false},
      {"name":"municipio_id","required":true,"type":"INTEGER","domain":[],"unique":false},
      {"name":"pais_id","required":false,"type":"INTEGER","domain":[],"unique":false},
      {"name":"municipio","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"estado","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"pais","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"bioma","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"numero_dias_sem_chuva","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"precipitacao","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"risco_fogo","required":false,"type":"NUMBER","domain":[],"unique":false},
      {"name":"frp","required":false,"type":"NUMBER","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'INPE - Queimadas';


-- IBGE - MALHA MUNICIPAL PR - 2025
INSERT INTO dicionario (
    fon_id,
    dic_nome,
    dic_descricao,
    dic_versao,
    dic_estrutura,
    dic_criado_em
)
SELECT
    f.fon_id,
    'IBGE - Malha municipal PR - 2025',
    'Dicionario para validacao dos atributos da malha '
    || 'municipal do Parana, edicao 2025, em formato GeoJSON.',
    7,
    '[
      {"name":"CD_MUN","required":true,"type":"STRING","domain":[],"unique":true},
      {"name":"NM_MUN","required":true,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_RGI","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_RGI","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_RGINT","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_RGINT","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_UF","required":true,"type":"STRING","domain":["41"],"unique":false},
      {"name":"NM_UF","required":true,"type":"STRING","domain":["Paraná"],"unique":false},
      {"name":"SIGLA_UF","required":true,"type":"STRING","domain":["PR"],"unique":false},
      {"name":"CD_REGIAO","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_REGIAO","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"SIGLA_RG","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"CD_CONCURB","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"NM_CONCURB","required":false,"type":"STRING","domain":[],"unique":false},
      {"name":"AREA_KM2","required":true,"type":"NUMBER","domain":[],"unique":false},
      {"name":"codarea","required":true,"type":"STRING","domain":[],"unique":false}
    ]',
    CURRENT_TIMESTAMP
FROM fonte f
WHERE f.fon_nome = 'IBGE';



COMMIT;

SELECT
    c.con_id,
    c.con_nome,
    c.usr_id,
    u.usr_nome AS responsavel
FROM conjunto c
LEFT JOIN usuario u
    ON u.usr_id = c.usr_id
ORDER BY c.con_id;

SELECT
    u.usr_id,
    u.usr_nome,
    u.usr_email,
    u.usr_tipo,
    u.usr_status
FROM usuario u
ORDER BY u.usr_id;


SELECT
    f.fon_id,
    f.fon_nome,
    f.fon_url,
    f.fon_descricao,
    f.fon_data_criacao,
    u.usr_nome AS responsavel,
    u.usr_tipo AS tipo_responsavel
FROM fonte f
JOIN usuario u
    ON u.usr_id = f.usr_id
ORDER BY f.fon_id;