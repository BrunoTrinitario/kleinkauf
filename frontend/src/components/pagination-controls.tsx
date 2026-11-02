import { strings } from "@/lib/strings"
import { Button } from "@/components/ui/button"

interface PaginationControlsProps {
  page: number
  totalPages: number
  disabled?: boolean
  onPageChange: (page: number) => void
}

// Anterior / Pagina X de Y / Siguiente. page empieza en 0, como en el backend. Con una sola pagina no se muestra
export function PaginationControls({ page, totalPages, disabled, onPageChange }: PaginationControlsProps) {
  if (totalPages <= 1) {
    return null
  }

  return (
    <div className="flex items-center justify-center gap-4">
      <Button
        variant="outline"
        size="sm"
        disabled={page === 0 || disabled}
        onClick={() => onPageChange(page - 1)}
      >
        {strings.pagination.prev}
      </Button>
      <span className="text-sm text-muted-foreground">
        {strings.pagination.pageOf(page + 1, totalPages)}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={page >= totalPages - 1 || disabled}
        onClick={() => onPageChange(page + 1)}
      >
        {strings.pagination.next}
      </Button>
    </div>
  )
}
