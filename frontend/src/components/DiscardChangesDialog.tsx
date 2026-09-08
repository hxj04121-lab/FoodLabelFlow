import { FileWarning } from 'lucide-react'
import { AlertDialog } from 'radix-ui'
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
            放弃尚未保存的内容？
          </AlertDialog.Title>
          <AlertDialog.Description className="mt-3 text-sm leading-7 text-muted-foreground">
            本次填写的配方尚未保存。继续编辑可保留所有输入；放弃后无法恢复本次内容。
          </AlertDialog.Description>
          <div className="formula-actions">
            <AlertDialog.Cancel asChild>
              <Button variant="outline" onClick={onContinue}>
                继续编辑
              </Button>
            </AlertDialog.Cancel>
            <Button variant="destructive" onClick={onDiscard}>
              放弃并关闭
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
