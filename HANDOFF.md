# Prompt de continuidade — Desafio Mercado Livre (Item Comparison V2)

> **Como usar:** copie este arquivo inteiro como primeira mensagem da nova
> sessão do Claude. Ele se posiciona como contexto + instrução, não como
> documentação do projeto.

---

## 1. Quem você é nesta conversa

Você é meu parceiro de engenharia neste desafio de contratação do Mercado
Livre. Comunique-se em **português do Brasil**. Código, identificadores,
docs e mensagens de commit ficam em inglês. Sem emojis em código. Sem
comentários inline em código (apenas docstrings em classes/métodos quando
o intent não é óbvio). Tipagem explícita em tudo.

Aja como engenheiro sênior pragmático: respostas curtas e objetivas,
implementações enxutas, sem over-engineering. Para perguntas exploratórias
("o que acha?", "como evoluiríamos?"), responda em 2-3 frases com
recomendação + tradeoff principal antes de qualquer arquivo. Não mude
arquivos sem pedido explícito.

## 2. O desafio

**Item Comparison API V2 — Mercado Livre HackerRank challenge.** Backend
RESTful para uma feature de comparação de produtos.

Requisitos do enunciado (resumo):

- **API**: endpoints REST que devolvem detalhes de múltiplos itens para
  comparação. Campos mínimos: nome, imagem, descrição, preço, rating,
  especificações.
- **Modelo de produto**: id, name, description, price, size, weight, color
  como atributos comuns. Categorias específicas (smartphone) trazem campos
  especializados (battery, camera, memory, storage, brand, model, OS).
- **User can query specific comparisons and ignore other fields** — usuário
  filtra os campos relevantes.
- **Stack livre**, persistência local (JSON/CSV/H2/SQLite). Sem banco real
  obrigatório.
- **Não-funcionais valorizados**: error handling, documentação, testes.
- **Entregar README/diagrama** com decisões arquiteturais.

Esqueleto do HackerRank é fixo: pacote `com.hackerrank.sample` com
subpacotes `controller`, `exception`, `model`, `repository`, `service` +
`Application.java`. **Esse layout não pode mudar** — mas pode ser
preenchido com qualidade.

## 3. O que estamos construindo (escopo já decidido)

Mais ambicioso que o mínimo, dentro de 5 dias. Os destaques que escolhi
para diferenciar:

### 3.1 Modelo `CatalogProduct + Offer`
Em vez de `Product` único, modelamos como o ML real: `CatalogProduct`
canônico + N `Offer` (uma por seller) + `buyBox` derivado
(deterministicamente). Mostra entendimento de domínio.

### 3.2 Comparação híbrida
- **Camada determinística (sempre presente):** `differences[]` calculado
  no backend, marcando vencedor por atributo quando comparável
  numericamente, ordenado com diferenças no topo.
- **Camada LLM (opcional):** campo `summary` em linguagem natural
  ("X tem 651 mAh a mais de bateria mas pesa 4 g a mais"). Inline na
  resposta de `/compare`, com timeout 2 s e fallback silencioso (some o
  campo) quando LLM indisponível.

### 3.3 Busca semântica (Nível 2 — RAG com embeddings em memória)
Endpoint `/search?q=...` aceitando linguagem natural ("geladeira que
caiba em 60 cm até R$ 3000"). Pipeline:
1. Embedding da query via OpenAI (Spring AI).
2. Vector similarity em `SimpleVectorStore` (Spring AI, in-memory).
3. LLM rerank + explicação por item.

Embeddings calculados no boot a partir do seed; opcional persistir o
vector store em disco para evitar re-chamar OpenAI a cada subida. Sem
chave OpenAI, cai em busca lexical (regex em `name + description +
attributes`). Nunca retorna 503.

Filtros estruturados na busca (`category`, `maxPrice`) já entram em v1.
Extração de filtros por LLM (Nível 3) está documentada em
`docs/roadmap.md` como evolução.

### 3.4 Stack
- **Java 21 + Spring Boot 3.3.5 + Maven 3.9.9**
- **H2 in-memory** com Spring Data JPA (modo PostgreSQL)
- **Caffeine** para cache de produto e cache de respostas LLM
- **Spring AI** abstraindo OpenAI (chat + embedding)
- **springdoc-openapi 2.6.0** → Swagger UI
- **Spring Boot Actuator** + Micrometer para métricas
- **JaCoCo** para cobertura ≥ 80 %
- Testes: JUnit 5 + AssertJ + MockMvc

## 4. Metodologia: Spec-Driven Development (SDD)

Esta é a *narrativa* do projeto. O avaliador deve ler `docs/` e entender
o método antes de olhar código.

### 4.1 Fluxo

```
SPECIFY  →  PLAN  →  DECIDE (ADRs)  →  TASKS  →  IMPLEMENT  →  VERIFY  →  EVOLVE
```

Cada fase só começa quando a anterior é aprovada. Mudanças que aparecem
durante a implementação voltam para a spec/plan e re-fluem — documentos
são mantidos verdadeiros, não retrofitados. **EVOLVE = roadmap explícito**
com formato fixo: *Trigger → Approach → Architecture → Tradeoffs → Effort*.

### 4.2 Convenções

- Cada spec tem frontmatter com `version: vN` e seção `Changelog` no
  final. Mudanças semânticas bumpam a versão.
- Requisitos têm IDs únicos (`FR-1`, `NFR-3`, `AC-2`) para que tasks,
  testes e ADRs referenciem.
- Toda spec termina com `Open Questions` — perguntas resolvidas migram
  para o corpo e somem; pendentes bloqueiam fases seguintes.
- ADRs no formato MADR enxuto: contexto, decisão, alternativas,
  consequências.
- Roadmap é artefato de primeira classe — *qualquer feature em
  `roadmap.md` é justificativa válida para não fazer agora*.

## 5. Estado atual do projeto

### 5.1 Setup da máquina (já feito)
- Java 21 já estava instalado (`/usr/lib/jvm/java-21-openjdk-amd64`)
- Maven 3.9.9 instalado em `~/.local/opt/apache-maven-3.9.9`, symlink em
  `~/.local/bin/mvn`, PATH no `.bashrc`. **Sem sudo, sem mexer no
  sistema.**
- `mvn -version` funciona.

### 5.2 Diretório do projeto
`/home/carlos/Área de Trabalho/Dados/Workspace/projetos/InnovaNotes/Desafio Mercado Livre/`

```
.
├── HANDOFF.md                   ← este arquivo
├── pom.xml                      Spring Boot 3.3.5, Java 21
├── .gitignore                   inclui .env, .env.*, *.pem, *.key, secrets/
├── .env.example                 template para OPENAI_API_KEY
├── src/
│   ├── main/
│   │   ├── java/com/hackerrank/sample/
│   │   │   ├── Application.java         @SpringBootApplication + @EnableCaching
│   │   │   ├── controller/  (vazio, só .gitkeep)
│   │   │   ├── exception/   (vazio)
│   │   │   ├── model/       (vazio)
│   │   │   ├── repository/  (vazio)
│   │   │   └── service/     (vazio)
│   │   └── resources/application.yml    H2 + Caffeine + Actuator + Swagger
│   └── test/java/com/hackerrank/sample/  (vazio)
├── target/sample-1.0.0.jar      build OK (57 MB fat jar)
└── docs/
    ├── README.md                          metodologia + índice
    ├── roadmap.md                v1       8 itens R-1..R-8
    └── specs/
        ├── 001-item-comparison.md        v2  (precisa virar v3 — ver §6)
        ├── 002-product-domain-model.md   v2  (precisa virar v3 — ver §6)
        └── 003-api-contract.md            v1  (precisa rewrite v2 — ver §7)
```

### 5.3 `mvn -DskipTests package` passa.

## 6. Decisões pendentes de aplicar nas specs (eu já respondi, falta integrar)

Estas respostas vieram na última rodada. **Sua primeira tarefa é
incorporar nas specs e bumpar versão para v3.**

| ID | Pergunta | Resposta |
|----|----------|----------|
| SPEC-001 Q-1 | `summary` aceita `language` hint? | **Sim**, default `pt-BR`. |
| SPEC-001 Q-2 | Busca aceita filtros estruturados (`category`, `maxPrice`) já em v1? | **Sim, em v1.** |
| SPEC-001 Q-3 | Forma de `differences[]` — só os diferenciadores ou todos com flag? | **Opção A — só os atributos que diferem** (lean, sem duplicar `items[]`). Aguardando confirmação final do usuário; tratar como aprovado salvo objeção. |
| SPEC-002 Q-1 | `/compare` retorna `buyBox` por default? | **Sim**, `?fields=offers` opt-in para offers completas. |
| SPEC-002 Q-2 | Multi-currency no compare? | **Não fazer agora.** Mover para `roadmap.md` (R-8 já cobre). Não inflar v1. |

## 7. Próximos passos imediatos (a ordem importa)

1. **Bumpar SPEC-001 para v3** com Q-1, Q-2 resolvidas e Q-3 (Opção A)
   integradas ao corpo. Remover essas Open Questions, atualizar Changelog.
2. **Bumpar SPEC-002 para v3** com Q-1 resolvida e Q-2 referenciando o
   roadmap explicitamente.
3. **Reescrever SPEC-003 para v2** — contrato completo da API:
   - `GET /api/v1/products` (lista paginada, summary projection)
   - `GET /api/v1/products/{id}` (full + offers + buyBox + sparse fields)
   - `GET /api/v1/products/compare?ids=&fields=&language=` (com
     `differences[]` formato A + `summary` opcional)
   - `GET /api/v1/products/search?q=&category=&maxPrice=&topK=` (RAG +
     fallback lexical)
   - Erros RFC 7807 com slugs `validation`, `bad-request`, `not-found`,
     `internal`
   - Exemplos curl para cada endpoint
4. **Criar SPEC-004 — AI Features**:
   - Configuração Spring AI (chat + embedding clients)
   - Prompts versionados em `src/main/resources/prompts/` com
     `compare-summary.v1.md` e `search-rerank.v1.md`
   - Política de fallback (timeout, cache miss, sem chave, rate limit)
   - Cache de inferência (Caffeine, chave por hash de inputs)
   - Métricas Micrometer: `ai_calls_total{kind,outcome}`,
     `ai_latency_seconds{kind}`, `ai_tokens_total{kind,direction}`,
     `ai_fallback_total{reason}`
   - Estratégia de testes — mockar `ChatClient` e `EmbeddingModel` para
     testes determinísticos sem custo
   - Vector store: `SimpleVectorStore` em memória, snapshot em disco
     opcional (`./.cache/vectors.json`, no `.gitignore`)
   - Custo guard: `AI_TIMEOUT_MS` (default 2000), `AI_DAILY_REQUEST_LIMIT`
     (opcional)
5. **Pedir aprovação das specs v3+v2+004 antes de avançar para PLAN.**
6. **Plan + ADRs + Tasks**, depois implementação.

## 8. Plano de tempo (reference)

5 dias úteis disponíveis.
- Dia 1 (em andamento): specs + plan + ADRs + setup → ~70 % feito
- Dias 2-3: modelo + endpoints + testes + cache + OpenAPI
- Dia 4: features de IA (summary + busca semântica)
- Dia 5: README final, diagramas Mermaid, polimento, screenshots

## 9. Constraints e regras invioláveis

- **Skeleton de pacotes do HackerRank é fixo.** Não mover classes para
  outros pacotes. Toda a qualidade tem que caber nesse layout.
- **OPENAI_API_KEY nunca vai para o repo.** O usuário compartilhou uma
  chave no chat anterior; ela já foi tratada como comprometida e o
  usuário deve revogá-la na OpenAI. Sempre via env var; `.env` está no
  `.gitignore`. README orienta o avaliador a setar a env var; sem ela,
  fallback heurístico é o caminho default.
- **Nada de comentários inline em código.** Só docstrings de classes/
  métodos quando o intent não for óbvio.
- **Type hints em tudo.** Bean Validation nos DTOs.
- **Sem features que não foram pedidas.** Se aparecer ideia nova, ou
  vai pra spec, ou vai pra roadmap.
- **Sem `Co-Authored-By: Claude`** ou menção a IA em commits / PRs.
  Mensagens de commit em inglês, focadas no *porquê*.
- **Git: só commit/push quando o usuário pedir explicitamente.** `git
  status/diff/log/add` livres.

## 9.1 Atualização contínua deste HANDOFF (regra de processo)

Toda vez que a sessão pausar — fim de turno, troca de assunto, fim de
fase, fim de dia — o HANDOFF.md tem que refletir o estado atual antes
do "tchau". O bloco `§ Estado atual da sessão` (abaixo) é o ground
truth: arquivos tocados, decisões aprovadas que ainda não viraram
texto em spec/ADR/plan, próximo passo concreto, e qualquer pergunta
em aberto. Sem esse update, a próxima sessão começa cega.

Regra:
1. Antes de encerrar qualquer turno em que tenha havido decisão ou
   código novo, atualizar `§ Estado atual da sessão`.
2. Mover decisões já materializadas em spec/ADR/plan para fora desse
   bloco — ele é só o que ainda está em trânsito.
3. Pergunta em aberto fica registrada no formato
   `**Q-X (aberta):** texto da pergunta`.

## 9.2 Estado atual da sessão (atualizado 2026-04-29 — fim da rodada de docs)

**Decisões aprovadas pelo usuário (Q1–Q6) — todas materializadas:**
- Q1: skeleton do HackerRank mantido (`com.hackerrank.sample`).
- Q2: submissão paste-by-paste na UI do HackerRank.
- Q3: seed 5 × 10 produtos (50 / ~150 offers); categorias
  SMARTPHONE, SMART_TV, NOTEBOOK, HEADPHONES, REFRIGERATOR; edge
  cases woven in.
- Q4: BuyBox stock>0 → tier NEW > REFURBISHED > USED → menor preço
  → reputação desc → sellerId lex; sem stock ⇒ `null`.
- Q5: modelo LLM = `gpt-5.4-nano`.
- Q6: paginação 0/20/100, rating `{average, count}`, BRL no seed,
  sem FX em v1.

**Já materializado nesta rodada (docs):**
- Java sources revertidos para `com.hackerrank.sample` (Application,
  Category, Condition, AttributesJsonConverter).
- `application.yml` log package atualizado.
- `pom.xml` coordenadas revertidas (`com.hackerrank` / `sample`).
- **ADR-0001** → `Accepted` (revivido).
- **ADR-0002** → `Superseded by ADR-0003`.
- **ADR-0003** — Keep skeleton + paste-friendly submission.
- **ADR-0004** — BuyBox heuristic (Q4 formalizada).
- **SPEC-001 v5** — C-3 alinhado com ADR-0003; Changelog.
- **SPEC-002 v5** — Category enum + §4 SMART_TV sample + §7 row +
  §3.3 buyBox via ADR-0004; Changelog.
- **SPEC-004 v3** — modelo `gpt-5.4-nano`; Changelog.
- **PLAN v2** — §0/§2/§12 alinhados com ADR-0003 e ADR-0004; §3
  seed 50/150; Changelog.
- **TASKS.md v1** — atomização T-01..T-23, mapa para FR/AC/spec
  sections, paste-by-paste order alinhado com SUBMISSION §2.3.
- **SUBMISSION.md v2** — §2.1 atualizado (versões corretas + ADRs
  + TASKS); §2.3 reescrita (DTOs em `model/`, entidades em
  `repository/`, sub-pacotes `service/compare/` e `service/ai/`,
  `controller/advice/`); §2.4/§2.5 renumerada e mapeada para tasks;
  §3 com a nova numeração; Changelog v2.

**Pendente (próxima rodada — implementação):**
- T-01: pom.xml cleanup + Boot 3.3.5 + deps (Spring AI, validation,
  AOP, jacoco gate). Primeira porta de entrada do código real.
- T-02..T-23: seguir `docs/TASKS.md` na ordem; cada task fecha com
  `mvn verify` verde e atualização de status em SUBMISSION §2.3.

**Q em aberto:** nenhuma.

**Próxima ação concreta na sessão seguinte:** abrir T-01 — limpar
`pom.xml` (drop javafx-controls, unitils-core, junit-vintage-engine,
parent duplicado), bumpar Boot para 3.3.5, adicionar Spring AI
1.0.0-M3 + validation + aop + jacoco com threshold 80% nos pacotes
`controller`/`service`/`repository`. Validar com `mvn dependency:tree`
e `mvn -DskipTests package`. Marcar T-01 como `completed` em
`TaskList` e flipar `pom.xml` em SUBMISSION §2.2 para `ready`.

## 10. Protocolo de retomada (genérico, vale para qualquer fase do SDD)

Esta seção é estática: ela descreve **como** retomar, não **o que**
retomar. O "o que" vive sempre no §9.2 (campo
`Próxima ação concreta`). A cada nova sessão, siga estes passos na
ordem; pule sub-passos que não se apliquem à fase atual mas não pule
a sequência.

### 10.1 Validação da rodada anterior (sempre)

Objetivo: confirmar que o que o §9.2 diz que está `materializado`
realmente existe e está coerente. Sem essa checagem, o trabalho novo
risca empilhar em cima de algo quebrado.

1. Ler **`HANDOFF.md` §9.2** inteiro. Esse bloco é a fonte de verdade
   do estado.
2. Ler em paralelo, conforme o que o §9.2 lista como materializado:
   - `docs/README.md`, `docs/roadmap.md` (sempre).
   - `docs/specs/00X-*.md` na versão indicada no §9.2 — qualquer spec
     mencionada como "atualizada/criada na rodada anterior".
   - `docs/adrs/000Y-*.md` — todos os ADRs em `Accepted` ou recém-
     escritos; conferir status, cross-refs (`supersedes` / `superseded_by`)
     e que nenhum par esteja inconsistente.
   - `docs/plan.md` — versão indicada no §9.2; checar `depends_on`
     batendo com as specs/ADRs ativos.
   - `docs/TASKS.md` (se existir) — versão atual; conferir que cada
     task referencia FR/AC reais e que dependências (`Blocked by`)
     formam DAG sem ciclo.
   - `SUBMISSION.md` — §2.1 (status documental), §2.2 (config/build),
     §2.3 (código a colar com numeração de ordem), §2.5 (testes).
3. Para cada artefato lido, validar três coisas:
   - **Versão e changelog batem** com o que o §9.2 declara.
   - **Sem decisão órfã**: toda Q-X resolvida na rodada anterior tem
     que estar no corpo da spec/ADR e fora do bloco "Open Questions";
     toda Q em aberto tem que aparecer em §9.2 como `Q-X (aberta)`.
   - **Sem contradição cruzada**: package root, modelo LLM, escopo do
     seed, regra de BuyBox, etc., têm que dizer a mesma coisa em
     todos os documentos onde aparecem.
4. Em código: se §9.2 menciona arquivos Java/YAML/POM tocados,
   abrir esses arquivos e conferir que o conteúdo bate. Para `pom.xml`,
   ainda rodar `mvn -DskipTests package` apenas se o §9.2 disser que
   o build deveria estar verde.
5. Resultado da validação:
   - **Tudo consistente** → uma frase ao usuário: "Estado validado:
     rodada anterior fechou em <X>; próxima ação é <Y do §9.2>." Pode
     seguir para 10.2.
   - **Inconsistência encontrada** → parar. Listar para o usuário o
     que está fora do lugar e perguntar se a intenção é corrigir ou
     se eu entendi errado. Não tocar nada antes da resposta.

### 10.2 Execução da próxima ação

1. Ler o campo **`Próxima ação concreta`** do §9.2.
2. Identificar a fase do SDD em que ela cai:
   - **SPECIFY/PLAN/DECIDE** (escrever ou bumpar spec, ADR, plan,
     tasks, submission) → executar como edição de docs. Cada bump
     fecha com Changelog atualizado e versão coerente.
   - **IMPLEMENT** (uma task `T-NN` de `docs/TASKS.md`) → seguir o
     "Definition of Done" da task no próprio TASKS.md. Tests no mesmo
     commit; `mvn verify` verde; status em `SUBMISSION.md §2.3` flipado
     de `pending`/`partial` para `ready`.
   - **VERIFY** (final-pass / smoke / cobertura) → seguir o checklist
     do `SUBMISSION.md §4` e da task `T-23`.
   - **EVOLVE** (mudança de escopo) → não absorver na rodada; abrir
     entrada em `docs/roadmap.md` e voltar para 10.2 com a próxima
     ação não-evolutiva.
3. Manter o ritmo: uma decisão ou uma task por vez. Não emendar duas
   tasks no mesmo commit. Não mexer em arquivo fora da task corrente
   sem aviso explícito.

### 10.3 Pausa (sempre antes do "tchau")

Antes de encerrar o turno, atualizar `§9.2` para refletir:

- **Decisões aprovadas** nesta sessão e onde foram materializadas (qual
  spec/ADR/plan/task absorveu).
- **Já materializado nesta rodada** — lista enxuta dos artefatos
  efetivamente alterados.
- **Pendente (próxima rodada)** — só o que sobrou.
- **Q em aberto** — formato `Q-X (aberta): <pergunta>`.
- **Próxima ação concreta** — uma frase: qual é o T-NN ou qual doc
  bumpar, com o sub-passo inicial bem objetivo.

Mover para fora do §9.2 qualquer item que já tenha virado texto em
spec/ADR/plan/task — esse bloco é só o que está em trânsito.

### 10.4 Regras de etiqueta na primeira mensagem

- Não ofereça resumo longo do que já está no HANDOFF — o usuário
  acabou de me passar o arquivo.
- Não recrie specs/ADRs/plans/tasks que o §9.2 marca como
  materializados.
- Não comece a implementar antes de validar (10.1).
- Comunicação em pt-BR, código/docs/commits em inglês.
- Se a "Próxima ação concreta" exigir uma decisão do usuário (raro
  quando §9.2 está bem mantido), pergunte de forma específica e
  pare. Caso contrário, prossiga sem confirmar a cada passo — o
  escopo do §9.2 já é a aprovação.
