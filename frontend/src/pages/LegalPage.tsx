import { useEffect } from "react"
import { strings } from "@/lib/strings"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"

interface LegalPageProps {
  document: "privacy" | "terms"
}

// Privacy (/privacidad) and terms (/terminos), linked from the footer. Public
export default function LegalPage({ document: kind }: LegalPageProps) {
  const content = strings.legal[kind]

  // When going from one to the other from the footer, start from the top
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [kind])

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        <PageHeader title={content.title} highlight={content.highlight} subtitle={content.subtitle} />
        <article className="max-w-3xl space-y-8">
          {content.sections.map((section) => (
            <section key={section.title} className="space-y-3">
              <h2 className="text-lg font-medium text-foreground">{section.title}</h2>
              {section.paragraphs.map((paragraph) => (
                <p key={paragraph} className="text-sm leading-relaxed text-muted-foreground">
                  {paragraph}
                </p>
              ))}
            </section>
          ))}
          <p className="text-xs text-muted-foreground">{strings.legal.updated}</p>
        </article>
      </main>
      <Footer />
    </div>
  )
}
