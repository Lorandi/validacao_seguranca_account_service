# Plano de Teste — Account Service

**Objetivo:** verificar e validar o comportamento funcional, as regras de negócio, a segurança de acesso e a qualidade técnica da API de gerenciamento de contas bancárias.

**Responsável:** Rodrigo Lorandi  
**Disciplina:** Verificação e Validação de Software  
**Data:** 25/11/2026

## 1. Escopo

- Cadastro de usuários `CUSTOMER`, login e emissão de JWT, incluindo rejeição de credenciais inválidas.
- Criação e consulta da conta do cliente autenticado.
- Depósito, saque e transferência, com validação de valor, propriedade, status e saldo.
- Bloqueio e desbloqueio de contas pelo administrador.
- Consulta de auditoria pelo auditor.
- Controle de acesso por token e perfil (`CUSTOMER`, `ADMIN`, `AUDITOR`) e respostas HTTP `400`, `401`, `403`, `404`, `409` e `500`.
- Testes opcionais de segurança para as consultas administrativa e de auditoria, com identidades simuladas.
- Persistência com Hibernate ORM, Panache e H2.
- Análise estática do código Java e cobertura dos testes.

## 2. Estratégia de teste

| Nível | Objetivo | Ferramentas | Isolamento |
| --- | --- | --- | --- |
| Estático | Detectar defeitos e problemas de qualidade sem executar a aplicação | PMD | Código-fonte |
| Unitário | Validar uma classe ou regra por vez | JUnit 5 e Mockito | Dependências simuladas |
| Componente | Validar recursos REST, serviços, repositórios e H2 em conjunto | `@QuarkusTest`, RestAssured e H2 | Aplicação em processo de teste |
| Sistema | Validar o artefato empacotado como um consumidor externo | `@QuarkusIntegrationTest`, RestAssured e Maven Failsafe | Caixa-preta, somente via HTTP |
| Segurança (opcional) | Conferir o acesso por perfil em rotas protegidas | `@QuarkusTest`, `@TestSecurity` e RestAssured | Identidades simuladas |
| Cobertura | Medir linhas e desvios exercitados | JaCoCo | Testes automatizados |

### Técnicas

- Particionamento de equivalência e análise de valor-limite (valores positivos, zero, negativos, nulos; saldo igual ou imediatamente inferior ao saque).
- Tabela de decisão para perfil, token e endpoint.
- Transição de estados da conta (`ACTIVE` e `BLOCKED`).
- Casos positivos e negativos para cada operação crítica.

## 3. Ficha técnica

| Item | Ambiente |
| --- | --- |
| Java | Java 25 |
| Aplicação | Quarkus 3.29.2, Hibernate ORM com Panache, H2 em memória, SmallRye JWT, BCrypt e Swagger UI |
| Execução de testes | Maven Wrapper, Surefire, Failsafe, JUnit 5, Mockito, RestAssured e Quarkus Test |
| Análise estática e cobertura | Maven PMD Plugin 3.28.0 (motor PMD 7.29.0) e JaCoCo 0.8.14 |

O H2 carrega os usuários e as contas de `src/main/resources/import.sql`. Os testes de componente usam as chaves JWT de `src/test/resources`. Os testes de sistema usam as chaves do artefato empacotado.

## 4. Critérios de aprovação

O build de verificação deve passar com todos os testes obrigatórios, zero violações do PMD e os limites de cobertura indicados na seção de resultados.

## 5. Cronograma

- [x] Sessão 1 — Plano de teste e validação do build inicial
- [x] Sessão 2 — Configuração de PMD, Mockito e JaCoCo
- [x] Sessões 3–4 — Testes unitários (contas, administração, autenticação, auditoria e exceptions)
- [x] Sessões 5–6 — Testes de componente (autenticação, autorização e operações bancárias)
- [x] Sessão 7 — Testes de sistema do artefato empacotado
- [x] Sessão 8 — Correções do PMD e consolidação das evidências
- [x] Sessão 9 — Testes de segurança (opcional)
- [ ] Sessão 10 — Testes de carga com K6 (opcional)

## 6. Evidências por etapa

### Análise estática

O PMD analisa o código de produção em `src/main/java`. O [ruleset](../config/pmd/ruleset.xml) reúne 81 regras de tratamento de erros, boas práticas e desempenho, sem supressões no código. O Maven executa `pmd:check` na fase `verify` e falha se detectar violações. Para validar o detector, um campo privado sem uso em uma classe temporária provocou a falha esperada na regra `UnusedPrivateField`. A classe foi removida após a verificação.

### Testes unitários

O JUnit executa as classes isoladamente. Mockito simula repositórios, token e serviços auxiliares onde necessário. A autenticação usa BCrypt real.

| Classe | Cenários verificados |
| --- | --- |
| `AccountStatusEnumTest` | Estados `ACTIVE` e `BLOCKED` e alternância de status |
| `ApiExceptionMapperTest` | Mapeamento de exceções para HTTP, inclusive `415`, e proteção de mensagens internas |
| `AccountAdminServiceTest` | Consulta administrativa e alternância de status |
| `AccountAuditServiceTest` | Registro e consulta de auditoria, falha de repositório e token sem `userId` |
| `AccountCustomerServiceTest` | Criação e consulta de conta, depósito, saque, transferência, limites de valor e saldo, propriedade e conta bloqueada |
| `AuthServiceTest` | Cadastro, normalização de username, BCrypt, duplicidade, credenciais inválidas e claims do JWT |

### Testes de componente

`@QuarkusTest` e RestAssured exercitam os endpoints, serviços, validação e persistência no H2. As requisições autenticadas usam JWT obtido no login real. Os testes que alteram dados restauram o estado e pressupõem execução sequencial.

| Classe | Cenários verificados |
| --- | --- |
| `AuthResourceTest` | Cadastro, duplicidade, validação dos campos, hash da senha, login e credenciais inválidas |
| `AuthorizationTest` | Token ausente ou malformado (`401`), perfil incorreto (`403`) e perfil correto (`200`) |
| `AccountCustomerResourceTest` | Criação e consulta de conta, operações financeiras, valores inválidos, limites de saldo, conta de terceiro, bloqueada ou inexistente, e tipo de conteúdo incompatível (`415`) |
| `AccountAdminResourceTest` | Listagem, consulta, bloqueio e desbloqueio, com verificação do estado persistido e da auditoria |
| `AccountAuditResourceTest` | Listagem e filtros por proprietário ou conta, limite de registros recentes, resultado vazio e preservação dos registros nas consultas |

### Testes de sistema

`AccountSystemIT` e `AccountAdminSecurityIT` exercitam por HTTP o JAR empacotado em processo separado. Quando os testes alteram o estado, a restauração é feita pela própria API.

| Cenário | Validação |
| --- | --- |
| Login, consulta e depósito | O JWT do cliente é aceito. O depósito de 10,00 em `ACC1002` aparece no saldo |
| Saque | O saque de 17,25 em `ACC1002` reduz o saldo no valor solicitado |
| Transferência e auditoria | A transferência de 25,50 entre `ACC1002` e `ACC1004` ajusta os dois saldos. O auditor encontra o registro da operação |
| Bloqueio e desbloqueio | O saque em `ACC1004` bloqueada retorna `409` e preserva o saldo. Após o desbloqueio, retorna `200` |
| Login inválido | Senha incorreta retorna `401` com `errorId` e sem expor senha, hash ou token |
| Acesso administrativo indevido | O perfil `CUSTOMER` recebe `403` na listagem administrativa e a resposta preserva os dados protegidos |

### Testes de segurança

`AccountRoleSecurityTest` usa `@QuarkusTest`, `@TestSecurity` e RestAssured. As identidades são simuladas e as regras de autorização da aplicação continuam ativas.

| Rota | `ADMIN` | `AUDITOR` | `CUSTOMER` |
| --- | --- | --- | --- |
| `GET /api/v1/accounts/ACC1002` | `200` com o identificador da conta | `403` | `403` |
| `GET /api/v1/audit/account/ACC1002` | `403` | `200` com um array JSON | `403` |

Nas recusas, a resposta deve vir sem `ACC1002`, `balance` e `operationId`. Na consulta de auditoria autorizada, um array vazio é válido.

## 7. Execução e resultados

Execute os comandos na raiz do projeto no PowerShell.

| Comando | Finalidade |
| --- | --- |
| `.\mvnw.cmd clean verify` | Executa a validação completa, incluindo Failsafe, PMD e verificação de cobertura |
| `.\mvnw.cmd test` | Executa unitários, componente e segurança via Surefire |
| `.\mvnw.cmd test "-Dtest=AccountRoleSecurityTest"` | Executa a suíte de segurança isoladamente |
| `.\mvnw.cmd pmd:pmd` | Gera o relatório de análise estática |

O build completo de **25/11/2026** terminou com `BUILD SUCCESS`, sem falhas, erros ou testes ignorados.

| Executor | Distribuição | Execuções |
| --- | --- | ---: |
| Surefire | 82 unitários, 78 de componente e 6 de segurança | 166 |
| Failsafe | Testes de sistema | 6 |
| **Total** | | **172** |

| Métrica | Cobertura mínima | Resultado |
| --- | --- | --- |
| Violações do PMD | — | 0 |
| Linhas no projeto | 70% | **99,7%** (351/352) |
| Linhas por classe de serviço e em `AuthService` | 80% | **100%** |
| Cobertura de desvios | — | **100%** (43/43) |

Os limites estão definidos no [`pom.xml`](../pom.xml).

```xml
<coverage.line.minimum>0.70</coverage.line.minimum>
<coverage.service.line.minimum>0.80</coverage.service.line.minimum>
```

Os relatórios são gerados em `target/`. O Surefire e o Failsafe geram seus arquivos em `target/surefire-reports/` e `target/failsafe-reports/`. O PMD gera `target/pmd.xml` e `target/reports/pmd.html`. O JaCoCo gera `target/jacoco-report/index.html` e `target/jacoco-report/jacoco.csv`.
