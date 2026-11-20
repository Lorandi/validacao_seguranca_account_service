# Account Service

Projeto da disciplina de Verificação e Validação de Software.

O `account-service` é uma API REST para gerenciamento de contas bancárias. A aplicação permite cadastrar e autenticar usuários, criar e consultar contas, realizar depósitos, saques e transferências, bloquear ou desbloquear contas e consultar registros de auditoria.

O acesso aos endpoints protegidos utiliza tokens JWT e os perfis `CUSTOMER`, `ADMIN` e `AUDITOR`.

## Documentação do projeto

- [Plano de teste](docs/plano-de-teste.md)
- [Análise estática](docs/analise-estatica.md)
- [Evidência dos testes unitários](docs/evidencias/testes-unitarios.md)
- [Evidência dos testes de componente](docs/evidencias/testes-componente.md)
- [Evidência de cobertura](docs/evidencias/cobertura.md)

## Tecnologias

- Java 25
- Quarkus 3.29.2
- Maven
- Hibernate ORM com Panache
- Banco de dados H2 em memória
- SmallRye JWT
- SmallRye OpenAPI e Swagger UI
- BCrypt para armazenamento seguro de senhas

## Executando o projeto

No terminal, dentro da pasta do projeto, execute:

```powershell
mvn quarkus:dev
```

A aplicação será iniciada em:

```text
http://localhost:8080
```

O Swagger UI estará disponível em:

```text
http://localhost:8080/swagger/
```

Para compilar e validar o projeto:

```powershell
mvn test
mvn package
```

## Verificação e qualidade

Comandos para o Windows (PowerShell), executados na pasta do projeto com o Maven Wrapper:

| Comando | O que faz |
| --- | --- |
| `.\mvnw.cmd pmd:pmd` | Executa a análise estática do PMD e gera o relatório, sem falhar o build |
| `.\mvnw.cmd pmd:check` | Falha o build se o PMD encontrar alguma violação |
| `.\mvnw.cmd test` | Executa os testes unitários e de componente |
| `.\mvnw.cmd verify` | Executa testes, PMD, relatório do JaCoCo e verificações finais |

Relatórios gerados (em `target/`, pasta não versionada):

- PMD: `target/pmd.xml` e `target/reports/pmd.html`;
- JaCoCo: `target/jacoco-report/index.html`, gerado pela extensão `quarkus-jacoco` quando há testes `@QuarkusTest`. Os dados brutos ficam em `target/jacoco-quarkus.exec`, que recebe tanto a cobertura dos testes `@QuarkusTest` quanto a dos testes JUnit/Mockito que não sobem o Quarkus (agente do `jacoco-maven-plugin`), seguindo o arranjo apresentado na página de JaCoCo do material da disciplina.

Os limites de cobertura (70% de linhas no total e 80% nos serviços e em `AuthService`) estão configurados no `pom.xml`. A propriedade `coverage.check.skip` permanece `true` por padrão. Para validar os limites, execute:

```powershell
.\mvnw.cmd clean verify "-Dcoverage.check.skip=false"
```

Na execução validada em 20/11/2026 com JDK 25, os 160 testes passaram, o PMD apresentou zero violações e os limites JaCoCo foram atendidos. A cobertura foi de 354/359 linhas (98,6%) e 45/47 desvios (95,7%). Os testes de componente foram concluídos; os testes de sistema ainda estão pendentes.

Os testes usam um par de chaves JWT próprio, em `src/test/resources`, e não as chaves de `src/main/resources`.

Detalhes da análise estática: [docs/analise-estatica.md](docs/analise-estatica.md). Estratégia completa de testes: [docs/plano-de-teste.md](docs/plano-de-teste.md).

## Autenticação

O login é realizado pelo endpoint:

```http
POST /api/v1/auth/login
```

Exemplo de requisição:

```json
{
  "username": "customer",
  "password": "customer123"
}
```

A resposta contém um token JWT:

```json
{
  "token": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

No Swagger, clique em **Authorize** e cole somente o valor do campo `token`. O Swagger adiciona o prefixo `Bearer` automaticamente.

## Usuários de demonstração

| Usuário | Senha | Perfil | Conta | Status da conta |
| --- | --- | --- | --- | --- |
| `customer` | `customer123` | `CUSTOMER` | `ACC1002` | `ACTIVE` |
| `customer_active` | `active123` | `CUSTOMER` | `ACC1004` | `ACTIVE` |
| `customer_blocked` | `blocked123` | `CUSTOMER` | `ACC1005` | `BLOCKED` |
| `admin` | `admin123` | `ADMIN` | `ACC1001` | `ACTIVE` |
| `auditor` | `auditor123` | `AUDITOR` | `ACC1003` | `BLOCKED` |

Os usuários e as contas de demonstração são recriados sempre que a aplicação é reiniciada, pois o banco H2 está configurado em memória.

## Cadastro de clientes

Novos usuários são cadastrados com o perfil `CUSTOMER`:

```http
POST /api/v1/auth/register
```

```json
{
  "username": "novo_cliente",
  "password": "senha123"
}
```

## Endpoints de cliente

Exigem o perfil `CUSTOMER`.

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/api/v1/accounts` | Cria uma conta para o usuário autenticado |
| `GET` | `/api/v1/accounts/owner` | Consulta a conta do usuário autenticado |
| `PATCH` | `/api/v1/accounts/{accountId}/deposit?amount=100` | Realiza um depósito |
| `PATCH` | `/api/v1/accounts/{accountId}/withdraw?amount=100` | Realiza um saque |
| `PATCH` | `/api/v1/accounts/{accountId}/transfer` | Transfere valores entre contas |

Exemplo do corpo de uma transferência:

```json
{
  "targetAccountId": "ACC1001",
  "amount": 100.00
}
```

## Endpoints administrativos

Exigem o perfil `ADMIN`.

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `GET` | `/api/v1/accounts` | Lista todas as contas |
| `GET` | `/api/v1/accounts/{accountId}` | Consulta uma conta pelo identificador |
| `PATCH` | `/api/v1/accounts/{accountId}/account-status-toggle` | Bloqueia ou desbloqueia uma conta |

## Endpoints de auditoria

Exigem o perfil `AUDITOR`.

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `GET` | `/api/v1/audit` | Lista todos os registros de auditoria |
| `GET` | `/api/v1/audit/recent?limit=10` | Lista os registros mais recentes |
| `GET` | `/api/v1/audit/owner/{id}` | Consulta registros pelo proprietário |
| `GET` | `/api/v1/audit/account/{accountId}` | Consulta registros relacionados a uma conta |

## Regras de autorização

- Requisições sem token válido recebem `401 Unauthorized`.
- Usuários autenticados sem o perfil exigido recebem `403 Forbidden`.
- O token contém o identificador do usuário no claim `userId` e o perfil no claim `groups`.
- O emissor esperado e utilizado pelos tokens é `users-issuer`.

## Tratamento de erros

A API utiliza códigos HTTP para representar falhas de autenticação, autorização, validação e regras de negócio.

| Código HTTP | Exceção ou origem | Situações |
| --- | --- | --- |
| `400 Bad Request` | `IllegalArgumentException` | Valor nulo ou negativo, contas de origem e destino iguais, usuário não proprietário da conta ou nome de usuário já cadastrado |
| `400 Bad Request` | Bean Validation | Campos obrigatórios ausentes, nome de usuário menor que 3 caracteres ou senha menor que 8 caracteres |
| `401 Unauthorized` | `InvalidCredentialsException` | Usuário inexistente ou senha incorreta |
| `401 Unauthorized` | SmallRye JWT | Token ausente, inválido, expirado ou assinado com uma chave diferente |
| `403 Forbidden` | Segurança do Quarkus | Usuário autenticado sem o perfil exigido pelo endpoint |
| `404 Not Found` | Tratamento do recurso | Conta ou conta do proprietário não encontrada em endpoints de consulta e administração |
| `409 Conflict` | `IllegalStateException` | Conta bloqueada, saldo insuficiente ou conflito com o estado atual da conta |
| `415 Unsupported Media Type` | `NotSupportedException` | Tipo de conteúdo incompatível com o endpoint |
| `500 Internal Server Error` | Exceção não tratada | Falha inesperada durante o processamento da requisição |

### Credenciais inválidas

O login retorna `401 Unauthorized` quando o usuário não existe ou a senha está incorreta.

```json
{
  "errorId": "74b4709e-0cd0-4e04-a031-08fe454c521e",
  "status": 401,
  "error": "Credenciais inválidas."
}
```

### Dados inválidos

Operações com valor igual ou menor que zero retornam `400 Bad Request`.

```json
{
  "error": "O valor deve ser maior que zero."
}
```

Outras situações que retornam `400` incluem:

- nome de usuário já cadastrado;
- usuário tentando movimentar uma conta da qual não é proprietário;
- conta de origem igual à conta de destino;
- campos obrigatórios ausentes;
- senha com menos de 8 caracteres.

### Recurso não encontrado

Consultas de contas inexistentes retornam `404 Not Found`.

```json
{
  "error": "Conta não encontrada: ACC9999"
}
```

### Conflito com a regra de negócio

Operações incompatíveis com o estado atual da conta retornam `409 Conflict`.

Exemplo de conta bloqueada:

```json
{
  "error": "Conta bloqueada: ACC1005"
}
```

Exemplo de saldo insuficiente:

```json
{
  "error": "Saldo insuficiente."
}
```

### Token ausente ou inválido

Endpoints protegidos sem um JWT válido retornam:

```text
401 Unauthorized
```

O token pode ser recusado quando:

- não foi enviado;
- está expirado;
- possui assinatura inválida;
- utiliza um emissor diferente de `users-issuer`.

### Perfil sem permissão

Um JWT válido sem o perfil exigido retorna:

```text
403 Forbidden
```

Exemplos:

- `CUSTOMER` tentando acessar endpoints administrativos;
- `ADMIN` tentando acessar endpoints de auditoria;
- `AUDITOR` tentando realizar operações bancárias.

### Tipo de conteúdo não suportado

Os POSTs válidos de criação de conta devem enviar `Content-Type: application/json`, mesmo sem corpo. Um tipo incompatível, como `text/plain`, retorna `415 Unsupported Media Type` com mensagem controlada:

```json
{
  "errorId": "76d2044d-ecc3-41b8-9648-ea2fa90bed52",
  "status": 415,
  "error": "Tipo de conteúdo não suportado."
}
```

Esse tratamento é específico para `NotSupportedException`. Outras exceções HTTP não mapeadas explicitamente ainda podem cair no retorno genérico 500; essa limitação permanece pendente.

### Erro interno

Falhas inesperadas retornam `500 Internal Server Error` com um identificador para rastreamento:

```json
{
  "errorId": "76d2044d-ecc3-41b8-9648-ea2fa90bed52",
  "status": 500,
  "error": "Erro inesperado."
}
```

Os detalhes da falha ficam registrados apenas no log da aplicação e podem ser localizados pelo `errorId` retornado na resposta.

## Persistência

O banco H2 é executado em memória. O esquema e os dados iniciais são carregados na inicialização por meio do arquivo `src/main/resources/import.sql`.
