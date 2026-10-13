# DP Sheet Music API

> Documento de contexto do projeto. Use-o como referência principal no Cursor (ex.: `@PROJECT.md`) ou copie as seções relevantes para `.cursor/rules`.

---

## 1. Visão geral

**DP Sheet Music** (*Domain Public Sheet Music*): API para um site que vende **arranjos de músicas de domínio público**.

- Uma **música (Work)** possui várias **versões (Arrangement)**, cada uma com uma formação instrumental e/ou tonalidade.
- Todas as versões têm **áudio de exemplo**.
- O cliente compra o arranjo (PDF) e recebe acesso seguro ao download.
- O site será **hospedado fora do Brasil** e voltado a público internacional.
- O dono precisa de **controle de métricas** (cliques, conversões, funil) e de um **painel admin** para gerenciar o catálogo.
- **Escopo atual: somente a API.** O front (cliente + admin) virá depois.

---

## 2. Configuração do projeto (Spring Initializr)

| Campo | Valor |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | 4.1.1 |
| Group | `com.dpsheetmusic` |
| Artifact | `dp-sheet-music-api` |
| Package name | `com.dpsheetmusic` |
| Packaging | Jar |
| Configuration | YAML (`application.yml`) |
| Java | 21 |

Dependências do Initializr: Spring Web, Spring Data JPA, Spring Security, Validation, PostgreSQL Driver, Liquibase Migration, Spring Boot Actuator, Lombok (opcional), Testcontainers.

Adicionar manualmente ao `pom.xml`: **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui`). Como o projeto usa Spring Boot 4.x, conferir na documentação do springdoc qual versão é compatível com Boot 4 antes de adicionar.

> Pacotes Java não aceitam hífen. Por isso o pacote é `com.dpsheetmusic`, enquanto o artifact mantém os hífens.

---

## 3. Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Build | Maven |
| Framework | Spring Boot 4.1.1, Spring Web, Spring Data JPA, Spring Security, Spring Validation |
| Banco | PostgreSQL |
| Migrations | **Liquibase** |
| Documentação | **springdoc-openapi (Swagger UI)** |
| Observabilidade | **Spring Boot Actuator** |
| Containers | Docker / Docker Compose |
| Testes | JUnit 5, Testcontainers (Postgres real), Spring Security Test |
| Storage | S3 ou Cloudflare R2 (bucket **privado** para PDFs; áudios via CDN) |
| E-mail transacional | Resend, Postmark ou Amazon SES (a definir) |
| Gateway de pagamento | **A definir** (ver seção 7) |

Outras dependências úteis: Lombok (opcional), MapStruct (DTO mapping), Apache PDFBox (marca d'água), bucket4j (rate limiting).

---

## 4. Arquitetura

**Monolito modular**, sem microsserviços. Cada módulo em seu pacote, comunicando-se por interfaces ou eventos internos do Spring (`ApplicationEventPublisher`), de forma que possa ser extraído no futuro.

```
com.dpsheetmusic
├── catalog       # Work, Composer, Arrangement, Instrumentation, AudioSample
├── customers     # Customer, magic link
├── orders        # Order, OrderItem
├── payments      # Payment, PaymentGateway (interface), webhooks
├── delivery      # DownloadGrant, storage, URLs assinadas, marca d'água
├── analytics     # TrackingEvent
├── admin         # endpoints administrativos
└── shared        # config, segurança, exceções, utilitários
```

Diretrizes:
- Controllers finos; regras de negócio em services.
- DTOs separados das entidades JPA (nunca expor entidade direto na API).
- Erros padronizados (RFC 7807 / `ProblemDetail`).
- Versionamento de API: prefixo `/api/v1`.
- Endpoints admin sob `/api/v1/admin/**`, protegidos por role `ADMIN`.

---

## 5. Modelo de domínio

### 5.1 Catálogo

**Composer**
- `id`, `name`, `birth_year`, `death_year` (necessário para justificar o domínio público)

**Work** (música)
- `id`, `slug` (único, estável, para SEO), `title`, `composer_id`, `period`, `description`, `tags`, `status` (`DRAFT`/`PUBLISHED`), timestamps
- Traduzível (ver i18n, seção 8)

**Instrumentation** (formação, como entidade, não string)
- `id`, `slug`, `name` (ex.: "Quarteto de cordas")
- Composta por instrumentos (ex.: violino I, violino II, viola, violoncelo)

**Arrangement** (versão de uma Work)
- `id`, `work_id`, `instrumentation_id`, `key_signature` (tonalidade), `difficulty`, `price_amount`, `price_currency`, `page_count`, `status`, timestamps

**AudioSample**
- `id`, `arrangement_id`, `storage_url`/`storage_key`, `duration_seconds`, `format` (mp3/ogg)

**ArrangementFile** (o PDF vendido; pode incluir partes separadas)
- `id`, `arrangement_id`, `storage_key`, `type` (`SCORE`/`PART`), `label`
- **Sempre em bucket privado.**

### 5.2 Vendas

**Customer**
- `id`, `email` (único), `name`, `locale`, `country`, `email_verified`

**Order**
- `id`, `customer_id`, `status` (`PENDING`, `PAID`, `FAILED`, `REFUNDED`), `currency`, `total_amount`, `gateway_reference`, timestamps

**OrderItem**
- `id`, `order_id`, `arrangement_id`, `unit_price_amount` (**snapshot do preço no momento da compra**), `currency`

**Payment**
- `id`, `order_id`, `gateway`, `gateway_transaction_id`, `status`, `raw_payload` (jsonb), timestamps

**WebhookEvent** (idempotência)
- `id`, `gateway`, `external_event_id` (único), `processed_at`, `payload`

**DownloadGrant**
- `id`, `order_item_id`, `customer_id`, `arrangement_file_id`, `max_downloads`, `download_count`, `expires_at` (opcional)

### 5.3 Usuários e métricas

**User/AdminUser**
- `id`, `email`, `password_hash`, `role` (`ADMIN`; clientes usam magic link)

**TrackingEvent**
- `id`, `type` (`PAGE_VIEW`, `AUDIO_PLAY`, `BUY_CLICK`, `CHECKOUT_STARTED`, `PURCHASE_COMPLETED`, `DOWNLOAD`, `REFUND`), `work_id?`, `arrangement_id?`, `customer_id?`, `session_id`, `referrer`, `utm_*`, `metadata` (jsonb), `created_at`

Convenções de banco:
- Migrations via **Liquibase** (changelogs em `src/main/resources/db/changelog/`, preferencialmente YAML ou SQL formatado).
- Valores monetários: **`amount` em centavos (inteiro/`BIGINT`) + `currency` (ISO 4217)**; nunca `float`/`double`.
- Chaves primárias: `UUID` ou `BIGINT` (decidir e manter consistente).
- `created_at`/`updated_at` em todas as tabelas (JPA auditing).
- Índices em `slug`, `email`, FKs e campos de filtro.

---

## 6. Decisões de arquitetura

### 6.1 Pedido só vira `PAID` via webhook
Nunca confiar no redirect do navegador. O fluxo é:
1. Front chama `POST /checkout` → API cria `Order` `PENDING` e sessão de checkout no gateway.
2. Cliente paga na **página hospedada pelo gateway** (a API nunca toca em dados de cartão).
3. Gateway chama o webhook → API valida a assinatura, verifica idempotência, marca `PAID`, cria `DownloadGrant`s e dispara e-mail.

### 6.2 Entrega segura dos arquivos
- PDFs em bucket privado (S3/R2).
- Após validar o `DownloadGrant`, gerar **URL assinada com expiração curta**.
- Opcional/diferencial: **marca d'água com o e-mail do comprador** no PDF (PDFBox), gerada no download ou na compra.
- Controlar `download_count` e `max_downloads`.

### 6.3 Checkout sem conta (guest)
- Compra apenas com e-mail.
- Acesso a "Minhas compras" via **magic link** enviado por e-mail.
- Conta completa é opcional (não necessária no MVP).

### 6.4 Métricas em duas camadas
- **Front:** Plausible, PostHog ou GA4 (cliques, funil, origem).
- **Backend:** eventos de negócio próprios (`TrackingEvent`): compra, reembolso, download. Imunes a bloqueadores de anúncio.

### 6.5 Segurança
- Spring Security com JWT (ou sessão) para admin.
- Rate limiting em login, magic link, download e endpoints públicos de tracking.
- CORS restrito ao domínio do front.
- Segredos via variáveis de ambiente (nunca no repositório).
- Validação de entrada em todos os DTOs.

---

## 7. Gateway de pagamento (DECISÃO PENDENTE)

Candidatos:
- **Stripe**: maduro, SDK Java excelente, multi-moeda. O vendedor é responsável por impostos sobre vendas digitais internacionais (ex.: VAT europeu).
- **Paddle / Lemon Squeezy** (*Merchant of Record*): vendem em nome do vendedor e cuidam dos impostos internacionais, com taxa maior.
- Verificar disponibilidade atual para contas brasileiras antes de decidir.

**Para não travar o desenvolvimento:** criar uma abstração no módulo `payments`:

```java
public interface PaymentGateway {
    CheckoutSession createCheckout(Order order, String successUrl, String cancelUrl);
    WebhookResult handleWebhook(String payload, Map<String, String> headers);
    void refund(Payment payment);
}
```

Implementar primeiro uma versão **fake/mock** para desenvolvimento e testes; depois plugar a implementação real.

---

## 8. Internacionalização

- Conteúdo traduzível via tabelas `*_translation` (ex.: `work_translation(work_id, locale, title, description)`).
- Resolver idioma via `Accept-Language` ou parâmetro `?locale=`.
- Moeda: começar com uma moeda única (USD ou EUR) e evoluir para multi-moeda se necessário.

---

## 9. Endpoints sugeridos (rascunho)

### Públicos
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/v1/works` | Lista/filtra obras (instrumento, tonalidade, dificuldade, busca) |
| GET | `/api/v1/works/{slug}` | Detalhe da obra com arranjos e áudios |
| GET | `/api/v1/arrangements/{id}` | Detalhe de um arranjo |
| GET | `/api/v1/instrumentations` | Lista de formações (para filtros) |
| GET | `/api/v1/composers` | Lista de compositores |
| POST | `/api/v1/checkout` | Cria pedido e sessão de checkout |
| POST | `/api/v1/webhooks/{gateway}` | Recebe webhooks do gateway |
| POST | `/api/v1/auth/magic-link` | Solicita link de acesso por e-mail |
| GET | `/api/v1/my/orders` | Compras do cliente (autenticado via magic link) |
| GET | `/api/v1/my/downloads/{grantId}` | Retorna URL assinada de download |
| POST | `/api/v1/events` | Registra evento de tracking |

### Admin (`ROLE_ADMIN`)
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/admin/auth/login` | Login do admin |
| CRUD | `/api/v1/admin/works` | Obras |
| CRUD | `/api/v1/admin/composers` | Compositores |
| CRUD | `/api/v1/admin/instrumentations` | Formações |
| CRUD | `/api/v1/admin/arrangements` | Arranjos |
| POST | `/api/v1/admin/arrangements/{id}/files` | Upload de PDF |
| POST | `/api/v1/admin/arrangements/{id}/audio` | Upload de áudio |
| PATCH | `/api/v1/admin/works/{id}/publish` | Publicar/despublicar |
| GET | `/api/v1/admin/orders` | Lista de pedidos |
| POST | `/api/v1/admin/orders/{id}/refund` | Reembolso |
| GET | `/api/v1/admin/metrics/*` | Métricas (vendas, funil, top obras) |

---

## 10. Checklist de requisitos transversais

- [ ] Liquibase configurado com changelog mestre
- [ ] springdoc-openapi com Swagger UI em `/swagger-ui.html` (restringir em produção)
- [ ] Actuator (`health`, `info`, `metrics`) com endpoints sensíveis protegidos
- [ ] Testcontainers para testes de integração
- [ ] Perfis de ambiente (`local`, `test`, `prod`)
- [ ] Docker Compose (app + PostgreSQL, e opcionalmente MinIO para simular S3 localmente)
- [ ] Dockerfile multi-stage
- [ ] E-mail transacional (confirmação de compra, link de download, magic link)
- [ ] Rate limiting
- [ ] Logs estruturados e alerta para falha de webhook
- [ ] Backup automático do banco
- [ ] Full-text search do PostgreSQL para busca no catálogo (sem Elasticsearch no início)
- [ ] Slugs estáveis para SEO

---

## 11. Pontos jurídicos e fiscais (fora do código, mas importantes)

- **Domínio público varia por país.** A regra brasileira (vida + 70 anos) difere de outros países. Registrar `death_year` e priorizar compositores falecidos há muito tempo.
- O **arranjo é autoral** (direitos do autor do arranjo), mas a **gravação de áudio** deve ser própria ou sintetizada. Não usar gravações de terceiros.
- Publicar **termos de uso, política de privacidade e política de reembolso**.
- Conformidade com **GDPR e LGPD**.
- Consultar um **contador** sobre tributação de receita em moeda estrangeira.

---

## 12. Ordem de implementação sugerida

1. Bootstrap do projeto, Docker Compose, Liquibase, Actuator, Swagger.
2. Módulo `catalog` (entidades, migrations, endpoints públicos de leitura).
3. Segurança e admin (login, CRUD do catálogo).
4. Upload/armazenamento de áudio e PDF (storage abstrato + MinIO local).
5. Módulo `orders` + `payments` com gateway **mock**.
6. Webhook com idempotência, criação de `DownloadGrant`.
7. Módulo `delivery` (URL assinada, limite de downloads, marca d'água).
8. Magic link e "Minhas compras".
9. E-mails transacionais.
10. `analytics` (eventos e métricas admin).
11. Integração com o gateway real.
12. Rate limiting, hardening, observabilidade e deploy.

---

## 13. Instruções para o Cursor

Ao gerar código neste projeto:
- Seguir a arquitetura modular da seção 4 e **não** criar microsserviços.
- Usar sempre DTOs (preferencialmente `record`) na camada web.
- Toda mudança de schema deve ser feita via **changelog Liquibase**; nunca usar `ddl-auto=update`/`create` (usar `validate` ou `none`).
- Dinheiro sempre como inteiro em centavos + código da moeda.
- Não expor nunca `storage_key` ou URLs permanentes dos PDFs.
- Escrever testes de integração com Testcontainers para fluxos críticos (checkout, webhook, download).
- Documentar endpoints com anotações do springdoc (`@Operation`, `@ApiResponse`).
- Nenhum segredo hardcoded; usar `application-*.yml` com variáveis de ambiente.
