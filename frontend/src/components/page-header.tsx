import type { ReactNode } from "react"
import { Link } from "react-router-dom"
import { ArrowLeft } from "lucide-react"
import { Headline } from "@/components/highlight"
import { Button } from "@/components/ui/button"

interface PageHeaderProps {
  title: string
  highlight: string
  subtitle: string
  // Route the arrow goes back to (e.g. /admin). Not shown without it
  backTo?: string
  // Main page action (e.g. "+ Crear producto"): on the right, outside any card
  action?: ReactNode
}

export function PageHeader({ title, highlight, subtitle, backTo, action }: PageHeaderProps) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-4">
        {backTo ? (
          <Button variant="ghost" size="icon" asChild>
            <Link to={backTo}>
              <ArrowLeft className="h-5 w-5" />
            </Link>
          </Button>
        ) : null}
        <div>
          <h1 className="text-heading-sm text-foreground">
            <Headline text={title} highlight={highlight} />
          </h1>
          <p className="text-muted-foreground mt-2">{subtitle}</p>
        </div>
      </div>
      {action}
    </div>
  )
}
