# project-summary.md

---

# Career Copilot — Executive Summary

## What It Is

Career Copilot is a deployed, multi-user web application that automates the most time-consuming and error-prone parts of a job search. A user enters their real work experience once. From that point forward, the system handles the rest: it maintains a live portal of job postings pulled from real job APIs; it tailors a resume to any posting the user selects, grounded strictly in their actual experience; it renders that resume as a polished PDF using a professional LaTeX template the user chooses; it scores the result against the job posting the way a real applicant-tracking system would and tells the user exactly what is missing and how to fix it; and it tracks every application and interview in one place. As users log their interview experiences, the system anonymizes and pools that information into a shared, public knowledge base so every user benefits from what others have learned about a company's hiring process. A later phase turns that accumulated data into an active mock-interview trainer with spaced-repetition scheduling.

## Who It Is For

**Job seekers** who are applying to multiple roles and losing hours to manually rewriting resumes, formatting documents, and guessing why their applications are not getting responses. The system is designed for at least one hundred concurrent users, with strict per-user data privacy and a clean, browser-based interface requiring no technical knowledge to operate.

**Recruiters and hiring teams** are not the audience, but the system is designed with their tools in mind: the ATS scoring component replicates the keyword-screening logic that real applicant-tracking systems use, so the advice it gives is grounded in how applications are actually filtered.

## Why It Matters

**For users**, the value is concrete and measurable. A resume tailored by Career Copilot is grounded in the user's real experience — the system is architecturally prevented from inventing qualifications — and optimized against the specific language of the job description. The ATS scorer shows, before the user applies, the gap between their current resume and what the posting requires, with specific fixes. The shared interview intelligence compounds over time: the more people use the system, the richer the picture of each company's hiring process becomes.

**As a technical artifact**, Career Copilot is a full-stack, production-deployed demonstration of modern AI application engineering. It combines retrieval-augmented generation over a private per-user vector corpus, a deterministic document-rendering pipeline that keeps the LLM strictly in its lane, a hybrid keyword-plus-semantic ATS scoring engine, a privacy-preserving data-pooling architecture with a tested anonymization layer, and a provider-agnostic LLM integration that runs on a roughly twenty-five dollar budget. Every design decision is locked, documented, and enforced by automated guardrails. The system is deployed continuously — not once at the end — with integration tests running against real infrastructure on every merge.

**The headline metric** is the before-and-after ATS match score improvement across a hand-labeled set of twenty job descriptions: a single number that makes the system's value legible to any audience, technical or not.

---

*Built with Java 21, Spring Boot, Spring AI, PostgreSQL with pgvector, React, and deployed on DigitalOcean. Live URL available at project completion.*