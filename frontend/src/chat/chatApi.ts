import { apiBaseUrl } from '../config'
import type { ChatMessageRequestDto, ChatMessageResponseDto } from './types'

/**
 * A failed call to Spring's chat endpoint. `status` is the HTTP status when
 * Spring responded (403 session-ownership mismatch, 409 idempotency
 * conflict per NFR-009, or anything else unexpected); absent for a
 * transport-level failure (Spring/network unreachable).
 */
export class ChatApiError extends Error {
  readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'ChatApiError'
    this.status = status
  }
}

/** POST /api/v1/chat/messages (docs/08-API-CONTRACTS.md). The only Spring endpoint React calls. */
export async function sendChatMessage(request: ChatMessageRequestDto): Promise<ChatMessageResponseDto> {
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}/api/v1/chat/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
  } catch {
    throw new ChatApiError('Could not reach the chat service. Check your connection and try again.')
  }

  if (response.status === 403) {
    throw new ChatApiError('This conversation is no longer available. Starting a new one.', 403)
  }
  if (response.status === 409) {
    throw new ChatApiError('That message could not be resent as-is. Please try again.', 409)
  }
  if (!response.ok) {
    throw new ChatApiError(`The chat service returned an unexpected error (${response.status}).`, response.status)
  }

  return (await response.json()) as ChatMessageResponseDto
}
