import { toast } from "sonner"
import { strings } from "@/lib/strings"
import { productShareUrl, whatsappShareUrl } from "@/lib/share"
import { Button } from "@/components/ui/button"
import { Link2, MessageCircle } from "lucide-react"

async function copyWithClipboardApi(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

// Plan B: without clipboard permission (or without the API) it copies by selecting a hidden textarea
function copyWithTextarea(text: string): boolean {
  const textarea = document.createElement("textarea")
  textarea.value = text
  textarea.setAttribute("readonly", "")
  textarea.style.position = "fixed"
  textarea.style.opacity = "0"
  document.body.appendChild(textarea)
  textarea.select()
  try {
    return document.execCommand("copy")
  } catch {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}

// Share a product: WhatsApp and copy the link (the /share one, with a preview)
export function ShareButtons({ slug, name }: { slug: string; name: string }) {
  const url = productShareUrl(slug)

  const copy = async () => {
    if ((await copyWithClipboardApi(url)) || copyWithTextarea(url)) {
      toast.success(strings.share.copied, { id: "share-copied" })
    } else {
      toast.error(strings.share.copyError)
    }
  }

  return (
    <div className="flex flex-wrap gap-2" aria-label={strings.share.title}>
      <Button variant="outline" size="sm" asChild>
        <a href={whatsappShareUrl(strings.share.message(name), url)} target="_blank" rel="noopener noreferrer">
          <MessageCircle className="h-4 w-4 mr-2" />
          {strings.share.whatsapp}
        </a>
      </Button>
      <Button variant="outline" size="sm" onClick={copy}>
        <Link2 className="h-4 w-4 mr-2" />
        {strings.share.copy}
      </Button>
    </div>
  )
}
