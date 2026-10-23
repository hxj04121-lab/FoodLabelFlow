export const demoOriginDisabledMessage = 'Course demo writes are disabled at this address. Use localhost or an explicitly enabled HTTPS demo origin.'

// This is a UI availability gate, not authentication. Hosted demos require site-wide access protection.
// Keep the shared export used by the additional course-demo controls in PR92.
export function isLocalDemoHost(
  currentLocation: Pick<Location, 'hostname' | 'origin'> = location,
  allowedOrigins: string = import.meta.env?.VITE_DEMO_ALLOWED_ORIGINS ?? '',
): boolean {
  if (['127.0.0.1', 'localhost', '[::1]'].includes(currentLocation.hostname)) return true
  return allowedOrigins.split(',').some(entry => {
    const origin = entry.trim()
    return origin.startsWith('https://') && !origin.includes('*') && origin === currentLocation.origin
  })
}
