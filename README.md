# BACKEND-API-4

Backend do GeoRural DataHub. Os contratos abaixo descrevem o escopo atual de gestão de conjuntos e associações da BE-08; não representam a conclusão de todos os critérios do backlog original.

## Integração de conjuntos no frontend

As rotas não têm prefixo `/api`. Use a URL do backend acessível pelo ambiente do frontend; no desenvolvimento local, normalmente é `http://localhost:8080`. No devcontainer, confira o encaminhamento da porta.

O fluxo recomendado é:

1. Cadastrar a fonte e seu dicionário.
2. Enviar ou coletar arquivos para a fonte, sem selecionar conjunto.
3. Consultar a validação dos arquivos.
4. Criar um conjunto e selecionar os arquivos que vão compô-lo.
5. Consultar a estrutura do conjunto e, quando disponíveis, suas referências de visualização.

Um arquivo pode pertencer a vários conjuntos. A associação não faz novo upload, não executa nova validação e não copia objetos no bucket. A desassociação preserva o arquivo, suas versões, seus relatórios e os vínculos com outros conjuntos.

### Contratos HTTP

| Método e rota | Corpo/parâmetros | Resultado |
| --- | --- | --- |
| `POST /conjuntos` | JSON `{"name":"Análise Paraná","userId":2}` | `201`: `{id,name,userId}`. O ID do exemplo deve ser substituído por um usuário existente. |
| `GET /conjuntos` | — | `200`: lista de `{id,name,userId}`. |
| `GET /conjuntos/{id}` | — | `200`: `{id,name,userId}`; `404` se não existir. |
| `PUT /conjuntos/{id}` | JSON `{"name":"Novo nome"}` | `200`: conjunto atualizado. Não modifica os arquivos associados. |
| `DELETE /conjuntos/{id}` | — | `204` para conjunto vazio; `409` se ainda houver arquivos associados. |
| `GET /fontes/{sourceId}/arquivos` | — | `200`: arquivos da fonte, inclusive os ainda sem conjunto; `404` para fonte inexistente. |
| `POST /fontes/{sourceId}/arquivos?userId=2` | Multipart com campo `file` do tipo arquivo | `201`: arquivo com relatório em `validation`. |
| `POST /fontes/{sourceId}/coletas` | JSON com `product`, `userId`, `competence` e parâmetros do produto | `201`: arquivo em `file` e metadados em `collection`. `datasetId` é opcional. |
| `PUT /conjuntos/{datasetId}/arquivos/{fileId}` | Sem corpo | `204`: associa um arquivo existente; repetir a associação não cria duplicata. |
| `DELETE /conjuntos/{datasetId}/arquivos/{fileId}` | Sem corpo | `204`: remove o vínculo; repetir a remoção não exclui o arquivo. |
| `GET /conjuntos/{id}/arquivos` | — | `200`: arquivos atualmente associados, sem agrupamento por fonte. |
| `GET /conjuntos/{id}/estrutura` | — | `200`: conjunto → fontes → arquivos → referências de visualização. |
| `GET /conjuntos/{id}/camadas` | — | `200`: referências dos arquivos com formato de visualização suportado e versão validada disponível. |
| `GET /arquivos/{id}/geojson` | — | `200`: `FeatureCollection` da versão validada; `409` se não houver registros aprovados. |
| `GET /arquivos/{id}/validacao` | — | `200`: relatório de validação persistido. |

As operações de associação e desassociação retornam `404` se o conjunto ou o arquivo não existir. As respostas `204` não têm corpo: não tente interpretá-las como JSON. Após uma alteração de vínculo, atualize a estrutura e as camadas do conjunto no estado do frontend.

O upload antigo `POST /conjuntos/{id}/arquivos?userId=...&sourceId=...` continua disponível por compatibilidade: faz a ingestão e a associação imediata. Para reutilizar um arquivo existente, use o `PUT` de associação. Conteúdo já recebido na mesma fonte retorna `409`, mesmo quando outro conjunto é informado.

Nomes de conjuntos são obrigatórios, têm espaços nas extremidades removidos e precisam caber em 100 bytes UTF-8, respeitando o schema utilizado. A API rejeita o excesso, inclusive quando o texto tem menos de 100 caracteres mas contém caracteres multibyte. O serviço retorna `422` para esse limite; entradas que falham nas anotações de validação do DTO retornam `400`.

### Estrutura para o explorador

Exemplo ilustrativo de `GET /conjuntos/1/estrutura`:

```json
{
  "id": 1,
  "name": "Análise Paraná",
  "userId": 2,
  "sources": [
    {
      "id": 4,
      "name": "IBGE — municípios",
      "url": "https://www.ibge.gov.br/",
      "files": [
        {
          "id": 6,
          "name": "municipios.geojson",
          "formatFile": "geojson",
          "size": 1234,
          "receivedDate": "2026-10-20T10:00:00",
          "ingestionId": 5,
          "dictionaryId": 3,
          "dictionaryVersion": 1,
          "layers": [
            {
              "fileId": 6,
              "name": "municipios.geojson",
              "formatFile": "geojson",
              "size": 1234,
              "receivedDate": "2026-10-20T10:00:00"
            }
          ]
        }
      ]
    }
  ]
}
```

A estrutura agrupa por ID da fonte, inclusive quando os arquivos usaram versões diferentes de dicionário. Arquivos sem referência de visualização permanecem na árvore com `layers: []`. Conjuntos vazios retornam `sources: []`. Fontes sem arquivos associados ao conjunto não aparecem nessa consulta; para selecionar novos arquivos, use as listagens de fontes e de arquivos por fonte.

Use `fileId` da referência para chamar `/arquivos/{fileId}/geojson`. A estrutura contém metadados e não baixa as geometrias do bucket. Datas de recebimento são serializadas de `LocalDateTime`, sem offset; não presuma que o valor contém um fuso horário.

### Validação e visualização atuais

HTTP `201` no upload/coleta indica que o resultado foi registrado, não que todo o conteúdo foi aprovado. Consulte `validation.status` no upload ou `file.validation.status` na coleta: `VALIDO`, `PARCIAL` ou `REJEITADO`. A listagem de arquivos não carrega o relatório do bucket; consulte a rota de validação para os detalhes.

As referências de visualização atuais usam versões `BRUTA` armazenadas sob `validada/`: contêm somente os registros aprovados pela validação. Isso **não significa** que o tratamento da BE-07 tenha sido concluído. Não apresente essas referências como dados tratados ou aprovados por um gestor.

CSV precisa ter colunas de latitude e longitude reconhecidas pelo conversor para gerar GeoJSON. Um CSV validado apenas por regras não espaciais pode aparecer como candidato na listagem de camadas e ainda falhar ao solicitar GeoJSON. Trate a mensagem de erro da API. Formatos não suportados para visualização retornam `422`.

### Limites deste escopo

- Não foram adicionados campos, tabelas ou queries nativas; os vínculos usam o relacionamento JPA existente em `conjunto_arquivo`.
- Não há autenticação/autorização de API implementada: a configuração atual permite as requisições. `userId` identifica um cadastro, não uma sessão autenticada. Não apresente os controles do frontend como proteção de acesso do backend.
- Não há proteção de associações por estado de aprovação de versões de conjuntos; essa regra depende do modelo e do fluxo da BE-11.
- A consulta de estrutura não faz reprojeção, transformação espacial, tratamento ou cálculo de indicadores. A visualização atual não oferece filtro `bbox`.
- As listagens descritas não são paginadas. O filtro geográfico das coletas é restrito ao Paraná, mas isso não limita automaticamente o tamanho de cada arquivo.
- O contrato revisado de gestão de conjuntos não substitui a homologação do escopo original da BE-08 com a equipe.

## Verificação

Com Java 21 e Maven, execute a suíte que não depende da inicialização completa com Oracle/OCI:

```sh
mvn -f georuraldatahub/pom.xml '-Dtest=*Test,!GeoruraldatahubApplicationTests' test
```

Os testes de downloads oficiais reais são opt-in, por `-Dcollection.live.tests=true`. Os testes de serviço usam dependências simuladas; os testes HTTP usam MockMvc. Eles não comprovam gravação, locks ou triggers no Oracle real. A persistência e a leitura dos objetos no ambiente Oracle/OCI precisam de homologação própria.
