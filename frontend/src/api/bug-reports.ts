import { useMutation } from "@tanstack/react-query"
import { api } from "@/lib/api"
import type { NewBugReport } from "@/lib/types"

// "Reportar un bug" del footer de la tienda: con o sin sesion. Lo leen los superadmins en el panel de la plataforma
export function useCreateBugReport() {
  return useMutation({
    mutationFn: (report: NewBugReport) => api.post("/bug-reports", report),
  })
}
