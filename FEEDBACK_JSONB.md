# Feedback JSONB Example, Flyway Migration, and Repository Queries

This document provides a concrete `feedback_form_templates.schema` JSON example that matches a typical dynamic feedback UI, a Flyway migration SQL script example to create tables and indexes, and sample Spring Data / native repository methods for querying answers stored in `jsonb`.

---

## 1. Example `feedback_form_templates.schema` (JSON)

This schema represents a dynamic form template the frontend can render. Questions have `id`, `type` (`rating`, `select`, `textarea`), `label`, `options` (for select), `required`, `helpText`, and an optional `validation` block.

```json
{
  "id": "template-interview-feedback-1",
  "name": "Standard Interview Feedback",
  "version": 1,
  "questions": [
    { "id": "q1", "type": "rating", "label": "Overall Fit (1-5)", "required": true, "helpText": "1 = Poor, 5 = Excellent" },
    { "id": "q2", "type": "rating", "label": "Technical Skills (1-5)", "required": true },
    { "id": "q3", "type": "select", "label": "Recommendation", "options": ["Hire", "Maybe", "No Hire"], "required": true },
    { "id": "q4", "type": "textarea", "label": "Notes and Feedback", "required": false },
    { "id": "q5", "type": "select", "label": "Interviewer Confidence", "options": ["High","Medium","Low"], "required": false }
  ],
  "meta": { "createdBy": "admin@company.com", "createdAt": "2026-05-25T00:00:00Z" }
}
```

---

## 2. Example `responses` JSON structure (to store in `feedback_submissions.responses`)

This is the payload your frontend would POST when a user saves/submits a feedback form. Keep `templateVersion` so you can interpret old responses.

```json
{
  "templateId": 1,
  "templateVersion": 1,
  "interviewId": 123,
  "interviewerId": 77,
  "candidateId": 555,
  "answers": [
    { "qId": "q1", "value": 5 },
    { "qId": "q2", "value": 4 },
    { "qId": "q3", "value": "Hire" },
    { "qId": "q4", "value": "Strong problem solving; minor gaps in frameworks." },
    { "qId": "q5", "value": "High" }
  ],
  "meta": { "submittedAt": null, "lastEditedAt": "2026-05-25T09:10:00Z" }
}
```

Notes:
- Store numeric ratings as numbers, selections as strings.
- `submittedAt` can be set when status flips to `submitted`.

---

## 3. Flyway migration SQL (place as `src/main/resources/db/migration/V2__create_feedback_tables.sql`)

```sql
-- V2__create_feedback_tables.sql
CREATE TABLE feedback_form_templates (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    schema JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE feedback_submissions (
    id SERIAL PRIMARY KEY,
    template_id INT REFERENCES feedback_form_templates(id),
    interview_id BIGINT,
    interviewer_id BIGINT,
    candidate_id BIGINT,
    responses JSONB NOT NULL,
    status TEXT DEFAULT 'draft',
    created_at TIMESTAMPTZ DEFAULT now()
);

-- GIN index for fast containment queries on responses
CREATE INDEX idx_feedback_submissions_responses_gin ON feedback_submissions USING gin (responses);

-- Expression index on a commonly queried top-level value inside responses
CREATE INDEX idx_feedback_submissions_candidateid ON feedback_submissions ((responses->>'candidateId')) WHERE (responses->>'candidateId') IS NOT NULL;

-- If you frequently query by interviewerId + status, a partial expression index can help
CREATE INDEX idx_feedback_submissions_interviewer_status ON feedback_submissions ((responses->>'interviewerId')) WHERE status = 'submitted';

-- Example: If rating q1 is queried often, you might add an index on a JSON path extraction (stored as expression)
CREATE INDEX idx_feedback_q1_rating ON feedback_submissions (((
    (SELECT (jsonb_array_elements(responses->'answers') ->> 'qId') -- not used directly; see queries below for practical approach
)));

-- Note: For complex array-extraction indexes you may prefer to maintain a denormalized numeric column
-- (e.g. q1_rating INT) that is updated on insert/update for really hot queries.
```

Important: The last `idx_feedback_q1_rating` is illustrative — Postgres doesn't support indexing the result of `jsonb_array_elements` directly in a straightforward expression; for hot fields consider storing them as top-level keys (e.g., `responses->'flat'->'q1'`) or denormalized columns.

---

## 4. Example SQL queries

- Insert submission (example):
```sql
INSERT INTO feedback_submissions (template_id, interview_id, interviewer_id, candidate_id, responses)
VALUES (1, 123, 77, 555, '{"templateId":1, "answers":[{"qId":"q1","value":5}], "meta":{}}'::jsonb);
```

- Find submissions where `q3` = 'Hire':
```sql
SELECT * FROM feedback_submissions
WHERE responses @> '{"answers":[{"qId":"q3","value":"Hire"}]}'::jsonb;
```

- Compute average for `q1` (ratings stored in `answers` array):
```sql
SELECT AVG((ans->>'value')::numeric) AS avg_q1
FROM feedback_submissions,
     jsonb_array_elements(responses->'answers') AS arr(ans)
WHERE (ans->>'qId') = 'q1' AND status = 'submitted';
```

- Update a single answer (replace answers[index].value) using `jsonb_set` (index-based):
```sql
-- Example: update the first answer's value to 4
UPDATE feedback_submissions
SET responses = jsonb_set(responses, '{answers,0,value}', '4', false)
WHERE id = 1;
```

- Atomically set `submittedAt` and status:
```sql
UPDATE feedback_submissions
SET responses = jsonb_set(responses, '{meta,submittedAt}', to_jsonb(now()), true),
    status = 'submitted'
WHERE id = 1;
```

---

## 5. Spring Data / Native repository examples

### 5.1 Entity mapping with `hibernate-types` (JsonBinaryType)

```java
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Entity
@Table(name = "feedback_submissions")
public class FeedbackSubmission {
    @Id
    private Long id;

    private Integer templateId;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> responses; // or a custom DTO class mapped via Jackson

    private String status;
    // getters/setters
}
```

Add dependency (Gradle/Maven): `com.vladmihalcea:hibernate-types-52`.

### 5.2 Spring Data repository (native query examples)

```java
public interface FeedbackSubmissionRepository extends JpaRepository<FeedbackSubmission, Long>, FeedbackSubmissionRepositoryCustom {

    // Example: native query to search submissions where q3 = 'Hire'
    @Query(value = "SELECT * FROM feedback_submissions WHERE responses @> cast(:jsonFilter as jsonb)", nativeQuery = true)
    List<FeedbackSubmission> findByJsonFilter(@Param("jsonFilter") String jsonFilter);

}
```

Call example from service:
```java
String json = "{\"answers\":[{\"qId\":\"q3\",\"value\":\"Hire\"}]}";
List<FeedbackSubmission> hire = repo.findByJsonFilter(json);
```

### 5.3 Custom repository for more complex queries (aggregation)

```java
public interface FeedbackSubmissionRepositoryCustom {
    Double findAverageRatingForQuestion(String qId);
}

@Repository
public class FeedbackSubmissionRepositoryImpl implements FeedbackSubmissionRepositoryCustom {
    @PersistenceContext
    private EntityManager em;

    @Override
    public Double findAverageRatingForQuestion(String qId) {
        String sql = "SELECT AVG((ans->> 'value')::numeric) AS avg_val " +
                     "FROM feedback_submissions, jsonb_array_elements(responses->'answers') AS arr(ans) " +
                     "WHERE (ans->> 'qId') = :qId AND status = 'submitted'";
        Object single = em.createNativeQuery(sql).setParameter("qId", qId).getSingleResult();
        return single == null ? null : ((Number) single).doubleValue();
    }
}
```

### 5.4 JdbcTemplate / NamedParameterJdbcTemplate alternative

For complex JSON path queries and pagination with performance, using `JdbcTemplate` with prepared SQL gives full control and avoids JPA pitfalls.

---

## 6. Best practices & recommendations

- Store template `schema` separately and reference by `template_id` + `template_version` in submissions.
- For fields you query or aggregate frequently (e.g., `q1` rating), consider denormalizing into separate columns (e.g., `q1_rating INT`) as part of the migration or maintain them via triggers or application logic — this provides fastest queries for dashboards.
- Use GIN indexes for flexible queries and expression indexes for top-level keys.
- Validate `responses` server-side against the template schema before accepting `submitted` status.
- Keep `status` column for quick filtering (draft vs submitted).
- If arrays are large or queries are heavy, consider a hybrid approach: key/value flattened in `responses.flat` and full details in `responses.raw`.

---

## 7. Next steps I can do for you

- Generate an actual Flyway file in the project `src/main/resources/db/migration/` with the SQL above.
- Add an entity class and repository samples in the backend codebase.
- Add a small example endpoint to search by a specific answer.

Tell me which you'd like me to add to the repo now.



# Structured Interview Feedback: JSONB vs Plain Text

**Short answer:** For structured interview feedback, JSONB is almost always a better choice than plain text — it keeps the structure, allows querying/indexing, and supports evolution. Plain text is only a good fit when you truly only need an opaque blob (logs, raw message) and never need to query fields.

## When Plain Text is OK
- **Use case:** simple logs, notes, or opaque blobs that you never need to filter, aggregate, or index.
- **Pros:** simplest to store and read; no parsing required.
- **Cons:** cannot query fields, no indexing on contents, hard to validate, and brittle for evolving structure.

## Why JSONB is Better for Feedback Forms
- **Structured data:** responses, question lists, metadata kept as JSON objects/arrays (natural fit for forms).
- **Queryable:** you can filter and aggregate by fields (`responses->'rating'`, `responses @> '{"q1":"yes"}'`).
- **Indexable:** GIN indexes let you index keys/paths for fast lookups (e.g., find all responses where `rating >= 4` with expression indexes).
- **Partial updates:** `jsonb_set` lets you update parts of the blob without rewriting unrelated data.
- **Schema flexibility:** you can evolve forms (add/remove questions) without altering DB schema or migration churn.
- **Performance:** JSONB is stored in a binary, compact format and is generally faster for structured queries than text+parsing.

## Drawbacks / Caution with JSONB
- **No enforced schema by the DB:** accidental inconsistent shapes unless you validate (app-level JSON Schema or DB check constraints).
- **Complex queries:** complex relational queries are more verbose and can be slower than normalized columns for very large datasets.
- **Index maintenance:** many or complex indexes add write overhead; index only the fields you query frequently.
- **Overuse anti-pattern:** storing everything in JSONB (when many fields are queried often) loses benefits of relational normalization (joins, foreign keys, constraints).

## Best Practice for Feedback Forms (Recommendation)
- **Hybrid model:** store metadata as columns for frequent queries, e.g., `id`, `interview_id`, `candidate_id`, `submitted_at`, `overall_rating` (indexed); store the full responses as a `jsonb` column for flexible structure and audit trail.
- **Validate at the app layer:** validate incoming JSON against a JSON Schema before insert (prevents garbage shapes).
- **Index what you query:** add a `GIN` index on the `jsonb` column and consider expression indexes for hot paths:
  ```sql
  CREATE INDEX idx_feedback_responses_gin ON feedback_response USING gin (responses);
  CREATE INDEX idx_feedback_rating ON feedback_response (( (responses->>'overallRating')::int ));