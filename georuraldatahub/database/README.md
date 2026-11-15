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

## Como montar a chamada no Postman

Selecione **POST** e **Body > raw > JSON**.

### Qual ID vai em cada lugar?

Exemplo: `POST http://localhost:8080/imoveis/928/indicadores/icv`

Body:
```json
{
  "vegetacao": [28, 35]
}
```

| Parte da chamada | Significado |
| --- | --- |
| `928` na URL | ID de UMA feicao: o poligono do imovel que sera analisado |
| `icv` na URL | Indicador que sera calculado |
| `vegetacao` no body | Nome fixo do parametro que o ICV espera |
| `28, 35` no array | IDs de DUAS camadas que contem vegetacao nativa |

Essa chamada significa: **calcule a cobertura vegetal do imovel 928 usando
todas as feicoes das camadas 28 e 35**. Para usar apenas uma camada, envie
`"vegetacao": [28]`. Para analisar outro imovel, troque o ID na URL e faca
outra chamada.

**Nao use IDs de arquivo, versao ou conjunto nesses arrays.** Importe cada
arquivo primeiro e utilize o `camadaId` retornado pela importacao.

### O body e sempre vegetacao?

**Nao. Cada indicador espera os nomes de parametros abaixo.** Esses nomes
foram definidos no projeto e devem ser escritos exatamente como nos exemplos.
Voce troca os IDs, mantendo os nomes. O nome do arquivo pode ser qualquer um,
mas o conteudo da camada deve corresponder ao papel informado: uma camada de
biomas, por exemplo, nao vira vegetacao nativa por estar em `vegetacao`.

Todos os IDs abaixo sao ilustrativos: substitua por IDs existentes no seu banco.
As URLs usam a base `http://localhost:8080`.

**ICV — cobertura vegetal**

`POST /imoveis/928/indicadores/icv`
```json
{"vegetacao": [28, 35]}
```

**IRL — reserva legal**

`POST /imoveis/928/indicadores/irl`
```json
{
  "reservaLegal": [30],
  "rlMinimoPercentual": 20,
  "fundamentoRl": "Justificativa do percentual adotado"
}
```
O percentual 20 e apenas um exemplo; informe o minimo aplicavel ao caso.

**IAPP — APP conservada**

`POST /imoveis/928/indicadores/iapp`
```json
{"app": [31], "vegetacao": [28, 35]}
```
Aqui sao necessarios dois tipos de dados: APP e vegetacao nativa.

**ISAP — areas protegidas**

`POST /imoveis/928/indicadores/isap`
```json
{"areasProtegidas": [32]}
```

**IAE — area embargada**

`POST /imoveis/928/indicadores/iae`
```json
{"embargos": [33]}
```

**IDesmat — desmatamento**

`POST /imoveis/928/indicadores/idesmat`
```json
{"desmatamento": [34], "fim": "2026-11-15"}
```
Retorna os tres marcos temporais ate a data fim.

**IFC — focos de calor**

`POST /imoveis/928/indicadores/ifc`
```json
{"focos": [36], "inicio": "2021-01-01", "fim": "2026-11-15"}
```

### Como calcular todos juntos?

`POST /imoveis/928/indicadores/calcular`

Esta rota exige todos os insumos e grava o historico. Enviar apenas
`{"vegetacao":[28]}` nela nao e suficiente.

```json
{
  "vegetacao": [28, 35],
  "reservaLegal": [30],
  "app": [31],
  "areasProtegidas": [32],
  "embargos": [33],
  "desmatamento": [34],
  "focos": [36],
  "rlMinimoPercentual": 20,
  "fundamentoRl": "Justificativa do percentual adotado",
  "inicio": "2021-01-01",
  "fim": "2026-11-15"
}
```

Em qualquer papel, voce pode informar varias camadas de diferentes arquivos.
O banco cruza suas feicoes com o imovel e une as areas sobrepostas para evitar
dupla contagem. Para focos, conta eventos unicos pelo atributo `id_evento`.

### CSV de focos de calor

A importacao tambem aceita CSV tratado UTF-8, separado por virgula ou ponto e
virgula, com `id`, `lat`, `lon` e `data_hora_gmt`. Coordenadas devem estar
em WGS84 e a data GMT deve iniciar com `YYYY-MM-DD`. Cada linha vira um ponto
(X=lon, Y=lat). As colunas sao preservadas e a carga acrescenta `id_evento`
a partir de `id` e `data_evento` a partir da data GMT. Use a camada retornada
em `focos` na chamada IFC. Para instalar em um banco existente, reexecute
`geodatalake_bucket.sql` com F5; nao precisa recriar as tabelas.

## Criar uma camada com varios arquivos

1. Importe cada versao de arquivo pela rota
   `POST /conjuntos/{conjuntoId}/versoes/{versaoId}/camada-oracle`.
2. Guarde o `camadaId` de cada retorno.
3. Crie a camada composta com os IDs dessas camadas:

```http
POST http://localhost:8080/conjuntos/21/camadas/combinar
Content-Type: application/json
```

```json
{
  "nome": "Focos de calor - varios arquivos",
  "camadas": [43, 44]
}
```

43 e 44 sao exemplos de **camadas**, nao IDs de arquivos. As camadas devem
pertencer ao conjunto da URL, estar aprovadas, ter feicoes e usar o mesmo SRID.
Selecione camadas importadas originais, sem compor outras composicoes.
Reuna dados do mesmo papel (por exemplo, varios arquivos de focos).

O retorno contem `camadaId` da nova camada e `quantidade` de feicoes copiadas.
As camadas originais continuam existindo. A nova camada reune os registros,
sem dissolver geometrias ou eliminar registros duplicados durante a criacao.
Nos indicadores, areas sobrepostas sao dissolvidas e focos sao deduplicados
por `id_evento`.

Use apenas o novo ID no papel correspondente: se o retorno for camada 50,
envie `"focos": [50]` no IFC. Isso continua analisando o imovel da URL;
compor os arquivos de entrada nao muda a area analisada.

A procedure `pkg_carga_ambiental.combinar_camadas(p_con_id, p_nome,
p_camadas, p_cam_id OUT, p_quantidade OUT)` recebe um array JSON em CLOB,
copia as feicoes atomicamente e nao executa COMMIT.
`camada_origem` registra todas as camadas de origem. Por compatibilidade com
a estrutura existente, a composicao tem uma versao tecnica vinculada a um
dos arquivos; a origem completa deve ser consultada em `camada_origem`.
O resultado dos indicadores rastreia os arquivos originais dessa relacao.

**Banco existente:** execute `geodatalake_bucket.sql` e depois
`geodatalake_indicadores.sql`, com F5, conferindo a compilacao.
Nao precisa apagar tabelas. Reinicie o backend apos compilar o Java.
