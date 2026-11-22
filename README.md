<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<p align="center">
  <img src="https://raw.githubusercontent.com/CatDevss/API-4/main/Docs/assets/Banner_Visiona.jpg" alt="Banner" width="900">
</p>

<h1 align="center">⌞ Manual de Instalação · Backend ⌝</h1>

<p align="center">
  Guia de configuração, instalação de dependências, execução e validação da API e do motor de processamento do
  <strong>GeoRural DataHub</strong>, desenvolvido em parceria com a <strong>Visiona Tecnologia Espacial</strong>.
</p>

<p align="center">
  <sub>Aprendizagem por Projetos Integrados · 2026-2 &nbsp;|&nbsp; 4º Semestre - Banco de Dados &nbsp;|&nbsp; FATEC São José dos Campos</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/modulo-backend_api-FE5000?style=for-the-badge&logoColor=white" alt="módulo" />
  <img src="https://img.shields.io/badge/java-21_%7C_spring_boot-2D2D2D?style=for-the-badge&logo=openjdk&logoColor=FFA300" alt="java" />
</p>

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/list-details.svg?color=%23FFFFFF" width="20" /> Sumário</h2>

<br>

<div align="center">

<table align="center" width="100%">
  <tr>
    <td width="50%" valign="top">
      <p>
        <img src="https://api.iconify.design/tabler/tool.svg?color=%23FFFFFF" width="16" />
        <a href="#-pré-requisitos">Pré-requisitos</a>
      </p>
      <p>
        <img src="https://api.iconify.design/tabler/git-branch.svg?color=%23FFFFFF" width="16" />
        <a href="#-clonagem-e-estrutura">Clonagem e Estrutura</a>
      </p>
      <p>
        <img src="https://api.iconify.design/tabler/settings.svg?color=%23FFFFFF" width="16" />
        <a href="#-variáveis-de-ambiente">Variáveis de Ambiente</a>
      </p>
      <p>
        <img src="https://api.iconify.design/tabler/package.svg?color=%23FFFFFF" width="16" />
        <a href="#-instalação-de-dependências">Instalação de Dependências</a>
      </p>
    </td>
    <td width="50%" valign="top">
      <p>
        <img src="https://api.iconify.design/tabler/player-play.svg?color=%23FFFFFF" width="16" />
        <a href="#-execução-local-e-build">Execução Local e Build</a>
      </p>
      <p>
        <img src="https://api.iconify.design/tabler/terminal-2.svg?color=%23FFFFFF" width="16" />
        <a href="#-scripts-e-qualidade-de-código">Scripts e Qualidade de Código</a>
      </p>
      <p>
        <img src="https://api.iconify.design/tabler/circle-check.svg?color=%23FFFFFF" width="16" />
        <a href="#-validação-do-ambiente">Validação do Ambiente</a>
      </p>
      <p>
        <img src="https://api.iconify.design/tabler/alert-triangle.svg?color=%23FFFFFF" width="16" />
        <a href="#-problemas-comuns">Problemas Comuns</a>
      </p>
    </td>
  </tr>
</table>

</div>

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/tool.svg?color=%23FFFFFF" width="20" /> Pré-requisitos</h2>

<br>

<p align="center">
  Para rodar o backend localmente sem conflitos de runtime ou dependências, prepare o ambiente com os itens abaixo.
</p>

<br>

<div align="center">

<table>
  <tr>
    <td align="center" width="50%" valign="top">
      <img width="60" height="3" src="https://placehold.co/60x3/FE5000/FE5000.png"/>
      <br><strong>AMBIENTE E FERRAMENTAS</strong><br><br>
      <sub>
      <img src="https://api.iconify.design/tabler/brand-java.svg?color=%23FFFFFF" width="16" /> <strong>Java 21</strong> - JDK 21 configurado no ambiente (conforme <code>java.version</code> no <code>pom.xml</code>)<br><br>
      <img src="https://api.iconify.design/tabler/package.svg?color=%23FFFFFF" width="16" /> <strong>Apache Maven</strong> - gerenciador de build e dependências (ou wrapper <code>./mvnw</code> / <code>mvnw.cmd</code>)<br><br>
      <img src="https://api.iconify.design/tabler/git-branch.svg?color=%23FFFFFF" width="16" /> <strong>Git</strong> - para clonar o repositório e acompanhar branches
      </sub>
    </td>
    <td align="center" width="50%" valign="top">
      <img width="60" height="3" src="https://placehold.co/60x3/FFA300/FFA300.png"/>
      <br><strong>SERVIÇOS E INTEGRAÇÃO</strong><br><br>
      <sub>
      <img src="https://api.iconify.design/tabler/database.svg?color=%23FFFFFF" width="16" /> <strong>Oracle Database</strong> - instância acessível via JDBC (<code>ojdbc17</code> e <code>oraclepki</code>)<br><br>
      <img src="https://api.iconify.design/tabler/cloud.svg?color=%23FFFFFF" width="16" /> <strong>Oracle Cloud Object Storage (OCI)</strong> - namespace, bucket e credenciais/configuração OCI ativos<br><br>
      <img src="https://api.iconify.design/tabler/container.svg?color=%23FFFFFF" width="16" /> <strong>Docker (opcional para testes)</strong> - necessário para suítes com Testcontainers (Oracle Free)
      </sub>
    </td>
  </tr>
</table>

</div>

<br>

> *Confira as versões instaladas no terminal antes de começar: `java -version`, `mvn -version` e `git --version`.*

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/git-branch.svg?color=%23FFFFFF" width="20" /> Clonagem e Estrutura</h2>

<br>

<p align="center">
  Obtenha o código-fonte do repositório de backend e entre no diretório do projeto.
</p>

<br>

```bash
git clone [https://github.com/CatDevss/BACKEND-API-4.git](https://github.com/CatDevss/BACKEND-API-4.git)
cd BACKEND-API-4
```

<br>

<div align="center">

| Arquivo / Pasta | Função no Projeto |
| --- | --- |
| `pom.xml` | Define o Spring Boot `4.1.1`, Java `21`, dependências (`GeoTools`, `Hibernate Spatial`, `JTS`, `OCI SDK`, `ojdbc17`, `Testcontainers`) e plugins Maven |
| `src/main/resources/application.properties` | Configura datasource Oracle, Object Storage OCI, limites de multipart (`1GB`), coleta agendada e processamento |
| `.env` / `.env.example` | Armazena credenciais e variáveis de ambiente carregadas automaticamente via `spring.config.import=optional:file:.env[.properties]` |
| `src/main/java/` | Contém controllers REST, serviços de ingestão/validação/cálculo geoespacial, segurança, entidades JPA e repositórios |
| `.devcontainer/devcontainer.json` | Define o ambiente de desenvolvimento em contêiner com Java 21, Maven e encaminhamento da porta `8080` |

</div>

<br>

> *O `pom.xml` também inclui o repositório Maven da OSGeo (`https://repo.osgeo.org/repository/release/`) para resolução dos artefatos do GeoTools.*

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/settings.svg?color=%23FFFFFF" width="20" /> Variáveis de Ambiente</h2>

<br>

<p align="center">
  A aplicação importa automaticamente variáveis de um arquivo <code>.env</code> na raiz do projeto quando presente.
</p>

<br>

```env
CHAVE_URL_DATASOURCE=jdbc:oracle:thin:@geodatalake_high?TNS_ADMIN=./wallet
CHAVE_USERNAME_DATABASE=senha_databae
CHAVE_DATABASE_PASSWORD=sua_senha_aqui

OCI_NAMESPACE=seu_namespace_oci
OCI_BUCKET_NAME=georural-datalake
OCI_COMPARTMENT_ID=ocid1.bucket.oc1...

# Opcionais
OCI_CONFIG_FILE=
OCI_CONFIG_PROFILE=DEFAULT
PROCESSING_PERSISTENCE_ENABLED=false
```

<br>

<div align="center">

| Variável | Obrigatória | Descrição |
| --- | :---: | --- |
| `CHAVE_URL_DATASOURCE` | Sim | String JDBC de conexão com o Oracle Database (ex.: usando Wallet em `TNS_ADMIN`) |
| `CHAVE_USERNAME_DATABASE` | Sim | Usuário do banco de dados Oracle |
| `CHAVE_DATABASE_PASSWORD` | Sim | Senha do usuário do banco de dados Oracle |
| `OCI_NAMESPACE` | Sim | Namespace da conta no Oracle Cloud Object Storage |
| `OCI_BUCKET_NAME` | Sim | Nome do bucket utilizado para armazenamento dos dados |
| `OCI_COMPARTMENT_ID` | Sim | OCID do compartimento/recurso na OCI |
| `OCI_CONFIG_FILE` | Não | Caminho para o arquivo de configuração OCI (se vazio, busca em `.oci/config` no projeto ou `~/.oci/config`) |
| `OCI_CONFIG_PROFILE` | Não | Perfil utilizado no arquivo de configuração OCI (padrão: `DEFAULT`) |
| `PROCESSING_PERSISTENCE_ENABLED` | Não | Habilita persistência de `execucao_tratamento` e `raster` (padrão: `false`) |

</div>

<br>

> *Use o `.env.example` como base para criar o seu `.env` e nunca versione senhas ou chaves reais no repositório.*

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/package.svg?color=%23FFFFFF" width="20" /> Instalação de Dependências</h2>

<br>

<p align="center">
  Baixe e resolva todas as dependências do Maven (incluindo artefatos GeoTools, JTS, Hibernate Spatial, Oracle JDBC e OCI SDK).
</p>

<br>

```bash
mvn dependency:resolve
```

<br>

> *Caso utilize o Maven Wrapper incluído no repositório, substitua `mvn` por `./mvnw` (Linux/macOS) ou `mvnw.cmd` (Windows).*

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/player-play.svg?color=%23FFFFFF" width="20" /> Execução Local e Build</h2>

<br>

<p align="center">
  Escolha o fluxo de execução conforme a etapa do desenvolvimento, testes ou empacotamento.
</p>

<br>

<div align="center">

<table>
  <tr>
    <td align="center" width="50%" valign="top">
      <img width="60" height="3" src="https://placehold.co/60x3/FE5000/FE5000.png"/>
      <br><strong>EXECUÇÃO LOCAL (SPRING BOOT)</strong><br><br>
      <sub>
      <img src="https://api.iconify.design/tabler/player-play.svg?color=%23FFFFFF" width="16" /> <strong><code>mvn spring-boot:run</code></strong> - sobe a aplicação Spring Boot com as configurações do <code>application.properties</code> e <code>.env</code><br><br>
      <img src="https://api.iconify.design/tabler/database.svg?color=%23FFFFFF" width="16" /> <strong>Validação de schema</strong> - <code>spring.jpa.hibernate.ddl-auto=validate</code> exige que as tabelas já existam no Oracle<br><br>
      <img src="https://api.iconify.design/tabler/clock.svg?color=%23FFFFFF" width="16" /> <strong>Coleta agendada</strong> - desativada por padrão (<code>collection.scheduling.enabled=false</code>)
      </sub>
    </td>
    <td align="center" width="50%" valign="top">
      <img width="60" height="3" src="https://placehold.co/60x3/FFA300/FFA300.png"/>
      <br><strong>BUILD E EMPACOTAMENTO</strong><br><br>
      <sub>
      <img src="https://api.iconify.design/tabler/hammer.svg?color=%23FFFFFF" width="16" /> <strong><code>mvn clean package</code></strong> - compila o código Java 21, executa testes e gera o artefato em <code>target/</code><br><br>
      <img src="https://api.iconify.design/tabler/bolt.svg?color=%23FFFFFF" width="16" /> <strong><code>mvn test-compile</code></strong> - valida compilação do código principal e das classes de teste<br><br>
      <img src="https://api.iconify.design/tabler/terminal.svg?color=%23FFFFFF" width="16" /> <strong><code>java -jar target/*.jar</code></strong> - executa o pacote gerado após o build
      </sub>
    </td>
  </tr>
</table>

</div>

<br>

```bash
# Executar a aplicação localmente
mvn spring-boot:run

# Compilar e gerar o pacote
mvn clean package
```

<br>

> *Se a string JDBC apontar para uma Wallet local (ex.: `TNS_ADMIN=./wallet`), certifique-se de que a pasta da Wallet exista no caminho esperado.*

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/terminal-2.svg?color=%23FFFFFF" width="20" /> Scripts e Qualidade de Código</h2>

<br>

<p align="center">
  Comandos Maven úteis para compilação, testes automatizados e verificação do build.
</p>

<br>

<div align="center">

| Comando | Finalidade |
| --- | --- |
| `mvn test` | Executa a suíte de testes unitários e de integração com Maven Surefire |
| `mvn clean test` | Limpa artefatos anteriores e roda os testes do zero |
| `mvn compile` | Compila as classes principais com Java 21 e Lombok |
| `mvn package -DskipTests` | Gera o pacote sem executar a suíte de testes |

</div>

<br>

```bash
mvn test
mvn clean package
```

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/circle-check.svg?color=%23FFFFFF" width="20" /> Validação do Ambiente</h2>

<br>

<p align="center">
  Checklist rápido para confirmar que a instalação do backend está funcional antes de iniciar novas tarefas ou demonstrações.
</p>

<br>

- [ ] **Versão do Java** - `java -version` apontando para o **JDK 21**;
- [ ] **Variáveis configuradas** - `.env` preenchido com `CHAVE_URL_DATASOURCE`, `CHAVE_USERNAME_DATABASE`, `CHAVE_DATABASE_PASSWORD` e parâmetros `OCI_*`;
- [ ] **Banco e Object Storage acessíveis** - conexão com o Oracle Database funcional e credenciais/perfil OCI válidos;
- [ ] **Build e testes** - `mvn test` e `mvn clean package` concluídos sem falhas.

<br>

> *Quando `PROCESSING_PERSISTENCE_ENABLED=true`, garanta que as estruturas de `execucao_tratamento` e `raster` estejam criadas no banco de dados.*

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<h2 align="center"><img src="https://api.iconify.design/tabler/alert-triangle.svg?color=%23FFFFFF" width="20" /> Problemas Comuns</h2>

<br>

<div align="center">

| Sintoma | Causa Provável | O que Verificar |
| --- | --- | --- |
| Falha na resolução do GeoTools | Sem acesso ao repositório Maven da OSGeo ou cache corrompido | Verifique a conectividade com `https://repo.osgeo.org/repository/release/` e rode novamente com `-U` |
| Erro de conexão com o Oracle Database | Credenciais inválidas, URL incorreta ou `TNS_ADMIN` apontando para pasta inexistente | Confira `CHAVE_URL_DATASOURCE`, `CHAVE_USERNAME_DATABASE`, `CHAVE_DATABASE_PASSWORD` e o caminho da Wallet |
| Falha de validação do Hibernate na inicialização | Schema no banco divergente das entidades (`ddl-auto=validate`) | Confirme se o DDL/schema esperado foi aplicado no Oracle Database |
| Falha ao acessar o Object Storage OCI | Variáveis `OCI_*` ausentes ou arquivo/perfil OCI não encontrado | Verifique `OCI_NAMESPACE`, `OCI_BUCKET_NAME`, `OCI_COMPARTMENT_ID` e `OCI_CONFIG_FILE` / `OCI_CONFIG_PROFILE` |

</div>

<br>

<img width="100%" src="https://capsule-render.vercel.app/api?type=rect&color=0:FE5000,100:FFA300&height=4" />

<br>

<p align="center">
  <sub>Projeto GeoRural DataHub, desenvolvido para a <strong>Visiona Tecnologia Espacial</strong> - Governança e Rastreabilidade de Dados Geoespaciais.</sub>
</p>
