import type { ClarificationParameterName, ClarificationView } from './types'

// Display-only copy for the closed clarification.reason/parameter enums
// (docs/08-API-CONTRACTS.md). Purely presentational: Spring has already
// decided *that* clarification is needed and *what* the options are; this
// only phrases it for a human. Not dictated by any doc — a Phase 5 design
// choice, see planning/PHASE-STATUS.md.
const PARAMETER_LABELS: Record<ClarificationParameterName, string> = {
  location_text: 'location',
  specialty_text: 'specialty',
  provider_reference: 'provider',
  role_text: 'role',
  contact_type: 'contact method',
  time_expression: 'time frame',
  time_context: 'time of day',
  declared_urgency: 'urgency (urgent or non-urgent)',
}

export function clarificationPrompt(view: ClarificationView): string {
  const parameter = view.parameter ? PARAMETER_LABELS[view.parameter] : 'that'
  switch (view.reason) {
    case 'MISSING_PARAMETER':
      return `Which ${parameter} did you mean?`
    case 'AMBIGUOUS_ENTITY':
      return `I found more than one match for ${parameter} — which one did you mean?`
    case 'AMBIGUOUS_RESULT':
      return `There's more than one possible ${parameter} here — which one did you mean?`
    case 'LANGUAGE_UNCERTAIN':
      return "I didn't quite catch that — could you rephrase your question?"
  }
}
