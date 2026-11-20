# Análise Estática — Account Service

Este documento registra a configuração e os resultados **realmente executados** da análise estática com PMD. Os dados abaixo foram obtidos em 20/11/2026.

## 1. Ferramenta e versões

| Item | Versão |
| --- | --- |
| Maven PMD Plugin | 3.28.0 |
| Motor PMD (`pmd-core` e `pmd-java`) | 7.29.0 |
| Java do projeto (`maven.compiler.release`) | 25 |
| JDK utilizado na execução | OpenJDK 26.0.2 |

O plugin 3.28.0 traz o PMD 7.17.0 por padrão. O motor foi fixado em 7.29.0 por meio de dependências do plugin (`pmd-engine.version` no `pom.xml`) por dois motivos:

- o PMD 7.17.0 falhou ao ler as classes do JDK 26 (`Unsupported class file major version 70`), o que degradava a resolução de tipos usada por várias regras;
- a versão fixa garante o mesmo conjunto de regras e o mesmo comportamento em qualquer máquina.

Com o PMD 7.29.0 essa mensagem de erro não ocorre mais.

## 2. Regras configuradas

O arquivo [`config/pmd/ruleset.xml`](../config/pmd/ruleset.xml) referencia **81 regras individuais**, sem regras genéricas por categoria e sem nenhuma supressão (`exclude`, `@SuppressWarnings` ou `NOPMD`):

| Categoria | Regras | Exemplos |
| --- | --- | --- |
| `errorprone` | 30 | `EmptyCatchBlock`, `CloseResource`, `CompareObjectsWithEquals`, `ReturnEmptyCollectionRatherThanNull`, `ImplicitSwitchFallThrough` |
| `bestpractices` | 30 | `UnusedPrivateField`, `UnusedLocalVariable`, `UnusedAssignment`, `PreserveStackTrace`, `MissingOverride`, `LooseCoupling`, `NonExhaustiveSwitch` |
| `performance` | 21 | `AddEmptyString`, `ConsecutiveAppendsShouldReuse`, `StringInstantiation`, `UselessStringValueOf`, `TooFewBranchesForSwitch` |

Foram deliberadamente **excluídas** do conjunto:

- regras marcadas como obsoletas ou programadas para remoção no PMD 8 (por exemplo `AvoidCatchingNPE`, `CheckSkipResult`, `AvoidLosingExceptionInformation` e `UselessOperationOnImmutable`);
- nomes antigos de regras renomeadas, substituídos pelos novos (`DefaultLabelNotLastInSwitch`, `NonExhaustiveSwitch` e `ReplaceJavaUtilVector`).

A execução final não emite nenhum aviso de regra obsoleta.

A análise cobre apenas o código de produção (`src/main/java`); `includeTests` está desativado.

## 3. Integração com o Maven

No `pom.xml`, o `maven-pmd-plugin`:

- usa o ruleset `config/pmd/ruleset.xml`;
- gera o relatório em formato XML (`target/pmd.xml`) e o HTML (`target/reports/pmd.html`);
- executa o goal `check` na fase `verify`, que antes executa `pmd:pmd`;
- falha o build para **qualquer violação** (`failOnViolation=true` e `failurePriority=5`);
- não usa cache de análise (`analysisCache=false`), para que cada execução seja reproduzível.

## 4. Comandos de execução

```powershell
# Gera o relatório sem falhar o build
.\mvnw.cmd pmd:pmd

# Falha o build se houver violação (usa o relatório gerado)
.\mvnw.cmd pmd:check

# Build completo, incluindo PMD e JaCoCo
.\mvnw.cmd clean verify
```

## 5. Resultado real da execução

Todos os comandos foram executados na ordem abaixo, com `BUILD SUCCESS` em cada um:

| Ordem | Comando | Resultado |
| --- | --- | --- |
| 1 | `.\mvnw.cmd clean compile` | `BUILD SUCCESS` — 26 classes compiladas com `release 25` |
| 2 | `.\mvnw.cmd pmd:pmd` | `BUILD SUCCESS` — PMD 7.29.0, **0 violações** |
| 3 | `.\mvnw.cmd pmd:check` | `BUILD SUCCESS` — **0 violações** |
| 4 | `.\mvnw.cmd clean verify` | `BUILD SUCCESS` — PMD, JaCoCo e demais fases executados |

### Violações encontradas

**Nenhuma.** O código atual (26 classes em `src/main/java`) não apresentou violações das 81 regras configuradas. Por isso, **nenhuma correção de código foi necessária** e nenhuma violação foi mantida ou justificada. Nenhum arquivo de `src/main` foi alterado nesta etapa.

### Validação de que o PMD detecta violações

Para garantir que o resultado "0 violações" não era um falso positivo da configuração, foi criada **temporariamente** uma classe com um campo privado não utilizado. O `pmd:check` falhou com o esperado:

```text
PMD Failure: br.edu.ifrs.account.TmpPmdSanity:4 Rule:UnusedPrivateField Priority:3
BUILD FAILURE — PMD 7.29.0 has found 1 violation.
```

A classe temporária foi removida logo em seguida e não faz parte do repositório.

## 6. Localização dos relatórios

Os relatórios ficam em `target/`, que não é versionado:

| Relatório | Caminho |
| --- | --- |
| PMD (XML) | `target/pmd.xml` |
| PMD (HTML) | `target/reports/pmd.html` |

Conforme o [plano de teste](plano-de-teste.md), cópias selecionadas serão guardadas em `docs/evidencias/` na etapa de consolidação.

## 7. Observações

- **Antivírus e certificados:** em uma máquina com antivírus que intercepta HTTPS (Norton), o Maven falhou ao baixar dependências novas com `PKIX path building failed`, porque o JDK não confia na CA do antivírus. Para a execução foi usado, somente na sessão do terminal, `$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"`. Nenhum arquivo do projeto foi alterado por causa disso.
- **Aviso do PMD sobre `jrt-fs.jar`:** com o JDK 26 e o código em Java 25, o PMD avisa que a plataforma atual foi adicionada ao `auxClasspath` e pode ser a versão errada. É apenas um aviso; a análise é concluída normalmente. Usar um JDK 25 elimina o aviso.
- **Chave privada de desenvolvimento versionada:** `src/main/resources/privateKey.pem` está no repositório. Isso foi apenas registrado, sem alteração, conforme o escopo desta etapa. Em um ambiente real, a chave deveria ser gerada por ambiente e mantida fora do Git.
