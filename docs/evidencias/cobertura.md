# Evidência de Cobertura dos Testes Unitários

Este documento registra a cobertura obtida em 20/11/2026 exclusivamente com as seis classes de testes unitários revisadas. Testes de componente e de sistema ainda não fazem parte destes números.

## 1. Geração do relatório

Após a execução unitária limpa, o relatório foi produzido com:

```powershell
.\mvnw.cmd "-Djacoco.dataFile=target/jacoco-quarkus.exec" jacoco:report
```

O relatório navegável foi gerado em `target/site/jacoco/index.html`, e os dados consolidados foram lidos de `target/site/jacoco/jacoco.csv`.

## 2. Cobertura total

| Métrica | Coberto | Total | Percentual |
| --- | ---: | ---: | ---: |
| Linhas | 260 | 356 | **73,0%** |
| Desvios | 40 | 44 | **90,9%** |

## 3. Classes centrais

| Classe | Linhas | Cobertura de linhas | Desvios |
| --- | ---: | ---: | ---: |
| `AccountCustomerService` | 108/108 | 100% | 22/22 |
| `AccountAdminService` | 17/17 | 100% | não aplicável |
| `AccountAuditService` | 28/28 | 100% | não aplicável |
| `AuthService` | 19/19 | 100% | 4/4 |
| `ApiExceptionMapper` | 19/19 | 100% | 8/8 |
| `AccountStatusEnum` | 11/11 | 100% | 6/6 |

## 4. Limites configurados

O `pom.xml` exige:

- pelo menos 70% de cobertura de linhas no bundle;
- pelo menos 80% de cobertura de linhas nas classes de serviço e em `AuthService`.

A verificação foi executada com:

```powershell
.\mvnw.cmd verify "-Dcoverage.check.skip=false" "-Dtest=AccountStatusEnumTest,ApiExceptionMapperTest,AccountAdminServiceTest,AccountAuditServiceTest,AccountCustomerServiceTest,AuthServiceTest"
```

O JaCoCo informou `All coverage checks have been met`, e o Maven terminou com `BUILD SUCCESS`.

## 5. Lacunas esperadas nesta etapa

As classes REST e os repositórios permanecem sem cobertura ou com cobertura indireta limitada porque não são o alvo dos testes unitários isolados. Entre elas estão:

- `AuthResource`;
- `AccountCustomerResource`;
- `AccountAdminResource`;
- `AccountAuditResource`;
- `UserRepository`;
- `AccountRepository`;
- `AccountAuditRepository`.

Essas lacunas serão tratadas pelos testes de componente com `@QuarkusTest`, RestAssured e H2. Não foram criados testes artificiais apenas para aumentar percentuais.

## 6. Reprodutibilidade

Para recriar esta evidência sem incluir testes de componente:

```powershell
.\mvnw.cmd clean test "-Dtest=AccountStatusEnumTest,ApiExceptionMapperTest,AccountAdminServiceTest,AccountAuditServiceTest,AccountCustomerServiceTest,AuthServiceTest"
.\mvnw.cmd "-Djacoco.dataFile=target/jacoco-quarkus.exec" jacoco:report
.\mvnw.cmd verify "-Dcoverage.check.skip=false" "-Dtest=AccountStatusEnumTest,ApiExceptionMapperTest,AccountAdminServiceTest,AccountAuditServiceTest,AccountCustomerServiceTest,AuthServiceTest"
```

Os arquivos de `target/` são transitórios. Este documento é a evidência versionável dos resultados observados.
