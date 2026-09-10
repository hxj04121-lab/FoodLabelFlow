import { FileWarning } from 'lucide-react'
import { AlertDialog } from 'radix-ui'
import { useEffect } from 'react'
import { Button } from './ui/button'

export function DiscardChangesDialog({
  open,
  onContinue,
  onDiscard,
}: {
  open: boolean
  onContinue: () => void
  onDiscard: () => void
}) {
  useEffect(() => {
    if (!open) return
    const continueOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopImmediatePropagation()
        onContinue()
      }
    }
    document.addEventListener('keydown', continueOnEscape, true)
    return () => document.removeEventListener('keydown', continueOnEscape, true)
  }, [open, onContinue])

  return (
    <AlertDialog.Root
      open={open}
      onOpenChange={(value) => {
        if (!value) onContinue()
      }}
    >
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 z-[60] bg-slate-950/45" />
        <AlertDialog.Content className="discard-dialog fixed left-1/2 top-1/2 z-[61] w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl border bg-white p-6 shadow-xl">
          <div className="discard-symbol">
            <FileWarning size={25} />
          </div>
          <AlertDialog.Title className="text-lg font-semibold">
            Discard unsaved changes?
          </AlertDialog.Title>
          <AlertDialog.Description className="mt-3 text-sm leading-7 text-muted-foreground">
            Your formula has not been saved. Keep editing to retain your entries, or discard them permanently.
          </AlertDialog.Description>
          <div className="formula-actions">
            <AlertDialog.Cancel asChild>
              <Button variant="outline" onClick={onContinue}>
                Keep editing
              </Button>
            </AlertDialog.Cancel>
            <Button variant="destructive" onClick={onDiscard}>
              Discard changes
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
