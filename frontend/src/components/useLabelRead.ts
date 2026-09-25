import { useEffect, useState } from 'react'
import { LabelApiError, type LabelDraft } from '@/api/labels'

type ReadState<T> =
  | { status: 'loading' }
  | { status: 'ready'; data: T }
  | { status: 'error'; message: string }

export function useLabelRead<T>(
  draft: LabelDraft | null,
  read: (draft: LabelDraft, signal?: AbortSignal) => Promise<T>,
  resource: string,
) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<ReadState<T>>({ status: 'loading' })

  useEffect(() => {
    if (!draft) return
    const controller = new AbortController()
    let active = true
    const timeout = setTimeout(() => controller.abort(), 15_000)
    setState({ status: 'loading' })
    read(draft, controller.signal)
      .then((data) => { if (active) setState({ status: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        setState({
          status: 'error',
          message: controller.signal.aborted
            ? `The ${resource} request timed out. Try again.`
            : error instanceof LabelApiError
              ? `${error.code}: ${error.message}`
              : `Unable to connect to the ${resource} service. Try again.`,
        })
      })
      .finally(() => clearTimeout(timeout))
    return () => {
      active = false
      clearTimeout(timeout)
      controller.abort()
    }
  }, [draft, read, resource, attempt])

  return { state, retry: () => setAttempt((value) => value + 1) }
}
