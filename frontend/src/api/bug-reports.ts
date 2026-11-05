import { useMutation } from "@tanstack/react-query"
import { api } from "@/lib/api"
import type { NewBugReport } from "@/lib/types"

// "Report a bug" from the store footer: with or without a session. Superadmins read them in the platform dashboard
export function useCreateBugReport() {
  return useMutation({
    mutationFn: (report: NewBugReport) => api.post("/bug-reports", report),
  })
}
