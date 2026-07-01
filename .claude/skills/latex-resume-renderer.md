# Skill: LaTeX Resume Renderer

## The Prime Rule

**The LLM NEVER produces LaTeX.** It produces structured JSON. The renderer converts JSON to LaTeX deterministically using Mustache templates. This separation means the LLM is never responsible for formatting, only for data.

---

## JSON Schema the LLM Must Emit

Every resume tailoring LLM task must output JSON conforming to this schema (registered as `ResumeDataSchema`):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ResumeData",
  "type": "object",
  "required": ["candidateName", "contactInfo", "summary", "experience", "education", "skills"],
  "properties": {
    "candidateName": { "type": "string", "minLength": 1 },
    "contactInfo": {
      "type": "object",
      "required": ["email"],
      "properties": {
        "email":    { "type": "string", "format": "email" },
        "phone":    { "type": "string" },
        "linkedin": { "type": "string" },
        "github":   { "type": "string" },
        "location": { "type": "string" }
      }
    },
    "summary": { "type": "string", "maxLength": 600 },
    "experience": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["title", "company", "startDate", "bullets"],
        "properties": {
          "title":     { "type": "string" },
          "company":   { "type": "string" },
          "location":  { "type": "string" },
          "startDate": { "type": "string", "pattern": "^\\d{4}-\\d{2}$" },
          "endDate":   { "type": "string", "pattern": "^(\\d{4}-\\d{2}|present)$" },
          "bullets":   {
            "type": "array",
            "items": { "type": "string", "maxLength": 200 },
            "minItems": 1,
            "maxItems": 6
          }
        }
      }
    },
    "education": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["degree", "institution"],
        "properties": {
          "degree":      { "type": "string" },
          "institution": { "type": "string" },
          "year":        { "type": "string" },
          "gpa":         { "type": "string" }
        }
      }
    },
    "skills": {
      "type": "array",
      "items": { "type": "string" },
      "minItems": 1
    },
    "certifications": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name":   { "type": "string" },
          "issuer": { "type": "string" },
          "year":   { "type": "string" }
        }
      }
    },
    "projects": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name", "bullets"],
        "properties": {
          "name":        { "type": "string" },
          "description": { "type": "string" },
          "url":         { "type": "string" },
          "bullets":     { "type": "array", "items": { "type": "string" } }
        }
      }
    }
  }
}
```

---

## Template Catalog

Templates live in `src/main/resources/templates/latex/`. Each template is a `.tex.mustache` file. The `TemplateRegistry` bean maps template IDs to file paths.

| Template ID | File | Style |
|---|---|---|
| `classic` | `classic.tex.mustache` | Traditional single-column |
| `modern` | `modern.tex.mustache` | Two-column with color accents |
| `minimal` | `minimal.tex.mustache` | Clean, ATS-optimized, no graphics |
| `academic` | `academic.tex.mustache` | For research/academic roles |
| `tech` | `tech.tex.mustache` | Tech-focused, skills prominent |

---

## How Each Template Consumes the JSON

Templates use Mustache syntax. The `ResumeData` Java object (deserialized from LLM JSON) is passed directly to the Mustache engine.

Key Mustache conventions:
- `{{candidateName}}` — scalar field
- `{{#experience}}...{{/experience}}` — loop over list
- `{{#endDate}}{{endDate}}{{/endDate}}{{^endDate}}Present{{/endDate}}` — optional field with default
- LaTeX special characters (`&`, `%`, `$`, `#`, `_`, `{`, `}`, `~`, `^`, `\`) must be escaped in the Java `ResumeData` mapper before template rendering — do this in `LatexEscaper.escape(String)`, not in the template.

---

## Compilation Pipeline

```
ResumeData (Java)
  → LatexEscaper.escapeAll(resumeData)        // escape LaTeX special chars
  → MustacheRenderer.render(templateId, data) // produce .tex string
  → TempFile.write("resume.tex")              // write to temp dir
  → Tectonic.compile("resume.tex")            // tectonic --outdir /tmp resume.tex
  → read("resume.pdf")                        // return as byte[]
  → clean up temp dir
```

### Tectonic invocation:

```java
ProcessBuilder pb = new ProcessBuilder(
    "tectonic", "--outdir", tempDir.toString(), texFile.toString()
);
pb.environment().put("TECTONIC_CACHE_DIR", "/tmp/tectonic-cache");
pb.redirectErrorStream(true);
Process p = pb.start();
int exit = p.waitFor();
if (exit != 0) throw new RenderingException("Tectonic failed: " + output);
```

Tectonic must be available on `PATH` in the Docker image. Add to Dockerfile:
```dockerfile
RUN curl -L https://github.com/tectonic-typesetting/tectonic/releases/download/tectonic%400.15.0/tectonic-0.15.0-x86_64-unknown-linux-musl.tar.gz | tar xz -C /usr/local/bin
```

---

## How to Add a New Template

1. Create `src/main/resources/templates/latex/{template-id}.tex.mustache`.
2. Use only the fields defined in the JSON schema above — no custom fields.
3. Test compilation locally: write a sample JSON, call `ResumeRenderingService.render(sampleData, "{template-id}")`.
4. Register in `TemplateRegistry`:
   ```java
   registry.register("{template-id}", "templates/latex/{template-id}.tex.mustache");
   ```
5. Add an entry to the `TemplateId` enum.
6. Add an integration test in `TemplateRenderingIntegrationTest` that compiles the template and asserts the PDF is non-empty.

---

## Service Boundary

```
TailoringController
  → TailoringService.tailorResume(userId, jobPostingId, templateId)
    → LlmGateway.tailorResume(experienceChunks, jobDescription) → ResumeDataJson
    → JsonSchemaValidator.validate(json, ResumeDataSchema)
    → ResumeDataMapper.fromJson(json) → ResumeData
    → ResumeRenderingService.render(resumeData, templateId) → byte[] (PDF)
    → TailoredResumeRepository.save(...)
  → returns download URL
```

---

## Key Invariants

- `LlmGateway.tailorResume()` prompt must explicitly state: "Return ONLY valid JSON conforming to the schema. Do not include any LaTeX, markdown, or prose."
- `JsonSchemaValidator` runs before `ResumeDataMapper` — never attempt to render unvalidated JSON.
- PDFs are stored in object storage (DigitalOcean Spaces) keyed by `userId/resumeId.pdf`.
- Generated PDFs are never stored in the database — only the storage key is persisted.
