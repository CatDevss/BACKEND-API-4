# Banco de dados — GeoRural

## Inicialização

Use Oracle com Spatial e DBMS_CLOUD disponíveis. No SQL Developer, conecte ao
mesmo PDB e dono das tabelas e execute com **F5**, na ordem abaixo, conferindo
os erros antes de continuar. Use uma sessão sem alterações pendentes: há DDL e
COMMIT nos scripts. O cadastro da projeção exige permissão no catálogo MDSYS.

1. `geodatalake_ddl.sql` — estrutura, incluindo camada, feicao e histórico.
2. `geodatalake_mockup.sql` — cadastros iniciais para um banco vazio; não repetir sem conferir os dados.
3. `geodatalake_bucket.sql` — configuração e package de importação.
4. `geodatalake_credencial_modelo.sql` — preencher com **sua própria credencial OCI e Auth Token**, ajustar o bucket e o nome da credencial na configuração. Não versionar segredos preenchidos. Se a credencial já existir no schema, conferir antes de recriá-la.
5. `geodatalake_indicadores.sql` — projeção local, validação de entrada e cálculos.

Confira a compilação:

```sql
SELECT name, type, line, position, text
FROM user_errors
WHERE name IN ('PKG_AMBIENTAL_CRS', 'PKG_CARGA_AMBIENTAL',
               'PKG_INDICADORES_AMBIENTAIS')
ORDER BY name, sequence;
```

Para habilitar as rotas, configure `environmental.enabled=true` no
`application.properties`. A autenticação OCI do Java não substitui a credencial
do Oracle.

## Funções e procedures

As funções de indicadores recebem `p_imovel_id` (ID da feição poligonal analisada)
e `p_config` (JSON em CLOB), e retornam JSON em CLOB. Todos os cálculos espaciais
são executados no banco.

| Rotina | Finalidade / parâmetros JSON |
| --- | --- |
| `pkg_ambiental_crs.srid_calculo()` | Retorna o SRID local de cálculo, 5009822 |
| `pkg_carga_ambiental.importar_geojson(p_con_id, p_ver_id, p_cam_id OUT, p_quantidade OUT)` | Importa uma versão de arquivo, criando a camada e suas feições |
| `pkg_indicadores_ambientais.area_ha(p_geometria)` | Área em hectares |
| `pkg_indicadores_ambientais.icv` | Cobertura vegetal; `vegetacao` |
| `pkg_indicadores_ambientais.irl` | Reserva legal; `reservaLegal`, `rlMinimoPercentual`, `fundamentoRl` |
| `pkg_indicadores_ambientais.iapp` | APP conservada e passivo; `app`, `vegetacao` |
| `pkg_indicadores_ambientais.isap` | Áreas protegidas; `areasProtegidas` |
| `pkg_indicadores_ambientais.iae` | Embargos; `embargos` |
| `pkg_indicadores_ambientais.idesmat` | Desmatamento nos três marcos; `desmatamento`, `fim` |
| `pkg_indicadores_ambientais.ifc` | Focos únicos e densidade; `focos`, `inicio`, `fim` |
| `pkg_indicadores_ambientais.calcular(p_imovel_id, p_config, p_resultado OUT)` | Calcula todos e grava histórico; requer todos os parâmetros acima |

As funções individuais não gravam histórico. A procedure `calcular` não dá COMMIT:
o chamador confirma a transação (a API faz isso).

Cada papel recebe **um array de IDs de camadas**, por exemplo
`"vegetacao": [28, 35]`. Um conjunto pode conter vários arquivos: importe cada
versão pela rota `POST /conjuntos/{conjuntoId}/versoes/{versaoId}/camada-oracle`
e use os IDs retornados nos cálculos. O banco reúne as feições de todas as
camadas informadas e dissolve sobreposições para não somar áreas duplicadas.
IDs de conjunto, arquivo, versão, camada e feição são diferentes.

As rotas individuais são `POST /imoveis/{feicaoId}/indicadores/{indicador}`
(`icv`, `irl`, `iapp`, `isap`, `iae`, `idesmat`, `ifc`).
Use `/calcular` para todos com histórico. Envie Body → raw → JSON.

Datas usam `YYYY-MM-DD`. Desmatamento precisa de `data_evento` nos atributos;
focos precisam de `data_evento` e `id_evento`. O mínimo de RL é informado pelo
chamador, não inferido automaticamente. Camada ausente não equivale a zero.
Bioma não substitui vegetação nativa. A projeção local é usada apenas na ida:
não transformar 5009822 de volta para 4326/4674 nesta instalação.
