// Wire contract for POST /api/v1/chat/messages (docs/08-API-CONTRACTS.md +
// planning/PHASE-STATUS.md Phase 3 deviation #1, the response shape Spring
// actually implements). snake_case field names mirror the backend's global
// Jackson naming strategy exactly — this file is the one place that
// contract is spelled out on the frontend. React never derives or
// canonicalizes any of this; it only renders what Spring returns
// (CLAUDE.md: "React owns UI/presentation state and calls Spring only").

export type ChatResponseStatus = 'ANSWER' | 'CLARIFICATION' | 'NO_MATCH' | 'UNSUPPORTED' | 'ERROR'

export type ClarificationReason =
  | 'MISSING_PARAMETER'
  | 'AMBIGUOUS_ENTITY'
  | 'AMBIGUOUS_RESULT'
  | 'LANGUAGE_UNCERTAIN'

export type ClarificationParameterName =
  | 'location_text'
  | 'specialty_text'
  | 'provider_reference'
  | 'role_text'
  | 'contact_type'
  | 'time_expression'
  | 'time_context'
  | 'declared_urgency'

export interface ClarificationOption {
  option_id: string
  label: string
}

export interface ClarificationView {
  reason: ClarificationReason
  parameter: ClarificationParameterName | null
  options: ClarificationOption[]
}

export interface ChatMessageRequestDto {
  session_id: string | null
  client_message_id: string
  message: string
}

export interface ChatMessageResponseDto {
  session_id: string
  status: ChatResponseStatus
  answer_text: string | null
  clarification: ClarificationView | null
  correlation_id: string
}
