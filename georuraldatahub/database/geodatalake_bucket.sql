-- -- GEORURAL DATAHUB | Acesso ao bucket: somente endereco e nome da credencial, sem segredos.
CREATE TABLE IF NOT EXISTS ambiental_bucket_config (
    config_id       NUMBER PRIMARY KEY CHECK ( config_id = 1 ),
    bucket_uri      VARCHAR2(2000) NOT NULL,
    credential_name VARCHAR2(128) NOT NULL
);


-- Mantem configuracao existente. Cadastra o bucket do projeto apenas se ausente.
MERGE INTO ambiental_bucket_config dest
USING (
    SELECT
        1                                                                                          AS config_id,
        'https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/gruinvyzrcf8/b/georural-datalake/o' AS bucket_uri,
        'GEORURAL_BUCKET_CRED'                                                                     AS credential_name
    FROM
        dual
) src ON ( dest.config_id = src.config_id )
WHEN NOT MATCHED THEN
INSERT (
    config_id,
    bucket_uri,
    credential_name )
VALUES
    ( src.config_id,
      src.bucket_uri,
      src.credential_name );

COMMIT;

CREATE OR REPLACE PACKAGE pkg_carga_ambiental AUTHID definer AS
    PROCEDURE importar_geojson (
        p_con_id     NUMBER,
        p_ver_id     NUMBER,
        p_cam_id     OUT NUMBER,
        p_quantidade OUT NUMBER
    );

END;
/

CREATE OR REPLACE PACKAGE BODY pkg_carga_ambiental AS

    PROCEDURE importar_geojson (
        p_con_id     NUMBER,
        p_ver_id     NUMBER,
        p_cam_id     OUT NUMBER,
        p_quantidade OUT NUMBER
    ) IS

        v_uri      VARCHAR2(4000);
        v_cred     VARCHAR2(128);
        v_key      VARCHAR2(4000);
        v_arq      NUMBER;
        v_lock     NUMBER;
        v_num      NUMBER;
        v_ver      NUMBER;
        v_count    NUMBER;
        v_status   VARCHAR2(30);
        v_nome     VARCHAR2(200);
        v_blob     BLOB;
        v_doc      CLOB;
        v_root     json_object_t;
        v_items    json_array_t;
        v_item     json_object_t;
        v_geom     mdsys.sdo_geometry;
        v_validation VARCHAR2(4000);
        v_geometry CLOB;
        v_props    CLOB;
        v_origin   VARCHAR2(100);
        v_dest     INTEGER := 1;
        v_src      INTEGER := 1;
        v_ctx      INTEGER := 0;
        v_warn     INTEGER;

        PROCEDURE liberar IS
        BEGIN
            IF dbms_lob.istemporary(v_blob) = 1 THEN
                dbms_lob.freetemporary(v_blob);
            END IF;

            IF dbms_lob.istemporary(v_doc) = 1 THEN
                dbms_lob.freetemporary(v_doc);
            END IF;

        END;

    BEGIN
        SAVEPOINT carga_ambiental;
        SELECT
            arq_id
        INTO v_arq
        FROM
            versao
        WHERE
            ver_id = p_ver_id;

        SELECT
            arq_id
        INTO v_lock
        FROM
            arquivo
        WHERE
            arq_id = v_arq
        FOR UPDATE;

        SELECT
            con_id
        INTO v_lock
        FROM
            conjunto
        WHERE
            con_id = p_con_id
        FOR UPDATE;

        SELECT
            COUNT(*)
        INTO v_count
        FROM
            conjunto_arquivo
        WHERE
                con_id = p_con_id
            AND arq_id = v_arq;

        IF v_count = 0 THEN
            raise_application_error(-20101, 'Arquivo nao associado ao conjunto.');
        END IF;
        SELECT
            ver_status,
            ver_localizacao
        INTO
            v_status,
            v_key
        FROM
            versao
        WHERE
            ver_id = p_ver_id;

        IF v_status NOT IN ( 'TRATADA', 'PUBLICADA' )
           OR v_key NOT LIKE 'tratada/%'
        OR NOT regexp_like(v_key, '\.(geojson|json)$', 'i') THEN
            raise_application_error(-20102, 'Selecione uma versao GeoJSON tratada.');
        END IF;

        SELECT
            COUNT(*)
        INTO v_count
        FROM
            camada
        WHERE
                con_id = p_con_id
            AND ver_id IN (
                SELECT
                    ver_id
                FROM
                    versao
                WHERE
                        arq_id = v_arq
                    AND ver_localizacao = v_key
            );

        IF v_count > 0 THEN
            raise_application_error(-20103, 'Esta revisao ja possui camada neste conjunto.');
        END IF;
        SELECT
            bucket_uri,
            credential_name
        INTO
            v_uri,
            v_cred
        FROM
            ambiental_bucket_config
        WHERE
            config_id = 1;

        v_blob := dbms_cloud.get_object(
            credential_name => v_cred,
            object_uri      => rtrim(v_uri, '/')
                          || '/'
                          || utl_url.escape(v_key, TRUE, 'UTF-8')
        );

        IF dbms_lob.getlength(v_blob) > 134217728 THEN
            raise_application_error(-20104, 'GeoJSON excede 128 MiB; particione a carga.');
        END IF;

        dbms_lob.createtemporary(v_doc, TRUE);
        dbms_lob.converttoclob(v_doc,
                               v_blob,
                               dbms_lob.lobmaxsize,
                               v_dest,
                               v_src,
                               nls_charset_id('AL32UTF8'),
                               v_ctx,
                               v_warn);

        IF v_warn <> 0 THEN
            raise_application_error(-20105, 'GeoJSON deve ser UTF-8.');
        END IF;
        v_root := json_object_t.parse(v_doc);
        IF nvl(
            v_root.get_string('type'),
            '?'
        ) <> 'FeatureCollection'
        OR v_root.has('crs') THEN
            raise_application_error(-20106, 'Exige FeatureCollection RFC 7946, coordenadas WGS84, sem crs.');
        END IF;

        v_items := v_root.get_array('features');
        IF v_items IS NULL
           OR v_items.get_size() = 0
        OR v_items.get_size() > 100000 THEN
            raise_application_error(-20107, 'Carga deve ter entre 1 e 100000 feicoes.');
        END IF;

        SELECT
            nvl(
                max(ver_numero),
                0
            ) + 1
        INTO v_num
        FROM
            versao
        WHERE
            arq_id = v_arq;

        INSERT INTO versao (
            arq_id,
            ver_numero,
            ver_localizacao,
            ver_status
        ) VALUES ( v_arq,
                   v_num,
                   v_key,
                   'BRUTA' ) RETURNING ver_id INTO v_ver;

        SELECT
            arq_nome
        INTO v_nome
        FROM
            arquivo
        WHERE
            arq_id = v_arq;

        INSERT INTO camada (
            con_id,
            ver_id,
            cam_nome,
            cam_tipo_geom,
            cam_srid
        ) VALUES ( p_con_id,
                   v_ver,
                   substr(v_nome, 1, 100),
                   'MIXED',
                   4326 ) RETURNING cam_id INTO p_cam_id;

        p_quantidade := 0;
        FOR i IN 0..v_items.get_size() - 1 LOOP
            v_item := treat(v_items.get(i) AS json_object_t);
            IF v_item IS NULL
               OR nvl(
                v_item.get_string('type'),
                '?'
            ) <> 'Feature' THEN
                raise_application_error(-20108, 'Elemento nao e uma Feature.');
            END IF;

            v_geometry := v_item.get_object('geometry').to_clob();
            -- Conversao SQL/JSON; manter a geometria completa como CLOB.
            SELECT JSON_VALUE(
                v_geometry, '$' RETURNING SDO_GEOMETRY ERROR ON ERROR
            )
            INTO v_geom
            FROM dual;
            IF v_geom IS NULL THEN
                raise_application_error(-20109, 'Geometria nula: indice ' || i);
            END IF;
            IF v_geom.sdo_gtype IS NULL
               OR v_geom.sdo_gtype NOT IN ( 2001, 2003, 2005, 2007 ) THEN
                raise_application_error(-20109, 'Tipo de geometria nao aceito: indice '
                    || i || '; GTYPE=' || nvl(to_char(v_geom.sdo_gtype), 'NULL'));
            END IF;
            v_validation := sdo_geom.validate_geometry_with_context(v_geom, 0.005);
            -- Tratar somente orientacao e vertices redundantes, com revalidacao.
            -- Duas tentativas permitem que a primeira correcao revele a outra.
            FOR tentativa IN 1..2 LOOP
                IF regexp_like(v_validation, '^13356([^0-9]|$)') THEN
                    v_geom := sdo_util.remove_duplicate_vertices(v_geom, 0.005);
                ELSIF regexp_like(v_validation, '^13367([^0-9]|$)') THEN
                    v_geom := sdo_util.rectify_geometry(v_geom, 0.005);
                ELSE
                    EXIT;
                END IF;
                v_validation := sdo_geom.validate_geometry_with_context(v_geom, 0.005);
            END LOOP;
            IF nvl(v_validation, 'INVALID') <> 'TRUE' THEN
                raise_application_error(-20109, 'Geometria invalida: indice ' || i
                    || '; GTYPE=' || v_geom.sdo_gtype
                    || '; SRID=' || nvl(to_char(v_geom.sdo_srid), 'NULL')
                    || '; tolerancia=0.005; validacao='
                    || substr(nvl(v_validation, 'NULL'), 1, 500));
            END IF;

            v_props := '{}';
            IF
                v_item.has('properties')
                AND NOT v_item.get('properties').is_null()
            THEN
                v_props := v_item.get_object('properties').to_clob();
            END IF;

            v_origin := NULL;
            IF v_item.has('id') THEN
                v_origin := substr(
                    v_item.get_string('id'),
                    1,
                    100
                );
            END IF;

            INSERT INTO feicao (
                cam_id,
                fei_id_origem,
                fei_geometria,
                fei_atributos
            ) VALUES ( p_cam_id,
                       v_origin,
                       v_geom,
                       v_props );

            p_quantidade := p_quantidade + 1;
        END LOOP;
        -- Somente aprovar depois da ultima feicao: respeita trg_proteger_feicao.
        UPDATE versao
        SET
            ver_status = 'TRATADA'
        WHERE
            ver_id = v_ver;

        liberar;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK TO carga_ambiental;
            liberar;
            RAISE;
    END;

END;
/