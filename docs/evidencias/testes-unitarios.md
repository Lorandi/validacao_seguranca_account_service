# Evidência dos Testes Unitários

Testes unitários do Account Service, validados em 20/11/2026.

## 1. Escopo

Classes isoladas com JUnit 5 e Mockito, sem Quarkus, servidor HTTP ou H2. O BCrypt é executado de forma real nos testes de autenticação, por fazer parte do comportamento observável do serviço.

## 2. Execução

```powershell
.\mvnw.cmd clean test "-Dtest=AccountStatusEnumTest,ApiExceptionMapperTest,AccountAdminServiceTest,AccountAuditServiceTest,AccountCustomerServiceTest,AuthServiceTest"
```

| Classe | Execuções | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| `AccountStatusEnumTest` | 7 | 0 | 0 | 0 |
| `ApiExceptionMapperTest` | 13 | 0 | 0 | 0 |
| `AccountAdminServiceTest` | 8 | 0 | 0 | 0 |
| `AccountAuditServiceTest` | 9 | 0 | 0 | 0 |
| `AccountCustomerServiceTest` | 40 | 0 | 0 | 0 |
| `AuthServiceTest` | 5 | 0 | 0 | 0 |
| **Total** | **82** | **0** | **0** | **0** |

Resultado: `BUILD SUCCESS`. O build completo (`.\mvnw.cmd clean verify`) também executa estes testes; veja [cobertura.md](cobertura.md).

## 3. Comportamentos verificados

- estados de conta e alternância entre `ACTIVE` e `BLOCKED`;
- associação de exceções aos códigos HTTP (incluindo `NotSupportedException` → 415) e proteção de mensagens internas;
- consulta administrativa e bloqueio ou desbloqueio de contas;
- registro e consulta de auditoria, incluindo falha do repositório e token sem `userId`;
- criação e consulta de conta, depósito, saque e transferência;
- valores nulos, zero, negativos e limites de saldo;
- propriedade da conta, status bloqueado e preservação dos saldos em falhas;
- cadastro com username normalizado e senha BCrypt, duplicidade de username, credenciais inválidas e claims do JWT.

## 4. Observações

Relatórios detalhados em `target/surefire-reports/` (não versionada). O aviso do Mockito sobre o carregamento dinâmico do agente Byte Buddy não causa falhas.
