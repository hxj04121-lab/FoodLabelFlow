import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { Shell } from './app/Shell'
const router = createBrowserRouter([{ path: '*', element: <Shell /> }])
export default function App() {
  return <RouterProvider router={router} />
}
