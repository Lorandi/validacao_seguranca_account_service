# Plano de Teste — Account Service

**Objetivo:** verificar e validar o comportamento funcional, as regras de negócio, a segurança de acesso e a qualidade técnica da API de gerenciamento de contas bancárias.

**Responsável:** Rodrigo Lorandi  
**Disciplina:** Verificação e Validação de Software  
**Data:** 20/11/2026

## 1. Escopo

### 1.1 Incluído

- Cadastro de usuários `CUSTOMER`, login e emissão de JWT, incluindo rejeição de credenciais inválidas.
- Criação e consulta da conta do cliente autenticado.
- Depósito, saque e transferência, com validação de valor, propriedade, status e saldo.
- Bloqueio e desbloqueio de contas pelo administrador.
- Consulta de auditoria pelo auditor.
- Controle de acesso por token e perfil (`CUSTOMER`, `ADMIN`, `AUDITOR`) e respostas HTTP `400`, `401`, `403`, `404`, `409` e `500`.
- Persistência com Hibernate ORM, Panache e H2.
- Análise estática do código Java e cobertura dos testes.

### 1.2 Não incluído

- Interface gráfica, pois o sistema é uma API REST.
- Banco de produção, implantação em nuvem e build nativo com GraalVM.
- Algoritmos criptográficos das bibliotecas SmallRye JWT e BCrypt.
- Testes de segurança e de carga, que são opcionais (pontuação extra) e não foram executados.

## 2. Estratégia de teste

| Nível | Objetivo | Ferramentas | Isolamento |
| --- | --- | --- | --- |
| Estático | Detectar defeitos e problemas de qualidade sem executar a aplicação | PMD | Código-fonte |
| Unitário | Validar uma classe ou regra por vez | JUnit 5 e Mockito | Dependências simuladas |
| Componente | Validar recursos REST, serviços, repositórios e H2 em conjunto | `@QuarkusTest`, RestAssured e H2 | Aplicação em processo de teste |
| Sistema | Validar o artefato empacotado como um consumidor externo | `@QuarkusIntegrationTest`, RestAssured e Maven Failsafe | Caixa-preta, somente via HTTP |
| Cobertura | Medir linhas e desvios exercitados | JaCoCo | Testes automatizados |

Como o sistema é uma API, os testes de sistema usam RestAssured contra o JAR empacotado, no lugar de ferramentas de interface como Selenium, Cypress ou Playwright.

**Testes unitários** (`src/test/java/.../service`, `auth`, `enums`, `exception`): serviços, `AuthService`, enum de status e `ApiExceptionMapper`. Repositórios, token e serviços auxiliares são simulados com Mockito; o BCrypt é executado de forma real.

**Testes de componente** (`.../resource`, `auth`): `AuthResourceTest`, `AccountCustomerResourceTest`, `AccountAdminResourceTest`, `AccountAuditResourceTest` e `AuthorizationTest`, com H2 em memória e restauração do estado alterado por cada teste.

**Testes de sistema** (`.../system`): `AccountSystemIT` (login, consulta, depósito, saque, transferência e auditoria) e `AccountAdminSecurityIT` (bloqueio, operação recusada, desbloqueio, login inválido e `403` para cliente em rota administrativa). Executam pelo Failsafe durante `verify`.

### Técnicas

- Particionamento de equivalência e análise de valor-limite (valores positivos, zero, negativos, nulos; saldo igual ou imediatamente inferior ao saque).
- Tabela de decisão para perfil, token e endpoint.
- Transição de estados da conta (`ACTIVE` e `BLOCKED`).
- Casos positivos e negativos para cada operação crítica.

## 3. Ambiente e ferramentas

Windows 11, Java 25, Quarkus 3.29.2, Maven Wrapper, JUnit 5, Mockito, RestAssured, Quarkus Test, Maven Failsafe, H2 em memória, PMD 7.29.0 e JaCoCo 0.8.14.

Dados de teste: os usuários e contas de `src/main/resources/import.sql` (listados no [README](../README.md)). Testes em processo usam chaves JWT próprias em `src/test/resources`; os de sistema validam as chaves empacotadas.

## 4. Critérios de entrada

- O projeto compila com Java 25 e `.\mvnw.cmd clean test` não apresenta erro de configuração.
- As dependências de teste estão declaradas no `pom.xml` e o H2 cria o esquema automaticamente.
- As chaves JWT de teste estão disponíveis.

**Suspensão:** os testes são suspensos se a aplicação não iniciar, se o esquema não puder ser criado ou se houver falha de infraestrutura generalizada; são retomados quando o teste mínimo de inicialização voltar a passar.

## 5. Critérios de saída

- `.\mvnw.cmd clean verify` conclui com sucesso, com todos os testes obrigatórios passando.
- PMD sem violações não tratadas.
- Cobertura de pelo menos 70% das linhas no total e 80% nos serviços e em `AuthService` (verificada pelo JaCoCo no build).
- Fluxos principais com casos de sucesso, erro e borda.
- Nenhum defeito crítico ou alto conhecido sem correção ou justificativa.
- Evidências versionadas em `docs/` e comandos de reprodução no README.

## 6. Rastreabilidade

| ID | Requisito | Unitário | Componente | Sistema |
| --- | --- | --- | --- | --- |
| RF01 | Cadastrar cliente | `AuthServiceTest` | `AuthResourceTest` | — |
| RF02 | Autenticar e emitir JWT | `AuthServiceTest` | `AuthResourceTest` | Login do cliente |
| RF03 | Criar e consultar conta | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Consulta da conta |
| RF04 | Realizar depósito | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Depósito em conta ativa |
| RF05 | Realizar saque | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Saque e saldo |
| RF06 | Realizar transferência | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Transferência entre contas |
| RF07 | Bloquear e desbloquear conta | `AccountAdminServiceTest` | `AccountAdminResourceTest` | Bloqueio, recusa e desbloqueio |
| RF08 | Consultar auditoria | `AccountAuditServiceTest` | `AccountAuditResourceTest` | Transferência seguida de auditoria |
| RNF01 | Exigir JWT válido | `AuthServiceTest` | `AuthorizationTest` | JWT real nos fluxos protegidos |
| RNF02 | Restringir acesso por perfil | Serviços protegidos | `AuthorizationTest` | Cliente em rota administrativa |
| RNF03 | Armazenar senha com hash | `AuthServiceTest` | `AuthResourceTest` | — |
| RNF04 | Retornar erros HTTP coerentes | `ApiExceptionMapperTest` | Testes dos recursos | Login inválido, `403` e operação recusada |

## 7. Riscos e mitigação

| Risco | Probabilidade | Impacto | Mitigação |
| --- | --- | --- | --- |
| Estado compartilhado entre testes de API | Alta | Alto | Restaurar o estado alterado em cada teste e não depender da ordem |
| BCrypt tornar os testes lentos | Média | Médio | Hashing real apenas nos testes de autenticação |
| Chaves JWT de teste diferentes das empacotadas | Média | Alto | Chaves próprias nos testes em processo; chaves reais validadas nos testes de sistema |
| Testes unitários acoplados ao Panache | Média | Médio | Simular `PanacheQuery` só na camada de serviço; validar repositórios nos testes de componente |
| Cobertura alta sem verificar comportamento | Média | Alto | Priorizar asserts de regra de negócio |
| Falsos positivos do PMD | Média | Baixo | Justificar cada exceção em `docs/analise-estatica.md` |
| Diferença entre teste em processo e artefato empacotado | Média | Alto | Testes de sistema com `@QuarkusIntegrationTest` |
| Prazo insuficiente para os extras | Média | Baixo | Concluir os itens obrigatórios antes de segurança e carga |

## 8. Cronograma

| Sessão | Atividade | Situação |
| --- | --- | --- |
| 1 | Plano de teste e validação do build inicial | Concluída |
| 2 | Configuração de PMD, Mockito e JaCoCo | Concluída |
| 3–4 | Testes unitários (contas, administração, autenticação, auditoria e exceptions) | Concluída |
| 5–6 | Testes de componente (autenticação, autorização e operações bancárias) | Concluída |
| 7 | Testes de sistema do artefato empacotado | Concluída |
| 8 | Correções do PMD e consolidação das evidências | Concluída |
| 9 | Testes de segurança (opcional) | Não realizada |
| 10 | Testes de carga com K6 (opcional) | Não realizada |

## 9. Evidências e execução

Evidências versionadas: [análise estática](analise-estatica.md), [testes unitários](evidencias/testes-unitarios.md), [testes de componente](evidencias/testes-componente.md), [testes de sistema](evidencias/testes-sistema.md) e [cobertura](evidencias/cobertura.md). A pasta `target/` não é versionada.

```powershell
.\mvnw.cmd clean test     # testes unitários e de componente
.\mvnw.cmd pmd:pmd        # análise estática (relatório em target/)
.\mvnw.cmd clean verify   # build completo: testes, PMD, testes de sistema e limites de cobertura
```
