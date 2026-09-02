import { useCallback, useRef, useState } from 'react'
import { ChatApiError, sendChatMessage } from './chatApi'
import type { ChatMessageResponseDto } from './types'

export type TurnStatus = 'sending' | 'sent' | 'failed'

/** One user message and (once it arrives) Spring's response to it. */
export interface ChatTurn {
  /** Doubles as the NFR-009 client_message_id — stable across a failed send's retries. */
  id: string
  userText: string
  status: TurnStatus
  response?: ChatMessageResponseDto
  errorMessage?: string
}

function createClientMessageId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // Fallback for a browser without crypto.randomUUID; still unique enough for a POC client id.
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`
}

/**
 * Owns only presentation state for one conversation (CLAUDE.md: "React owns
 * UI/presentation state and calls Spring only"). session_id is whatever
 * Spring last returned; an unknown/expired one is tolerated transparently
 * by Spring itself (docs/06-CONVERSATION-DESIGN.md "POC Restart Behavior"),
 * so this hook never inspects or validates it beyond echoing it back.
 */
export function useChat() {
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [isSending, setIsSending] = useState(false)
  const sessionIdRef = useRef<string | null>(null)

  const dispatch = useCallback(async (turnId: string, text: string) => {
    setIsSending(true)
    try {
      const response = await sendChatMessage({
        session_id: sessionIdRef.current,
        client_message_id: turnId,
        message: text,
      })
      sessionIdRef.current = response.session_id
      setTurns((prev) =>
        prev.map((turn) =>
          turn.id === turnId ? { ...turn, status: 'sent', response, errorMessage: undefined } : turn,
        ),
      )
    } catch (error) {
      if (error instanceof ChatApiError && error.status === 403) {
        // Our remembered session_id is no longer valid server-side (e.g. a Spring
        // restart cleared it); drop it so the next attempt starts a fresh session
        // instead of repeating the same rejection.
        sessionIdRef.current = null
      }
      const message =
        error instanceof ChatApiError ? error.message : 'Something went wrong. Please try again.'
      setTurns((prev) =>
        prev.map((turn) => (turn.id === turnId ? { ...turn, status: 'failed', errorMessage: message } : turn)),
      )
    } finally {
      setIsSending(false)
    }
  }, [])

  const sendMessage = useCallback(
    (text: string) => {
      const trimmed = text.trim()
      if (!trimmed || isSending) {
        return
      }
      const id = createClientMessageId()
      setTurns((prev) => [...prev, { id, userText: trimmed, status: 'sending' }])
      void dispatch(id, trimmed)
    },
    [dispatch, isSending],
  )

  /** Resends a failed turn with its original client_message_id + text (safe per NFR-009). */
  const retry = useCallback(
    (turnId: string) => {
      if (isSending) {
        return
      }
      const turn = turns.find((candidate) => candidate.id === turnId)
      if (!turn || turn.status !== 'failed') {
        return
      }
      setTurns((prev) =>
        prev.map((candidate) =>
          candidate.id === turnId ? { ...candidate, status: 'sending', errorMessage: undefined } : candidate,
        ),
      )
      void dispatch(turnId, turn.userText)
    },
    [turns, isSending, dispatch],
  )

  /** Client-side reset only: Spring keeps its own state until it separately expires/restarts. */
  const startNewConversation = useCallback(() => {
    sessionIdRef.current = null
    setTurns([])
  }, [])

  return { turns, isSending, sendMessage, retry, startNewConversation }
}
