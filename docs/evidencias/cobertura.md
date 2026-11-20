# Evidência de Cobertura Automatizada

Este documento registra a cobertura obtida em 20/11/2026 com as seis classes de testes unitários e os testes de componente `AuthResourceTest`, `AuthorizationTest` e `AccountCustomerResourceTest`. Testes de sistema ainda não fazem parte destes números.

## 1. Geração do relatório

O build completo e a verificação dos limites foram executados com JDK 25:

```powershell
.\mvnw.cmd clean verify "-Dcoverage.check.skip=false"
```

O relatório navegável foi gerado pela extensão `quarkus-jacoco` em `target/jacoco-report/index.html`, e os dados consolidados foram lidos de `target/jacoco-report/jacoco.csv`.

## 2. Cobertura total

| Métrica | Coberto | Total | Percentual |
| --- | ---: | ---: | ---: |
| Linhas | 320 | 356 | **89,9%** |
| Desvios | 42 | 44 | **95,5%** |

Evolução observada:

| Etapa | Linhas cobertas | Percentual |
| --- | ---: | ---: |
| Testes unitários | 260/356 | 73,0% |
| Unitários + `AuthResourceTest` | 272/356 | 76,4% |
| Unitários + autenticação e autorização | 281/356 | 78,9% |
| Unitários + três lotes de componente | 294/356 | 82,6% |
| Unitários + componente de depósito e saque | 305/356 | 85,7% |
| Unitários + componente de transferência | 320/356 | 89,9% |

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
| `ApiExceptionMapper` | 19/19 | 100% | 8/8 |
| `AccountStatusEnum` | 11/11 | 100% | 6/6 |
| `AccountAdminResource` | 2/18 | 11,1% | não aplicável |
| `AccountAuditResource` | 2/5 | 40,0% | não aplicável |
| `AccountCustomerResource` | 38/50 | 76,0% | não aplicável |

## 4. Limites configurados

O `pom.xml` exige:

- pelo menos 70% de cobertura de linhas no bundle;
- pelo menos 80% de cobertura de linhas nas classes de serviço e em `AuthService`.

A verificação foi executada com:

```powershell
.\mvnw.cmd clean verify "-Dcoverage.check.skip=false"
```

O JaCoCo informou `All coverage checks have been met`, e o Maven terminou com `BUILD SUCCESS`.

## 5. Lacunas esperadas nesta etapa

Os endpoints GET representativos dos recursos protegidos possuem cobertura parcial por causa do `AuthorizationTest`. A consulta da conta do cliente e os principais resultados de depósito, saque e transferência também são exercitados pelo `AccountCustomerResourceTest`, mas os demais fluxos REST e repositórios continuam pendentes. Entre as lacunas estão:

- criação de conta em `AccountCustomerResource`;
- consulta individual e alteração de status em `AccountAdminResource`;
- filtros e consulta de registros em `AccountAuditResource`;
- `AccountRepository`;
- `AccountAuditRepository`.

Essas lacunas serão tratadas pelos testes de componente com `@QuarkusTest`, RestAssured e H2. Não foram criados testes artificiais apenas para aumentar percentuais.

## 6. Reprodutibilidade

Para recriar a evidência atual:

```powershell
.\mvnw.cmd clean verify "-Dcoverage.check.skip=false"
```

Os arquivos de `target/` são transitórios. Este documento é a evidência versionável dos resultados observados.
