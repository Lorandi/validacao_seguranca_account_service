# Evidência dos Testes de Sistema

Este documento registra incrementalmente os testes executados contra o artefato empacotado do Account Service. A etapa de sistema está em andamento, com os fluxos financeiros e de auditoria implementados e validados; administração e segurança permanecem pendentes.

## 1. Estratégia

Os testes de sistema usam `@QuarkusIntegrationTest`, RestAssured e Maven Failsafe. A aplicação é empacotada e iniciada em processo separado, sendo exercitada como caixa-preta somente por endpoints HTTP públicos.

Não há injeção CDI, acesso a repositórios, transações de teste, mocks ou conexão JDBC pelo teste. O processo usa H2 em memória com recriação do esquema e dos dados iniciais na inicialização, sem acessar banco externo.

## 2. Fluxos validados

Classe: `src/test/java/br/edu/ifrs/account/system/AccountSystemIT.java`

| Cenário | Validações observáveis |
| --- | --- |
| Login, consulta e depósito na aplicação empacotada | Login real do usuário `customer`; JWT Bearer emitido e aceito; consulta da conta `ACC1002`; depósito de 10,00; saldo posterior igual ao inicial mais 10,00 |
| Saque | Saque de 17,25 em `ACC1002`; saldo posterior igual ao inicial menos 17,25 |
| Transferência | Transferência de 25,50 de `ACC1002` para `ACC1004`; débito e crédito confirmados por consultas autenticadas dos dois proprietários |
| Auditoria | Consulta como `auditor` pelo endpoint de conta; registro localizado por ação, resultado, proprietário, origem, destino e valor, com `operationId` e timestamp presentes e erro nulo |
| Restauração pela interface pública | Depósito revertido por saque; saque revertido por depósito; transferência revertida pelo proprietário de `ACC1004`; saldos finais iguais aos valores capturados no início de cada método |

Os fluxos também confirmaram que o artefato empacotado inicia com as configurações e as chaves JWT de `src/main/resources`. Os registros de auditoria das operações e das restaurações permanecem somente no H2 do processo e são descartados quando a aplicação de teste é encerrada.

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
| Failsafe — sistema | 3 | 0 | 0 | 0 |
| **Total** | **163** | **0** | **0** | **0** |

O Maven terminou com `BUILD SUCCESS`, o PMD apresentou zero violações e os limites de cobertura existentes foram atendidos. O relatório Failsafe registrou três execuções de `AccountSystemIT` em aproximadamente 11,36 segundos.

Relatórios transitórios gerados:

- `target/failsafe-reports/br.edu.ifrs.account.system.AccountSystemIT.txt`;
- `target/failsafe-reports/TEST-br.edu.ifrs.account.system.AccountSystemIT.xml`;
- `target/failsafe-reports/failsafe-summary.xml`.

## 4. Cobertura

O relatório permaneceu em 354/359 linhas (98,6%) e 45/47 desvios (95,7%). Esses números representam os testes unitários e de componente.

A JVM externa iniciada por `@QuarkusIntegrationTest` não está instrumentada pela configuração JaCoCo atual. Portanto, esta execução comprova o comportamento do artefato empacotado, mas não deve ser apresentada como incremento de cobertura de código.

## 5. Review e avisos

- As ressalvas do primeiro lote foram corrigidas: as operações aceitas são restauradas em `finally`, e as consultas de conta validam HTTP 200 e Content-Type JSON antes da desserialização.
- O review do segundo lote identificou que `String.valueOf(null)` permitiria um `operationId` ausente. A validação passou a exigir explicitamente valor não nulo e não vazio; o build foi reproduzido depois da correção.
- No encerramento do Quarkus foram observados os avisos já conhecidos de acesso do JBoss Threads a `java.lang`, `NoClassDefFoundError` associado ao encerramento, self-attach do Mockito e uso de `sun.misc.Unsafe` pelo Netty. Eles ocorreram depois dos testes e não alteraram o resultado `BUILD SUCCESS`.
- O aviso de conversão futura de LF para CRLF foi observado na verificação do arquivo, sem erro de whitespace.

## 6. Fluxos pendentes

Continuam pendentes os demais cenários planejados:

- bloqueio administrativo seguido de operação recusada;
- desbloqueio administrativo seguido de operação permitida;
- login inválido retornando `401`;
- cliente tentando rota administrativa e recebendo `403`.

A etapa de testes de sistema somente deverá ser marcada como concluída após a implementação, revisão e evidência desses fluxos.
