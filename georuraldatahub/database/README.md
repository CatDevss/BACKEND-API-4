# Banco de dados — GeoRural DataHub

## 1. Executar os scripts

Use Oracle com **Spatial e DBMS_CLOUD** disponíveis. No SQL Developer, conecte-se ao PDB e ao schema que serão usados pelo backend. Execute os arquivos com **F5**, nesta ordem:

| Ordem | Arquivo | Finalidade |
| --- | --- | --- |
| 1 | `geodatalake_ddl.sql` | Criar tabelas, restrições e triggers de proteção e auditoria. |
| 2 | `geodatalake_mockup.sql` | Inserir os cadastros iniciais no banco vazio. |
| 3 | `geodatalake_bucket.sql` | Configurar o endereço do bucket e criar as rotinas de importação de camadas. |
| 4 | `geodatalake_credencial_modelo.sql` | Criar a credencial de acesso ao bucket. Preencha seu usuário OCI e Auth Token antes de executar. |
| 5 | `geodatalake_indicadores_mapa.sql` | Criar a projeção de cálculo, os indicadores, a gravação do histórico e a função do mapa. |

Os objetos de relatórios e exclusões fazem parte de `geodatalake_ddl.sql`.

No script do bucket, ajuste `bucket_uri` e `credential_name` para seu ambiente. O nome da credencial deve ser o mesmo usado em `DBMS_CLOUD.CREATE_CREDENTIAL`. Não salve credenciais preenchidas no Git.

O cadastro da projeção requer permissão no catálogo MDSYS. Confira os erros após cada arquivo antes de continuar. Os scripts contêm DDL e COMMIT: use uma sessão sem alterações pendentes. Em banco já instalado, aplique somente os objetos necessários.

Confira a compilação:

```sql
SELECT name, type, line, position, text
FROM user_errors
WHERE type IN ('PACKAGE', 'PACKAGE BODY', 'PROCEDURE', 'FUNCTION', 'TRIGGER')
ORDER BY name, sequence;
```

A consulta não deve retornar erros dos objetos instalados.

## 2. Importar dados para os indicadores

No backend, configure e reinicie a aplicação:

```properties
environmental.enabled=true
```

O backend deve usar o mesmo schema dos scripts. Os exemplos abaixo usam `http://localhost:8080`.

Os arquivos de entrada precisam estar tratados e vinculados a um conjunto. Para gerar uma camada, importe uma versão pelo Postman:

```http
POST /conjuntos/{conjuntoId}/versoes/{versaoId}/camada-oracle
```

Essa chamada não recebe body. O retorno contém `camadaId` e `quantidade` de feições importadas. A importação aceita GeoJSON ou CSV de focos com `id`, `lat`, `lon` e `data_hora_gmt`.

Consulte os IDs disponíveis:

```sql
SELECT cam_id, cam_nome, con_id, ver_id, cam_srid
FROM camada
ORDER BY cam_id;

SELECT fei_id, cam_id, fei_id_origem,
       DBMS_LOB.SUBSTR(fei_atributos, 500, 1) AS atributos
FROM feicao
ORDER BY fei_id
FETCH FIRST 20 ROWS ONLY;
```

**Na URL do indicador, use o ID de uma feição poligonal como área de análise. Nos arrays do JSON, use IDs de camadas de entrada.** IDs de conjunto, arquivo e versão não substituem esses IDs.

Uma camada reúne várias feições. Cada papel aceita uma ou mais camadas, por exemplo `"vegetacao": [28, 35]`. Escolha camadas cujo conteúdo corresponda ao papel: biomas, por exemplo, não substituem vegetação nativa.

Desmatamento precisa de `data_evento` nos atributos. Focos precisam de `data_evento` e `id_evento`; a importação CSV normaliza esses campos. Datas dos testes usam `YYYY-MM-DD`.

## 3. Catálogo dos indicadores

Os cálculos são executados no **Oracle Spatial**. As áreas são calculadas em hectares, com projeção equivalente Albers baseada em SIRGAS 2000. As camadas são recortadas pela área de análise e as sobreposições de áreas são dissolvidas para evitar dupla contagem.

Considere **A** como a área total da feição analisada, em hectares.

| Indicador | Cálculo |
| --- | --- |
| **ICV — cobertura vegetal nativa** | `100 × área de vegetação nativa dentro da área analisada / A`. |
| **IRL — reserva legal** | `100 × área de reserva legal dentro da área analisada / A`. Área mínima exigida: `A × rlMinimoPercentual / 100`. Déficit: `máximo(mínimo exigido − área de RL, 0)`. Excedente: `máximo(área de RL − mínimo exigido, 0)`. |
| **IAPP — APP conservada** | `100 × área da interseção entre APP e vegetação nativa / área total de APP`, considerando o recorte da área analisada. Passivo: `APP total − APP conservada`. |
| **ISAP — áreas protegidas** | `100 × área da união das áreas protegidas dentro da área analisada / A`. |
| **IAE — áreas embargadas** | `100 × área da união dos embargos dentro da área analisada / A`. |
| **IDesmat — desmatamento** | `100 × área desmatada dentro da área analisada / A`, em três janelas: de **22/07/2008**, **31/07/2019** e **31/12/2020** até `fim`, com limites inclusivos. |
| **IFC — focos de calor** | Conta eventos únicos por `id_evento` dentro da área analisada entre `inicio` e `fim`, inclusive. Densidade: `quantidade de focos / A × 1.000`, em focos por mil hectares. Pontos na borda são incluídos. |

Os indicadores de área retornam hectares e percentual. IFC retorna quantidade e densidade. No IAPP, se a APP do recorte tiver área zero, o percentual é nulo. Uma camada ausente ou vazia gera erro; não equivale a resultado zero. O mínimo de RL é informado por quem faz a chamada, junto de seu fundamento.

## 4. Consultar os indicadores com POST e JSON

No Postman, selecione **POST > Body > raw > JSON**, com `Content-Type: application/json`.

**Todos os IDs abaixo são exemplos.** Substitua `928` por uma feição poligonal existente e os IDs dos arrays pelas camadas correspondentes do seu banco.

### ICV

`POST http://localhost:8080/imoveis/928/indicadores/icv`

```json
{"vegetacao": [28, 35]}
```

### IRL

`POST http://localhost:8080/imoveis/928/indicadores/irl`

```json
{
  "reservaLegal": [30],
  "rlMinimoPercentual": 20,
  "fundamentoRl": "Justificativa do percentual adotado neste teste"
}
```

O percentual de 20% é apenas ilustrativo; informe o aplicável ao caso analisado.

### IAPP

`POST http://localhost:8080/imoveis/928/indicadores/iapp`

```json
{"app": [31], "vegetacao": [28, 35]}
```

### ISAP

`POST http://localhost:8080/imoveis/928/indicadores/isap`

```json
{"areasProtegidas": [32]}
```

### IAE

`POST http://localhost:8080/imoveis/928/indicadores/iae`

```json
{"embargos": [33]}
```

### IDesmat

`POST http://localhost:8080/imoveis/928/indicadores/idesmat`

```json
{"desmatamento": [34], "fim": "2026-11-15"}
```

Retorna `IDesmat2008`, `IDesmat2019` e `IDesmat2020`, cada um com `areaHa` e `percentual`.

### IFC

`POST http://localhost:8080/imoveis/928/indicadores/ifc`

```json
{"focos": [36], "inicio": "2021-01-01", "fim": "2026-11-15"}
```

Retorna `IFC.quantidade` e `IFC.porMilHa`. Para um boletim diário, envie o mesmo dia em `inicio` e `fim`. Sem registros no período, os valores são nulos; zero indica que não foram encontrados focos na área nas fontes consultadas.

### Calcular e gravar no histórico

As sete chamadas individuais acima **não gravam histórico**. Para salvar somente um indicador, acrescente `/calcular` à sua URL e envie o mesmo JSON. Exemplo:

`POST http://localhost:8080/imoveis/928/indicadores/ifc/calcular`

Para calcular os **sete indicadores e salvar juntos**, use:

`POST http://localhost:8080/imoveis/928/indicadores/calcular`

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
  "fundamentoRl": "Justificativa do percentual adotado neste teste",
  "inicio": "2021-01-01",
  "fim": "2026-11-15"
}
```

Essa rota exige todos os insumos. A API confirma a transação; o retorno inclui `calculoId`.
