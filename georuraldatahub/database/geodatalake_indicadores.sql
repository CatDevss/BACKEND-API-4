-- GEORURAL DATAHUB | INDICADORES AMBIENTAIS
DECLARE
    v_n NUMBER;
BEGIN
    SAVEPOINT cadastro_albers;
    SELECT
        COUNT(*)
    INTO v_n
    FROM
        mdsys.sdo_coord_ref_sys
    WHERE
        srid = 5009822;

    IF v_n > 0 THEN
        SELECT
            COUNT(*)
        INTO v_n
        FROM
            mdsys.sdo_coord_ref_sys
        WHERE
                srid = 5009822
            AND coord_ref_sys_name = 'GeoRural / SIRGAS2000 Albers Brasil'
            AND coord_ref_sys_kind = 'PROJECTED'
            AND coord_sys_id = 4400
            AND source_geog_srid = 4674
            AND projection_conv_id = 5009823
            AND geog_crs_datum_id = 6674
            AND is_legacy = 'FALSE';

        IF v_n <> 1 THEN
            raise_application_error(-20140, 'SRID 5009822 ocupado por outra definicao. Nada alterado.');
        END IF;
        dbms_output.put_line('Projecao Albers Brasil ja cadastrada; metadados serao conferidos a seguir.');
        RETURN;
    END IF;

    SELECT
        COUNT(*)
    INTO v_n
    FROM
        mdsys.sdo_coord_ops
    WHERE
        coord_op_id = 5009823;

    IF v_n <> 0 THEN
        raise_application_error(-20140, 'Operacao 5009823 ja existe sem o CRS esperado. Nada alterado.');
    END IF;
    SELECT
        COUNT(*)
    INTO v_n
    FROM
        mdsys.sdo_coord_ref_sys
    WHERE
            srid = 4674
        AND datum_id = 6674;

    IF v_n <> 1 THEN
        raise_application_error(-20141, 'Base SIRGAS2000 4674/6674 indisponivel.');
    END IF;
    SELECT
        COUNT(*)
    INTO v_n
    FROM
        mdsys.sdo_coord_op_param_use
    WHERE
            coord_op_method_id = 9822
        AND parameter_id IN ( 8821, 8822, 8823, 8824, 8826,
                              8827 );

    IF v_n <> 6 THEN
        raise_application_error(-20141, 'Metodo Albers sem os seis parametros esperados.');
    END IF;
    INSERT INTO mdsys.sdo_coord_ops (
        coord_op_id,
        coord_op_name,
        coord_op_type,
        coord_op_method_id,
        data_source,
        show_operation,
        is_legacy,
        reverse_op,
        is_implemented_forward,
        is_implemented_reverse
    ) VALUES ( 5009823,
               'GeoRural / Albers Brasil',
               'CONVERSION',
               9822,
               'GeoRural',
               1,
               'FALSE',
               1,
               1,
               1 );

    INSERT INTO mdsys.sdo_coord_op_param_vals (
        coord_op_id,
        coord_op_method_id,
        parameter_id,
        parameter_value,
        uom_id
    ) VALUES ( 5009823,
               9822,
               8821,
               - 12,
               9102 );

    INSERT INTO mdsys.sdo_coord_op_param_vals (
        coord_op_id,
        coord_op_method_id,
        parameter_id,
        parameter_value,
        uom_id
    ) VALUES ( 5009823,
               9822,
               8822,
               - 54,
               9102 );

    INSERT INTO mdsys.sdo_coord_op_param_vals (
        coord_op_id,
        coord_op_method_id,
        parameter_id,
        parameter_value,
        uom_id
    ) VALUES ( 5009823,
               9822,
               8823,
               - 2,
               9102 );

    INSERT INTO mdsys.sdo_coord_op_param_vals (
        coord_op_id,
        coord_op_method_id,
        parameter_id,
        parameter_value,
        uom_id
    ) VALUES ( 5009823,
               9822,
               8824,
               - 22,
               9102 );

    INSERT INTO mdsys.sdo_coord_op_param_vals (
        coord_op_id,
        coord_op_method_id,
        parameter_id,
        parameter_value,
        uom_id
    ) VALUES ( 5009823,
               9822,
               8826,
               0,
               9001 );

    INSERT INTO mdsys.sdo_coord_op_param_vals (
        coord_op_id,
        coord_op_method_id,
        parameter_id,
        parameter_value,
        uom_id
    ) VALUES ( 5009823,
               9822,
               8827,
               0,
               9001 );

    INSERT INTO mdsys.sdo_coord_ref_system (
        srid,
        coord_ref_sys_name,
        coord_ref_sys_kind,
        coord_sys_id,
        datum_id,
        geog_crs_datum_id,
        source_geog_srid,
        projection_conv_id,
        information_source,
        data_source,
        is_legacy,
        is_valid,
        supports_sdo_geometry
    ) VALUES ( 5009822,
               'GeoRural / SIRGAS2000 Albers Brasil',
               'PROJECTED',
               4400,
               NULL,
               6674,
               4674,
               5009823,
               'IBGE: parametros angulares Albers Brasil; offsets locais zero',
               'GeoRural',
               'FALSE',
               'TRUE',
               'TRUE' );

    COMMIT;
    dbms_output.put_line('Projecao Albers Brasil cadastrada.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK TO cadastro_albers;
        RAISE;
END;
/

-- Auxiliar de referencia espacial.
CREATE OR REPLACE PACKAGE pkg_ambiental_crs AUTHID definer AS
    FUNCTION srid_calculo RETURN NUMBER;

END;
/

CREATE OR REPLACE PACKAGE BODY pkg_ambiental_crs AS

    FUNCTION srid_calculo RETURN NUMBER IS
        v_n NUMBER;
    BEGIN
        SELECT
            COUNT(*)
        INTO v_n
        FROM
                 mdsys.sdo_coord_ref_sys c
            JOIN mdsys.sdo_coord_ops o ON o.coord_op_id = c.projection_conv_id
        WHERE
                c.srid = 5009822
            AND c.coord_ref_sys_kind = 'PROJECTED'
            AND c.coord_sys_id = 4400
            AND c.source_geog_srid = 4674
            AND c.geog_crs_datum_id = 6674
            AND c.is_legacy = 'FALSE'
            AND o.coord_op_id = 5009823
            AND o.coord_op_method_id = 9822
            AND o.coord_op_type = 'CONVERSION'
            AND o.is_legacy = 'FALSE';

        IF v_n <> 1 THEN
            raise_application_error(-20142, 'Albers Brasil ausente/incompativel. Confira o cadastro Albers no mesmo PDB (secao 1 deste arquivo).'
            );
        END IF;
        SELECT
            COUNT(*)
        INTO v_n
        FROM
            mdsys.sdo_coord_op_param_vals
        WHERE
            coord_op_id = 5009823;

        IF v_n <> 6 THEN
            raise_application_error(-20142, 'Quantidade inesperada de parametros Albers.');
        END IF;
        SELECT
            COUNT(*)
        INTO v_n
        FROM
            mdsys.sdo_coord_op_param_vals
        WHERE
                coord_op_id = 5009823
            AND coord_op_method_id = 9822
            AND ( ( parameter_id = 8821
                    AND parameter_value = - 12
                    AND uom_id = 9102 )
                  OR ( parameter_id = 8822
                       AND parameter_value = - 54
                       AND uom_id = 9102 )
                  OR ( parameter_id = 8823
                       AND parameter_value = - 2
                       AND uom_id = 9102 )
                  OR ( parameter_id = 8824
                       AND parameter_value = - 22
                       AND uom_id = 9102 )
                  OR ( parameter_id IN ( 8826, 8827 )
                       AND parameter_value = 0
                       AND uom_id = 9001 ) );

        IF v_n <> 6 THEN
            raise_application_error(-20142, 'Parametros incompativeis com Albers Brasil.');
        END IF;
        RETURN 5009822;
    END;

END;
/

-- Validacao da transformacao de entrada: oito pontos e 100 ha.
DECLARE
    v_srid     NUMBER;
    v_g        mdsys.sdo_geometry;
    v_composta mdsys.sdo_geometry;
    v_x        NUMBER;
    v_y        NUMBER;
    v_cx       NUMBER;
    v_cy       NUMBER;
    v_area     NUMBER;
    v_falhas   PLS_INTEGER := 0;
    v_ord      mdsys.sdo_ordinate_array := mdsys.sdo_ordinate_array();

    PROCEDURE xy (
        p_g mdsys.sdo_geometry,
        p_x OUT NUMBER,
        p_y OUT NUMBER
    ) IS
    BEGIN
        IF p_g IS NULL THEN
            raise_application_error(-20143, 'Resultado nulo.');
        END IF;
        IF p_g.sdo_point IS NOT NULL THEN
            p_x := p_g.sdo_point.x;
            p_y := p_g.sdo_point.y;
        ELSE
            p_x := p_g.sdo_ordinates(1);
            p_y := p_g.sdo_ordinates(2);
        END IF;

        IF p_x IS NULL
           OR p_y IS NULL THEN
            raise_application_error(-20143, 'Coordenada nula.');
        END IF;

    END;

    PROCEDURE conferir (
        p_nome VARCHAR2,
        p_real NUMBER,
        p_ref  NUMBER,
        p_tol  NUMBER
    ) IS
    BEGIN
        dbms_output.put_line(p_nome
                             || ': esperado='
                             || p_ref
                             || '; recebido='
                             || p_real);

        IF p_real IS NULL
           OR abs(p_real - p_ref) > p_tol THEN
            v_falhas := v_falhas + 1;
            dbms_output.put_line('FALHA: limite=' || p_tol);
        END IF;

    END;

    PROCEDURE ponto (
        p_lon NUMBER,
        p_lat NUMBER,
        p_x   NUMBER,
        p_y   NUMBER
    ) IS
        v_original mdsys.sdo_geometry;
    BEGIN
        v_original := mdsys.sdo_geometry(2001,
                                         4674,
                                         mdsys.sdo_point_type(p_lon, p_lat, NULL),
                                         NULL,
                                         NULL);

        v_g := sdo_cs.transform(v_original, v_srid);
        xy(v_g, v_x, v_y);
        conferir('SIRGAS2000 X '
                 || p_lon
                 || ','
                 || p_lat, v_x, p_x, 0.01);

        conferir('SIRGAS2000 Y '
                 || p_lon
                 || ','
                 || p_lat, v_y, p_y, 0.01);
        -- A rota WGS84 direta deve concordar com WGS84 -> SIRGAS2000 -> Albers.
        -- Isso nao presume que coordenadas em datums diferentes sejam identicas.
        v_original := mdsys.sdo_geometry(2001,
                                         4326,
                                         mdsys.sdo_point_type(p_lon, p_lat, NULL),
                                         NULL,
                                         NULL);

        v_g := sdo_cs.transform(v_original, v_srid);
        xy(v_g, v_x, v_y);
        v_composta := sdo_cs.transform(
            sdo_cs.transform(v_original, 4674),
            v_srid
        );

        xy(v_composta, v_cx, v_cy);
        conferir('Rota WGS84 X', v_x, v_cx, 0.01);
        conferir('Rota WGS84 Y', v_y, v_cy, 0.01);
    END;

    PROCEDURE vertice (
        p_lon NUMBER,
        p_lat NUMBER
    ) IS
    BEGIN
        v_ord.extend(2);
        v_ord(v_ord.count - 1) := p_lon;
        v_ord(v_ord.count) := p_lat;
    END;

BEGIN
    dbms_output.put_line('ALBERS V3 - VALIDACAO DA TRANSFORMACAO DE ENTRADA');
    v_srid := pkg_ambiental_crs.srid_calculo();
    ponto(-54, -12, 0.000000000000000, 0.000000000000000);
    ponto(-51, -24, 307413.492956683097873, -1340560.875268414616585);
    ponto(-60, -3, -665086.854882238199934, 999205.009873505681753);
    ponto(-47, -15, 742307.397066990379244, -346153.173208050429821);
    ponto(-35, -8, 2066814.502399530028924, 378591.207232397049665);
    ponto(-51, 0, 336114.056615200359374, 1335727.507779631763697);
    ponto(-60, 5, -683854.081090611871332, 1874113.492573827505112);
    ponto(-53, -33, 99011.507694881191128, -2307441.852773115038872);
    vertice(-54.000000000000000, -23.646412894657445);
    vertice(-53.990254572275539, -23.646412736490817);
    vertice(-53.990254911749368, -23.637332082355698);
    vertice(-54.000000000000000, -23.637332240511647);
    vertice(-54.000000000000000, -23.646412894657445);
    v_g := mdsys.sdo_geometry(2003,
                              4674,
                              NULL,
                              mdsys.sdo_elem_info_array(1, 1003, 1),
                              v_ord);

    v_g := sdo_cs.transform(v_g, v_srid);
    v_area := sdo_geom.sdo_area(v_g, 0.01, 'unit=SQ_METER') / 10000;
    conferir('Area em hectares', v_area, 100, 0.01);
    IF v_falhas > 0 THEN
        raise_application_error(-20143, 'Validacao de entrada falhou em '
                                        || v_falhas
                                        || ' verificacoes.');
    END IF;

    dbms_output.put_line('TRANSFORMACAO DE ENTRADA E AREA VALIDADAS.');
    dbms_output.put_line('A inversa nativa continua fora de uso: nao transformar 5009822 para 4674/4326.');
END;
/

--Historico imutavel e calculos ambientais.

CREATE OR REPLACE PACKAGE pkg_indicadores_ambientais AUTHID definer AS
    FUNCTION area_ha (
        p_geometria mdsys.sdo_geometry
    ) RETURN NUMBER;

    PROCEDURE calcular (
        p_imovel_id NUMBER,
        p_config    CLOB,
        p_resultado OUT CLOB
    );

END;
/

CREATE OR REPLACE PACKAGE BODY pkg_indicadores_ambientais AS

    c_tol   CONSTANT NUMBER := 0.01; -- metros em SIRGAS2000 / Albers Brasil local
    c_regra CONSTANT VARCHAR2(30) := 'GEORURAL-1.1';

    FUNCTION projetar (
        p_geom mdsys.sdo_geometry
    ) RETURN mdsys.sdo_geometry IS
        v_srid NUMBER;
        v_geom mdsys.sdo_geometry;
    BEGIN
        IF p_geom IS NULL
           OR p_geom.sdo_srid IS NULL THEN
            raise_application_error(-20121, 'Geometria sem SRID.');
        END IF;

        v_srid := pkg_ambiental_crs.srid_calculo();
        v_geom := sdo_cs.transform(p_geom, v_srid);
        IF nvl(
            sdo_geom.validate_geometry_with_context(v_geom, c_tol),
            'INVALID'
        ) <> 'TRUE' THEN
            raise_application_error(-20123, 'Geometria invalida na projecao equivalente.');
        END IF;

        RETURN v_geom;
    END;

    FUNCTION area_plana (
        p_geom mdsys.sdo_geometry
    ) RETURN NUMBER IS
    BEGIN
        IF p_geom IS NULL THEN
            RETURN 0;
        END IF;
        IF MOD(p_geom.sdo_gtype, 100) NOT IN ( 3, 7 ) THEN
            -- Uma intersecao apenas na borda pode resultar em linha/ponto.
            IF MOD(p_geom.sdo_gtype, 100) IN ( 1, 2, 5, 6 ) THEN
                RETURN 0;
            END IF;

        END IF;

        RETURN sdo_geom.sdo_area(p_geom, c_tol, 'unit=SQ_METER') / 10000;
    END;

    FUNCTION area_ha (
        p_geometria mdsys.sdo_geometry
    ) RETURN NUMBER IS
    BEGIN
        RETURN area_plana(projetar(p_geometria));
    END;

    FUNCTION recortar (
        p_a mdsys.sdo_geometry,
        p_b mdsys.sdo_geometry
    ) RETURN mdsys.sdo_geometry IS
    BEGIN
        IF p_a IS NULL
           OR p_b IS NULL THEN
            RETURN NULL;
        END IF;
        RETURN sdo_geom.sdo_intersection(p_a, p_b, c_tol);
    END;

    PROCEDURE calcular (
        p_imovel_id NUMBER,
        p_config    CLOB,
        p_resultado OUT CLOB
    ) IS

        v_cfg        json_object_t;
        v_out        json_object_t := json_object_t();
        v_trace      json_array_t := json_array_t();
        v_details    json_object_t;
        v_imovel     mdsys.sdo_geometry;
        v_native     mdsys.sdo_geometry;
        v_app        mdsys.sdo_geometry;
        v_rl         mdsys.sdo_geometry;
        v_geom       mdsys.sdo_geometry;
        v_raw        mdsys.sdo_geometry;
        v_area       NUMBER;
        v_rl_min     NUMBER;
        v_rl_area    NUMBER;
        v_app_area   NUMBER;
        v_conservada NUMBER;
        v_focos      NUMBER := 0;
        v_calc       NUMBER;
        v_inicio     DATE;
        v_fim        DATE;
        v_status     VARCHAR2(30);
        v_ver        NUMBER;
        v_arq        NUMBER;
        v_fonte      NUMBER;
        TYPE eventos_t IS
            TABLE OF BOOLEAN INDEX BY VARCHAR2(4000);
        v_eventos    eventos_t;
        v_evento     VARCHAR2(4000);

        FUNCTION data_iso (
            p_text VARCHAR2
        ) RETURN DATE IS
            v_date DATE;
        BEGIN
            IF p_text IS NULL
               OR NOT regexp_like(p_text, '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') THEN
                raise_application_error(-20124, 'Data obrigatoria no formato YYYY-MM-DD.');
            END IF;

            BEGIN
                v_date := TO_DATE ( p_text, 'FXYYYY-MM-DD' );
            EXCEPTION
                WHEN OTHERS THEN
                    raise_application_error(-20124, 'Data invalida.');
            END;

            RETURN v_date;
        END;

        PROCEDURE rastrear (
            p_cam NUMBER
        ) IS

            v_obj      json_object_t := json_object_t();
            v_hash     VARCHAR2(300);
            v_location VARCHAR2(500);
            v_dic      NUMBER;
            v_dic_ver  NUMBER;
        BEGIN
            SELECT
                v.ver_status,
                v.ver_id,
                v.arq_id,
                d.fon_id,
                a.arq_hash,
                v.ver_localizacao,
                d.dic_id,
                d.dic_versao
            INTO
                v_status,
                v_ver,
                v_arq,
                v_fonte,
                v_hash,
                v_location,
                v_dic,
                v_dic_ver
            FROM
                     camada c
                JOIN versao     v ON v.ver_id = c.ver_id
                JOIN arquivo    a ON a.arq_id = v.arq_id
                JOIN ingestao   i ON i.ing_id = a.ing_id
                JOIN dicionario d ON d.dic_id = i.dic_id
            WHERE
                c.cam_id = p_cam;

            IF v_status NOT IN ( 'TRATADA', 'PUBLICADA' ) THEN
                raise_application_error(-20125, 'Indicadores exigem camadas de versoes aprovadas.');
            END IF;

            v_obj.put('camadaId', p_cam);
            v_obj.put('versaoId', v_ver);
            v_obj.put('arquivoId', v_arq);
            v_obj.put('fonteId', v_fonte);
            v_obj.put('hashArquivoOriginal', v_hash);
            v_obj.put('localizacaoVersao', v_location);
            v_obj.put('dicionarioId', v_dic);
            v_obj.put('dicionarioVersao', v_dic_ver);
            v_trace.append(v_obj);
        END;

        FUNCTION unir (
            p_papel  VARCHAR2,
            p_desde  DATE DEFAULT NULL,
            p_ate    DATE DEFAULT NULL,
            p_pontos BOOLEAN DEFAULT FALSE
        ) RETURN mdsys.sdo_geometry IS

            v_ids   json_array_t;
            v_cam   NUMBER;
            v_n     NUMBER;
            v_date  DATE;
            v_union mdsys.sdo_geometry;
            v_clip  mdsys.sdo_geometry;
        BEGIN
            IF NOT v_cfg.has(p_papel) THEN
                raise_application_error(-20126, 'Informe camadas para '
                                                || p_papel
                                                || '; ausencia nao significa zero.');
            END IF;

            v_ids := v_cfg.get_array(p_papel);
            IF v_ids IS NULL
               OR v_ids.get_size() = 0 THEN
                raise_application_error(-20126, 'Informe camadas para '
                                                || p_papel
                                                || '; ausencia nao significa zero.');
            END IF;

            FOR j IN 0..v_ids.get_size() - 1 LOOP
                v_cam := v_ids.get_number(j);
                IF v_cam IS NULL
                   OR v_cam <= 0
                OR v_cam <> trunc(v_cam) THEN
                    raise_application_error(-20126, 'ID de camada invalido.');
                END IF;

                FOR k IN 0..j - 1 LOOP
                    IF v_ids.get_number(k) = v_cam THEN
                        raise_application_error(-20126, 'Camada duplicada.');
                    END IF;
                END LOOP;

                rastrear(v_cam);
                v_n := 0;
                FOR f IN (
                    SELECT
                        fei_geometria,
                        fei_atributos
                    FROM
                        feicao
                    WHERE
                        cam_id = v_cam
                ) LOOP
                    v_n := v_n + 1;
                    IF (
                        p_pontos
                        AND f.fei_geometria.sdo_gtype <> 2001
                    )
                    OR (
                        NOT p_pontos
                        AND MOD(f.fei_geometria.sdo_gtype,
                                100) NOT IN ( 3, 7 )
                    ) THEN
                        raise_application_error(-20127, 'Tipo geometrico incompativel com ' || p_papel);
                    END IF;

                    IF p_desde IS NOT NULL THEN
                        v_details := json_object_t.parse(f.fei_atributos);
                        v_date := data_iso(v_details.get_string('data_evento'));
                        IF v_date < p_desde
                        OR v_date > p_ate THEN
                            CONTINUE;
                        END IF;
                    END IF;

                    v_geom := projetar(f.fei_geometria);
                    IF p_pontos THEN
                        v_details := json_object_t.parse(f.fei_atributos);
                        v_evento := v_details.get_string('id_evento');
                        IF v_evento IS NULL THEN
                            raise_application_error(-20130, 'Foco sem id_evento normalizado.');
                        END IF;
                        -- Um registro = uma deteccao; a borda pertence ao imovel.
                        IF
                            sdo_geom.relate(v_imovel, 'ANYINTERACT', v_geom, c_tol) = 'TRUE'
                            AND NOT v_eventos.EXISTS(v_evento)
                        THEN
                            v_focos := v_focos + 1;
                            v_eventos(v_evento) := TRUE;
                        END IF;

                    ELSE
                        v_clip := recortar(v_imovel, v_geom);
                        IF area_plana(v_clip) > 0 THEN
                            IF v_union IS NULL THEN
                                v_union := v_clip;
                            ELSE
                                v_union := sdo_geom.sdo_union(v_union, v_clip, c_tol);
                            END IF;

                        END IF;

                    END IF;

                END LOOP;

                IF v_n = 0 THEN
                    raise_application_error(-20128, 'Camada vazia; confirme a carga antes de calcular.');
                END IF;
            END LOOP;

            RETURN v_union;
        END;

        PROCEDURE medida (
            p_nome        VARCHAR2,
            p_ha          NUMBER,
            p_denominador NUMBER
        ) IS
            v_obj json_object_t := json_object_t();
        BEGIN
            v_obj.put('areaHa', p_ha);
            IF p_denominador = 0 THEN
                v_obj.put_null('percentual');
            ELSE
                v_obj.put('percentual', p_ha / p_denominador * 100);
            END IF;

            v_out.put(p_nome, v_obj);
        END;

    BEGIN
        SAVEPOINT calculo_ambiental;
        v_cfg := json_object_t.parse(p_config);
        v_cfg.on_error(1);
        v_rl_min := v_cfg.get_number('rlMinimoPercentual');
        IF v_rl_min IS NULL
           OR v_rl_min < 0
        OR v_rl_min > 100 THEN
            raise_application_error(-20129, 'Informe o percentual minimo de RL entre 0 e 100.');
        END IF;

        IF v_cfg.get_string('fundamentoRl') IS NULL THEN
            raise_application_error(-20129, 'Informe o fundamento do percentual de RL adotado.');
        END IF;

        v_inicio := data_iso(v_cfg.get_string('inicio'));
        v_fim := data_iso(v_cfg.get_string('fim'));
        IF v_inicio > v_fim THEN
            raise_application_error(-20124, 'Periodo invertido.');
        END IF;
        SELECT
            f.fei_geometria,
            f.cam_id
        INTO
            v_raw,
            v_calc
        FROM
            feicao f
        WHERE
            f.fei_id = p_imovel_id;

        IF MOD(v_raw.sdo_gtype, 100) NOT IN ( 3, 7 ) THEN
            raise_application_error(-20127, 'Imovel deve ser poligonal.');
        END IF;

        rastrear(v_calc);
        v_imovel := projetar(v_raw);
        v_area := area_plana(v_imovel);
        IF v_area <= 0 THEN
            raise_application_error(-20127, 'Imovel sem area positiva.');
        END IF;
        v_native := unir('vegetacao');
        medida('ICV',
               area_plana(v_native),
               v_area);
        v_rl := unir('reservaLegal');
        v_rl_area := area_plana(v_rl);
        medida('IRL', v_rl_area, v_area);
        v_details := v_out.get_object('IRL');
        v_details.put('minimoPercentual', v_rl_min);
        v_details.put('deficitHa',
                      greatest(v_area * v_rl_min / 100 - v_rl_area, 0));

        v_details.put('excedenteHa',
                      greatest(v_rl_area - v_area * v_rl_min / 100, 0));

        v_out.put('IRL', v_details);
        v_app := unir('app');
        v_app_area := area_plana(v_app);
        v_conservada := area_plana(recortar(v_app, v_native));
        medida('IAPP', v_conservada, v_app_area);
        v_details := v_out.get_object('IAPP');
        v_details.put('appTotalHa', v_app_area);
        v_details.put('passivoHa',
                      greatest(v_app_area - v_conservada, 0));
        v_out.put('IAPP', v_details);
        medida('ISAP',
               area_plana(unir('areasProtegidas')),
               v_area);
        medida('IAE',
               area_plana(unir('embargos')),
               v_area);
        medida('IDesmat2008',
               area_plana(unir('desmatamento', DATE '2008-07-22', v_fim)),
               v_area);

        medida('IDesmat2019',
               area_plana(unir('desmatamento', DATE '2019-07-31', v_fim)),
               v_area);

        medida('IDesmat2020',
               area_plana(unir('desmatamento', DATE '2020-12-31', v_fim)),
               v_area);

        v_geom := unir('focos', v_inicio, v_fim, TRUE);
        v_details := json_object_t();
        v_details.put('quantidade', v_focos);
        v_details.put('porMilHa', v_focos / v_area * 1000);
        v_out.put('IFC', v_details);
        v_out.put('imovelId', p_imovel_id);
        v_out.put('areaImovelHa', v_area);
        v_out.put('regra', c_regra);
        v_out.put_null('epsgCalculo');
        v_out.put('projecaoCalculo', 'SIRGAS2000 / Albers Brasil (local)');
        v_out.put('epsgBaseCalculo', 4674);
        v_out.put('metodoEpsgCalculo', 9822);
        v_out.put('sridOracleCalculo',
                  pkg_ambiental_crs.srid_calculo());
        v_out.put('toleranciaMetros', c_tol);
        v_out.put('fontes', v_trace);
        v_out.put('parametros', v_cfg);
        v_out.put('calculadoEm',
                  to_char(systimestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"'));
        p_resultado := v_out.to_clob();
        INSERT INTO calculo_ambiental (
            fei_imovel_id,
            calc_regra,
            calc_parametros,
            calc_resultado
        ) VALUES ( p_imovel_id,
                   c_regra,
                   p_config,
                   p_resultado ) RETURNING calc_id INTO v_calc;

        v_out.put('calculoId', v_calc);
        p_resultado := v_out.to_clob();
        -- O identificador fica na chave da tabela; o documento armazenado tem todo o restante.
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK TO calculo_ambiental;
            RAISE;
    END;

END;
/