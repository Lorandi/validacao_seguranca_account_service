# Evidência dos Testes de Sistema

Testes executados contra o artefato empacotado do Account Service, validados em 20/11/2026.

## 1. Estratégia

Usam `@QuarkusIntegrationTest`, RestAssured e Maven Failsafe. A aplicação é empacotada, iniciada em processo separado (H2 em memória, esquema e dados recriados a cada início) e exercitada como caixa-preta somente por endpoints HTTP públicos, sem CDI, repositórios, transações ou mocks. Por se tratar de uma API, não se aplicam ferramentas de interface como Selenium, Cypress ou Playwright.

## 2. Fluxos validados

Classes: `AccountSystemIT` e `AccountAdminSecurityIT`, em `src/test/java/br/edu/ifrs/account/system/`.

| Cenário | Validações |
| --- | --- |
| Login, consulta e depósito | Login do `customer`; JWT aceito; consulta de `ACC1002`; depósito de 10,00 reflete no saldo |
| Saque | Saque de 17,25 em `ACC1002` reduz o saldo no valor solicitado |
| Transferência | 25,50 de `ACC1002` para `ACC1004`; débito e crédito confirmados por consultas dos dois proprietários |
| Auditoria | O `auditor` localiza o registro da operação (ação, resultado, proprietário, origem, destino, valor, `operationId` e timestamp) |
| Bloqueio e recusa | O administrador bloqueia `ACC1004`; o saque do proprietário retorna `409` e o saldo é preservado |
| Desbloqueio | O administrador desbloqueia `ACC1004`; o saque passa a retornar `200` |
| Login inválido | Senha incorreta retorna `401` com `errorId`, sem token nem exposição da senha ou de `passwordHash` |
| Autorização por perfil | `CUSTOMER` em `GET /api/v1/accounts` retorna `403` sem expor dados |

Os testes também confirmam que o artefato inicia com as configurações e chaves JWT de `src/main/resources`. As operações são revertidas pela própria API em `finally`; os registros de auditoria ficam só no H2 do processo e são descartados no encerramento.

## 3. Execução

Em 20/11/2026, com Azul Zulu JDK 25.0.5.1:

```powershell
.\mvnw.cmd clean verify
```

| Executor | Execuções | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| Surefire — unitários e componente | 160 | 0 | 0 | 0 |
| Failsafe — sistema | 6 | 0 | 0 | 0 |
| **Total** | **166** | **0** | **0** | **0** |

`BUILD SUCCESS`, PMD sem violações e limites JaCoCo atendidos. Relatórios transitórios em `target/failsafe-reports/`.

## 4. Cobertura e observações

- A JVM externa do `@QuarkusIntegrationTest` não é instrumentada pelo JaCoCo; esta etapa comprova o comportamento do artefato, não aumenta a cobertura (351/352 linhas, 99,7%, vem dos testes unitários e de componente).
- Os avisos de encerramento (JBoss Threads, self-attach do Mockito e `sun.misc.Unsafe` do Netty) ocorrem após os testes e não alteram o resultado.

## 5. Conclusão

Os seis métodos cobrem os sete fluxos planejados (login/consulta/depósito, saque, transferência, auditoria, ciclo de bloqueio e desbloqueio, login inválido e `403`). A etapa obrigatória está concluída; testes de segurança e carga são opcionais e não fazem parte desta evidência.
