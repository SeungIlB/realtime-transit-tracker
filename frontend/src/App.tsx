import { Navigate, Route, Routes } from 'react-router-dom'
import { TransitJourneyPage } from './features/journey/TransitJourneyPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route path="/" element={<TransitJourneyPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
