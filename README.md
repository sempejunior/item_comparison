# Item Comparison API — Mercado Livre Challenge

Backend RESTful para a feature de comparação de produtos descrita no
desafio *Item Comparison V2* do Mercado Livre. Este repositório está em
construção ativa; o que está presente reflete a **fase de especificação**
do projeto, antes da implementação propriamente dita.

> Status atual: **specs aprovadas, implementação em andamento.** O
> trilho de tasks atômicas está em
> [`docs/TASKS.md`](./docs/TASKS.md); o histórico de commits no `main`
> reflete o progresso real.

## Visão geral

A API expõe três operações de leitura sobre um catálogo simulado:

1. **Detalhes de um produto** — `GET /api/v1/products/{id}` com sparse
   fieldsets opcionais.
2. **Comparação de N produtos (2 a 10)** — `GET /api/v1/products/compare`
   com cálculo determinístico de diferenças e *summary* opcional em
   linguagem natural via LLM, com fallback silencioso quando o LLM não
   está disponível.
3. **Listagem com paginação** — `GET /api/v1/products` para alimentar
   pickers de UI.

O modelo de domínio adota a abstração **`CatalogProduct + Offer`** que
o Mercado Livre real usa: um produto canônico com N ofertas (uma por
seller) e um *buy-box* derivado deterministicamente. Os campos do
desafio (`size`, `weight`, `color`, `battery`, `camera`, etc.) vivem
em um mapa flexível `attributes`, permitindo categorias específicas
(smartphone, livro, geladeira) sem combinatorial class explosion.

## Stack

- **Java 21** + **Spring Boot 3.3.5** + **Maven**
- **H2 in-memory** (modo PostgreSQL) com **Spring Data JPA**
- **Caffeine** para cache de produtos e respostas LLM
- **Spring AI** abstraindo OpenAI (chat) — *somente para o `summary`
  opcional; ausência de chave gera fallback determinístico*
- **springdoc-openapi** → Swagger UI em `/swagger-ui.html`
- **Spring Boot Actuator** + Micrometer
- **JUnit 5** + **AssertJ** + **MockMvc**, cobertura via **JaCoCo** ≥ 80 %

## Como construímos isto: Spec-Driven Development

O diferencial deste projeto é o método. Em vez de pular para código, o
trabalho seguiu — e seguirá — um trilho explícito de SDD:

```
SPECIFY  →  PLAN  →  DECIDE (ADRs)  →  TASKS  →  IMPLEMENT  →  VERIFY  →  EVOLVE
```

Cada fase só começa com a anterior aprovada. Mudanças encontradas durante
a implementação retornam para a spec, são re-versionadas, e re-fluem.
Documentos não são retrofitados.

A pasta [`docs/`](./docs/) contém o trilho completo. A ordem de leitura
recomendada para um avaliador é:

1. [`docs/README.md`](./docs/README.md) — metodologia e índice
2. [`docs/specs/001-item-comparison.md`](./docs/specs/001-item-comparison.md)
   — *o que* e *por que*
3. [`docs/specs/002-product-domain-model.md`](./docs/specs/002-product-domain-model.md)
   — modelo de dados e invariantes
4. [`docs/specs/003-api-contract.md`](./docs/specs/003-api-contract.md)
   — contrato HTTP, RFC 7807, exemplos curl
5. [`docs/specs/004-ai-features.md`](./docs/specs/004-ai-features.md)
   — política de fallback, métricas, prompt versionado
6. [`docs/roadmap.md`](./docs/roadmap.md) — evolução para produção
   (busca semântica, embeddings em escala, hybrid retrieval,
   resilience/SLO, multi-region)

## Decisões arquiteturais de destaque

- **`CatalogProduct + Offer`** em vez de `Product` simples. Decisão
  detalhada em SPEC-002 §1–§3.
- **Comparação híbrida**: camada determinística (`differences[]`) sempre
  presente; camada LLM (`summary`) opcional com timeout 2 s, cache, e
  fallback silencioso. SPEC-001 §5.2, SPEC-004 §6.
- **`differences[]` em forma enxuta** — apenas atributos que diferem,
  com `winnerId` quando comparável. SPEC-003 §2.3.
- **Busca semântica fora do escopo v1**, deliberadamente. O desafio
  pede *comparação*, não *busca*. A pipeline RAG completa
  (embeddings + vector store + LLM rerank + escala via outbox/Kafka)
  está descrita com o mesmo rigor no roadmap (R-2.0 → R-2.5, R-3),
  sinalizando consciência sem overreach. Veja
  [`docs/roadmap.md`](./docs/roadmap.md) §R-2.
- **Layered architecture** respeitando o esqueleto fixo do desafio
  (`controller / service / repository / model / exception`); zero
  lógica de negócio em controllers; Bean Validation em DTOs;
  `BuyBoxSelector` e `DifferencesCalculator` como funções puras
  testáveis em isolamento.

## Como rodar (uma vez implementado)

```bash
# build + testes + cobertura
mvn verify

# subir a aplicação
mvn spring-boot:run

# documentação interativa
open http://localhost:8080/swagger-ui.html
```

A aplicação boota em < 10 s sem chave OpenAI. Para ativar o `summary`
LLM:

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

A ausência da chave **não** quebra nenhum endpoint — apenas o campo
`summary` deixa de aparecer em respostas de comparação, conforme
documentado no contrato.

## Estrutura do repositório

```
.
├── README.md                    este arquivo
├── pom.xml                      Spring Boot 3.3.5, Java 21
├── docs/
│   ├── README.md                metodologia SDD
│   ├── specs/                   SPEC-001..004
│   └── roadmap.md               evolução para produção
└── src/
    ├── main/
    │   ├── java/com/hackerrank/sample/
    │   │   ├── Application.java
    │   │   ├── controller/
    │   │   ├── exception/
    │   │   ├── model/
    │   │   ├── repository/
    │   │   └── service/
    │   └── resources/
    │       ├── application.yml
    │       ├── seed/catalog.json
    │       └── prompts/compare-summary.v1.md
    └── test/                    espelho da estrutura de main/
```

## O que não está aqui (e onde foi parar)

| Item                                            | Onde                  |
|-------------------------------------------------|-----------------------|
| Autenticação / autorização                      | não planejado         |
| Operações de escrita (POST/PUT/DELETE)          | não planejado         |
| Banco persistente                               | roadmap R-1           |
| Busca semântica (`/search` com embeddings)      | roadmap R-2.0 / R-3   |
| Filter extraction por LLM                       | roadmap R-3           |
| Rate limiting                                   | roadmap R-6           |
| Multi-currency / FX                             | roadmap R-8           |
| Multi-tenant / per-vertical prompts             | roadmap R-4           |

A omissão é deliberada e rastreada — não é dívida silenciosa.
