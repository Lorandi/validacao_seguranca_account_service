# Account Service

Projeto da disciplina de Verificação e Validação de Software.

O `account-service` é uma API REST para gerenciamento de contas bancárias. A aplicação permite cadastrar e autenticar usuários, criar e consultar contas, realizar depósitos, saques e transferências, bloquear ou desbloquear contas e consultar registros de auditoria.

O acesso aos endpoints protegidos utiliza tokens JWT e os perfis `CUSTOMER`, `ADMIN` e `AUDITOR`.

## Documentação do projeto

- [Plano de teste](docs/plano-de-teste.md)
- [Análise estática](docs/analise-estatica.md)
- [Evidência dos testes unitários](docs/evidencias/testes-unitarios.md)
- [Evidência dos testes de componente](docs/evidencias/testes-componente.md)
- [Evidência dos testes de sistema](docs/evidencias/testes-sistema.md)
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
.\mvnw.cmd quarkus:dev
```

A aplicação será iniciada em:

```text
http://localhost:8080
```

O Swagger UI estará disponível em:

```text
http://localhost:8080/swagger/
```

## Verificação e qualidade

Requer JDK 25. Comandos para o Windows (PowerShell), na pasta do projeto:

| Comando | O que faz |
| --- | --- |
| `.\mvnw.cmd test` | Executa os testes unitários e de componente |
| `.\mvnw.cmd package` | Compila e empacota a aplicação |
| `.\mvnw.cmd pmd:pmd` | Gera o relatório do PMD, sem falhar o build |
| `.\mvnw.cmd clean verify` | Build completo: testes, PMD, testes de sistema (Failsafe) e limites de cobertura do JaCoCo |

Relatórios gerados em `target/` (não versionada): PMD em `target/reports/pmd.html`, JaCoCo em `target/jacoco-report/index.html` e Failsafe em `target/failsafe-reports/`.

Os limites de cobertura (70% de linhas no total e 80% nos serviços e em `AuthService`) são verificados no `verify`.

Os resultados e as evidências estão nos documentos listados em [Documentação do projeto](#documentação-do-projeto).

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

A API usa códigos HTTP para falhas de autenticação, autorização, validação e regras de negócio. Os erros tratados retornam JSON com `errorId`, `status` e `error`; os detalhes de falhas inesperadas ficam apenas no log, localizáveis pelo `errorId`.

| Código HTTP | Origem | Situações |
| --- | --- | --- |
| `400 Bad Request` | `IllegalArgumentException` e Bean Validation | Valor nulo, zero ou negativo; contas de origem e destino iguais; usuário não proprietário da conta; username já cadastrado; campos obrigatórios ausentes; username com menos de 3 ou senha com menos de 8 caracteres |
| `401 Unauthorized` | `InvalidCredentialsException` e SmallRye JWT | Credenciais inválidas; token ausente, inválido, expirado ou de outro emissor (`users-issuer`) |
| `403 Forbidden` | Segurança do Quarkus | Usuário autenticado sem o perfil exigido |
| `404 Not Found` | Recurso | Conta não encontrada em consultas e administração |
| `409 Conflict` | `IllegalStateException` | Conta bloqueada ou saldo insuficiente |
| `415 Unsupported Media Type` | `NotSupportedException` | Tipo de conteúdo incompatível (os POSTs de criação de conta exigem `Content-Type: application/json`, mesmo sem corpo) |
| `500 Internal Server Error` | Exceção não tratada | Falha inesperada |

Exemplo de resposta:

```json
{
  "errorId": "74b4709e-0cd0-4e04-a031-08fe454c521e",
  "status": 401,
  "error": "Credenciais inválidas."
}
```

## Persistência

O banco H2 é executado em memória. O esquema e os dados iniciais são carregados na inicialização por meio do arquivo `src/main/resources/import.sql`.
