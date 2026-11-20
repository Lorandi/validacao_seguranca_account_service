# Análise Estática — Account Service

Análise estática com PMD, integrada ao build Maven e executada em 20/11/2026.

## 1. Ferramenta

| Item | Versão |
| --- | --- |
| Maven PMD Plugin | 3.28.0 |
| Motor PMD (`pmd-core` e `pmd-java`) | 7.29.0 |
| Java do projeto | 25 |

O plugin 3.28.0 traz o PMD 7.17.0 por padrão. O motor foi fixado em 7.29.0 por dependências do plugin (`pmd-engine.version` no `pom.xml`), o que garante o mesmo conjunto de regras em qualquer máquina.

## 2. Regras

[`config/pmd/ruleset.xml`](../config/pmd/ruleset.xml) lista **81 regras individuais**, sem supressões (`exclude`, `@SuppressWarnings` ou `NOPMD`):

| Categoria | Regras | Exemplos |
| --- | --- | --- |
| `errorprone` | 30 | `EmptyCatchBlock`, `CloseResource`, `CompareObjectsWithEquals`, `ImplicitSwitchFallThrough` |
| `bestpractices` | 30 | `UnusedPrivateField`, `UnusedAssignment`, `PreserveStackTrace`, `MissingOverride`, `LooseCoupling` |
| `performance` | 21 | `AddEmptyString`, `ConsecutiveAppendsShouldReuse`, `StringInstantiation`, `TooFewBranchesForSwitch` |

Regras obsoletas ou removidas no PMD 8 ficaram de fora, e a execução não emite avisos de regra obsoleta. A análise cobre apenas o código de produção (`src/main/java`).

## 3. Integração com o build

O `maven-pmd-plugin` usa o ruleset acima e executa o goal `check` na fase `verify` (que antes gera o relatório com `pmd:pmd`). O build falha diante de qualquer violação.

```powershell
.\mvnw.cmd pmd:pmd        # gera o relatório (target/pmd.xml e target/reports/pmd.html)
.\mvnw.cmd pmd:check      # falha o build se houver violação
.\mvnw.cmd clean verify   # build completo, incluindo o PMD
```

## 4. Resultado

| Comando | Resultado |
| --- | --- |
| `.\mvnw.cmd clean compile` | `BUILD SUCCESS` — 26 classes compiladas com `release 25` |
| `.\mvnw.cmd pmd:check` | `BUILD SUCCESS` — **0 violações** |
| `.\mvnw.cmd clean verify` (JDK 25) | `BUILD SUCCESS` — 166 execuções de teste, PMD sem violações e limites JaCoCo atendidos |

**Violações encontradas: nenhuma.** As 26 classes de `src/main/java` não violaram as 81 regras, então nenhuma correção ou justificativa foi necessária.

**Validação do detector:** uma classe temporária com um campo privado não utilizado fez o `pmd:check` falhar como esperado (`TmpPmdSanity:4 Rule:UnusedPrivateField Priority:3 — BUILD FAILURE, 1 violation`). A classe foi removida em seguida.

Os relatórios ficam em `target/`, pasta não versionada; este documento registra os resultados.

## 5. Observações

- Com antivírus que intercepta HTTPS, o Maven pode falhar ao baixar dependências (`PKIX path building failed`). Solução usada, apenas na sessão: `$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"`.
- `src/main/resources/privateKey.pem` está versionada por ser uma chave de desenvolvimento. Em produção, a chave deveria ser gerada por ambiente e ficar fora do Git.
