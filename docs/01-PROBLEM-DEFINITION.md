# 01 — Problem Definition

## Business Problem

Clinicians and staff currently navigate multiple Clinician Connect pages, filters, dropdowns, schedules, and contact views to answer common operational questions about on-call coverage and approved consult routing.

Examples:

- Who is on call for Cardiology?
- Who is covering Neurology in Oakland?
- Who is on call for Pediatrics this weekend?
- What is the consult routing for Antioch Rheumatology?
- How do I reach the on-call clinician?

## Desired Outcome

Provide an authorized conversational entry point that retrieves the same approved enterprise information with materially less navigation.

The chatbot should reduce:
- repeated navigation;
- dropdown/filter effort;
- time spent locating schedule/contact information;
- ambiguity about stored routing instructions.

## Primary Users

- clinicians;
- clinical support staff;
- other authorized enterprise users who currently use Clinician Connect information.

The exact production role matrix remains a business/security decision.

## Baseline Information Domains

1. Locations
2. Specialties
3. Narrow department information tied to a location/specialty
4. Current on-call coverage
5. Scheduled on-call coverage
6. Provider contact information
7. On-call role definitions
8. Approved consult-routing information
9. Approved Chart Chat guidance when stored

## Important Scope Boundary

This is an information-retrieval and approved-routing system.

It is not a clinical reasoning engine.

The chatbot must not decide whether symptoms or a patient condition are clinically urgent.

`triage_consult` is retained as a legacy canonical intent ID from the supplied intent map, but its permitted behavior is only retrieval of routing instructions when the user explicitly supplies an urgency category such as `URGENT` or `NON_URGENT`.

## Success Direction

Success means:
- correct retrieval;
- safe clarification rather than guessing;
- reduced manual navigation;
- reliable handling of supported conversational follow-ups.

A formal business KPI for click/time reduction has not yet been supplied and is not invented here.
