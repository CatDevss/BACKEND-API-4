# Autenticação com Keycloak

O back-end é um **OAuth2 Resource Server**: o usuário faz login no Keycloak pelo
portal (Vue), recebe um **access token JWT** e o front envia
`Authorization: Bearer <token>` em toda chamada à API. O Spring valida a
assinatura e o emissor do token e autoriza pelo perfil.

## 1. Subir o Keycloak (desenvolvimento)

```bash
docker compose -f docker-compose.keycloak.yml up -d
```

- Console: http://localhost:8081 (admin / admin)
- Realm `georural` importado de `keycloak/realm-georural.json`
- Client público `georural-frontend` (Authorization Code + PKCE S256), com redirect liberado para `http://localhost:5173/*` e `http://localhost:3000/*`

Usuários de teste (senha `georural123`). Troque ou remova antes de publicar na nuvem:

| Usuário    | Perfil    |
|------------|-----------|
| `admin`    | ADMIN     |
| `operador` | OPERADOR  |
| `analista` | ANALISTA  |
| `gestor`   | GESTOR    |
| `auditor`  | AUDITOR   |

## 2. Configurar o back-end (`georuraldatahub/.env`)

```properties
KEYCLOAK_ENABLED=true
KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/georural
KEYCLOAK_CLIENT_ID=georural-frontend
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

`KEYCLOAK_ENABLED=false` libera todas as rotas. Use apenas localmente, sem o Keycloak.

## 3. Perfis e permissões

Os perfis são **realm roles** (ADMIN, OPERADOR, ANALISTA, GESTOR, AUDITOR), iguais ao
`usr_tipo` da tabela `usuario`. Client roles do `georural-frontend` com o mesmo nome também são aceitos.

A autorização é feita em **dois níveis**:

1. `config/SecurityConfig.java`: toda rota exige um token válido do Keycloak. Só ficam abertos
   o pré-flight do CORS, `/error` e a documentação.
2. `@PreAuthorize` em **cada endpoint** dos controllers, com as regras de
   `security/Permissions.java`. Elas **espelham a matriz do portal** (`src/access/session.ts`
   do front): cada constante corresponde a uma permissão de lá, com os mesmos perfis.

```java
@PreAuthorize(Permissions.SOURCES_CREATE)   // = can('sources.create') no front
@PostMapping
public ResponseEntity<SourceResponseDTO> create(...)
```

| Constante | Permissão no front | Perfis | Endpoints |
|---|---|---|---|
| `FILES_READ` | `files.read` | todos | GET `/arquivos/**`, `/fontes/{id}/arquivos`, `/bucket/**`, `/tratamentos/**`, `/rasters/**` |
| `SOURCES_READ` | `sources.read` | todos | GET `/fontes`, `/fontes/{id}`, `/fontes/conectores/**` |
| `SOURCES_CREATE` | `sources.create` | GESTOR | POST `/fontes` |
| `SOURCES_UPDATE` | `sources.update` | GESTOR, OPERADOR, ANALISTA | PUT `/fontes/{id}`, PATCH ativar/desativar |
| `DICTIONARIES_READ` | `dictionaries.read` | GESTOR, OPERADOR, ANALISTA, AUDITOR | GET dicionários |
| `DICTIONARIES_MANAGE` | `dictionaries.manage` | GESTOR | POST/PUT/DELETE dicionários e versões |
| `DICTIONARIES_GENERATE` | `dictionaries.generate` | GESTOR, OPERADOR | POST `gerar-arquivo`, `gerar-coleta` |
| `DATASETS_READ` | `datasets.read` | GESTOR, OPERADOR, ANALISTA, AUDITOR | GET `/conjuntos/**` |
| `DATASETS_MANAGE` | `datasets.manage` | OPERADOR | POST/PUT/DELETE `/conjuntos`, associar/desassociar arquivos |
| `INGESTION_RUN` | `ingestion.run` | OPERADOR | upload, coleta, verificação, tratamento, criação/importação/combinação de camadas |
| `MAP_READ` | `map.read` | GESTOR, OPERADOR, ANALISTA, AUDITOR | `/ambiental/**`, `/imoveis/**` (cálculo de indicadores), `/camadas/{id}/geojson`, exportação xlsx |
| `QUARANTINE_READ` | `quarantine.read` | todos | GET `/quarentenas/**` |
| `QUARANTINE_MANAGE` | `quarantine.manage` | OPERADOR | assumir, observação e resolver |
| `REPORTS_DELETE` | `reports.delete` | ADMIN, GESTOR, OPERADOR, ANALISTA | reservado para os endpoints de relatórios |
| `USERS_MANAGE` | — | ADMIN | GET `/usuarios` |
| `AUTHENTICATED` | — | qualquer usuário logado | GET `/usuarios/me` |

Respostas: **401** sem token ou com token inválido/expirado; **403** quando o perfil não tem permissão.

Ao criar um endpoint novo, coloque o `@PreAuthorize`. O teste `ControllerPermissionsTest`
falha se algum endpoint ficar sem regra.

## 4. `GET /usuarios/me`: ligação com a tabela `usuario`

Na primeira chamada, o back-end procura o usuário pelo **e-mail** do token. Se não
encontrar, cria o registro em `usuario`. Nas chamadas seguintes, atualiza nome e perfil
a partir do Keycloak. Um usuário com `usr_status = 0` recebe 403.

```json
{
  "id": 3,
  "username": "analista",
  "name": "Alice Analista",
  "email": "analista@georural.local",
  "userType": "ANALISTA",
  "roles": ["ANALISTA", "OFFLINE_ACCESS", "UMA_AUTHORIZATION", "DEFAULT-ROLES-GEORURAL"]
}
```

O front deve chamar esse endpoint logo após o login e usar o `id` como `userId`
nos endpoints que já pedem esse campo (upload, fontes, conjuntos, quarentena, tratamento).
Os perfis (`roles`/`userType`) servem para montar o menu.

## 5. Front-end (Vue + Axios)

No repositório `frontend-api-4`, a integração fica em `src/access/keycloak.ts`:

- usa `keycloak-js` com PKCE (S256) e `check-sso`, checando a sessão em um iframe
  invisível por meio de `public/silent-check-sso.html`;
- o botão **Entrar** da `LoginView` abre a tela de login do Keycloak;
- um interceptor do Axios renova o token e envia `Authorization: Bearer <token>`;
- depois do login, `GET /usuarios/me` alimenta o `setSessionUser`. Assim, menu, rotas, `can(...)` e
  `currentUserId` continuam funcionando como antes;
- o botão **Sair** encerra a sessão no Keycloak.

Variáveis do `.env` do front:

```properties
VITE_KEYCLOAK_ENABLED=true
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=georural
VITE_KEYCLOAK_CLIENT_ID=georural-frontend
```

Com `VITE_KEYCLOAK_ENABLED=false` e `KEYCLOAK_ENABLED=false` no backend, volta a prévia de
cargo de desenvolvimento, sem login.

## Backend no Dev Container (VS Code)

Se o backend roda no Dev Container (o terminal mostra `vscode ➜ /workspaces/...`), `localhost` dentro dele é o
próprio container, não a sua máquina. O login funciona no navegador, mas o backend responde
`Connection refused` ao buscar as chaves do Keycloak. Informe, no `georuraldatahub/.env`, um endereço da máquina
que o container alcance:

```properties
# Windows/Mac (Docker Desktop)
KEYCLOAK_JWK_SET_URI=http://host.docker.internal:8081/realms/georural/protocol/openid-connect/certs
# Linux
# KEYCLOAK_JWK_SET_URI=http://172.17.0.1:8081/realms/georural/protocol/openid-connect/certs
```

Teste antes com `curl -s http://host.docker.internal:8081/realms/georural` no terminal do container. Não mude o
`KEYCLOAK_ISSUER_URI`: ele continua `http://localhost:8081/realms/georural`, que é o endereço gravado no token.

## 6. Testar via linha de comando

```bash
TOKEN=$(curl -s -d grant_type=password -d client_id=georural-frontend \
  -d username=analista -d password=georural123 \
  http://localhost:8081/realms/georural/protocol/openid-connect/token | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/usuarios/me
```

O fluxo *password* (Direct Access Grants) só existe para facilitar testes. Desative-o
no client antes de publicar em produção.

## 7. Nuvem

- Rode o Keycloak em modo `start` (não `start-dev`), com banco próprio e HTTPS (`KC_HOSTNAME=https://auth.seu-dominio`).
- Ajuste `KEYCLOAK_ISSUER_URI` para a URL pública do realm. Se o back-end alcança o Keycloak por um endereço interno, informe também `KEYCLOAK_JWK_SET_URI`.
- Atualize redirect URIs, web origins e `CORS_ALLOWED_ORIGINS` com o domínio do portal.
- Troque a senha do admin e remova os usuários de teste.
