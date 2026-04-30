# Guia de Testes Manuais e Exploração da Arquitetura

Documento prático para validar a API ponta-a-ponta, entender o fluxo
interno e saber **exatamente onde colocar breakpoints** para acompanhar
cada etapa. Pareado com o README e com SPEC-001..004.

> Ponteiros de código no formato `arquivo:linha` apontam para o estado
> atual da branch `tests/product-service-and-attribute-metadata` (com PR
> #5 mergeado por baixo). Se o número da linha drift por refactor, o
> nome do método continua válido.

---

## 1. Subindo o ambiente

```bash
# sem LLM (summary será omitido — modo padrão do avaliador)
mvn spring-boot:run

# com LLM
export OPENAI_API_KEY=sk-...
mvn spring-boot:run

# porta alternativa se 8080 estiver ocupada
SERVER_PORT=8081 mvn spring-boot:run
```

URLs úteis logo após o boot:

| Recurso                | URL                                                  |
|------------------------|------------------------------------------------------|
| Swagger UI             | http://localhost:8081/swagger-ui.html                |
| OpenAPI JSON           | http://localhost:8081/v3/api-docs                    |
| H2 Console             | http://localhost:8081/h2-console (jdbc: `jdbc:h2:mem:items`) |
| Actuator health        | http://localhost:8081/actuator/health                |
| Métrica AI calls       | http://localhost:8081/actuator/metrics/ai_calls_total |
| Métrica AI fallback    | http://localhost:8081/actuator/metrics/ai_fallback_total |
| Caches                 | http://localhost:8081/actuator/caches                |

Logs SQL formatados estão habilitados (`hibernate.format_sql=true`) —
acompanhe as queries reais no console enquanto debuga.

---

## 2. Mapa de arquitetura (com pontos de debug)

Fluxo do `/compare` rotulado com onde parar:

```
HTTP GET /api/v1/products/compare?ids=1,2&fields=...&language=pt-BR
   │
   ▼  [BP-1]  CompareController.compare(...)
   │         CompareController.java:113
   │
   ▼  [BP-2]  CompareService.compare(...)
   │         CompareService.java:36
   │         ├── validações inline (ids null/vazio/dup/positivo/2..10)   :37–53
   │         ├── FieldSetProjector.parse(fieldsCsv)                      :55
   │         ├── resolve(deduped) ────────► [BP-3]                       :56
   │         ├── DifferencesCalculator.compute(products, fields)         :61
   │         ├── isCrossCategory(products)                               :62
   │         └── summaryService.summarise(...) ──────► [BP-7]            :64
   │
   ▼  [BP-3]  CompareService.resolve(...)
   │         CompareService.java:83
   │         tenta batch e cai pro per-id se vier incompleto / 404
   │
   ▼  [BP-4]  ProductService.getByIds(...)
   │         ProductService.java:58
   │         ├── findAllByIdIn  (batch 1)                                :63
   │         ├── findAllByCatalogProductIdIn  (batch 2)                  :67
   │         └── assemble(...) por produto                               :76
   │
   ▼  [BP-5]  BuyBoxSelector.select(productOffers)
   │         BuyBoxSelector.java  (escolha tier+preço+reputação)
   │
   ▼  [BP-6]  DifferencesCalculator.compute(products, fields, metadata)
   │         DifferencesCalculator.java:33
   │         ├── intersectAttributeKeys                                  :213
   │         ├── exclusivesPerProduct                                    :226
   │         ├── computeOutcome  (aplica AttributeMetadata.directionFor) :120
   │         └── ordenação por spread descendente
   │
   ▼  [BP-7]  SummaryService.summarise(...)
             SummaryService.java:101
             ├── hasUsableApiKey()                                        :134
             ├── readCached(cache, key)                                   :138
             ├── budget.tryConsume()                                      :119
             ├── renderPromptOrFallback(...)                              :150
             ├── invokeAndStore  ► invokeWithTimeout (3.5s)               :160 / :204
             └── classifyError  (auth / server_error / client_error)     :280
```

### Pontos de erro RFC 7807

Toda exceção converge em `GlobalExceptionHandler`
(`controller/advice/GlobalExceptionHandler.java`). Coloque um breakpoint
condicional em `problem(...)` (linha `:168`) para ver o body que vai
sair — ou nos handlers específicos:

| Cenário                          | Handler                                | Linha |
|----------------------------------|-----------------------------------------|-------|
| `ProductNotFoundException`       | `handleNotFound`                       | :42   |
| `ProductsNotFoundException` (404 com `missingIds`) | `handleProductsNotFound`  | :49   |
| `InvalidFieldsException`         | `handleInvalidFields`                  | :56   |
| `InvalidCompareRequestException` | `handleInvalidCompare`                 | :63   |
| `InvalidLanguageException`       | `handleInvalidLanguage`                | :70   |
| `ConstraintViolationException` (`@Min`/`@Max` no controller) | `handleConstraint` | :77 |
| `MethodArgumentTypeMismatchException` (id não numérico) | `handleTypeMismatch` | :97 |
| `MissingServletRequestParameterException` (sem `ids`) | `handleMissingParam` | :105 |
| 405 (POST/PUT/DELETE)            | `handleMethodNotAllowed`               | :122  |
| Catch-all (5xx)                  | `handleFallback`                        | :130  |

---

## 3. Mapa do dataset (seed em memória)

Carregado por `SeedLoader` no boot — 50 produtos, ~150 ofertas:

| IDs       | Categoria      | Notas relevantes                                  |
|-----------|----------------|---------------------------------------------------|
| 1..10     | `SMARTPHONE`   | id=1 "Galaxy S24", id=2 "Galaxy S24+"             |
| 11..20    | `SMART_TV`     | atributos típicos: `screen_size_inches`, `hdmi_ports`, `weight_kg` |
| 21..30    | `NOTEBOOK`     | atributos: `memory`, `storage`, `weight` (kg), `battery` |
| 31..40    | `HEADPHONES`   | —                                                 |
| 41..50    | `REFRIGERATOR` | id=41 só REFURBISHED/USED (sem NEW) — testar tier do BuyBox; id=50 com **stock=0** em todas ofertas → `buyBox=null` |

Use o H2 Console para sanity check:

```sql
SELECT id, name, category FROM catalog_products ORDER BY id;
SELECT catalog_product_id, condition, stock, price FROM offers WHERE catalog_product_id IN (1,41,50) ORDER BY catalog_product_id, price;
```

---

## 4. Cenários de teste por endpoint

### 4.1 `GET /api/v1/products` — listagem paginada

| # | Caso                                        | Comando                                                                 | Esperado                                          | Observação |
|---|---------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------|------------|
| L1 | Default (page=0, size=20)                  | `curl -i http://localhost:8080/api/v1/products`                          | 200, 20 itens, `totalElements: 50`               | BP em `ProductController.list` (`:72`) |
| L2 | Filtro por categoria                       | `curl 'http://localhost:8080/api/v1/products?category=SMARTPHONE&size=10'` | 200, 10 itens, todos `SMARTPHONE`             | Confere `findAllByCategory` no log SQL |
| L3 | Página fora do range                       | `curl 'http://localhost:8080/api/v1/products?page=10&size=20'`           | 200, `items: []`, `totalElements: 50`            | — |
| L4 | `size > 100`                               | `curl -i 'http://localhost:8080/api/v1/products?size=101'`               | 400 RFC 7807 `validation` (`@Max(100)`)          | Cai em `handleConstraint` |
| L5 | `page` negativo                            | `curl -i 'http://localhost:8080/api/v1/products?page=-1'`                | 400 `validation` (`@Min(0)`)                     | — |
| L6 | Categoria inválida                         | `curl -i 'http://localhost:8080/api/v1/products?category=FAKE'`          | 400 `bad-request` (type mismatch enum)           | `handleTypeMismatch` |

### 4.2 `GET /api/v1/products/{id}` — detalhe

| # | Caso                                       | Comando                                                                 | Esperado                                          |
|---|--------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------|
| D1 | Detalhe completo                          | `curl http://localhost:8080/api/v1/products/1`                          | 200, com `buyBox` (NEW, menor preço, reputação alta) |
| D2 | Sparse fields                             | `curl 'http://localhost:8080/api/v1/products/1?fields=name,buyBox.price'` | Apenas `id`, `name`, `buyBox.price`              |
| D3 | Produto sem ofertas com stock (id=50)     | `curl http://localhost:8080/api/v1/products/50`                         | 200, `offers: []` ou stock=0, `buyBox: null`     |
| D4 | Produto com tier USED/REFURBISHED (id=41) | `curl http://localhost:8080/api/v1/products/41`                         | 200, `buyBox.condition` = REFURBISHED (tier > USED) |
| D5 | ID inexistente                            | `curl -i http://localhost:8080/api/v1/products/9999`                    | 404 `not-found`                                  |
| D6 | ID negativo                               | `curl -i http://localhost:8080/api/v1/products/-1`                      | 400 `validation` (positive guard)                |
| D7 | ID não numérico                           | `curl -i http://localhost:8080/api/v1/products/abc`                     | 400 `bad-request` (`handleTypeMismatch`)         |
| D8 | `fields` inválido                         | `curl -i 'http://localhost:8080/api/v1/products/1?fields=invalid.path'` | 400 `validation`                                  |
| D9 | Cache hit                                 | Repetir D1 duas vezes                                                    | 2ª chamada **não** dispara SQL (Caffeine `products`) |

> Para D9 deixe um breakpoint em `ProductService.getById` (`:51`) — só
> deve parar na primeira; depois inspecione `/actuator/caches/products`.

### 4.3 `GET /api/v1/products/compare` — comparação

#### Happy paths

| # | Caso                              | Comando                                                                   | Esperado                                                  |
|---|-----------------------------------|---------------------------------------------------------------------------|-----------------------------------------------------------|
| C1 | Mesma categoria                  | `curl 'http://localhost:8080/api/v1/products/compare?ids=1,2'`            | 200, `crossCategory: false`, `differences[]` com `winnerId`, `exclusiveAttributes` ausente ou vazio |
| C2 | Cross-category                   | `curl 'http://localhost:8080/api/v1/products/compare?ids=1,21'`           | 200, `crossCategory: true`, `differences[]` apenas para a interseção, `exclusiveAttributes` por id |
| C3 | Sparse fields                    | `curl 'http://localhost:8080/api/v1/products/compare?ids=1,2&fields=buyBox.price,attributes.battery'` | `differences[]` apenas nesses paths |
| C4 | Língua inglês                    | `curl 'http://localhost:8080/api/v1/products/compare?ids=1,2&language=en'` | `language: "en"` no body; `summary` (se chave) em inglês |
| C5 | 10 produtos (limite máximo)      | `curl 'http://localhost:8080/api/v1/products/compare?ids=1,2,3,4,5,6,7,8,9,10'` | 200, payload completo |
| C6 | Tier mistura (id=41 USED-only)   | `curl 'http://localhost:8080/api/v1/products/compare?ids=1,41'`           | 200, `buyBox.price` comparable mesmo com condições diferentes (currency igual) |
| C7 | Sem stock no buyBox (id=50)      | `curl 'http://localhost:8080/api/v1/products/compare?ids=1,50'`           | 200, mas `buyBox.price` em id=50 é `null` → `differences` para essa path marcada como `comparable: false` |

#### Validação (todos 400 RFC 7807 `validation` ou `bad-request`)

| # | Caso                              | Comando                                                                   | Esperado                                          |
|---|-----------------------------------|---------------------------------------------------------------------------|---------------------------------------------------|
| V1 | Sem `ids`                        | `curl -i http://localhost:8080/api/v1/products/compare`                   | 400 `bad-request` (`handleMissingParam`)         |
| V2 | `ids` vazio                      | `curl -i 'http://localhost:8080/api/v1/products/compare?ids='`            | 400 `validation`                                  |
| V3 | Apenas 1 id                      | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=1'`           | 400 `validation` "ids must contain between 2 and 10" |
| V4 | 11 ids                            | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=1,2,3,4,5,6,7,8,9,10,11'` | 400 mesma mensagem |
| V5 | Duplicatas                       | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=1,1'`         | 400 "ids must not contain duplicate values"      |
| V6 | ID zero                          | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=0,1'`         | 400 "ids must be positive"                       |
| V7 | ID negativo                      | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=-1,2'`        | 400 "ids must be positive"                       |
| V8 | ID não numérico                  | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=abc,1'`       | 400 `bad-request` (`handleTypeMismatch`)         |
| V9 | Algum `id` faltando (404)        | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=1,9999'`     | **404** `products-not-found` com `missingIds: [9999]` |
| V10 | Vários ids faltando             | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=9998,9999,1'`| 404 com `missingIds: [9998, 9999]` (ordem preservada — confere o fallback per-id em `resolveCollectingMissing`) |
| V11 | `language` inválida              | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=1,2&language=zz'` | 400 `validation` (`InvalidLanguageException`) |
| V12 | `fields` inválido                | `curl -i 'http://localhost:8080/api/v1/products/compare?ids=1,2&fields=foo.bar'` | 400 `validation` (`InvalidFieldsException`) |
| V13 | Method not allowed               | `curl -i -X POST 'http://localhost:8080/api/v1/products/compare?ids=1,2'` | 405 `method-not-allowed` |

---

## 5. Onde colocar breakpoints (cheat sheet)

| Quero observar...                              | Arquivo : método (linha)                                                             |
|------------------------------------------------|--------------------------------------------------------------------------------------|
| Entrada do controller                          | `controller/CompareController.java:113` `compare(...)`                              |
| Validação de ids                               | `service/compare/CompareService.java:37–53`                                         |
| Parse de `fields=...`                          | `service/compare/FieldSetProjector.java` `parse(...)`                               |
| Batch fetch (N+1 fix)                          | `service/ProductService.java:58` `getByIds(...)` — prove com SQL log que vê 2 queries |
| Fallback per-id (preserva `missingIds` no 404) | `service/compare/CompareService.java:94` `resolveCollectingMissing(...)`            |
| Heurística do BuyBox                           | `service/compare/BuyBoxSelector.java` `select(...)`                                 |
| Direção (HIGHER/LOWER) por atributo            | `service/compare/AttributeMetadata.java:37` `directionFor(...)`                     |
| Ordenação por spread no `differences[]`         | `service/compare/DifferencesCalculator.java` `spreadKey(...)`                       |
| Cache hit do summary                           | `service/ai/SummaryService.java:138` `readCached(...)`                              |
| Decisão de fallback (sem chave/budget)         | `service/ai/SummaryService.java:101` `summarise(...)`                               |
| Classificação de erro do LLM                   | `service/ai/SummaryService.java:280` `classifyError(...)` + `:307` `findHttpStatusCause(...)` |
| Body RFC 7807                                  | `controller/advice/GlobalExceptionHandler.java:168` `problem(...)`                  |

Em IDE (IntelliJ/VS Code): use breakpoint **condicional** em `problem(...)`
filtrando por `slug.equals("validation")` para ver só erros de validação,
ou em `getByIds` filtrando por `ids.size() > 1` para focar batches.

---

## 6. Roteiros de exploração sugeridos

### 6.1 Provando o N+1 fix (PR #5)

1. BP em `ProductService.getByIds` (`:58`) e `ProductService.getById` (`:51`).
2. `curl 'http://localhost:8080/api/v1/products/compare?ids=1,2,3,4,5'`.
3. Observe: cai apenas em `getByIds` (1 vez), não em `getById` (per-id).
4. No console SQL: deve aparecer **uma** `select ... from catalog_products ... where id in (?,?,?,?,?)` e **uma** de offers `in (...)`. Antes do fix eram 5+5.

### 6.2 Provando o fallback per-id (preservação de `missingIds`)

1. BP em `CompareService.resolve` (`:83`) e `resolveCollectingMissing` (`:94`).
2. `curl -i 'http://localhost:8080/api/v1/products/compare?ids=9998,9999,1'`.
3. Step-over: o batch lança `ProductNotFoundException` no primeiro id ausente; o catch silencioso direciona pro per-id, que coleta **todos** os faltantes em ordem antes de lançar `ProductsNotFoundException`.
4. Body: `404` `products-not-found` com `missingIds: [9998, 9999]`.

### 6.3 Provando `AttributeMetadata` carregada do JSON

1. BP em `AttributeMetadata.directionFor` (`:37`).
2. `curl 'http://localhost:8080/api/v1/products/compare?ids=11,12'` (2 TVs).
3. Para `screen_size_inches` deve retornar `HIGHER_BETTER`; para `weight_kg`, `LOWER_BETTER` (ambos vêm de `src/main/resources/attribute-metadata.json`).
4. Edite o JSON, restart, rode de novo: o resultado muda sem recompilar nenhuma classe.

### 6.4 Provando os 4 caminhos do `SummaryService`

| Modo                  | Como reproduzir                                                          | BP                                                       | Métrica                              |
|-----------------------|--------------------------------------------------------------------------|----------------------------------------------------------|--------------------------------------|
| Sem chave             | rodar sem `OPENAI_API_KEY` (ou com `disabled`)                           | `summarise` early return em `:107`                       | `ai_fallback_total{reason="no_key"}` |
| Cache hit             | mesma request `/compare?ids=...` duas vezes seguidas (com chave válida)  | `readCached` em `:138`                                   | `ai_calls_total{outcome="cache_hit"}` |
| Timeout               | mock de modelo lento ou `app.ai.timeout-ms=1` em `application.yml`       | `invokeWithTimeout` em `:204`                            | `ai_calls_total{outcome="timeout"}`  |
| Erro classificado     | apontar para chave inválida → 401; provider down → 5xx                   | `classifyError` em `:280`                                | `ai_fallback_total{reason="auth"}` ou `server_error` |

Verificar com:

```bash
curl http://localhost:8080/actuator/metrics/ai_calls_total
curl http://localhost:8080/actuator/metrics/ai_fallback_total
```

### 6.5 Provando o cache de produtos

1. BP em `ProductService.getById` (`:51`).
2. `curl http://localhost:8080/api/v1/products/1` duas vezes.
3. Segunda chamada **não** para no breakpoint (foi servida pelo `@Cacheable("products")`).
4. `curl http://localhost:8080/actuator/caches/products` mostra `size > 0`.

> Atenção: `getByIds` **não** está anotada com `@Cacheable` (Caffeine não
> compõe bem com chaves coleção). A cache é consultada só pelo
> `getById`. O batch sempre vai ao banco — é por isso que o N+1 fix
> importa.

---

## 7. Logs estratégicos

Sem mexer em código, três logs já presentes ajudam:

- **Hibernate SQL formatado** — toda query JPA aparece no console; útil para validar batches.
- **`SummaryService` warnings** — toda saída de fallback loga em WARN com a razão (`no_key`, `budget`, `timeout`, `auth`, `server_error`, `client_error`, `exception`). Localizado em `SummaryService.java`.
- **`GlobalExceptionHandler` LOG** (`:33`) — em `handleFallback` (5xx) loga o stack completo; valide que erros de validação **não** caem aqui.

Se quiser injetar logs temporários só pra debug local (sem commitar):

| Onde                                             | Sugestão de log                                                       |
|--------------------------------------------------|------------------------------------------------------------------------|
| `CompareService.compare` topo (`:36`)            | `log.debug("compare ids={} fields={} lang={}", ids, fieldsCsv, language)` |
| `CompareService.resolve` (`:83`)                 | `log.debug("resolve ids={} bulkSize={}", ids, bulk.size())`            |
| `DifferencesCalculator.computeOutcome`           | `log.trace("path={} outcome={}", path, outcome)`                       |
| `SummaryService.summarise` antes de `invokeAndStore` | `log.debug("LLM call key={}", key)` (chave já é hash, seguro)        |

> Lembrar de **não commitar** esses logs — o projeto segue clean-code e
> os testes assumem o nível de log atual.

---

## 8. Smoke completo em um one-liner

Rodar tudo isso em sequência reproduz cobertura razoável dos
controllers e do handler de erros:

```bash
BASE=http://localhost:8080/api/v1
for u in \
  "$BASE/products" \
  "$BASE/products?category=SMARTPHONE&size=10" \
  "$BASE/products?size=101" \
  "$BASE/products/1" \
  "$BASE/products/1?fields=name,buyBox.price" \
  "$BASE/products/41" \
  "$BASE/products/50" \
  "$BASE/products/9999" \
  "$BASE/products/compare?ids=1,2" \
  "$BASE/products/compare?ids=1,21" \
  "$BASE/products/compare?ids=1,2&language=en" \
  "$BASE/products/compare?ids=1,1" \
  "$BASE/products/compare?ids=1" \
  "$BASE/products/compare?ids=1,9999" \
  "$BASE/products/compare?ids=9998,9999,1" \
  "$BASE/products/compare" \
; do
  echo; echo "=== $u ==="; curl -s -o /dev/null -w "%{http_code}\n" "$u"
done
```

Esperado (na ordem): `200 200 400 200 200 200 200 404 200 200 200 400 400 404 404 400`.

---

## 9. Checklist final antes de declarar "tudo testado"

- [ ] Os 16 status codes do smoke acima batem.
- [ ] `actuator/caches/products` tem entradas após chamadas repetidas.
- [ ] `ai_fallback_total{reason="no_key"}` cresce quando se sobe sem chave.
- [ ] `ai_calls_total{outcome="cache_hit"}` cresce na 2ª chamada idêntica de `/compare` (com chave).
- [ ] H2 console mostra 50 produtos e ~150 ofertas após boot.
- [ ] Em todos os 4xx/5xx, `Content-Type: application/problem+json` e body com `type`, `title`, `status`, `detail`, `slug`.
- [ ] `ids=9998,9999,1` retorna `missingIds: [9998, 9999]` (provando o fallback per-id do PR #5).
