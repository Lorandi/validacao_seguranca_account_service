# Evidência dos Testes de Componente

Este documento acompanha progressivamente os testes de componente do Account Service. Em 20/11/2026, os dois primeiros lotes revisados cobrem autenticação e autorização.

## 1. Lote revisado: `AuthResourceTest`

O teste usa `@QuarkusTest` e RestAssured para executar em conjunto:

- `AuthResource`;
- `AuthService`;
- `UserRepository`;
- Hibernate ORM com Panache;
- banco H2 em memória;
- Bean Validation;
- assinatura JWT com as chaves exclusivas de `src/test/resources`.

Não são utilizados Mockito, `@InjectMock` ou servidor externo.

## 2. Cenários

| Área | Cenários |
| --- | --- |
| Cadastro válido | HTTP 201, username normalizado, perfil `CUSTOMER`, ID positivo, persistência no H2 e hash BCrypt sem exposição da senha |
| Cadastro duplicado | HTTP 400, mensagem controlada, `errorId` e ausência de nova persistência |
| Validação do cadastro | Username ausente, em branco, abaixo e acima do limite; password ausente, em branco, abaixo e acima do limite |
| Login válido | HTTP 200, JWT com três segmentos, tipo `Bearer` e duração de 3600 segundos |
| Credenciais inválidas | Usuário inexistente e senha incorreta retornando HTTP 401 sem token |
| Validação do login | Username e password ausentes ou em branco retornando HTTP 400 |

O teste possui 7 métodos e 17 execuções, considerando os casos parametrizados.

## 3. Isolamento

O usuário `component_auth_user` é removido antes e depois de cada teste em transação própria. Os usuários de demonstração não são excluídos nem modificados, e os testes não dependem de ordem.

## 4. Lote revisado: `AuthorizationTest`

O teste usa tokens obtidos pelo login real e valida três fronteiras de acesso:

- token ausente ou malformado retorna HTTP 401;
- token válido sem o perfil necessário retorna HTTP 403;
- token válido com o perfil correto retorna HTTP 200.

Foram utilizados apenas endpoints GET, sem alteração do banco:

- CUSTOMER: `/api/v1/accounts/owner/`;
- ADMIN: `/api/v1/accounts`;
- AUDITOR: `/api/v1/audit`.

A matriz cobre os seis cruzamentos de perfis incorretos, além dos três endpoints sem token, com token malformado e com o perfil correto. O teste possui 5 métodos e 16 execuções.

## 5. Comandos e resultados

Execução isolada:

```powershell
.\mvnw.cmd "-Dtest=AuthResourceTest" test
.\mvnw.cmd "-Dtest=AuthorizationTest" test
```

Resultado dos lotes isolados:

- `AuthResourceTest`: 17 execuções;
- `AuthorizationTest`: 16 execuções;
- nenhuma falha, erro ou teste ignorado;
- `BUILD SUCCESS`.

Build completo com JDK 25:

```powershell
.\mvnw.cmd clean verify "-Dcoverage.check.skip=false"
```

Resultado:

- 114 execuções no total;
- 0 falhas, 0 erros e 0 ignorados;
- PMD sem violações;
- limites JaCoCo atendidos;
- `BUILD SUCCESS`.

## 6. Contribuição para cobertura

Após o lote:

- cobertura geral de linhas: 281/356, ou 78,9%;
- cobertura de desvios: 40/44, ou 90,9%;
- `AuthResource`: 4/4 linhas;
- `UserRepository`: 2/2 linhas;
- `User`: 11/11 linhas;
- `AccountAdminResource`: 2/18 linhas;
- `AccountAuditResource`: 2/5 linhas;
- `AccountCustomerResource`: 3/50 linhas.

## 7. Observações

No encerramento do Quarkus, JDKs 24 ou superiores exibem `IllegalAccessError` em uma thread do `JBoss Threads` e solicitam `--add-opens java.base/java.lang=ALL-UNNAMED`. O comportamento foi reproduzido com JDK 25 e JDK 26. A exceção ocorre depois das assertions, não altera a contagem dos testes e não muda o resultado `BUILD SUCCESS`, mas deverá ser tratada separadamente para limpar a saída final.

As verificações de corpo das respostas 401 e 403 procuram marcadores de dados protegidos. Essa checagem é complementar ao contrato principal de status HTTP e poderá ser ampliada nos testes opcionais de segurança.

## 8. Escopo restante

Ainda faltam os testes de componente das operações de cliente, administração e auditoria. Por isso, a etapa de testes de componente permanece em andamento no plano de teste.
