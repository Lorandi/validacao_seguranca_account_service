# Evidência de Cobertura Automatizada

Este documento registra a cobertura obtida em 20/11/2026 com as seis classes de testes unitários e os testes de componente `AuthResourceTest`, `AuthorizationTest`, `AccountCustomerResourceTest`, `AccountAdminResourceTest` e `AccountAuditResourceTest`. Três testes de sistema já são executados pelo Failsafe, mas não fazem parte destes números porque o processo externo da aplicação empacotada não está instrumentado pelo JaCoCo.

## 1. Geração do relatório

O build completo e a verificação dos limites foram executados com JDK 25:

```powershell
.\mvnw.cmd clean verify "-DskipITs=false" "-Dcoverage.check.skip=false"
```

O relatório navegável foi gerado pela extensão `quarkus-jacoco` em `target/jacoco-report/index.html`, e os dados consolidados foram lidos de `target/jacoco-report/jacoco.csv`.

## 2. Cobertura total

| Métrica | Coberto | Total | Percentual |
| --- | ---: | ---: | ---: |
| Linhas | 354 | 359 | **98,6%** |
| Desvios | 45 | 47 | **95,7%** |

Evolução observada:

| Etapa | Linhas cobertas | Percentual |
| --- | ---: | ---: |
| Testes unitários | 260/356 | 73,0% |
| Unitários + `AuthResourceTest` | 272/356 | 76,4% |
| Unitários + autenticação e autorização | 281/356 | 78,9% |
| Unitários + três lotes de componente | 294/356 | 82,6% |
| Unitários + componente de depósito e saque | 305/356 | 85,7% |
| Unitários + componente de transferência | 320/356 | 89,9% |
| Unitários + componente de criação e consulta sem conta | 332/356 | 93,3% |
| Correção HTTP 415 e testes de regressão | 335/359 | 93,3% |
| Testes de componente administrativos | 351/359 | 97,8% |
| Testes de componente de auditoria | 354/359 | 98,6% |
| Três testes de sistema, sem instrumentação do processo externo | 354/359 | 98,6% |

## 3. Classes centrais

| Classe | Linhas | Cobertura de linhas | Desvios |
| --- | ---: | ---: | ---: |
| `AccountCustomerService` | 108/108 | 100% | 22/22 |
| `AccountAdminService` | 17/17 | 100% | não aplicável |
| `AccountAuditService` | 28/28 | 100% | não aplicável |
| `AuthService` | 19/19 | 100% | 4/4 |
| `AuthResource` | 4/4 | 100% | não aplicável |
| `UserRepository` | 2/2 | 100% | não aplicável |
| `User` | 11/11 | 100% | não aplicável |
| `ApiExceptionMapper` | 22/22 | 100% | 11/11 |
| `AccountStatusEnum` | 11/11 | 100% | 6/6 |
| `AccountAdminResource` | 18/18 | 100% | não aplicável |
| `AccountAuditResource` | 5/5 | 100% | não aplicável |
| `AccountCustomerResource` | 50/50 | 100% | não aplicável |

## 4. Limites configurados

O `pom.xml` exige:

- pelo menos 70% de cobertura de linhas no bundle;
- pelo menos 80% de cobertura de linhas nas classes de serviço e em `AuthService`.

A verificação foi executada com:

```powershell
.\mvnw.cmd clean verify "-DskipITs=false" "-Dcoverage.check.skip=false"
```

O JaCoCo informou `All coverage checks have been met`, e o Maven terminou com `BUILD SUCCESS`.

## 5. Lacunas esperadas nesta etapa

Os lotes de componente planejados foram concluídos. Todos os resources possuem 100% de cobertura de linhas, o que não implica cobertura de todas as combinações de entrada. Restam cinco linhas sem cobertura:

- `AccountAuditRepository`: duas linhas;
- `AccountAudit`: duas linhas;
- `OpenApiConfig`: uma linha.

Não foram criados testes artificiais apenas para aumentar percentuais. Os três testes de sistema validam o comportamento do artefato empacotado, mas não alteram o relatório atual. Medir cobertura do processo externo exigiria configuração adicional de instrumentação e não foi incluído neste lote.

## 6. Reprodutibilidade

Para recriar a evidência atual:

```powershell
.\mvnw.cmd clean verify "-DskipITs=false" "-Dcoverage.check.skip=false"
```

Os arquivos de `target/` são transitórios. Este documento é a evidência versionável dos resultados observados.
