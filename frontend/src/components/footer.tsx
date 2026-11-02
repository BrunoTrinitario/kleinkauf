import { strings } from "@/lib/strings"

export function Footer() {
  return (
    <footer className="border-t border-border mt-16">
      <div className="container mx-auto px-4 py-8">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground">
            {strings.footer.platform}
          </p>
        </div>
      </div>
    </footer>
  )
}
