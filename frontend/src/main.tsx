import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './config' // fail fast if required environment variables are missing
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
