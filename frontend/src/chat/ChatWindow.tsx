import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { clarificationPrompt } from './clarificationCopy'
import './ChatWindow.css'
import type { ChatTurn } from './useChat'
import { useChat } from './useChat'

const STATUS_COPY: Record<'NO_MATCH' | 'UNSUPPORTED' | 'ERROR', string> = {
  NO_MATCH: "I couldn't find a match for that.",
  UNSUPPORTED: "I'm not able to help with that request.",
  ERROR: 'Something went wrong on my end.',
}

/** The single assistant reply for one turn, rendered from Spring's response status. */
function AssistantReply({ turn, isLatest, onOption, onCancel }: {
  turn: ChatTurn
  isLatest: boolean
  onOption: (label: string) => void
  onCancel: () => void
}) {
  if (turn.status === 'sending') {
    return (
      <div className="bubble bubble-assistant bubble-pending" aria-live="polite">
        <span className="typing-dot" />
        <span className="typing-dot" />
        <span className="typing-dot" />
      </div>
    )
  }

  if (turn.status === 'failed') {
    return (
      <div className="bubble bubble-assistant bubble-error" role="alert">
        <p>{turn.errorMessage ?? 'Something went wrong. Please try again.'}</p>
      </div>
    )
  }

  const response = turn.response
  if (!response) {
    return null
  }

  if (response.status === 'ANSWER') {
    return (
      <div className="bubble bubble-assistant">
        <p>{response.answer_text}</p>
      </div>
    )
  }

  if (response.status === 'CLARIFICATION' && response.clarification) {
    const clarification = response.clarification
    const showActions = isLatest && clarification.reason !== 'LANGUAGE_UNCERTAIN'
    return (
      <div className="bubble bubble-assistant">
        <p>{clarificationPrompt(clarification)}</p>
        {showActions && clarification.options.length > 0 && (
          <div className="option-row">
            {clarification.options.map((option) => (
              <button
                key={option.option_id}
                type="button"
                className="option-button"
                onClick={() => onOption(option.label)}
              >
                {option.label}
              </button>
            ))}
          </div>
        )}
        {showActions && (
          <button type="button" className="cancel-link" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    )
  }

  return (
    <div className={`bubble bubble-assistant${response.status === 'ERROR' ? ' bubble-error' : ''}`}>
      <p>{STATUS_COPY[response.status as 'NO_MATCH' | 'UNSUPPORTED' | 'ERROR']}</p>
    </div>
  )
}

export default function ChatWindow() {
  const { turns, isSending, sendMessage, retry, startNewConversation } = useChat()
  const [draft, setDraft] = useState('')
  const scrollAnchorRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollAnchorRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [turns])

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    sendMessage(draft)
    setDraft('')
  }

  const lastTurnId = turns.length > 0 ? turns[turns.length - 1].id : null

  return (
    <div className="chat-window">
      <header className="chat-header">
        <h1>ClinConnect Assistant</h1>
        <button type="button" className="new-conversation" onClick={startNewConversation} disabled={turns.length === 0}>
          New conversation
        </button>
      </header>

      <div className="chat-transcript" role="log" aria-live="polite">
        {turns.length === 0 && (
          <p className="empty-state">
            Ask about on-call coverage, locations, specialties, or how to reach a provider.
          </p>
        )}
        {turns.map((turn) => (
          <div className="turn" key={turn.id}>
            <div className="bubble bubble-user">
              <p>{turn.userText}</p>
            </div>
            <AssistantReply
              turn={turn}
              isLatest={turn.id === lastTurnId}
              onOption={(label) => sendMessage(label)}
              onCancel={() => sendMessage('cancel')}
            />
            {turn.status === 'failed' && (
              <button type="button" className="retry-button" onClick={() => retry(turn.id)} disabled={isSending}>
                Retry
              </button>
            )}
          </div>
        ))}
        <div ref={scrollAnchorRef} />
      </div>

      <form className="chat-input-row" onSubmit={handleSubmit}>
        <label htmlFor="chat-input" className="sr-only">
          Message
        </label>
        <input
          id="chat-input"
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Who is on call for Neurology in Oakland?"
          disabled={isSending}
          autoComplete="off"
        />
        <button type="submit" disabled={isSending || draft.trim().length === 0}>
          Send
        </button>
      </form>
    </div>
  )
}
