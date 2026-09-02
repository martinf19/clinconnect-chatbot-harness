// Phase 0: environment validation only. React does not call Spring's chat
// API yet (that begins in Phase 3) or interpret intents/entities/time — see
// CLAUDE.md Ownership Contract.
function requireEnv(name: keyof ImportMetaEnv): string {
  const value = import.meta.env[name]
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`)
  }
  return value
}

export const apiBaseUrl = requireEnv('VITE_API_BASE_URL')
