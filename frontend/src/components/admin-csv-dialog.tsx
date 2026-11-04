import { useEffect, useState } from "react"
import { strings } from "@/lib/strings"
import { getErrorMessage, notifyError } from "@/lib/api-error"
import { downloadImportTemplate, exportProducts, useImportProducts, type ImportResult } from "@/api/products"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Download, FileSpreadsheet, Upload } from "lucide-react"

interface AdminCsvDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// Same limit as the backend (5 MB): a larger file is not even sent
const MAX_CSV_BYTES = 5 * 1024 * 1024

// Import and export the catalog as CSV (PRODUCTOS and PRECIOS: the file carries costs). Format in docs/backend/products.md
export function AdminCsvDialog({ open, onOpenChange }: AdminCsvDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)
  const importProducts = useImportProducts()

  useEffect(() => {
    if (!open) return
    setFile(null)
    setResult(null)
    setError(null)
  }, [open])

  const download = async (action: () => Promise<void>) => {
    setDownloading(true)
    try {
      await action()
    } catch (err) {
      notifyError(err, strings.adminCsv.exportError)
    } finally {
      setDownloading(false)
    }
  }

  const handleFile = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0] ?? null
    setResult(null)
    setError(selected && selected.size > MAX_CSV_BYTES ? strings.adminCsv.fileTooLarge : null)
    setFile(selected && selected.size <= MAX_CSV_BYTES ? selected : null)
  }

  const handleImport = async () => {
    if (!file) return
    setError(null)
    try {
      setResult(await importProducts.mutateAsync(file))
    } catch (err) {
      setError(getErrorMessage(err, strings.adminCsv.importError))
    }
  }

  const failed = result !== null && result.errors.length > 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.adminCsv.title}</DialogTitle>
          <DialogDescription>{strings.adminCsv.description}</DialogDescription>
        </DialogHeader>

        <div className="grid gap-2 sm:grid-cols-2">
          <Button variant="outline" disabled={downloading} onClick={() => download(exportProducts)}>
            <Download className="h-4 w-4 mr-2" />
            {strings.adminCsv.export}
          </Button>
          <Button variant="outline" disabled={downloading} onClick={() => download(downloadImportTemplate)}>
            <FileSpreadsheet className="h-4 w-4 mr-2" />
            {strings.adminCsv.template}
          </Button>
        </div>

        <div className="space-y-3 border-t border-border pt-4">
          <label htmlFor="csv-file" className="text-sm font-medium">
            {strings.adminCsv.fileLabel}
          </label>
          <input
            id="csv-file"
            type="file"
            accept=".csv,text/csv"
            onChange={handleFile}
            className="block w-full text-sm file:mr-3 file:rounded-full file:border-0 file:bg-muted file:px-4 file:py-2 file:text-sm file:font-medium"
          />
          <p className="text-xs text-muted-foreground">{strings.adminCsv.fileHint}</p>

          {error ? <p className="text-sm text-destructive">{error}</p> : null}

          {result && !failed ? (
            <p className="text-sm text-cyan-edge">{strings.adminCsv.done(result.created, result.updated)}</p>
          ) : null}

          {failed ? (
            <div className="space-y-2">
              <p className="text-sm font-medium text-destructive">{strings.adminCsv.errorsTitle(result.errors.length)}</p>
              <ul className="max-h-56 overflow-y-auto rounded-md border border-border text-sm divide-y divide-border">
                {result.errors.map((item, index) => (
                  <li key={`${item.row}-${index}`} className="flex gap-3 px-3 py-2">
                    <span className="w-24 shrink-0 text-muted-foreground">{strings.adminCsv.rowLabel(item.row)}</span>
                    <span className="min-w-0 break-words">{item.message}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {strings.adminCsv.close}
          </Button>
          <Button onClick={handleImport} disabled={!file || importProducts.isPending}>
            <Upload className="h-4 w-4 mr-2" />
            {importProducts.isPending ? strings.adminCsv.importing : strings.adminCsv.import}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
