# Company Risk Assessment

A prototype system that gathers and presents company information from Companies House and LLM-powered adverse media search, enabling analysts to make informed risk decisions about payment beneficiaries.

The system does **not** compute a risk score. It gathers, structures, and presents data — the analyst decides.

## Quick Start

### Prerequisites

- **Java 21** (or later)
- **Python 3.9+** (for the Streamlit frontend)
- **OpenRouter API key** (the only required credential)

### 1. Start the backend

```bash
export OPENROUTER_API_KEY="your-key-here"
./gradlew bootRun
```

The API starts on `http://localhost:8080`. By default, Companies House data is **mocked** — no additional API keys needed.

To use the **real** Companies House API (optional):

```bash
export COMPANIES_HOUSE_API_KEY="your-ch-key"
```

### 2. Start the frontend

```bash
cd frontend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
streamlit run app.py
```

Opens on `http://localhost:8501`.

### 3. Run tests

```bash
./gradlew test
```

## Architecture

```
┌─────────────────────┐         ┌───────────────────────────────────────────────┐
│  Streamlit Frontend  │  HTTP   │              Spring Boot Backend              │
│  (Python)            │────────▶│                                               │
│                      │         │  ┌─────────────┐     ┌─────────────────────┐  │
│  - Search form       │         │  │ Controller   │────▶│  AssessmentService   │  │
│  - Disambiguation    │         │  └─────────────┘     │                     │  │
│  - Data tables       │         │                      │  1. Check cache     │  │
│  - Adverse media     │         │                      │  2. Gather data     │  │
│  - Confidence score  │         │                      │  3. Map CH → Java   │  │
└─────────────────────┘         │                      │  4. Compute fields  │  │
                                │                      │  5. Confidence score │  │
                                │                      │  6. Cache & return   │  │
                                │                      └──────────┬──────────┘  │
                                │                                 │              │
                                │              ┌──────────────────┴───────┐      │
                                │              │                         │      │
                                │    ┌─────────▼──────┐     ┌──────────▼────┐  │
                                │    │ Companies House │     │ Adverse Media │  │
                                │    │  (mock / real)  │     │   (LLM call)  │  │
                                │    │                 │     └───────────────┘  │
                                │    │  5 async calls: │                        │
                                │    │  - Profile      │                        │
                                │    │  - Officers     │                        │
                                │    │  - Accounts     │                        │
                                │    │  - Conf. Stmt   │                        │
                                │    │  - Liquidation  │                        │
                                │    └────────────────┘                        │
                                └───────────────────────────────────────────────┘
```

**Stack**: Java 21 / Spring Boot 3.4 backend + Python / Streamlit frontend.

### Data flow

1. **Search** (`GET /api/v1/companies/search`): user enters company name and/or registration number. Returns a list of candidates for disambiguation.
2. **Assess** (`GET /api/v1/companies/assess`): takes a company number + exact name, runs the full pipeline:
   - **Companies House** data source makes 5 parallel async calls (profile, officers, accounts filings, confirmation statement filings, liquidation filings) — mapped directly in Java, no LLM needed.
   - **Adverse Media** data source queries the LLM's training knowledge for fraud, regulatory actions, lawsuits, etc.
   - **Filtering**: only filings from the last 5 years are shown (based on date of cessation, or today if the company is active). Liquidation status flagged.
   - **Confidence scores** computed server-side in Java (deterministic, testable).

### API endpoints

| Method | Endpoint | Parameters | Description |
|--------|----------|------------|-------------|
| GET | `/api/v1/companies/search` | `company_name?`, `registration_number?`, `jurisdiction` | Search for companies. At least one of name/number required. |
| GET | `/api/v1/companies/assess` | `company_number`, `company_name`, `jurisdiction` | Gather data and return structured assessment. Name must be exact (from search). |

### Key design decisions

| Decision | Rationale                                                                                                                               |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------|
| LLM used only for adverse media search | Companies House returns structured JSON — no LLM needed. Risk scoring is left to the human analyst.                                     |
| No automated risk scoring | The system presents data; the analyst decides. Avoids false confidence in automated risk labels.                                        |
| Config-driven Companies House (real/mock) | It can run with just an OpenRouter key. Set `COMPANIES_HOUSE_API_KEY` to switch to the real API.                             |
| 5 parallel async CH calls | Profile, officers, and 3 filing categories fetched concurrently via `HttpClient.sendAsync()` + `CompletableFuture`.                     |
| Officers API needs exact company name | The CH officers endpoint returns empty without exact name. The two-step search→assess flow ensures the exact name from search is used.  |
| Separate filing category queries | Accounts, confirmation statements, and liquidation are different filing types. Querying separately keeps parsing clean.                 |
| 5-year filing window | Only recent filings are relevant to risk assessment. Cutoff is from date of cessation (dissolved companies) or today (active companies). |
| In-memory assessment cache with TTL | Avoids redundant LLM calls for repeated lookups. Default 24h TTL, configurable.                                                         |
| Corrective retry as LLM guardrail | On JSON parse failure, retries once with error details. Falls back to empty array.                                                      |

### Filing display rules

| Filing type | Display | Notes |
|------------|---------|-------|
| Annual Accounts | Made Up Date, Filing Date | Last 5 years only |
| Confirmation Statements | Made Up Date, Filing Date | Last 5 years only |
| Liquidation | Filing Date, Type, Description | Presence flagged as `has_liquidation: true` |

### Confidence scoring

- **`completeness_score`**: sections with data / 6 total sections (company, officers, accounts, confirmation statements, liquidation, adverse media).
- **`source_coverage`**: successful data sources / total data sources (2 for prototype: Companies House + adverse media LLM).

### LLM integration

- **Model**: configurable via `OPENROUTER_MODEL` env var
- **Reproducibility**: `temperature=0`, `seed=42`
- **Prompt versioning**: version string (`v1`) stored as a constant
- **Schema enforcement**: responses parsed as JSON arrays via Jackson; markdown code fences stripped
- **Guardrails**: corrective retry on parse failure (max 1 retry), fallback to empty array

## Trade-offs

- **Synchronous API**: entire assessment in one HTTP call. With 2 data sources latency is ~5–15s, but adding more sources (sanctions, credit, PEPs, court records) would push well beyond the 10-second target. Even SSE streaming wouldn't help much — the bottleneck is waiting for upstream sources, not delivering results to the client.
- **LLM adverse media**: uses model training knowledge with a cutoff date. Very recent events won't appear. Production would use a real search API.
- **LLM hallucination risk**: mitigated by prompt instructions and by labelling it as LLM-knowledge-based in the UI. Production would verify against real sources.
- **No retry policies for API calls**: no exponential backoff or circuit breakers for Companies House or OpenRouter. Production would add resilience4j or similar.
- **No other_directorships**: requires a separate CH API call per officer. Deferred — would add in production.
- **In-memory cache**: lost on restart. Production would use a database.
- **404 handling for dissolved companies**: dissolved companies may return 404 from some CH endpoints. Officers and filings gracefully return empty; profile 404 is treated as a real error.

## Production upgrade path

### Data sources
- **Stream results to FE**: use Server Sent Events (SSE) to stream results as they are completed to the FE, instead of the current sync API
- **Real adverse media**: replace LLM knowledge search with SerpAPI (Google Search) or similar for fresh web results, then use the LLM to structure and summarize the raw results. This removes the knowledge cutoff limitation and provides verifiable source URLs
- **Async data refresh**: daily batch job for Companies House data into a database; live API calls only for first lookups or stale data
- **Other directorships**: requires a separate CH appointments API call per officer — too slow for a live API request (5-15 extra calls per assessment). Should be a daily batch job that pre-computes active directorship counts into a database, with the assessment API reading the cached counts

### Resilience
- **Retry policies**: exponential backoff with jitter for transient failures on all external API calls
- **Circuit breakers**: prevent cascading failures when upstream services are degraded
- **Rate limiting**: per-client rate limits to protect the system and respect upstream API quotas

### Scalability
- **Database cache**: Store assessments in database and refresh them online/offline
- **Batch + live hybrid**: pre-fetch slow/stable data sources (CH filings, sanctions, credit registries, directorships, PEPs) into a database via a daily/hourly batch pipeline. The live API reads pre-computed data from the DB and only calls time-sensitive sources (e.g. adverse media search) on demand. This keeps response times under 10 seconds regardless of how many data sources are added, and includes a staleness indicator per section
- **Request deduplication**: if the same company is being assessed concurrently, share the result
- **Horizontal scaling**: stateless backend behind a load balancer

### Observability
- **Metrics**: Prometheus/Grafana for latency percentiles, throughput, error rates
- **LLM cost tracking**: tokens used per assessment
- **Token budgets**: per-request limits to prevent runaway costs

## Project structure

```
src/main/java/com/tunicpay/riskassessment/
├── config/          AppConfig (OpenRouter, Companies House, cache TTL)
├── controller/      CompanyController (search + assess), GlobalExceptionHandler
├── datasource/      DataSource interface, DataGatherer, CH + adverse media sources
├── llm/             OpenRouterClient
├── model/           Data records (CompanyProfile, Officer, AccountFiling, etc.)
└── service/         AssessmentService, FilingProcessor, ConfidenceCalculator, cache

src/test/java/com/tunicpay/riskassessment/
├── controller/      CompanyControllerTest
├── datasource/      AdverseMediaDataSourceTest
└── service/         FilingProcessorTest, ConfidenceCalculatorTest, AssessmentServiceTest

frontend/
└── app.py           Streamlit UI (search, disambiguation, tabular data display)
```
