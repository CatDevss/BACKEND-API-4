# Banco de dados — GeoRural DataHub

## Finalidade dos arquivos

| Arquivo | Conteúdo |
| --- | --- |
| `geodatalake_ddl.sql` | Tabelas, chaves, restrições e triggers de proteção/auditoria |
| `geodatalake_mockup.sql` | Dados iniciais de usuários, fontes, conjuntos e dicionários |
| `geodatalake_bucket.sql` | Configuração de armazenamento e package de importação para `camada` e `feicao` |
| `geodatalake_credencial_modelo.sql` | Modelo para cada responsável cadastrar sua própria credencial OCI no banco |
| `geodatalake_indicadores.sql` | Projeção Albers Brasil, validação de área e packages dos indicadores ambientais |

## 1. Preparar o ambiente

Utilizar Oracle 26ai com Oracle Spatial, tipos JSON PL/SQL e `DBMS_CLOUD`
disponíveis. O cadastro da projeção exige permissão administrativa no catálogo
`MDSYS`; na instalação validada foi usada a conexão `ADMIN`.

Conectar o SQL Developer ao PDB de destino e executar todos os arquivos com o
mesmo dono das tabelas. Executar scripts com **F5**, em uma sessão sem outras
alterações pendentes, pois comandos DDL e COMMIT confirmam transações.

Antes de começar, executar na mesma sessão:

```sql
SET SERVEROUTPUT ON SIZE UNLIMITED
SET DEFINE OFF
```

A credencial de acesso ao banco e a credencial de acesso ao bucket têm funções
diferentes. Cada responsável pela instalação deve configurar o acesso ao bucket
com **seu próprio usuário OCI e Auth Token**, com permissão de leitura no destino.
Não utilizar credenciais de outro integrante como parte da instalação.

Se apenas as tabelas foram removidas e o schema foi mantido, uma credencial
pessoal configurada anteriormente pode continuar disponível. Verificar antes
de criar novamente; não é necessário apagá-la apenas para reconstruir as tabelas.
O cadastro da projeção também é verificado pelo script antes de uma nova inserção.

## 2. Sequência de execução

| Ordem | Arquivo | Ação e resultado esperado |
| --- | --- | --- |
| 1 | `geodatalake_ddl.sql` | Criar a estrutura e os triggers, incluindo `camada`, `feicao` e `calculo_ambiental` |
| 2 | `geodatalake_mockup.sql` | Popular dados iniciais, se o ambiente estiver vazio e precisar desses cadastros |
| 3 | `geodatalake_bucket.sql` | Criar `ambiental_bucket_config` e compilar `pkg_carga_ambiental` |
| 4 | `geodatalake_credencial_modelo.sql` | Copiar o modelo para uma aba SQL, preencher os dados pessoais e criar a credencial, se ainda não existir |
| 5 | `geodatalake_indicadores.sql` | Cadastrar/verificar Albers Brasil, instalar o auxiliar, validar a projeção e compilar os cálculos |

Após a etapa 3, revisar o endereço do bucket conforme a seção 4. Depois da
etapa 5, executar as consultas da seção 5 para confirmar a instalação.

A carga de mockup é opcional para compilar os packages, mas os cadastros de
fontes, arquivos, versões e conjuntos são necessários para trabalhar com dados
reais. `geodatalake_mockup.sql` não cadastra os arquivos do bucket nem importa
feições. Não repetir esse arquivo sem conferir os registros existentes: os
INSERTs podem causar duplicidades ou violações de unicidade.

Os scripts não interrompem automaticamente toda a sequência ao primeiro erro.
Conferir a Saída do Script após cada arquivo e resolver eventuais erros antes
de avançar. `CREATE TABLE IF NOT EXISTS` preserva tabelas existentes, mas não
corrige colunas ou constraints divergentes. `CREATE OR REPLACE` recompila os
packages e triggers.

## 3. Configurar a própria credencial

### Verificar se ela já existe

```sql
SELECT credential_name, username
FROM user_credentials
WHERE credential_name = 'GEORURAL_BUCKET_CRED';
```

Se retornar uma credencial vinculada ao próprio usuário OCI, ela pode ser
reutilizada; sua presença não comprova que o token ou o acesso ao bucket estejam
válidos. Se o registro não existir, utilizar o modelo fornecido. Se estiver
vinculado a outro usuário, solicitar a configuração correta ao administrador,
sem sobrescrever silenciosamente um acesso que possa estar em uso.

### Preencher o modelo

No console OCI, acessar o perfil do usuário e gerar um **Auth Token** em
**Auth Tokens / Tokens de autenticação**. Depois, copiar o conteúdo de
`geodatalake_credencial_modelo.sql` para uma aba SQL e substituir:

| Campo | Valor a preencher |
| --- | --- |
| `username` | Nome exato do próprio usuário OCI; se necessário, `dominio/usuario` |
| `password` | Auth Token gerado por esse mesmo usuário, não a senha do console nem a senha do banco |
| `credential_name` | Manter `GEORURAL_BUCKET_CRED`, que é o nome usado na configuração do importador |

Em um domínio de identidade diferente de `Default`, incluir o domínio conforme
a identificação da conta OCI. Executar o bloco preenchido com F5 e repetir a
consulta acima para confirmar o nome e o usuário cadastrados.

A credencial é criada com `DBMS_CLOUD.CREATE_CREDENTIAL`, não por INSERT direto
em tabelas internas do Oracle. O arquivo é somente um modelo: não executar com
os marcadores `<SEU_USUARIO_OCI>` e `<SEU_AUTH_TOKEN>` sem substituir.
Não salvar tokens, senhas ou chaves privadas no repositório nem compartilhá-los
em mensagens ou capturas de tela.

## 4. Configurar o bucket de destino

`geodatalake_bucket.sql` contém o endereço do bucket do projeto e insere a
configuração somente se `config_id=1` não existir. O MERGE não atualiza uma
configuração existente. Para instalar em outro bucket, informar o endereço
correto na configuração após executar esse arquivo.

Conferir o destino atual:

```sql
SELECT config_id, bucket_uri, credential_name
FROM ambiental_bucket_config;
```

Se for necessário utilizar o próprio bucket, preencher e executar o modelo
abaixo na conexão de instalação:

```sql
UPDATE ambiental_bucket_config
SET bucket_uri = 'https://objectstorage.<REGIAO>.oraclecloud.com/n/<NAMESPACE>/b/<NOME_BUCKET>/o',
    credential_name = 'GEORURAL_BUCKET_CRED'
WHERE config_id = 1;

COMMIT;
```

A URL deve terminar em `/o`, sem acrescentar o nome de um arquivo. A chave do
objeto será obtida de `versao.ver_localizacao`. O usuário da credencial deve
ter acesso de leitura ao bucket escolhido, seja ele pessoal ou compartilhado.
Criar a credencial e preencher a URL não testa esse acesso; a leitura real deve
ser confirmada antes de considerar a carga operacional.

## 5. Conferir a instalação

### Compilação

```sql
SELECT object_name, object_type, status
FROM user_objects
WHERE object_name IN (
    'AMBIENTAL_BUCKET_CONFIG', 'CALCULO_AMBIENTAL', 'CAMADA', 'FEICAO',
    'PKG_CARGA_AMBIENTAL', 'PKG_AMBIENTAL_CRS', 'PKG_INDICADORES_AMBIENTAIS',
    'TRG_PROTEGER_CAMADA', 'TRG_CAMADA_METADADOS', 'TRG_PROTEGER_FEICAO',
    'TRG_CALCULO_AMBIENTAL_IMUTAVEL'
)
ORDER BY object_name, object_type;

SELECT name, type, line, position, text
FROM user_errors
ORDER BY name, sequence;
```

Os objetos esperados devem aparecer com status VALID. A consulta de erros
permite conferir também os triggers do DDL; não deve haver erros de compilação.
Objeto ausente indica que sua criação não foi concluída.

### Projeção e área

Com SERVEROUTPUT habilitado, o teste incluído em
`geodatalake_indicadores.sql` deve apresentar:

```text
TRANSFORMACAO DE ENTRADA E AREA VALIDADAS.
```

O teste verifica oito pontos e uma referência de 100 hectares. A mensagem é
emitida antes da criação do package dos indicadores, portanto ainda é necessário
conferir a compilação ao final. O SRID Oracle local é `5009822`, com base
SIRGAS2000 `4674` e projeção equivalente Albers Brasil em metros.
A inversa nativa para coordenadas geográficas não deve ser utilizada nesta
instalação; os cálculos usam apenas a transformação de entrada para Albers.

## 6. Importar dados após instalar

Instalar os scripts não popula automaticamente `camada` e `feicao`. Para a
carga, é necessário registrar o arquivo, associá-lo a um conjunto e disponibilizar
uma versão TRATADA/PUBLICADA com caminho `tratada/` e conteúdo GeoJSON RFC 7946,
WGS84 2D, sem campo `crs`. Outros formatos precisam de preparação.

A procedure `pkg_carga_ambiental.importar_geojson` recebe os IDs do conjunto e
da versão tratada. Ela cria outra versão BRUTA, cria a camada, insere as feições
e só então aprova a nova versão como TRATADA. A entrada é preservada e uma
revisão já carregada no mesmo conjunto é recusada para evitar duplicidade.
O chamador deve confirmar a transação somente depois do sucesso.

A conversão das geometrias usa `JSON_VALUE` com `RETURNING SDO_GEOMETRY`
e `ERROR ON ERROR`, mantendo o conteúdo completo em CLOB. A validação espacial
continua obrigatória antes do INSERT; uma geometria inválida reverte a carga.
Esse conversor foi verificado isoladamente na feição MultiPolygon que falhava
com FROM_GEOJSON. A carga completa ainda precisa ser confirmada após recompilar.

Para aplicar uma atualização no importador, executar `geodatalake_bucket.sql`
com F5 e conferir os erros de compilação. O CREATE TABLE IF NOT EXISTS preserva
a configuração existente, e o MERGE não a altera quando já está cadastrada.
É possível também executar somente os trechos CREATE OR REPLACE PACKAGE e
PACKAGE BODY, sem executar o restante do arquivo. Não é necessário reiniciar
o Spring Boot quando apenas o package do Oracle foi atualizado.


O backend pode solicitar a carga por:

```http
POST /conjuntos/{conjuntoId}/versoes/{versaoId}/camada-oracle
```

## 7. Calcular os indicadores

`pkg_indicadores_ambientais.area_ha` retorna hectares.
`pkg_indicadores_ambientais.calcular` recebe a feição do imóvel e o JSON de
configuração das camadas, período e parâmetros de Reserva Legal. O cálculo
completo exige camadas de vegetação, RL, APP, áreas protegidas, embargos,
desmatamento e focos. Ausência de dados não significa resultado zero.

Supressão e focos exigem `data_evento` no formato YYYY-MM-DD; focos também exigem
`id_evento` estável para deduplicação. A importação preserva os atributos;
a preparação da fonte deve normalizá-los.

```http
POST /imoveis/{feicaoId}/indicadores/calcular
```

Os cálculos são por imóvel; a agregação municipal não está implementada.
O histórico é gravado em `calculo_ambiental` e confirmado pelo chamador.
A coleta/integração do CAR está fora do escopo destes scripts.

## Referências

- [Oracle: DBMS_CLOUD e credenciais OCI](https://docs.oracle.com/en/database/oracle/oracle-database/26/arpls/dbms_cloud.html)
- [Oracle: gerenciamento de credenciais](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/autonomous-manage-credentials.html)
