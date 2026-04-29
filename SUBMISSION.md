---
id: SUBMISSION
title: HackerRank submission manifest
version: v2
status: Living document
last_updated: 2026-04-29
depends_on: [SPEC-001, SPEC-002, SPEC-003, SPEC-004, PLAN, TASKS, ADR-0001, ADR-0003, ADR-0004]
---

# Manifesto de submissão — HackerRank

Este arquivo é o **único trilho** para a entrega. O HackerRank deste
desafio aceita submissão via **editor de arquivos** (cola classe por
classe na UI deles), formalizado em **ADR-0003**. Conforme o PLAN
avança e os arquivos são implementados localmente, este documento é
atualizado: cada arquivo sai de `pending` para `ready` (pronto para
copiar) e depois `submitted` (já colado na UI do HackerRank).

> **Como usar.** Quando for colar no HackerRank, leia este manifesto de
> cima para baixo. A ordem é desenhada para evitar erros de compilação
> (modelos antes de quem os usa). Marque cada linha como `submitted`
> aqui no arquivo após colar. Se algum arquivo for *re-editado*
> localmente após colado, ele volta para `ready` (precisa re-colar).

## 1. Skeleton fixo do HackerRank

```
com.hackerrank.sample
├── Application.java
├── controller/
├── exception/
├── model/
├── repository/
└── service/
```

Não criar pacotes fora dessa árvore. Toda a qualidade tem que caber
nesse layout — é uma restrição do desafio.

## 2. Estado dos artefatos

### 2.1 Documentação (não vai para o HackerRank, mas é a fonte de verdade)

| Arquivo                                     | Status   |
|---------------------------------------------|----------|
| `docs/README.md`                            | ready    |
| `docs/specs/001-item-comparison.md` (v5)    | ready    |
| `docs/specs/002-product-domain-model.md` (v5)| ready   |
| `docs/specs/003-api-contract.md` (v2)       | ready    |
| `docs/specs/004-ai-features.md` (v3)        | ready    |
| `docs/roadmap.md` (v2)                      | ready    |
| `docs/plan.md` (v2)                         | ready    |
| `docs/adrs/0001-…` (Accepted, revived)      | ready    |
| `docs/adrs/0002-…` (Superseded by 0003)     | ready    |
| `docs/adrs/0003-…` (paste-by-paste lock)    | ready    |
| `docs/adrs/0004-…` (BuyBox heuristic)       | ready    |
| `docs/TASKS.md` (v1)                        | ready    |

### 2.2 Configuração e build

| Arquivo                                     | Local | Status   | Nota                                                  |
|---------------------------------------------|-------|----------|-------------------------------------------------------|
| `pom.xml`                                   | sim   | ready    | T-01 fechado (2026-04-29): Boot 3.3.5, Java 21, JPA, H2, Caffeine, validation, **aop**, Spring AI 1.0.0-M3 (BOM), springdoc 2.6.0, JaCoCo plugin (gate ativado em T-10). |
| `src/main/resources/application.yml`        | sim   | partial  | H2 + Caffeine + Actuator + Swagger base. Vai ganhar `app.ai.*`. |
| `.env.example`                              | sim   | ready    | Documenta `OPENAI_API_KEY`.                           |
| `.gitignore`                                | sim   | ready    | Inclui `.env`, secrets, build outputs.                |

### 2.3 Código Java (o que efetivamente vai colado no HackerRank)

Ordem alinhada com `docs/TASKS.md` (T-01..T-23) e `docs/plan.md` §12.
Bottom-up: enums → converter → entidades → repositórios → DTOs →
problem details → service utils (puros) → service principal →
exceptions → exception handler → controllers → AI surface → seed.
Cada arquivo colado compila com o que já está no editor.

#### `com.hackerrank.sample` (raiz)

| Ordem | Arquivo                       | Status   | Origem / Task                          |
|-------|-------------------------------|----------|----------------------------------------|
| 1     | `Application.java`            | ready    | já existe (`@SpringBootApplication + @EnableCaching`) |

#### `model/` — DTOs públicos + enums (entidades vão em `repository/`)

| Ordem | Arquivo                              | Status   | Origem / Task                           |
|-------|--------------------------------------|----------|-----------------------------------------|
| 2     | `Category.java` (enum)               | ready    | SPEC-002 §2.1 / T-03                    |
| 3     | `Condition.java` (enum)              | ready    | SPEC-002 §3.1 / T-03                    |
| 4     | `Language.java` (enum)               | pending  | SPEC-001 FR-8 / T-03                    |
| 5     | `ProductSummary.java` (record)       | pending  | SPEC-003 §2.1 / T-07                    |
| 6     | `BuyBox.java` (record)               | pending  | SPEC-002 §3.3 / T-07                    |
| 7     | `ProductDetail.java` (record)        | pending  | SPEC-003 §2.2 / T-07                    |
| 8     | `CompareItem.java` (record)          | pending  | SPEC-003 §2.3 / T-07                    |
| 9     | `DifferenceEntry.java` (record)      | pending  | SPEC-001 FR-7 / T-07                    |
| 10    | `CompareResponse.java` (record)      | pending  | SPEC-003 §2.3 / T-07                    |
| 11    | `PageResponse.java` (record)         | pending  | SPEC-003 §2.1 / T-07                    |
| 12    | `problem/ProblemDetail7807.java`     | pending  | SPEC-003 §4 / T-07                      |

#### `repository/` — entidades JPA, converter, repositórios, seed

| Ordem | Arquivo                              | Status   | Origem / Task                           |
|-------|--------------------------------------|----------|-----------------------------------------|
| 13    | `AttributesJsonConverter.java`       | ready    | SPEC-002 §5 / T-04                      |
| 14    | `CatalogProductEntity.java`          | pending  | SPEC-002 §2 / T-05                      |
| 15    | `OfferEntity.java`                   | pending  | SPEC-002 §3 / T-05                      |
| 16    | `CatalogProductRepository.java`      | pending  | SPEC-002 §5 / T-06                      |
| 17    | `OfferRepository.java`               | pending  | SPEC-002 §5 / T-06                      |
| 18    | `seed/SeedLoader.java`               | pending  | SPEC-002 §5,§6 / T-09                   |

#### `service/` — utilitários puros + serviços

| Ordem | Arquivo                              | Status   | Origem / Task                           |
|-------|--------------------------------------|----------|-----------------------------------------|
| 19    | `compare/BuyBoxSelector.java`        | pending  | SPEC-002 §3.3, ADR-0004 / T-08          |
| 20    | `compare/FieldSetProjector.java`     | pending  | SPEC-001 FR-3, SPEC-003 §3 / T-12       |
| 21    | `compare/NumericValue.java`          | pending  | SPEC-003 §2.3 / T-13                    |
| 22    | `compare/DifferencesCalculator.java` | pending  | SPEC-001 FR-7 / T-13                    |
| 23    | `ProductService.java`                | pending  | SPEC-001 FR-1/4/5 / T-10                |
| 24    | `compare/CompareService.java`        | pending  | SPEC-001 FR-2/7 / T-14                  |
| 25    | `ai/AiMetrics.java`                  | pending  | SPEC-004 §7 / T-18                      |
| 26    | `ai/PromptLoader.java`               | pending  | SPEC-004 §4 / T-17                      |
| 27    | `ai/DailyBudget.java`                | pending  | SPEC-004 §8 / T-19                      |
| 28    | `ai/SummaryService.java`             | pending  | SPEC-004 §2/§5/§6 / T-19                |

#### `exception/`

| Ordem | Arquivo                                 | Status   | Origem / Task                        |
|-------|-----------------------------------------|----------|--------------------------------------|
| 29    | `ProductNotFoundException.java`         | pending  | FR-11 / T-15                         |
| 30    | `ProductsNotFoundException.java`        | pending  | FR-11 (carries `missingIds`) / T-15  |
| 31    | `InvalidFieldsException.java`           | pending  | FR-3 / T-15                          |
| 32    | `InvalidCompareRequestException.java`   | pending  | FR-10 / T-15                         |
| 33    | `InvalidLanguageException.java`         | pending  | FR-8 / T-15                          |

#### `controller/`

| Ordem | Arquivo                                 | Status   | Origem / Task                        |
|-------|-----------------------------------------|----------|--------------------------------------|
| 34    | `advice/GlobalExceptionHandler.java`    | pending  | FR-12, NFR-4 / T-15                  |
| 35    | `ProductController.java`                | pending  | SPEC-003 §2.1/2.2 / T-11             |
| 36    | `CompareController.java`                | pending  | SPEC-003 §2.3 / T-16, T-20           |

### 2.4 Resources (não-código)

| Ordem | Arquivo                                                  | Status   | Origem / Task            |
|-------|----------------------------------------------------------|----------|--------------------------|
| 37    | `src/main/resources/application.yml` (final)             | partial  | NFR-5/8, SPEC-004 §3 / T-02 |
| 38    | `src/main/resources/seed/catalog.json`                   | pending  | SPEC-002 §5 / T-09        |
| 39    | `src/main/resources/prompts/compare-summary.v1.md`       | pending  | SPEC-004 §4 / T-17        |

### 2.5 Testes

Coverage gate: ≥ 80 % nos pacotes `controller`, `service`, `repository`
(NFR-2). Testes acompanham o arquivo de produção na mesma task — não
existem tasks "tests later".

| Arquivo                                                                | Status  | Task | Cobre                                          |
|------------------------------------------------------------------------|---------|------|------------------------------------------------|
| `service/compare/BuyBoxSelectorTest.java`                              | pending | T-08 | SPEC-002 §3.3, ADR-0004                        |
| `service/compare/FieldSetProjectorTest.java`                           | pending | T-12 | SPEC-003 §3, AC-3, AC-4                        |
| `service/compare/NumericValueTest.java`                                | pending | T-13 | parse + unit equality                          |
| `service/compare/DifferencesCalculatorTest.java`                       | pending | T-13 | FR-7, currency mismatch, ordering              |
| `service/compare/CompareServiceTest.java`                              | pending | T-14 | FR-2, FR-10, FR-11                             |
| `service/ProductServiceTest.java`                                      | pending | T-10 | FR-1, FR-4, cache hit                          |
| `service/ai/PromptLoaderTest.java`                                     | pending | T-17 | SPEC-004 §4 + golden prompt                    |
| `service/ai/AiMetricsTest.java`                                        | pending | T-18 | SPEC-004 §7                                    |
| `service/ai/SummaryServiceTest.java`                                   | pending | T-19 | SPEC-004 §6 (every fallback row), cache_hit    |
| `controller/ProductControllerTest.java`                                | pending | T-11 | AC-1, MockMvc list + get-by-id                 |
| `controller/CompareControllerTest.java`                                | pending | T-16, T-20 | AC-2, AC-3, AC-4, AC-5, AC-6, AC-7        |
| `controller/advice/GlobalExceptionHandlerTest.java`                    | pending | T-15 | FR-12, NFR-4, every PLAN §4 row                |
| `repository/AttributesJsonConverterTest.java`                          | pending | T-04 | SPEC-002 §5, INV-4                             |
| `repository/CatalogProductEntityTest.java`                             | pending | T-05 | SPEC-002 INV-1..INV-4                          |
| `repository/OfferEntityTest.java`                                      | pending | T-05 | SPEC-002 INV-5..INV-8                          |
| `repository/CatalogProductRepositoryTest.java`                         | pending | T-06 | `findAllByCategory`, `findAllByIdIn`           |
| `repository/seed/SeedLoaderTest.java`                                  | pending | T-09 | seed counts + edge cases + fail-loud           |
| `model/LanguageTest.java`                                              | pending | T-03 | tag round-trip + unknown                       |

> **Nota sobre HackerRank vs. testes locais.** O HackerRank costuma
> rodar uma bateria interna de testes próprios — os nossos testes
> garantem qualidade local mas não são, em geral, parte do que precisa
> ser colado na UI do desafio. Confirmar antes de submeter: se a UI
> tem aba de "tests", colar; se não, manter só localmente. Atualizar
> esta nota quando tivermos certeza.

## 3. Ordem de cópia recomendada (sequencial)

A primeira coluna da tabela 2.3 numera 1..36 e a §2.4 numera 37..39.
Seguindo essa ordem, cada arquivo colado tem todas as suas
dependências já presentes no editor do HackerRank. Pontos de atenção:

- **Antes da ordem 1**: garantir que `pom.xml` (T-01) no editor do
  HackerRank bate com o local — Spring Boot 3.3.5 + Spring AI +
  Caffeine + springdoc + JPA + H2 + Validation + JaCoCo. Se a UI não
  permitir editar `pom.xml`, validar que o classpath deles resolve
  as deps adicionadas; senão, abrir issue antes de submeter.
- **Após ordem 12**: todos os DTOs públicos prontos — pode pular para
  o `application.yml` (37) sem prejuízo.
- **Após ordem 18**: o `SeedLoader` boota o catálogo; `catalog.json`
  (38) pode ser colado a qualquer momento depois daqui.
- **Antes da ordem 28** (SummaryService): `prompts/compare-summary.v1.md`
  (39) precisa estar disponível como recurso, senão `PromptLoader`
  falha no boot.
- **Após ordem 36**: aplicação inteira boota. Validar `mvn
  spring-boot:run` localmente com e sem `OPENAI_API_KEY`, depois colar.

## 4. Checklist final antes de submeter

- [ ] `mvn verify` passa localmente (lint + testes + JaCoCo ≥ 80 %)
- [ ] `mvn spring-boot:run` boota em < 10 s sem `OPENAI_API_KEY`
- [ ] `curl` em todos os exemplos de SPEC-003 §7 retorna o esperado
- [ ] `/swagger-ui.html` lista todos os endpoints com payloads de exemplo
- [ ] Todos os arquivos da §2.3 deste manifesto marcados como `submitted`
- [ ] `README.md` raiz preenchido com setup, decisões e link para `docs/`
- [ ] `OPENAI_API_KEY` **não** está em nenhum arquivo do repo
      (`grep -r 'sk-' .` retorna vazio)

## 5. Convenções deste manifesto

- **Status**:
  - `pending` — ainda não implementado localmente.
  - `partial` — existe parcialmente; ainda vai sofrer mudanças.
  - `ready` — implementado, testado, pronto para colar.
  - `submitted` — colado na UI do HackerRank na sessão de submissão atual.
  - `outdated` — estava `submitted`, mas o arquivo local foi alterado;
    precisa re-colar.
- A coluna **Ordem** em §2.3/§2.4 é a sequência de cópia. Não pular.
- Cada task em `docs/TASKS.md` que tocar um arquivo deve atualizar o
  status aqui antes de fechar.

## 6. Changelog

- **v2 (2026-04-29)** — §2.1 alinhado com PLAN v2, ADRs 0001/0003/0004
  e TASKS v1; SPECs em v5/v5/v2/v3. §2.3 reescrita: DTOs em `model/`,
  entidades em `repository/` (skeleton do HackerRank força o split);
  `service/compare/` e `service/ai/` como sub-pacotes; `controller/
  advice/GlobalExceptionHandler.java` e `CompareController.java`
  separados. Numeração de cópia 1..36 (código) + 37..39 (resources)
  alinhada com `TASKS.md` (T-01..T-23). §2.5 expandida com mapeamento
  arquivo-de-teste → task. §3 atualizada para a nova numeração.
  Header bumpado para v2; `depends_on` adicionado.
- **v1 (2026-04-28)** — Manifesto inicial. Reflete escopo de SPEC-001 v3
  + SPEC-002 v3 + SPEC-003 v2 + SPEC-004 v1. Sem código implementado
  ainda; todos os artefatos de §2.3 e §2.5 estão `pending`.
