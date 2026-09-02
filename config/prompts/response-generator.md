# Response Generator Prompt — Reserved, Not Baseline

The baseline architecture does **not** use an LLM to generate factual business responses.

Spring Boot deterministically formats factual answers from structured tool results.

This file is retained because it was part of the requested harness, but Claude Code must not wire it into baseline implementation phases.

Future use requires:
- explicit user approval;
- architecture review;
- grounding validation design;
- evaluation coverage proving no added provider/contact/schedule/routing facts.

Until then, this prompt is documentation-only.
