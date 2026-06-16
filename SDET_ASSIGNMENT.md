# SDET Practical Assignment — RealWorld Spring Boot (Conduit API)

**Candidate submission** for the Conduit SDET practical.  
**Implementation:** [raeperd/realworld-springboot-java](https://github.com/raeperd/realworld-springboot-java) — a Spring Boot + JPA implementation of the [RealWorld](https://github.com/gothinkster/realworld) / Conduit API spec.

## Why this repo

- Runs locally with `./gradlew bootRun` (H2 in-memory, no Docker required).
- Already ships unit/`@WebMvcTest` coverage and a Postman-style `IntegrationTest`; I added a **focused SDET layer** on top rather than duplicating the entire Newman collection.
- Matches the assignment’s “Conduit” API surface: JWT auth, articles, comments, profiles/follows, favorites.

## What I tested

| Area | Location | Coverage |
|------|----------|----------|
| **Auth API** | `ApiAuthTest` | Register, login, invalid email, missing/malformed JWT |
| **CRUD + social** | `ApiSocialAndArticleCrudTest` | Follow/unfollow, favorite/unfavorite, author update |
| **E2E flow** | `E2EArticleLifecycleTest` | Register → login → publish → comment → delete comment → delete article |
| **Boundaries / bugs** | `ApiSecurityAndBoundaryTest` | Feed spec mismatch, comment-delete auth, unauthorized update, duplicate email, unauthenticated create |

**Shared helpers:** `sdet/support/ApiTestSupport.java` (register user, login, unique IDs per test).

### Deliberately left out (time / scope)

- Tags API (`GET /tags`) — read-only, low risk.
- Pagination edge cases (`offset`/`limit` extremes).
- Newman/Postman parity — upstream `IntegrationTest` + `doc/run-api-tests.sh` already cover this.
- UI / Playwright — API-only backend.
- Performance / load testing.

## How to run

```bash
./gradlew test --tests "io.github.raeperd.realworld.sdet.*"
```

Full suite (includes upstream 100% JaCoCo gate):

```bash
./gradlew test
```

**Requirements:** JDK 25 (per `build.gradle`).

## Bugs found (tests document expected vs actual)

| # | Area | Expected | Actual | Test |
|---|------|----------|--------|------|
| 1 | **Feed** | `GET /articles/feed` returns articles from **followed** authors | `ArticleService.getFeedByUserId` uses `findAllByUserFavoritedContains` — returns **favorited** articles | `feed_shouldShowFollowedAuthorsArticles_perSpec` |
| 2 | **Comment delete** | Comment author can delete their comment | `Article.removeCommentByUser` uses `if (!author \|\| !commentAuthor)` (OR) — rejects valid deletes | `commentAuthor_shouldDeleteOwnComment_onOthersArticle` |
| 3 | **AuthZ errors** | Non-author update → **403** | `IllegalAccessError` propagates → **500** | `nonAuthor_updateArticle_shouldBeForbidden` |
| 4 | **Duplicate email** | Second signup with same email → **409** | No unique constraint; second signup **succeeds** | `register_duplicateEmail_shouldRejectSecondSignup` |

These failing tests are **intentional defect detectors**, not mistakes in the test code.

## Using AI agents (Cursor)

### Tools used

- **Cursor Agent** — explored the repo, mapped controllers vs existing `IntegrationTest`, drafted test classes and README.
- **Semantic search / grep** — traced `ArticleService.getFeedByUserId` and `Article.removeCommentByUser` to confirm bugs before writing assertions.

### Where the agent helped

- Fast navigation of a Gradle/Spring layout I hadn’t seen before.
- Boilerplate for `@SpringBootTest` + `MockMvc` patterns consistent with existing `IntegrationTest`.
- Drafting the README structure against the assignment rubric.

### Where I corrected or rejected the agent

- **Slug assumptions:** The agent initially hard-coded slugs (e.g. `favorite-test-article-{id}`). Titles are slugified in `ArticleTitle.slugFromTitle()`; I **overrode** that and always read `article.slug` from the create response.
- **“Passing” bug tests:** The agent suggested `@Disabled` on defect tests. I **rejected** that — for this assignment, failing tests that prove real bugs are more valuable than green builds.
- **Login failure status:** Agent assumed 401; the API returns **404** for bad credentials (`ResponseEntity.of(Optional.empty())`). Test asserts 404 to match implementation.

### Example override

> Agent proposed merging all scenarios into one ordered `IntegrationTest` clone.  
> **Decision:** Keep upstream `IntegrationTest` untouched; add a separate `io.github.raeperd.realworld.sdet` package with isolated tests and unique users per run (`UUID` suffix) so failures are easier to attribute.

## CI

Upstream repo already runs `./gradlew build` on push via [`.github/workflows/build.yml`](.github/workflows/build.yml), which executes all tests including the SDET package.

## With more time

1. Fix or `@Tag("known-bug")` the four defects above and open GitHub issues with stack traces.
2. Add contract tests against `doc/swagger.json` (Schemathesis or Dredd).
3. Wire Newman collection in CI against `bootRun` for spec parity.
4. Add `Testcontainers` + Postgres for constraint/transaction behavior closer to production.

---

## Bonus: Testing non-deterministic GenAI features

For an LLM that auto-summarizes articles or suggests tags, I would **not** assert exact strings. I would combine **layered checks**: (1) **schema/contract** — response has `summary` under N words, `tags` is a non-empty array of strings matching `^[a-z0-9-]+$`; (2) **reference grounding** — keywords or entities from the source article appear in the summary (embedding similarity or simple substring checks on named entities); (3) **safety/policy** — blocklist and toxicity classifier scores below threshold; (4) **stability** — same input run twice, Jaccard similarity of tag sets ≥ X or summary embeddings cosine similarity ≥ Y; (5) **human eval set** — golden articles with human-rated “acceptable” summaries for periodic regression. Assert on **structure, grounding, and statistical quality**, not verbatim LLM output.

---

*Time spent on test design/implementation: ~3 hours (excluding initial clone/`./gradlew` setup).*
