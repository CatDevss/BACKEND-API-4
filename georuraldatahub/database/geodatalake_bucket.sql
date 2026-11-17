-- GEORURAL DATAHUB | Acesso ao bucket: somente endereco e nome da credencial, sem segredos.
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

-- As origens tornam-se imutaveis junto com as feicoes da camada aprovada.
CREATE OR REPLACE TRIGGER trg_proteger_camada_origem
BEFORE INSERT OR UPDATE OR DELETE ON camada_origem FOR EACH ROW
DECLARE
    v_status VARCHAR2(30);
BEGIN
    SELECT v.ver_status INTO v_status FROM camada c
    JOIN versao v ON v.ver_id=c.ver_id
    WHERE c.cam_id=coalesce(:old.cam_id,:new.cam_id);
    IF v_status IN ('TRATADA','PUBLICADA') THEN
        raise_application_error(-20111,'Origens de camada aprovada sao imutaveis.');
    END IF;
    IF updating THEN
        raise_application_error(-20111,'Nao altere vinculos de origem; crie outra camada.');
    END IF;
END;
/

CREATE OR REPLACE PACKAGE pkg_carga_ambiental AUTHID definer AS
    -- Reune feicoes de camadas importadas do mesmo conjunto em uma nova camada.
    PROCEDURE combinar_camadas(p_con_id NUMBER, p_nome VARCHAR2, p_camadas CLOB,
                              p_cam_id OUT NUMBER, p_quantidade OUT NUMBER);

    PROCEDURE importar_geojson (
        p_con_id     NUMBER,
        p_ver_id     NUMBER,
        p_cam_id     OUT NUMBER,
        p_quantidade OUT NUMBER
    );

END;
/

CREATE OR REPLACE PACKAGE BODY pkg_carga_ambiental AS

    -- Cria uma copia composta atomica; nao altera nem dissolve as fontes.
    PROCEDURE combinar_camadas(p_con_id NUMBER, p_nome VARCHAR2, p_camadas CLOB,
                              p_cam_id OUT NUMBER, p_quantidade OUT NUMBER) IS
        ids json_array_t; id NUMBER; n NUMBER; arq NUMBER; ver NUMBER;
        srid NUMBER; base_srid NUMBER; numero NUMBER; conjunto NUMBER; status VARCHAR2(30);
        TYPE vistos_t IS TABLE OF BOOLEAN INDEX BY VARCHAR2(100);
        vistos vistos_t;
    BEGIN
        SAVEPOINT combinar_ambiental;
        IF trim(p_nome) IS NULL OR length(p_nome)>200 THEN
            raise_application_error(-20111,'Informe nome da camada com ate 200 caracteres.');
        END IF;
        ids := json_array_t.parse(p_camadas);
        IF ids.get_size()<2 OR ids.get_size()>100 THEN
            raise_application_error(-20111,'Informe de 2 a 100 camadas de origem.');
        END IF;
        -- Valida todas as origens antes de copiar dados.
        FOR j IN 0..ids.get_size()-1 LOOP
            id := ids.get_number(j);
            IF id IS NULL OR id<=0 OR id<>trunc(id) THEN
                raise_application_error(-20111,'ID de camada invalido.');
            END IF;
            IF vistos.exists(to_char(id)) THEN
                raise_application_error(-20111,'Camada de origem repetida.');
            END IF;
            vistos(to_char(id)):=TRUE;
            SELECT c.con_id,c.cam_srid,v.arq_id,v.ver_status
              INTO conjunto,srid,arq,status
              FROM camada c JOIN versao v ON v.ver_id=c.ver_id WHERE c.cam_id=id;
            IF conjunto<>p_con_id OR status NOT IN ('TRATADA','PUBLICADA') THEN
                raise_application_error(-20111,'Use camadas aprovadas pertencentes ao conjunto informado.');
            END IF;
            SELECT count(*) INTO n FROM camada_origem WHERE cam_id=id;
            IF n>0 THEN
                raise_application_error(-20111,'Selecione as camadas importadas originais, sem composicoes aninhadas.');
            END IF;
            SELECT count(*) INTO n FROM feicao WHERE cam_id=id;
            IF n=0 THEN raise_application_error(-20111,'Camada de origem vazia.'); END IF;
            IF j=0 THEN base_srid:=srid;
            ELSIF srid<>base_srid THEN
                raise_application_error(-20111,'As camadas devem usar o mesmo SRID.');
            END IF;
        END LOOP;
        -- Versao tecnica para manter a protecao existente durante a copia.
        -- A lista completa de arquivos e rastreada por camada_origem.
        SELECT arq_id INTO n FROM arquivo WHERE arq_id=arq FOR UPDATE;
        SELECT nvl(max(ver_numero),0)+1 INTO numero FROM versao WHERE arq_id=arq;
        INSERT INTO versao(arq_id,ver_numero,ver_localizacao,ver_status)
        VALUES(arq,numero,'composta/' || rawtohex(sys_guid()),'BRUTA')
        RETURNING ver_id INTO ver;
        INSERT INTO camada(con_id,ver_id,cam_nome,cam_tipo_geom,cam_srid)
        VALUES(p_con_id,ver,trim(p_nome),'MIXED',base_srid) RETURNING cam_id INTO p_cam_id;
        p_quantidade:=0;
        FOR j IN 0..ids.get_size()-1 LOOP
            id:=ids.get_number(j);
            INSERT INTO camada_origem(cam_id,cam_origem_id) VALUES(p_cam_id,id);
            INSERT INTO feicao(cam_id,fei_id_origem,fei_geometria,fei_atributos)
            SELECT p_cam_id,fei_id_origem,fei_geometria,fei_atributos FROM feicao WHERE cam_id=id;
            p_quantidade:=p_quantidade+SQL%ROWCOUNT;
        END LOOP;
        UPDATE versao SET ver_status='TRATADA' WHERE ver_id=ver;
    EXCEPTION WHEN OTHERS THEN
        ROLLBACK TO combinar_ambiental;
        RAISE;
    END;



    -- Converte CSV UTF-8 de focos em pontos GeoJSON; preserva as colunas originais.
    -- Aceita virgula ou ponto e virgula, campos entre aspas e aspas escapadas.
    FUNCTION csv_focos(p_doc CLOB) RETURN json_object_t IS
        TYPE textos IS TABLE OF VARCHAR2(32767) INDEX BY PLS_INTEGER;
        cab textos; campos textos;
        posicao PLS_INTEGER := 1; total PLS_INTEGER := dbms_lob.getlength(p_doc);
        coluna PLS_INTEGER := 1; linha PLS_INTEGER := 0;
        valor VARCHAR2(32767);
        -- SUBSTR de CLOB retorna um caractere, que pode ocupar varios bytes UTF-8.
        -- Evita ORA-06502 ao ler acentos em municipio, estado ou outros campos.
        ch VARCHAR2(4 BYTE);
        sep VARCHAR2(1 BYTE);
        aspas BOOLEAN := FALSE;
        lat NUMBER; lon NUMBER;
        props json_object_t; geom json_object_t; item json_object_t;
        coords json_array_t; itens json_array_t := json_array_t();
        raiz json_object_t := json_object_t();
        FUNCTION numero(p_val VARCHAR2) RETURN NUMBER IS
            obj json_object_t;
            txt VARCHAR2(32767) := replace(trim(p_val), ',', '.');
        BEGIN
            IF NOT regexp_like(txt, '^-?[0-9]+([.][0-9]+)?([eE][+-]?[0-9]+)?$')
               OR txt IS NULL THEN
                raise_application_error(-20110,'CSV: coordenada numerica invalida.');
            END IF;
            obj := json_object_t.parse('{"n":' || txt || '}');
            RETURN obj.get_number('n');
        END;
        PROCEDURE registro IS
            ilat PLS_INTEGER; ilon PLS_INTEGER; iid PLS_INTEGER; idata PLS_INTEGER;
            data_txt VARCHAR2(100); data_val DATE;
        BEGIN
            campos(coluna) := valor; valor := NULL;
            IF coluna = 1 AND campos(1) IS NULL THEN RETURN; END IF;
            linha := linha + 1;
            IF linha = 1 THEN
                FOR j IN 1..coluna LOOP cab(j) := lower(trim(campos(j))); END LOOP;
                RETURN;
            END IF;
            IF coluna <> cab.count THEN
                raise_application_error(-20110, 'CSV: quantidade de colunas divergente na linha ' || linha);
            END IF;
            props := json_object_t();
            FOR j IN 1..cab.count LOOP
                props.put(cab(j), campos(j));
                CASE cab(j)
                    WHEN 'lat' THEN ilat := j;
                    WHEN 'lon' THEN ilon := j;
                    WHEN 'id' THEN iid := j;
                    WHEN 'data_hora_gmt' THEN idata := j;
                    ELSE NULL;
                END CASE;
            END LOOP;
            IF ilat IS NULL OR ilon IS NULL OR iid IS NULL OR idata IS NULL THEN
                raise_application_error(-20110, 'CSV de focos exige id, lat, lon e data_hora_gmt.');
            END IF;
            lat := numero(campos(ilat)); lon := numero(campos(ilon));
            IF lat IS NULL OR lon IS NULL OR abs(lat)>90 OR abs(lon)>180
               OR trim(campos(iid)) IS NULL THEN
                raise_application_error(-20110, 'CSV: coordenadas ou id invalidos na linha ' || linha);
            END IF;
            -- GMT do INPE: YYYY-MM-DD HH24:MI:SS (ou separador T).
            data_txt := substr(trim(campos(idata)),1,10);
            data_val := to_date(data_txt, 'FXYYYY-MM-DD');
            props.put('id_evento', trim(campos(iid)));
            props.put('data_evento', to_char(data_val,'YYYY-MM-DD'));
            coords := json_array_t(); coords.append(lon); coords.append(lat);
            geom := json_object_t(); geom.put('type','Point'); geom.put('coordinates',coords);
            item := json_object_t(); item.put('type','Feature');
            item.put('geometry',geom); item.put('properties',props);
            itens.append(item);
            IF itens.get_size()>100000 THEN
                raise_application_error(-20107,'CSV excede 100000 pontos; particione a carga.');
            END IF;
        END;
    BEGIN
        -- Detecta o separador apenas fora de campos entre aspas.
        FOR j IN 1..least(total,32767) LOOP
            ch := dbms_lob.substr(p_doc,1,j);
            IF ch = '"' THEN aspas := NOT aspas;
            ELSIF NOT aspas AND ch IN (',',';') THEN sep := ch; EXIT;
            ELSIF ch IN (chr(10),chr(13)) THEN EXIT;
            END IF;
        END LOOP;
        IF sep IS NULL THEN raise_application_error(-20110,'CSV sem separador reconhecido.'); END IF;
        aspas := FALSE;
        IF dbms_lob.substr(p_doc,1,1)=unistr('\FEFF') THEN posicao := 2; END IF;
        WHILE posicao<=total LOOP
            ch := dbms_lob.substr(p_doc,1,posicao);
            IF ch = '"' THEN
                IF aspas AND dbms_lob.substr(p_doc,1,posicao+1) = '"' THEN
                    valor := valor || '"'; posicao := posicao+1;
                ELSE aspas := NOT aspas;
                END IF;
            ELSIF NOT aspas AND ch=sep THEN
                campos(coluna):=valor; coluna:=coluna+1; valor:=NULL;
            ELSIF NOT aspas AND ch IN (chr(10),chr(13)) THEN
                registro; campos.delete; coluna:=1;
                IF ch=chr(13) AND dbms_lob.substr(p_doc,1,posicao+1)=chr(10) THEN
                    posicao:=posicao+1;
                END IF;
            ELSE valor:=valor || ch;
            END IF;
            posicao:=posicao+1;
        END LOOP;
        IF aspas THEN raise_application_error(-20110,'CSV com aspas nao fechadas.'); END IF;
        IF valor IS NOT NULL OR coluna>1 THEN registro; END IF;
        raiz.put('type','FeatureCollection'); raiz.put('features',itens);
        RETURN raiz;
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE BETWEEN -20999 AND -20000 THEN RAISE; END IF;
        raise_application_error(-20110,
            'CSV: falha apos ' || linha || ' registro(s), coluna ' || coluna ||
            ', caractere ' || posicao || ': ' || substr(SQLERRM, 1, 500) ||
            '; origem: ' || substr(dbms_utility.format_error_backtrace, 1, 700));

    END;


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
        OR NOT regexp_like(v_key, '\.(geojson|json|csv)$', 'i') THEN
            raise_application_error(-20102, 'Selecione uma versao tratada GeoJSON ou CSV de focos.');
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
        IF regexp_like(v_key, '\.csv$', 'i') THEN
            v_root := csv_focos(v_doc);
        ELSE
            v_root := json_object_t.parse(v_doc);
        END IF;
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