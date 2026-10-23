/** Writes stay disabled off loopback until approved authentication replaces the header identity. */
export function isLocalDemoHost(hostname: string = location.hostname) {
  return ['127.0.0.1', 'localhost', '[::1]'].includes(hostname)
}
