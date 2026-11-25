# Account Service

Projeto da disciplina de Verificação e Validação de Software.

O `account-service` é uma API REST para gerenciamento de contas bancárias. A aplicação permite cadastrar e autenticar usuários, criar e consultar contas, realizar depósitos, saques e transferências, bloquear ou desbloquear contas e consultar registros de auditoria.

O acesso aos endpoints protegidos utiliza tokens JWT e os perfis `CUSTOMER`, `ADMIN` e `AUDITOR`.

## Documentação do projeto

O [plano de teste](docs/plano-de-teste.md) reúne a ficha técnica, os comandos de verificação, os cenários e os resultados.

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
