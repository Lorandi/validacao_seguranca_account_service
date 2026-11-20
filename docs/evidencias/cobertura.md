# Evidência de Cobertura Automatizada

Cobertura medida em 20/11/2026 com os testes unitários e de componente (160 execuções). Os 6 testes de sistema não entram nestes números, pois o processo externo da aplicação empacotada não é instrumentado pelo JaCoCo.

## 1. Geração

```powershell
.\mvnw.cmd clean verify
```

O relatório é gerado pela extensão `quarkus-jacoco` em `target/jacoco-report/index.html` (dados em `target/jacoco-report/jacoco.csv`), pasta não versionada.

## 2. Resultado total

| Métrica | Coberto | Total | Percentual |
| --- | ---: | ---: | ---: |
| Linhas | 351 | 352 | **99,7%** |
| Desvios | 43 | 43 | **100%** |

Evolução: 73,0% com os testes unitários, 82,6% com os primeiros lotes de componente, 93,3% com cliente e HTTP 415, 97,8% com administração, 98,6% com auditoria e **99,7%** após remover código não utilizado (dois métodos do repositório de auditoria e o `prePersist` da entidade `AccountAudit`, já cobertos pelo serviço).

## 3. Classes centrais

| Classe | Linhas | Desvios |
| --- | ---: | ---: |
| `AccountCustomerService` | 108/108 | 22/22 |
| `AccountAdminService` | 17/17 | — |
| `AccountAuditService` | 28/28 | — |
| `AuthService` | 19/19 | 4/4 |
| `ApiExceptionMapper` | 22/22 | 11/11 |
| `AccountStatusEnum` | 11/11 | 6/6 |
| `AuthResource`, `AccountAdminResource`, `AccountAuditResource`, `AccountCustomerResource` | 4/4, 18/18, 5/5 e 50/50 | — |
| `User` e `UserRepository` | 11/11 e 2/2 | — |

## 4. Limites verificados no build

O `pom.xml` exige, na fase `verify`, ao menos 70% de cobertura de linhas no total e 80% nas classes de serviço e em `AuthService`. O JaCoCo informou `All coverage checks have been met`.

## 5. Lacunas

Uma linha não coberta: `OpenApiConfig` (construtor implícito de classe só com anotações). Não foram criados testes artificiais para elevar o percentual. Cobertura de 100% das linhas nos resources não implica cobertura de todas as combinações de entrada.
