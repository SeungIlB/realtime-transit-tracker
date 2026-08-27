import { Navigate, Route, Routes } from 'react-router-dom'
import { SystemStatusPage } from './features/system/SystemStatusPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route path="/" element={<SystemStatusPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
