# Evidência dos Testes de Componente

Testes de componente do Account Service, validados em 20/11/2026.

## 1. Estratégia

Os testes usam `@QuarkusTest` e RestAssured para exercitar em conjunto recursos REST, serviços, repositórios, Hibernate ORM/Panache, Bean Validation e H2 em memória, sem mocks nem servidor externo. Os tokens vêm do login real, assinados com as chaves de `src/test/resources`.

Cada teste restaura em transação própria o estado que altera e não depende de ordem de execução (pressupõe execução sequencial).

## 2. Classes e cenários

| Classe | Execuções | Cenários |
| --- | ---: | --- |
| `AuthResourceTest` | 17 | Cadastro válido (HTTP 201, perfil `CUSTOMER`, hash BCrypt sem expor a senha), duplicado (400), validação de username e password, login válido (JWT `Bearer`, 3600 s), credenciais inválidas (401) |
| `AuthorizationTest` | 16 | Para os endpoints de `CUSTOMER`, `ADMIN` e `AUDITOR`: sem token ou token malformado (401), perfil incorreto (403, seis cruzamentos) e perfil correto (200) |
| `AccountCustomerResourceTest` | 31 | Consulta e criação de conta (inclusive duplicada e cliente sem conta, 404); depósito, saque e transferência válidos e nos limites de saldo; valor ausente, zero ou negativo; conta de terceiro, bloqueada ou inexistente; saldo insuficiente; origem igual ao destino; regressão HTTP 415. Conferem códigos HTTP, corpos, saldos e registros de auditoria diretamente no H2 |
| `AccountAdminResourceTest` | 6 | Listagem, consulta existente e inexistente, bloqueio, desbloqueio e alternância de conta inexistente; verifica o estado persistido e uma única auditoria `TOGGLE_STATUS` (`SUCCESS` ou `FAILURE`) |
| `AccountAuditResourceTest` | 8 | Listagem, recentes com limite explícito, padrão (10) e maior que o total, filtros por proprietário e por conta (como origem, destino ou ambos) e resultados vazios; confirma que os GETs não alteram os registros |
| **Total** | **78** | |

## 3. Execução

```powershell
.\mvnw.cmd clean test
```

Resultado dos 160 testes (82 unitários + 78 de componente): 0 falhas, 0 erros, 0 ignorados, `BUILD SUCCESS`. O resultado do build completo está em [testes-sistema.md](testes-sistema.md) e [cobertura.md](cobertura.md).

## 4. Observações

- No JDK 25, o encerramento do Quarkus exibe `IllegalAccessError` em uma thread do JBoss Threads (`--add-opens java.base/java.lang=ALL-UNNAMED`). Ocorre depois das assertions e não altera o resultado.
- Depósito em conta de terceiro é permitido. Conta inexistente retorna 400 e conta bloqueada, 409; nas falhas de depósito, a auditoria registra `ownerId` nulo.
- `NotSupportedException` (POST com `Content-Type` incompatível) retorna 415 com `errorId`, `status` e a mensagem "Tipo de conteúdo não suportado.", coberto por testes unitário e de componente. Outras `WebApplicationException` não mapeadas ainda podem retornar 500.
- As verificações de corpo das respostas 401 e 403 complementam o status HTTP e poderiam ser ampliadas em testes de segurança.
