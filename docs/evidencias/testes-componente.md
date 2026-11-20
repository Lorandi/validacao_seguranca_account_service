# Evidência dos Testes de Componente

Este documento acompanha progressivamente os testes de componente do Account Service. Em 20/11/2026, os lotes revisados cobrem autenticação, autorização, consulta da conta do cliente, depósitos, saques e transferências.

## 1. Lote revisado: `AuthResourceTest`

O teste usa `@QuarkusTest` e RestAssured para executar em conjunto:

- `AuthResource`;
- `AuthService`;
- `UserRepository`;
- Hibernate ORM com Panache;
- banco H2 em memória;
- Bean Validation;
- assinatura JWT com as chaves exclusivas de `src/test/resources`.

Não são utilizados Mockito, `@InjectMock` ou servidor externo.

## 2. Cenários

| Área | Cenários |
| --- | --- |
| Cadastro válido | HTTP 201, username normalizado, perfil `CUSTOMER`, ID positivo, persistência no H2 e hash BCrypt sem exposição da senha |
| Cadastro duplicado | HTTP 400, mensagem controlada, `errorId` e ausência de nova persistência |
| Validação do cadastro | Username ausente, em branco, abaixo e acima do limite; password ausente, em branco, abaixo e acima do limite |
| Login válido | HTTP 200, JWT com três segmentos, tipo `Bearer` e duração de 3600 segundos |
| Credenciais inválidas | Usuário inexistente e senha incorreta retornando HTTP 401 sem token |
| Validação do login | Username e password ausentes ou em branco retornando HTTP 400 |

O teste possui 7 métodos e 17 execuções, considerando os casos parametrizados.

## 3. Isolamento

O usuário `component_auth_user` é removido antes e depois de cada teste em transação própria. Os usuários de demonstração não são excluídos nem modificados, e os testes não dependem de ordem.

## 4. Lote revisado: `AuthorizationTest`

O teste usa tokens obtidos pelo login real e valida três fronteiras de acesso:

- token ausente ou malformado retorna HTTP 401;
- token válido sem o perfil necessário retorna HTTP 403;
- token válido com o perfil correto retorna HTTP 200.

Foram utilizados apenas endpoints GET, sem alteração do banco:

- CUSTOMER: `/api/v1/accounts/owner/`;
- ADMIN: `/api/v1/accounts`;
- AUDITOR: `/api/v1/audit`.

A matriz cobre os seis cruzamentos de perfis incorretos, além dos três endpoints sem token, com token malformado e com o perfil correto. O teste possui 5 métodos e 16 execuções.

## 5. Lote revisado: `AccountCustomerResourceTest`

O teste usa `@QuarkusTest`, RestAssured, Hibernate ORM e H2 reais, sem mocks. Os quatro lotes do recurso do cliente cobrem:

- consulta da conta pertencente ao cliente autenticado;
- depósito válido realizado por um cliente em conta ativa de terceiro;
- valor de depósito ausente, zero ou negativo;
- tentativa de depósito em conta bloqueada;
- tentativa de depósito em conta inexistente;
- saque válido e saque exatamente igual ao saldo disponível;
- valor de saque ausente, zero ou negativo;
- tentativa de saque em conta de terceiro;
- tentativa de saque em conta bloqueada;
- saque com saldo insuficiente;
- tentativa de saque em conta inexistente;
- transferência válida com débito na origem e crédito no destino;
- valor de transferência ausente, zero ou negativo;
- transferência com origem e destino iguais;
- tentativa de transferência a partir de conta de terceiro;
- origem ou destino bloqueado;
- transferência com saldo insuficiente;
- origem ou destino inexistente;
- criação de conta bloqueada e com saldo zero para cliente novo, seguida de consulta;
- rejeição de criação para cliente que já possui conta;
- consulta HTTP 404 para cliente sem conta.

Além dos códigos HTTP e corpos das respostas, são verificados diretamente no H2 os saldos, a quantidade de contas e os registros de auditoria `DEPOSIT`, `WITHDRAW`, `TRANSFER` e `CREATE_ACCOUNT`. O teste possui 25 métodos e 31 execuções, incluindo a regressão HTTP 415 e os casos parametrizados de valor inválido para depósito, saque e transferência.

Os cenários de cliente novo cadastram e autenticam `component_account_user` pela API e usam o ID retornado pelo cadastro. A limpeza remove sua conta antes do usuário. As auditorias de criação são identificadas por diferença em relação aos IDs existentes antes do teste e removidas no encerramento. Essa estratégia pressupõe execução sequencial.

Os saldos das contas envolvidas e as auditorias de depósito, saque e transferência são restaurados antes e depois de cada teste em transação própria. As demais contas de demonstração têm seus saldos conferidos para detectar efeitos colaterais. Os testes não dependem de ordem.

## 6. Comandos e resultados

Execução isolada:

```powershell
.\mvnw.cmd "-Dtest=AuthResourceTest" test
.\mvnw.cmd "-Dtest=AuthorizationTest" test
.\mvnw.cmd "-Dtest=AccountCustomerResourceTest" test
.\mvnw.cmd "-Dtest=AccountAdminResourceTest" test
```

Resultado dos lotes isolados:

- `AuthResourceTest`: 17 execuções;
- `AuthorizationTest`: 16 execuções;
- `AccountCustomerResourceTest`: 31 execuções;
- `AccountAdminResourceTest`: 6 execuções;
- nenhuma falha, erro ou teste ignorado;
- `BUILD SUCCESS`.

Build completo com JDK 25:

```powershell
.\mvnw.cmd clean verify "-Dcoverage.check.skip=false"
```

Resultado:

- 152 execuções no total;
- 0 falhas, 0 erros e 0 ignorados;
- PMD sem violações;
- limites JaCoCo atendidos;
- `BUILD SUCCESS`.

## 7. Contribuição para cobertura

Após o lote:

- cobertura geral de linhas: 351/359, ou 97,8%;
- cobertura de desvios: 45/47, ou 95,7%;
- `AuthResource`: 4/4 linhas;
- `UserRepository`: 2/2 linhas;
- `User`: 11/11 linhas;
- `AccountAdminResource`: 18/18 linhas;
- `AccountAuditResource`: 2/5 linhas;
- `AccountCustomerResource`: 50/50 linhas.

## 8. Observações

No encerramento do Quarkus, JDKs 24 ou superiores exibem `IllegalAccessError` em uma thread do `JBoss Threads` e solicitam `--add-opens java.base/java.lang=ALL-UNNAMED`. O comportamento foi reproduzido com JDK 25 e JDK 26. A exceção ocorre depois das assertions, não altera a contagem dos testes e não muda o resultado `BUILD SUCCESS`, mas deverá ser tratada separadamente para limpar a saída final.

As verificações de corpo das respostas 401 e 403 procuram marcadores de dados protegidos. Essa checagem é complementar ao contrato principal de status HTTP e poderá ser ampliada nos testes opcionais de segurança.

O depósito em conta de terceiro é comportamento permitido pelo projeto. Conta inexistente retorna HTTP 400 no contrato atual, enquanto conta bloqueada retorna HTTP 409. Nas falhas de depósito, a auditoria registra `ownerId` nulo.

## 9. Escopo restante

Ainda faltam os testes de componente do recurso de auditoria. Por isso, a etapa de testes de componente permanece em andamento no plano de teste.

## 10. Correção do tipo de conteúdo não suportado

Um POST de criação sem corpo e sem `Content-Type` foi relatado como HTTP 500, com `NotSupportedException` no log. A inspeção do `ApiExceptionMapper` confirmou que exceções HTTP não tratadas especificamente caem no retorno genérico 500. Os testes deste lote enviam `Content-Type: application/json`, conforme o `@Consumes` atual.

O defeito médio foi corrigido em lote separado: `NotSupportedException` agora retorna HTTP 415, `errorId`, `status` e a mensagem controlada "Tipo de conteúdo não suportado.". O teste de componente envia POST sem corpo com `Content-Type: text/plain` e verifica a resposta JSON e a preservação de contas, saldos e auditorias. O teste unitário verifica que a mensagem interna da exceção não aparece na resposta.

A outra LLM relatou que os dois novos testes falharam com HTTP 500 antes da correção. No review, o build completo foi reproduzido com 146 testes aprovados, zero violações PMD e limites JaCoCo atendidos. O tratamento é específico para `NotSupportedException`; outras `WebApplicationException` não mapeadas ainda podem retornar 500 e permanecem pendentes.

## 11. Lote revisado: `AccountAdminResourceTest`

Os seis testes usam `@QuarkusTest`, RestAssured, login real de administrador e H2, sem mocks. Cobrem listagem, consulta existente, consulta inexistente, bloqueio, desbloqueio e alternância de conta inexistente.

As consultas validam HTTP 200 ou 404 e ausência de efeitos no banco. As alternâncias verificam a resposta, o estado persistido e exatamente uma nova auditoria `TOGGLE_STATUS`, com resultado `SUCCESS` ou `FAILURE` e os demais campos correspondentes.

Um snapshot de todas as contas verifica quantidade, identificadores, proprietário, saldo e status; somente o status solicitado pode mudar. A preparação e o encerramento restauram os estados de ACC1004 e ACC1005 em transação própria, sem restaurar saldos. A limpeza remove apenas novas auditorias `TOGGLE_STATUS` dos alvos utilizados. A identificação por snapshot pressupõe execução sequencial.

O review reproduziu o build completo com JDK 25: 152 testes aprovados, zero violações PMD, limites JaCoCo atendidos e `AccountAdminResource` com 18/18 linhas cobertas. Os avisos conhecidos de encerramento do JBoss Threads e do PMD permaneceram.
