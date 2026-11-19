# Account Service

Projeto da disciplina de Verificação e Validação de Software.

O `account-service` é uma API REST para gerenciamento de contas bancárias. A aplicação permite cadastrar e autenticar usuários, criar e consultar contas, realizar depósitos, saques e transferências, bloquear ou desbloquear contas e consultar registros de auditoria.

O acesso aos endpoints protegidos utiliza tokens JWT e os perfis `CUSTOMER`, `ADMIN` e `AUDITOR`.

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

## Persistência

O banco H2 é executado em memória. O esquema e os dados iniciais são carregados na inicialização por meio do arquivo `src/main/resources/import.sql`.

Essa configuração é adequada para desenvolvimento e demonstração do projeto.
