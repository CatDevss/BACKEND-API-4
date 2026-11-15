-- Criação das tabelas
CREATE TABLE IF NOT EXISTS usuario (
    usr_id     NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    usr_nome   VARCHAR2(100) NOT NULL,
    usr_email  VARCHAR2(100) NOT NULL,
    usr_tipo   VARCHAR2(20),
    usr_status NUMBER(1, 0) DEFAULT 1 NOT NULL,
    CONSTRAINT unique_email UNIQUE ( usr_email ),
    CONSTRAINT chk_usuario_tipo
        CHECK ( usr_tipo IN ( 'ADMIN', 'OPERADOR', 'ANALISTA', 'GESTOR', 'AUDITOR' ) ),
    CONSTRAINT chk_usuario_status CHECK ( usr_status IN ( 0, 1 ) )
);

COMMENT ON COLUMN usuario.usr_tipo IS
    'ADMIN, OPERADOR, ANALISTA, GESTOR, AUDITOR';

CREATE TABLE IF NOT EXISTS fonte (
    fon_id           NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    fon_nome         VARCHAR2(100) NOT NULL,
    fon_url          VARCHAR2(200) NOT NULL,
    fon_descricao    VARCHAR2(500),
    fon_data_criacao TIMESTAMP DEFAULT current_timestamp NOT NULL,
    fon_status       NUMBER(1, 0) DEFAULT 1 NOT NULL,
    usr_id           NUMBER NOT NULL,
    CONSTRAINT fk_fonte_usuario FOREIGN KEY ( usr_id )
        REFERENCES usuario ( usr_id ),
    CONSTRAINT unique_fonte UNIQUE ( fon_nome ),
    CONSTRAINT chk_fon_status CHECK ( fon_status IN ( 0, 1 ) )
);

CREATE TABLE IF NOT EXISTS dicionario (
    dic_id        NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    fon_id        NUMBER NOT NULL,
    dic_nome      VARCHAR2(100) NOT NULL,
    dic_descricao VARCHAR2(500),
    dic_versao    NUMBER(2, 0) DEFAULT 1,
    dic_estrutura VARCHAR2(10000),
    dic_criado_em TIMESTAMP DEFAULT current_timestamp NOT NULL,
    CONSTRAINT fk_fonte_dicionario FOREIGN KEY ( fon_id )
        REFERENCES fonte ( fon_id ),
    CONSTRAINT dic_estrutura_json CHECK ( dic_estrutura IS JSON )
);

CREATE TABLE IF NOT EXISTS conjunto (
    con_id   NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    con_nome VARCHAR2(100) NOT NULL,
    usr_id   NUMBER,
    CONSTRAINT fk_conjunto_usuario FOREIGN KEY ( usr_id )
        REFERENCES usuario ( usr_id )
);

CREATE TABLE IF NOT EXISTS ingestao (
    ing_id       NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    dic_id       NUMBER NOT NULL,
    usr_id       NUMBER NOT NULL,
    ing_tipo     VARCHAR2(20) NOT NULL,
    ing_status   VARCHAR2(20) NOT NULL,
    ing_conector VARCHAR2(100),
    ing_inicio   TIMESTAMP DEFAULT current_timestamp,
    CONSTRAINT fk_ingestao_dicionario FOREIGN KEY ( dic_id )
        REFERENCES dicionario ( dic_id ),
    CONSTRAINT fk_ingestao_usuario FOREIGN KEY ( usr_id )
        REFERENCES usuario ( usr_id ),
    CONSTRAINT chk_ingestao_status
        CHECK ( ing_status IN ( 'CONCLUIDO', 'ERRO', 'CANCELADO' ) ),
    CONSTRAINT chk_ingestao_tipo
        CHECK ( ing_tipo IN ( 'UPLOAD', 'API', 'DOWNLOAD', 'SERVICO_GEOGRAFICO' ) )
);

COMMENT ON COLUMN ingestao.ing_status IS
    'CONCLUIDO, ERRO, CANCELADO';

COMMENT ON COLUMN ingestao.ing_tipo IS
    'UPLOAD, API, DOWNLOAD, SERVICO_GEOGRAFICO';

CREATE TABLE IF NOT EXISTS arquivo (
    arq_id               NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    ing_id               NUMBER NOT NULL,
    arq_nome             VARCHAR2(200) NOT NULL,
    arq_formato          VARCHAR2(10) NOT NULL,
    arq_tamanho          NUMBER(19, 0) NOT NULL,
    arq_hash             VARCHAR2(300) NOT NULL,
    arq_localizacao      VARCHAR2(500) NOT NULL,
    arq_data_recebimento TIMESTAMP DEFAULT current_timestamp NOT NULL,
    CONSTRAINT fk_arquivo_ingestao FOREIGN KEY ( ing_id )
        REFERENCES ingestao ( ing_id )
);

COMMENT ON COLUMN arquivo.arq_hash IS
    'Hash SHA-256';

COMMENT ON COLUMN arquivo.arq_localizacao IS
    'Localização do arquivo original imutável';

CREATE TABLE IF NOT EXISTS conjunto_arquivo (
    con_id NUMBER NOT NULL,
    arq_id NUMBER NOT NULL,
    CONSTRAINT pk_conjunto_arquivo PRIMARY KEY ( con_id,
                                                 arq_id ),
    CONSTRAINT fk_conjunto_arquivo_conjunto FOREIGN KEY ( con_id )
        REFERENCES conjunto ( con_id ),
    CONSTRAINT fk_conjunto_arquivo_arquivo FOREIGN KEY ( arq_id )
        REFERENCES arquivo ( arq_id )
);

CREATE TABLE IF NOT EXISTS versao (
    ver_id          NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    arq_id          NUMBER NOT NULL,
    ver_numero      NUMBER NOT NULL,
    ver_status      VARCHAR2(20) NOT NULL,
    ver_localizacao VARCHAR2(500) NOT NULL,
    ver_criada_em   TIMESTAMP DEFAULT current_timestamp NOT NULL,
    CONSTRAINT fk_versao_arquivo FOREIGN KEY ( arq_id )
        REFERENCES arquivo ( arq_id ),
    CONSTRAINT chk_versao_status
        CHECK ( ver_status IN ( 'BRUTA', 'QUARENTENA', 'TRATADA', 'PUBLICADA' ) ),
    CONSTRAINT uq_versao_arquivo_numero UNIQUE ( arq_id,
                                                 ver_numero )
);

COMMENT ON COLUMN versao.ver_status IS
    'BRUTA, QUARENTENA, TRATADA, PUBLICADA';

CREATE TABLE IF NOT EXISTS quarentena (
    qua_id             NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    ing_id             NUMBER NOT NULL,
    arq_id             NUMBER,
    qua_status         VARCHAR2(30) NOT NULL,
    qua_motivo         VARCHAR2(500) NOT NULL,
    qua_tratamento     VARCHAR2(500),
    qua_data_entrada   TIMESTAMP DEFAULT current_timestamp NOT NULL,
    qua_data_resolucao TIMESTAMP,
    usr_id             NUMBER,
    CONSTRAINT fk_quarentena_usuario FOREIGN KEY ( usr_id )
        REFERENCES usuario ( usr_id ),
    CONSTRAINT fk_quarentena_ingestao FOREIGN KEY ( ing_id )
        REFERENCES ingestao ( ing_id ),
    CONSTRAINT fk_quarentena_arquivo FOREIGN KEY ( arq_id )
        REFERENCES arquivo ( arq_id )
);

CREATE TABLE IF NOT EXISTS auditoria (
    aud_id       NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    usr_id       NUMBER,
    arq_id       NUMBER,
    ing_id       NUMBER,
    aud_entidade VARCHAR2(100),
    aud_acao     VARCHAR2(10) NOT NULL,
    aud_detalhes VARCHAR2(4000),
    aud_data     TIMESTAMP DEFAULT current_timestamp NOT NULL,
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY ( usr_id )
        REFERENCES usuario ( usr_id ),
    CONSTRAINT fk_auditoria_arquivo FOREIGN KEY ( arq_id )
        REFERENCES arquivo ( arq_id ),
    CONSTRAINT fk_auditoria_ingestao FOREIGN KEY ( ing_id )
        REFERENCES ingestao ( ing_id ),
    CONSTRAINT chk_auditoria_acao
        CHECK ( aud_acao IN ( 'INSERT', 'UPDATE', 'DELETE' ) )
);

CREATE TABLE IF NOT EXISTS camada (
    cam_id           NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    con_id           NUMBER NOT NULL,
    ver_id           NUMBER NOT NULL,
    cam_nome         VARCHAR2(200) NOT NULL,
    cam_descricao    VARCHAR2(500),
    cam_tipo_geom    VARCHAR2(30) NOT NULL,
    cam_srid         NUMBER NOT NULL,
    cam_data_criacao TIMESTAMP DEFAULT current_timestamp NOT NULL,
    CONSTRAINT fk_camada_conjunto FOREIGN KEY ( con_id )
        REFERENCES conjunto ( con_id ),
    CONSTRAINT fk_camada_versao FOREIGN KEY ( ver_id )
        REFERENCES versao ( ver_id ),
    CONSTRAINT chk_camada_tipo_geom
        CHECK ( cam_tipo_geom IN ( 'POINT', 'MULTIPOINT', 'LINESTRING', 'MULTILINESTRING', 'POLYGON',
                                   'MULTIPOLYGON', 'MIXED' ) )
);

CREATE TABLE IF NOT EXISTS camada_origem (
    cam_id NUMBER NOT NULL REFERENCES camada(cam_id),
    cam_origem_id NUMBER NOT NULL REFERENCES camada(cam_id),
    PRIMARY KEY (cam_id, cam_origem_id),
    CHECK (cam_id <> cam_origem_id)
);

CREATE TABLE IF NOT EXISTS feicao (
    fei_id        NUMBER
        GENERATED AS IDENTITY
    PRIMARY KEY,
    cam_id        NUMBER NOT NULL,
    fei_id_origem VARCHAR2(100),
    fei_geometria mdsys.sdo_geometry NOT NULL,
    fei_atributos CLOB,
    CONSTRAINT fk_feicao_camada FOREIGN KEY ( cam_id )
        REFERENCES camada ( cam_id ),
    CONSTRAINT chk_fei_atributos CHECK ( fei_atributos IS JSON )
);

CREATE TABLE IF NOT EXISTS calculo_ambiental (
    calc_id NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    fei_imovel_id NUMBER NOT NULL REFERENCES feicao(fei_id),
    calc_data TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    calc_regra VARCHAR2(30) NOT NULL,
    calc_parametros CLOB NOT NULL CHECK (calc_parametros IS JSON),
    calc_resultado CLOB NOT NULL CHECK (calc_resultado IS JSON)
);


-- Triggers de validação e segurança
CREATE OR REPLACE TRIGGER trg_impedir_exclusao_usuario BEFORE
    DELETE ON usuario
BEGIN
    raise_application_error(-20001, 'Não é permitido excluir usuários. Inative o usuário.');
END;
/

CREATE OR REPLACE TRIGGER trg_impedir_exclusao_fonte BEFORE
    DELETE ON fonte
BEGIN
    raise_application_error(-20001, 'Não é permitido excluir fontes. Inative a fonte.');
END;
/

CREATE OR REPLACE TRIGGER trg_valida_tipo_fonte_usr BEFORE
    INSERT OR UPDATE ON fonte
    FOR EACH ROW
DECLARE
    v_usr_tipo usuario.usr_tipo%TYPE;
BEGIN
    SELECT
        usr_tipo
    INTO v_usr_tipo
    FROM
        usuario
    WHERE
            usr_id = :new.usr_id
        AND usr_status = 1;

    IF v_usr_tipo IS NULL
       OR v_usr_tipo NOT IN ( 'ADMIN', 'GESTOR' ) THEN
        raise_application_error(-20001, 'Usuário não autorizado para cadastrar fonte.');
    END IF;

EXCEPTION
    WHEN no_data_found THEN
        raise_application_error(-20002, 'O usuário informado para a fonte não existe.');
END;
/

CREATE OR REPLACE TRIGGER trg_calculo_ambiental_imutavel BEFORE
    UPDATE OR DELETE ON calculo_ambiental
BEGIN
    raise_application_error(-20120, 'Calculo imutavel; execute um novo calculo.');
END;
/

-- Proteção das camadas de versões aprovadas
CREATE OR REPLACE TRIGGER trg_proteger_camada BEFORE
    UPDATE OR DELETE ON camada
    FOR EACH ROW
DECLARE
    v_status versao.ver_status%TYPE;
BEGIN
    SELECT
        ver_status
    INTO v_status
    FROM
        versao
    WHERE
        ver_id = :old.ver_id;

    IF v_status IN ( 'TRATADA', 'PUBLICADA' ) THEN
        raise_application_error(-20002, 'Camada protegida: pertence a versao aprovada ('
                                        || v_status
                                        || ').');
    END IF;

END;
/

CREATE OR REPLACE TRIGGER trg_camada_metadados BEFORE
    UPDATE ON camada
    FOR EACH ROW
BEGIN
    IF :new.con_id <> :old.con_id
    OR :new.ver_id <> :old.ver_id
    OR :new.cam_srid <> :old.cam_srid
    OR :new.cam_tipo_geom <> :old.cam_tipo_geom
    OR :new.cam_data_criacao <> :old.cam_data_criacao THEN
        raise_application_error(-20003, 'Somente nome e descricao da camada podem ser alterados.');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_proteger_feicao BEFORE
    INSERT OR UPDATE OR DELETE ON feicao
    FOR EACH ROW
DECLARE
    v_status versao.ver_status%TYPE;
BEGIN
    SELECT
        v.ver_status
    INTO v_status
    FROM
             camada c
        JOIN versao v ON v.ver_id = c.ver_id
    WHERE
        c.cam_id = coalesce(:new.cam_id,
                            :old.cam_id);

    IF v_status IN ( 'TRATADA', 'PUBLICADA' ) THEN
        raise_application_error(-20004, 'Feicao protegida: a camada pertence a versao aprovada ('
                                        || v_status
                                        || ').');
    END IF;

END;
/

-- Triggers de auditoria

CREATE OR REPLACE TRIGGER trg_bloqueia_alteracao_auditoria BEFORE
    UPDATE OR DELETE ON auditoria
BEGIN
    raise_application_error(-20090, 'Registros de auditoria são imutáveis.');
END;
/

-- Usuário
CREATE OR REPLACE TRIGGER trg_auditoria_usuario AFTER
    INSERT OR UPDATE OR DELETE ON usuario
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
BEGIN
    IF inserting THEN
        v_acao := 'INSERT';
        v_usr_id := :new.usr_id;
        v_detalhes := 'Novo usuario inserido: '
                      || :new.usr_nome
                      || ' ('
                      || :new.usr_email
                      || ')';

    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_usr_id := :new.usr_id;
        v_detalhes := 'Usuario atualizado ID: '
                      || :old.usr_id
                      || ' Status anterior: '
                      || :old.usr_status
                      || ' Novo Status: '
                      || :new.usr_status;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_usr_id := NULL;
        v_detalhes := 'Usuario removido: '
                      || :old.usr_nome
                      || ' (Email: '
                      || :old.usr_email
                      || ')';

    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               NULL,
               NULL,
               'USUARIO',
               v_acao,
               v_detalhes );

END;
/

-- Fonte
CREATE OR REPLACE TRIGGER trg_aud_fonte AFTER
    INSERT OR UPDATE OR DELETE ON fonte
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
BEGIN
    v_usr_id := coalesce(:new.usr_id,
                         :old.usr_id);
    IF inserting THEN
        v_acao := 'INSERT';
        v_detalhes := 'Nova fonte de dados criada: '
                      || :new.fon_nome
                      || ' | URL: '
                      || :new.fon_url;

    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_detalhes := 'Fonte atualizada ID: '
                      || :old.fon_id
                      || ' | Nome: '
                      || :new.fon_nome;

        IF :old.fon_status <> :new.fon_status THEN
            v_detalhes := v_detalhes
                          || ' | Status: '
                          ||
                CASE :old.fon_status
                    WHEN 1 THEN
                        'ATIVA'
                    ELSE 'INATIVA'
                END
                          || ' -> '
                          || CASE :new.fon_status
                WHEN 1 THEN
                    'ATIVA'
                ELSE 'INATIVA'
            END;
        END IF;

        IF nvl(:old.fon_descricao,
               '#') <> nvl(:new.fon_descricao,
                           '#') THEN
            v_detalhes := v_detalhes || ' | Descricao alterada';
        END IF;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_detalhes := 'Fonte removida: ' || :old.fon_nome;
    END IF;

    INSERT INTO auditoria (
        aud_acao,
        aud_entidade,
        aud_detalhes,
        usr_id
    ) VALUES ( v_acao,
               'FONTE',
               v_detalhes,
               v_usr_id );

END;
/

-- Dicionário
CREATE OR REPLACE TRIGGER trg_aud_dicionario AFTER
    INSERT OR UPDATE OR DELETE ON dicionario
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
BEGIN
    BEGIN
        SELECT
            usr_id
        INTO v_usr_id
        FROM
            fonte
        WHERE
            fon_id = coalesce(:new.fon_id,
                              :old.fon_id);

    EXCEPTION
        WHEN no_data_found THEN
            v_usr_id := NULL;
    END;

    IF inserting THEN
        v_acao := 'INSERT';
        v_detalhes := 'Novo dicionario cadastrado: '
                      || :new.dic_nome
                      || ' (Versao: '
                      || :new.dic_versao
                      || ')';

    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_detalhes := 'Dicionario atualizado ID: '
                      || :old.dic_id
                      || ' | Versao: '
                      || :old.dic_versao
                      || ' -> '
                      || :new.dic_versao;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_detalhes := 'Dicionario removido: ' || :old.dic_nome;
    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               NULL,
               NULL,
               'DICIONARIO',
               v_acao,
               v_detalhes );

END;
/

-- Ingestão
CREATE OR REPLACE TRIGGER trg_aud_ingestao AFTER
    INSERT OR UPDATE OR DELETE ON ingestao
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
    v_ing_id   NUMBER;
BEGIN
    v_usr_id := coalesce(:new.usr_id,
                         :old.usr_id);
    IF inserting THEN
        v_acao := 'INSERT';
        v_ing_id := :new.ing_id;
        v_detalhes := 'Nova ingestao iniciada (ID: '
                      || :new.ing_id
                      || ') | Status: '
                      || :new.ing_status;

    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_ing_id := :new.ing_id;
        v_detalhes := 'Ingestao atualizada ID: '
                      || :old.ing_id
                      || ' | Status: '
                      || :old.ing_status
                      || ' -> '
                      || :new.ing_status;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_ing_id := NULL;
        v_detalhes := 'Registro de ingestao removido (ID: '
                      || :old.ing_id
                      || ')';
    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               NULL,
               v_ing_id,
               'INGESTAO',
               v_acao,
               v_detalhes );

END;
/

-- Arquivo
CREATE OR REPLACE TRIGGER trg_aud_arquivo AFTER
    INSERT OR UPDATE OR DELETE ON arquivo
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
    v_arq_id   NUMBER;
    v_ing_id   NUMBER;
BEGIN
    BEGIN
        SELECT
            usr_id
        INTO v_usr_id
        FROM
            ingestao
        WHERE
            ing_id = coalesce(:new.ing_id,
                              :old.ing_id);

    EXCEPTION
        WHEN no_data_found THEN
            v_usr_id := NULL;
    END;

    IF inserting THEN
        v_acao := 'INSERT';
        v_arq_id := :new.arq_id;
        v_ing_id := :new.ing_id;
        v_detalhes := 'Arquivo registrado: '
                      || :new.arq_nome
                      || ' | Formato: '
                      || :new.arq_formato;

    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_arq_id := :new.arq_id;
        v_ing_id := :new.ing_id;
        v_detalhes := 'Metadados de arquivo atualizados ID: ' || :old.arq_id;
    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_arq_id := NULL;
        v_ing_id := :old.ing_id;
        v_detalhes := 'Arquivo removido: ' || :old.arq_nome;
    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               v_arq_id,
               v_ing_id,
               'ARQUIVO',
               v_acao,
               v_detalhes );

END;
/

-- Quarentena
CREATE OR REPLACE TRIGGER trg_aud_quarentena AFTER
    INSERT OR UPDATE OR DELETE ON quarentena
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
    v_ing_id   NUMBER;
BEGIN
    BEGIN
        SELECT
            usr_id
        INTO v_usr_id
        FROM
            ingestao
        WHERE
            ing_id = coalesce(:new.ing_id,
                              :old.ing_id);

    EXCEPTION
        WHEN no_data_found THEN
            v_usr_id := NULL;
    END;

    IF inserting THEN
        v_acao := 'INSERT';
        v_ing_id := :new.ing_id;
        v_detalhes := 'Registro enviado para quarentena | Motivo: ' || :new.qua_motivo;
    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_ing_id := :new.ing_id;
        v_detalhes := 'Quarentena atualizada ID: '
                      || :old.qua_id
                      || ' | Status: '
                      || :old.qua_status
                      || ' -> '
                      || :new.qua_status;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_ing_id := :old.ing_id;
        v_detalhes := 'Registro de quarentena removido ID: ' || :old.qua_id;
    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               NULL,
               v_ing_id,
               'QUARENTENA',
               v_acao,
               v_detalhes );

END;
/

-- Conjunto
CREATE OR REPLACE TRIGGER trg_aud_conjunto AFTER
    INSERT OR UPDATE OR DELETE ON conjunto
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
BEGIN
    v_usr_id := coalesce(:new.usr_id,
                         :old.usr_id);
    IF inserting THEN
        v_acao := 'INSERT';
        v_detalhes := 'Novo conjunto criado: ' || :new.con_nome;
    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_detalhes := 'Conjunto atualizado ID: '
                      || :old.con_id
                      || ' | Nome: '
                      || :new.con_nome;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_detalhes := 'Conjunto removido: ' || :old.con_nome;
    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               NULL,
               NULL,
               'CONJUNTO',
               v_acao,
               v_detalhes );

END;
/

-- Versão
CREATE OR REPLACE TRIGGER trg_aud_versao AFTER
    INSERT OR UPDATE OR DELETE ON versao
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
    v_arq_id   NUMBER;
BEGIN
    v_arq_id := coalesce(:new.arq_id,
                         :old.arq_id);
    BEGIN
        SELECT
            i.usr_id
        INTO v_usr_id
        FROM
                 arquivo a
            JOIN ingestao i ON i.ing_id = a.ing_id
        WHERE
            a.arq_id = v_arq_id;

    EXCEPTION
        WHEN no_data_found THEN
            v_usr_id := NULL;
    END;

    IF inserting THEN
        v_acao := 'INSERT';
        v_detalhes := 'Nova versao gerada: '
                      || :new.ver_numero
                      || ' | Status: '
                      || :new.ver_status;

    ELSIF updating THEN
        v_acao := 'UPDATE';
        v_detalhes := 'Versao atualizada ID: '
                      || :old.ver_id
                      || ' | Status: '
                      || :old.ver_status
                      || ' -> '
                      || :new.ver_status;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_detalhes := 'Versao removida ID: ' || :old.ver_id;
    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               v_arq_id,
               NULL,
               'VERSAO',
               v_acao,
               v_detalhes );

END;
/

-- Associação entre conjunto e arquivo
CREATE OR REPLACE TRIGGER trg_aud_conjunto_arquivo AFTER
    INSERT OR DELETE ON conjunto_arquivo
    FOR EACH ROW
DECLARE
    v_acao     VARCHAR2(10);
    v_detalhes VARCHAR2(4000);
    v_usr_id   NUMBER;
    v_con_id   NUMBER;
    v_arq_id   NUMBER;
BEGIN
    v_con_id := coalesce(:new.con_id,
                         :old.con_id);
    v_arq_id := coalesce(:new.arq_id,
                         :old.arq_id);
    BEGIN
        SELECT
            usr_id
        INTO v_usr_id
        FROM
            conjunto
        WHERE
            con_id = v_con_id;

    EXCEPTION
        WHEN no_data_found THEN
            v_usr_id := NULL;
    END;

    IF inserting THEN
        v_acao := 'INSERT';
        v_detalhes := 'Arquivo ID '
                      || :new.arq_id
                      || ' associado ao Conjunto ID '
                      || :new.con_id;

    ELSIF deleting THEN
        v_acao := 'DELETE';
        v_detalhes := 'Arquivo ID '
                      || :old.arq_id
                      || ' desassociado do Conjunto ID '
                      || :old.con_id;

    END IF;

    INSERT INTO auditoria (
        usr_id,
        arq_id,
        ing_id,
        aud_entidade,
        aud_acao,
        aud_detalhes
    ) VALUES ( v_usr_id,
               v_arq_id,
               NULL,
               'CONJUNTO_ARQUIVO',
               v_acao,
               v_detalhes );

END;
/