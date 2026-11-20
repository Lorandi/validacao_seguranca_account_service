# Evidência dos Testes de Sistema

Este documento registra incrementalmente os testes executados contra o artefato empacotado do Account Service. A etapa de sistema está em andamento e somente o primeiro fluxo foi implementado e validado.

## 1. Estratégia

Os testes de sistema usam `@QuarkusIntegrationTest`, RestAssured e Maven Failsafe. A aplicação é empacotada e iniciada em processo separado, sendo exercitada como caixa-preta somente por endpoints HTTP públicos.

Não há injeção CDI, acesso a repositórios, transações de teste, mocks ou conexão JDBC pelo teste. O processo usa H2 em memória com recriação do esquema e dos dados iniciais na inicialização, sem acessar banco externo.

## 2. Primeiro lote validado

Classe: `src/test/java/br/edu/ifrs/account/system/AccountSystemIT.java`

| Cenário | Validações observáveis |
| --- | --- |
| Login, consulta e depósito na aplicação empacotada | Login real do usuário `customer`; JWT Bearer emitido e aceito; consulta da conta `ACC1002`; depósito de 10,00; saldo posterior igual ao inicial mais 10,00 |
| Restauração pela interface pública | Saque de 10,00 no bloco de limpeza e consulta final com saldo igual ao valor inicial |

O fluxo também confirmou que o artefato empacotado inicia com as configurações e as chaves JWT de `src/main/resources`. Os registros de auditoria do depósito e do saque permanecem somente no H2 do processo e são descartados quando a aplicação de teste é encerrada.

## 3. Execução reproduzida

Execução realizada em 20/11/2026 com Azul Zulu JDK 25.0.5.1:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean verify "-DskipITs=false" "-Dcoverage.check.skip=false"
```

Resultado observado:

| Executor | Execuções | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| Surefire — unitários e componente | 160 | 0 | 0 | 0 |
| Failsafe — sistema | 1 | 0 | 0 | 0 |
| **Total** | **161** | **0** | **0** | **0** |

O Maven terminou com `BUILD SUCCESS`, o PMD apresentou zero violações e os limites de cobertura existentes foram atendidos. O relatório Failsafe registrou uma execução de `AccountSystemIT` em aproximadamente 12,37 segundos.

Relatórios transitórios gerados:

- `target/failsafe-reports/br.edu.ifrs.account.system.AccountSystemIT.txt`;
- `target/failsafe-reports/TEST-br.edu.ifrs.account.system.AccountSystemIT.xml`;
- `target/failsafe-reports/failsafe-summary.xml`.

## 4. Cobertura

O relatório permaneceu em 354/359 linhas (98,6%) e 45/47 desvios (95,7%). Esses números representam os testes unitários e de componente.

A JVM externa iniciada por `@QuarkusIntegrationTest` não está instrumentada pela configuração JaCoCo atual. Portanto, esta execução comprova o comportamento do artefato empacotado, mas não deve ser apresentada como incremento de cobertura de código.

## 5. Ressalvas e avisos

- O review identificou uma ressalva média: as assertions da resposta do depósito ocorrem antes da entrada no `try/finally`. Se o depósito for efetivado, mas o contrato da resposta divergir, a limpeza pode não ser executada e afetar testes posteriores no mesmo processo.
- As consultas posteriores ao depósito e à restauração extraem o saldo sem validar previamente status HTTP e Content-Type, reduzindo a precisão do diagnóstico em caso de falha.
- No encerramento do Quarkus foram observados os avisos já conhecidos de acesso do JBoss Threads a `java.lang`, `NoClassDefFoundError` associado ao encerramento, self-attach do Mockito e uso de `sun.misc.Unsafe` pelo Netty. Eles ocorreram depois dos testes e não alteraram o resultado `BUILD SUCCESS`.
- O aviso de conversão futura de LF para CRLF foi observado na verificação do arquivo novo, sem erro de whitespace.

## 6. Fluxos pendentes

Continuam pendentes os demais cenários planejados:

- saque e transferência com atualização dos saldos;
- bloqueio administrativo seguido de operação recusada;
- desbloqueio administrativo seguido de operação permitida;
- operação bancária seguida de consulta da auditoria;
- login inválido retornando `401`;
- cliente tentando rota administrativa e recebendo `403`.

A etapa de testes de sistema somente deverá ser marcada como concluída após a implementação, revisão e evidência desses fluxos.
