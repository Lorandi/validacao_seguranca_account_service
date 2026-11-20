# Evidência dos Testes Unitários

Este documento registra a execução revisada dos testes unitários do Account Service em 20/11/2026. A seleção foi feita explicitamente por classe para não misturar testes de componente ainda em desenvolvimento.

## 1. Escopo

Os testes exercitam classes isoladas com JUnit 5 e Mockito, sem iniciar Quarkus, servidor HTTP ou banco H2. O BCrypt é executado de forma real nos testes de autenticação por fazer parte do comportamento observável do serviço.

Classes executadas:

- `AccountStatusEnumTest`;
- `ApiExceptionMapperTest`;
- `AccountAdminServiceTest`;
- `AccountAuditServiceTest`;
- `AccountCustomerServiceTest`;
- `AuthServiceTest`.

## 2. Comando reproduzível

```powershell
.\mvnw.cmd clean test "-Dtest=AccountStatusEnumTest,ApiExceptionMapperTest,AccountAdminServiceTest,AccountAuditServiceTest,AccountCustomerServiceTest,AuthServiceTest"
```

## 3. Resultado

| Classe | Execuções | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| `AccountStatusEnumTest` | 7 | 0 | 0 | 0 |
| `ApiExceptionMapperTest` | 12 | 0 | 0 | 0 |
| `AccountAdminServiceTest` | 8 | 0 | 0 | 0 |
| `AccountAuditServiceTest` | 9 | 0 | 0 | 0 |
| `AccountCustomerServiceTest` | 40 | 0 | 0 | 0 |
| `AuthServiceTest` | 5 | 0 | 0 | 0 |
| **Total** | **81** | **0** | **0** | **0** |

Resultado final: `BUILD SUCCESS`.

## 4. Comportamentos verificados

- estados de conta e alternância entre `ACTIVE` e `BLOCKED`;
- associação de exceções aos códigos HTTP e proteção de mensagens internas;
- consulta administrativa e bloqueio ou desbloqueio de contas;
- criação e consulta de registros de auditoria, incluindo falha do repositório;
- criação e consulta de conta, depósito, saque e transferência;
- valores nulos, zero, negativos e limites de saldo;
- propriedade da conta, status bloqueado e preservação dos saldos em falhas;
- auditoria de sucessos e falhas, inclusive token sem `userId`;
- cadastro com username normalizado e senha BCrypt;
- duplicidade de username, credenciais inválidas e emissão do JWT com os claims esperados.

## 5. Verificação integrada

Também foi executado o build até a fase `verify`, mantendo a mesma seleção unitária e ativando os limites JaCoCo:

```powershell
.\mvnw.cmd verify "-Dcoverage.check.skip=false" "-Dtest=AccountStatusEnumTest,ApiExceptionMapperTest,AccountAdminServiceTest,AccountAuditServiceTest,AccountCustomerServiceTest,AuthServiceTest"
```

Resultado:

- 81 execuções, sem falhas, erros ou testes ignorados;
- PMD sem violações;
- limites de cobertura atendidos;
- `BUILD SUCCESS`.

## 6. Relatórios transitórios

O Maven Surefire gera os relatórios detalhados em `target/surefire-reports/`. Essa pasta não é versionada e pode ser recriada pelos comandos acima.

O aviso do Mockito sobre carregamento dinâmico do agente Byte Buddy no JDK atual não causou falhas. Ele indica uma necessidade futura de configuração quando o JDK deixar de permitir esse carregamento por padrão.

