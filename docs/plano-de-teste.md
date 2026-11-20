# Plano de Teste — Account Service

> **Acompanhamento:** as atividades usam caixas de seleção para permitir o acompanhamento da execução.
> **Contexto Git:** o plano deve ser executado na branch atual do repositório, sem exigir a criação de outra branch.

**Objetivo:** verificar e validar o comportamento funcional, as regras de negócio, a segurança de acesso e a qualidade técnica da API de gerenciamento de contas bancárias.

**Arquitetura de teste:** os testes serão organizados em níveis complementares. Testes unitários isolarão regras de negócio; testes de componente executarão a API com Quarkus e H2; testes de sistema validarão a aplicação empacotada como uma caixa-preta. PMD e JaCoCo fornecerão evidências de qualidade estática e cobertura.

**Tecnologias:** Java 25, Quarkus 3.29.2, Maven, JUnit 5, Mockito, RestAssured, Quarkus Test, H2, PMD e JaCoCo.

**Responsável:** Rodrigo Lorandi  
**Disciplina:** Verificação e Validação de Software  
**Versão inicial:** 20/11/2026  
**Arquivo:** `docs/plano-de-teste.md`

---

## 1. Artefatos previstos

- Criar: `config/pmd/ruleset.xml` — regras de análise estática aplicadas ao projeto.
- Criar: `docs/analise-estatica.md` — execução do PMD, apontamentos, correções e justificativas.
- Criar: `docs/evidencias/` — relatórios e evidências finais das execuções.
- Criar: `src/test/java/br/edu/ifrs/account/auth/` — testes unitários e de componente da autenticação.
- Criar: `src/test/java/br/edu/ifrs/account/service/` — testes unitários dos serviços bancários.
- Criar: `src/test/java/br/edu/ifrs/account/resource/` — testes de componente dos recursos bancários e de auditoria.
- Criar: `src/test/java/br/edu/ifrs/account/system/` — testes de sistema da aplicação empacotada.
- Criar: `src/test/resources/application.properties` — configuração exclusiva do ambiente de teste.
- Modificar: `pom.xml` — integração de Mockito, PMD e JaCoCo ao build Maven.
- Modificar: `README.md` — instruções para reprodução das verificações e acesso às evidências.

## 2. Objetivos dos testes

- Verificar se as regras bancárias produzem resultados corretos para entradas válidas e inválidas.
- Validar o cadastro e a autenticação dos usuários.
- Confirmar que os tokens JWT são exigidos nos endpoints protegidos.
- Confirmar a separação de permissões entre `CUSTOMER`, `ADMIN` e `AUDITOR`.
- Validar a persistência das contas e dos registros de auditoria no H2.
- Detectar defeitos de implementação por análise estática.
- Mensurar a cobertura alcançada pelos testes automatizados.
- Produzir evidências reproduzíveis sem depender de apresentação presencial.

## 3. Escopo

### 3.1 Incluído

- Cadastro de usuários com perfil `CUSTOMER`.
- Login e emissão de JWT.
- Rejeição de credenciais inválidas.
- Criação e consulta da conta do cliente autenticado.
- Depósito em conta ativa e rejeição em conta bloqueada.
- Saque com validação de valor, propriedade, status e saldo.
- Transferência entre contas, incluindo atualização dos dois saldos.
- Bloqueio e desbloqueio de contas pelo administrador.
- Consulta de auditoria pelo auditor.
- Respostas HTTP `400`, `401`, `403`, `404`, `409` e `500` quando aplicáveis.
- Persistência com Hibernate ORM, Panache e H2.
- Análise estática do código Java.
- Cobertura dos testes unitários e de componente.

### 3.2 Não incluído

- Interface gráfica, pois o sistema entregue é uma API REST.
- Aplicativos móveis ou clientes externos.
- Banco de dados de produção ou migração de dados reais.
- Implantação em nuvem, alta disponibilidade e recuperação de desastre.
- Build nativo com GraalVM.
- Validação interna dos algoritmos criptográficos fornecidos pelas bibliotecas SmallRye JWT e BCrypt.
- Desempenho de infraestrutura externa, rede ou hardware.

### 3.3 Escopo opcional para pontuação extra

- Testes de segurança sobre autenticação, autorização, manipulação de tokens e exposição de dados.
- Testes de carga com K6 sobre login, consulta e movimentações bancárias.

## 4. Estratégia de teste

| Nível | Objetivo | Ferramentas | Isolamento |
| --- | --- | --- | --- |
| Estático | Detectar problemas de qualidade, complexidade e possíveis defeitos sem executar a aplicação | PMD e Maven | Código-fonte |
| Unitário | Validar uma classe ou regra de negócio por vez | JUnit 5, Mockito e AssertJ/JUnit Assertions | Dependências simuladas |
| Componente | Validar recursos REST, serviços, repositórios e H2 em conjunto | `@QuarkusTest`, RestAssured e H2 | Aplicação em processo de teste |
| Sistema | Validar o artefato empacotado do ponto de vista de um consumidor externo | `@QuarkusIntegrationTest`, RestAssured e Maven Failsafe | Aplicação executada como caixa-preta |
| Cobertura | Medir linhas e desvios exercitados | JaCoCo | Testes automatizados |
| Segurança opcional | Validar controles de autenticação, autorização e exposição de dados | RestAssured e inspeção documentada | API executada |
| Carga opcional | Medir erros e tempos de resposta sob concorrência | K6 | Aplicação executada |

### 4.1 Testes unitários planejados

- `AccountStatusEnumTest`: estado ativo, bloqueado e alternância.
- `AccountCustomerServiceTest`: criação, depósito, saque, transferência e exceções.
- `AccountAdminServiceTest`: consulta e alternância de status.
- `AccountAuditServiceTest`: registro e conversão para DTO.
- `AuthServiceTest`: cadastro, duplicidade, login e credenciais inválidas.
- `ApiExceptionMapperTest`: associação entre exceptions e códigos HTTP.

Os repositórios, o token atual e os serviços auxiliares serão simulados com Mockito. BCrypt poderá ser executado de forma real nos testes de autenticação, pois faz parte da regra observável do serviço.

### 4.2 Testes de componente planejados

- `AuthResourceTest`: cadastro, validação de entrada, login e falha de autenticação.
- `AccountCustomerResourceTest`: criação, consulta, depósito, saque, transferência e regras de borda concluídos, incluindo cliente sem conta e criação duplicada.
- `AccountAdminResourceTest`: listagem, consulta, bloqueio e desbloqueio concluídos, incluindo conta inexistente e auditoria de falha.
- `AccountAuditResourceTest`: listagem, recentes com limite explícito e padrão, filtros por proprietário e conta e coleções vazias concluídos.
- `AuthorizationTest`: ausência de token e perfis sem permissão.

Os testes usarão H2 em memória e dados controlados em `src/test/resources/import.sql` ou preparação transacional equivalente. Cada teste deverá restaurar o estado necessário, evitando dependência de ordem.

### 4.3 Testes de sistema planejados

- Login de cliente, consulta da conta e depósito bem-sucedido — primeiro lote concluído em `AccountSystemIT`, com restauração do saldo pela API.
- Saque e transferência com atualização e restauração dos saldos — concluídos em `AccountSystemIT`.
- Bloqueio administrativo seguido de operação recusada — concluído em `AccountAdminSecurityIT`.
- Desbloqueio administrativo seguido de operação permitida — concluído em `AccountAdminSecurityIT`.
- Operação bancária seguida de consulta do registro pelo auditor — concluída para transferência em `AccountSystemIT`.
- Login inválido retornando `401` — concluído em `AccountAdminSecurityIT`.
- Cliente tentando endpoint administrativo e recebendo `403` — concluído em `AccountAdminSecurityIT`.

Os testes de sistema terão nomes terminados em `IT` e serão executados pelo Maven Failsafe durante `mvn verify`.

## 5. Técnicas empregadas

- Particionamento de equivalência para valores positivos, zero, negativos e nulos.
- Análise de valor-limite para saldo igual ao valor do saque e saldo imediatamente inferior.
- Tabela de decisão para perfil, token e endpoint solicitado.
- Transição de estados para contas `ACTIVE` e `BLOCKED`.
- Testes positivos e negativos para cada operação crítica.
- Test doubles com Mockito para isolar persistência, auditoria e identidade JWT.
- Testes caixa-preta nos fluxos de sistema.
- Regressão automatizada por meio do build Maven.

## 6. Ambiente e ferramentas

| Item | Configuração |
| --- | --- |
| Sistema operacional de desenvolvimento | Windows 11 |
| Linguagem | Java 25 |
| Framework principal | Quarkus 3.29.2 |
| Build | Maven e Maven Wrapper |
| Teste unitário | JUnit 5 e Mockito |
| Teste de API | RestAssured |
| Teste de componente | Quarkus Test |
| Teste de sistema | Quarkus Integration Test e Maven Failsafe |
| Banco de teste | H2 em memória |
| Análise estática | PMD |
| Cobertura | JaCoCo |
| Documentação da API | OpenAPI e Swagger UI |
| Carga opcional | K6 |

## 7. Dados de teste

| Usuário | Perfil | Conta | Estado inicial |
| --- | --- | --- | --- |
| `customer` | `CUSTOMER` | `ACC1002` | `ACTIVE` |
| `customer_active` | `CUSTOMER` | `ACC1004` | `ACTIVE` |
| `customer_blocked` | `CUSTOMER` | `ACC1005` | `BLOCKED` |
| `admin` | `ADMIN` | `ACC1001` | `ACTIVE` |
| `auditor` | `AUDITOR` | `ACC1003` | `BLOCKED` |

Os testes automatizados não deverão depender de banco externo iniciado para demonstração. Os testes unitários e de componente usam o ambiente do Quarkus Test; os testes de sistema executam uma nova instância do artefato empacotado, com H2 em memória recriado a cada processo e acesso somente pelas interfaces HTTP públicas.

## 8. Critérios de entrada

Os testes de cada nível poderão começar quando:

- o projeto compilar com Java 25;
- `mvn clean test` concluir sem erro de configuração;
- as dependências de teste estiverem declaradas no `pom.xml`;
- o H2 de teste puder criar o esquema automaticamente;
- as chaves JWT de teste estiverem disponíveis no ambiente de teste;
- a especificação dos endpoints no Swagger estiver atualizada;
- não houver alteração funcional desconhecida pendente de análise.

## 9. Critérios de suspensão e retomada

Os testes serão suspensos quando:

- a aplicação não iniciar;
- o schema do banco não puder ser criado;
- uma falha de infraestrutura impedir a maioria dos cenários;
- mudanças simultâneas tornarem os resultados não reproduzíveis.

A execução será retomada depois que a causa for corrigida e o teste mínimo de inicialização voltar a passar.

## 10. Critérios de saída

A etapa de testes será considerada concluída quando:

- todos os testes obrigatórios estiverem automatizados e passando;
- `mvn clean verify` concluir com sucesso;
- não houver defeito crítico ou alto conhecido sem correção ou justificativa;
- o PMD não apresentar violação não tratada;
- a cobertura geral de linhas for de pelo menos 70%;
- serviços e regras de negócio atingirem pelo menos 80% de cobertura de linhas;
- os principais fluxos possuírem casos de sucesso, erro e borda;
- relatórios e evidências estiverem disponíveis no repositório;
- o README explicar como reproduzir cada verificação.

## 11. Matriz de rastreabilidade

| ID | Requisito | Unitário | Componente | Sistema |
| --- | --- | --- | --- | --- |
| RF01 | Cadastrar cliente | `AuthServiceTest` | `AuthResourceTest` | Fluxo de cadastro e login |
| RF02 | Autenticar e emitir JWT | `AuthServiceTest` | `AuthResourceTest` | Login do cliente |
| RF03 | Criar e consultar conta | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Login e consulta da conta |
| RF04 | Realizar depósito | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Depósito em conta ativa |
| RF05 | Realizar saque | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Saque e validação do saldo |
| RF06 | Realizar transferência | `AccountCustomerServiceTest` | `AccountCustomerResourceTest` | Transferência entre contas |
| RF07 | Bloquear e desbloquear conta | `AccountAdminServiceTest` | `AccountAdminResourceTest` | Bloqueio, recusa e desbloqueio |
| RF08 | Consultar auditoria | `AccountAuditServiceTest` | `AccountAuditResourceTest` | Operação seguida de auditoria |
| RNF01 | Exigir JWT válido | `AuthServiceTest` | `AuthorizationTest` | Requisição sem token |
| RNF02 | Restringir acesso por perfil | Serviços protegidos | `AuthorizationTest` | Cliente em rota administrativa |
| RNF03 | Armazenar senha com hash | `AuthServiceTest` | `AuthResourceTest` | Cadastro e login sem exposição do hash |
| RNF04 | Retornar erros HTTP coerentes | `ApiExceptionMapperTest` | Testes dos recursos | Fluxos alternativos da API |

## 12. Cenários prioritários

| Prioridade | Cenário | Resultado esperado |
| --- | --- | --- |
| Crítica | Login com credenciais válidas | JWT emitido com `userId` e perfil corretos |
| Crítica | Cliente acessando rota administrativa | HTTP `403` |
| Crítica | Requisição protegida sem token | HTTP `401` |
| Crítica | Transferência válida | Débito, crédito e auditoria consistentes |
| Alta | Saque acima do saldo | HTTP `409` e saldo preservado |
| Alta | Operação em conta bloqueada | HTTP `409` e saldo preservado |
| Alta | Bloqueio pelo administrador | Estado alterado para `BLOCKED` |
| Alta | Auditor consultando operação | Registro correspondente retornado |
| Média | Valor zero ou negativo | HTTP `400` |
| Média | Cadastro duplicado | HTTP `400` sem criar outro usuário |

## 13. Classificação de defeitos

| Severidade | Definição | Exemplo |
| --- | --- | --- |
| Crítica | Compromete segurança ou integridade financeira | Usuário movimenta conta de terceiro ou saldo é criado incorretamente |
| Alta | Impede uma funcionalidade principal | Login indisponível ou transferência sempre falha |
| Média | Comportamento incorreto com alternativa possível | Código HTTP divergente ou filtro de auditoria incorreto |
| Baixa | Problema sem impacto funcional relevante | Texto de erro inconsistente ou documentação desatualizada |

Defeitos críticos e altos impedem a conclusão da etapa. Defeitos médios e baixos deverão ser corrigidos ou registrados com justificativa.

## 14. Riscos e mitigação

| Risco | Probabilidade | Impacto | Mitigação |
| --- | --- | --- | --- |
| Estado compartilhado entre testes de API | Alta | Alto | Reinicializar dados por teste e não depender da ordem de execução |
| BCrypt tornar testes lentos | Média | Médio | Concentrar hashing real nos testes de autenticação e usar hashes preparados onde não for objeto do teste |
| JWT de teste depender da chave de desenvolvimento | Média | Alto | Utilizar chaves exclusivas em `src/test/resources` nos testes em processo; nos testes de sistema, validar por HTTP as chaves realmente empacotadas no artefato |
| Testes unitários acoplados ao Panache | Média | Médio | Simular `PanacheQuery` somente na camada de serviço e validar repositórios em testes de componente |
| Cobertura alta sem testar comportamento relevante | Média | Alto | Priorizar asserts de regra de negócio e mutações observáveis, não apenas execução de linhas |
| PMD apontar falsos positivos | Média | Baixo | Justificar cada supressão no relatório de análise estática |
| Diferença entre teste em processo e aplicação empacotada | Média | Alto | Manter testes de sistema com `@QuarkusIntegrationTest` |
| Dependência de configuração local | Média | Alto | Usar Maven Wrapper e documentar variáveis e comandos necessários |
| Prazo insuficiente para extras | Média | Baixo | Finalizar os 10 pontos obrigatórios antes de segurança e carga |

## 15. Cronograma

| Sessão | Duração estimada | Atividade | Evidência de conclusão |
| --- | --- | --- | --- |
| 1 | 1–2 horas | Plano de teste e validação do build inicial | `docs/plano-de-teste.md` revisado |
| 2 | 1–2 horas | Configuração de PMD, Mockito e JaCoCo | Plugins integrados e build executado |
| 3 | 1–2 horas | Testes unitários de conta e administração | Testes de serviço passando |
| 4 | 1–2 horas | Testes unitários de autenticação, auditoria e exceptions | Relatório JaCoCo inicial |
| 5 | 1–2 horas | Testes de componente de autenticação e autorização | Relatório Surefire |
| 6 | 1–2 horas | Testes de componente das operações bancárias | Cenários positivos e negativos passando |
| 7 | 1–2 horas | Testes de sistema do artefato empacotado | Relatório Failsafe |
| 8 | 1–2 horas | Correções do PMD e consolidação das evidências | Relatório estático e cobertura final |
| 9 | 1–2 horas | Testes de segurança opcionais | Evidências e achados documentados |
| 10 | 1–2 horas | Testes de carga opcionais | Script K6 e resumo da execução |

## 16. Evidências e relatórios

Serão mantidos no repositório:

- `docs/analise-estatica.md` com comando, resultado, correções e justificativas do PMD;
- `docs/evidencias/cobertura.md` com resumo do JaCoCo;
- `docs/evidencias/testes-unitarios.md` com quantidade e resultado dos testes;
- `docs/evidencias/testes-componente.md` com cenários e resultado;
- `docs/evidencias/testes-sistema.md` com fluxos e resultado do artefato empacotado;
- relatórios HTML ou XML selecionados quando necessários para comprovação;
- comandos de reprodução no `README.md`.

Arquivos transitórios em `target/` não serão tratados como única evidência, pois essa pasta não é versionada.

## 17. Comandos de execução previstos

```powershell
# Testes unitários e de componente
.\mvnw.cmd clean test

# Análise estática
.\mvnw.cmd pmd:pmd

# Cobertura
.\mvnw.cmd jacoco:report

# Build completo, análise e testes de sistema
.\mvnw.cmd clean verify "-DskipITs=false" "-Dcoverage.check.skip=false"
```

Resultados esperados:

- todos os comandos terminam com `BUILD SUCCESS`;
- o PMD gera relatório sem violação não justificada;
- o JaCoCo gera o relatório de cobertura;
- Surefire registra testes unitários e de componente;
- Failsafe registra os testes de sistema.

## 18. Acompanhamento da execução

- [x] Plano de teste criado.
- [x] PMD integrado ao Maven e relatório inicial gerado.
- [x] Mockito e JaCoCo integrados ao Maven.
- [x] Testes unitários implementados e cobertura medida.
- [x] Testes de componente implementados.
- [x] Testes de sistema implementados.
- [x] Apontamentos estáticos corrigidos ou justificados.
- [x] Evidências consolidadas em `docs/evidencias/`.
- [x] README atualizado com todos os comandos de reprodução.
- [ ] Testes de segurança opcionais executados.
- [ ] Testes de carga opcionais executados.
