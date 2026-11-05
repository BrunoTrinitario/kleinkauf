import type { ComponentType, SVGProps } from "react"
import { Facebook, Instagram } from "lucide-react"
import { useStoreConfig } from "@/api/store"
import { strings } from "@/lib/strings"
import type { SocialLinks } from "@/lib/types"

type IconProps = SVGProps<SVGSVGElement> & { className?: string }

// lucide-react does not have the X or TikTok logos: their marks, filled, in the same 24x24 box
function XIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden {...props}>
      <path d="M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z" />
    </svg>
  )
}

function TikTokIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden {...props}>
      <path d="M12.525.02c1.31-.02 2.61-.01 3.91-.02.08 1.53.63 3.09 1.75 4.17 1.12 1.11 2.7 1.62 4.24 1.79v4.03c-1.44-.05-2.89-.35-4.2-.97-.57-.26-1.1-.59-1.62-.93-.01 2.92.01 5.84-.02 8.75-.08 1.4-.54 2.79-1.35 3.94-1.31 1.92-3.58 3.17-5.91 3.21-1.43.08-2.86-.31-4.08-1.03-2.02-1.19-3.44-3.37-3.65-5.71-.02-.5-.03-1-.01-1.49.18-1.9 1.12-3.72 2.58-4.96 1.66-1.44 3.98-2.13 6.15-1.72.02 1.48-.04 2.96-.04 4.44-.99-.32-2.15-.23-3.02.37-.63.41-1.11 1.04-1.36 1.75-.21.51-.15 1.07-.14 1.61.24 1.64 1.82 3.02 3.5 2.87 1.12-.01 2.19-.66 2.77-1.61.19-.33.4-.67.41-1.06.1-1.79.06-3.57.07-5.36.01-4.03-.01-8.05.02-12.07z" />
    </svg>
  )
}

export type SocialNetwork = keyof SocialLinks

// Order in which they are shown; the label also names the network in the settings form
export const SOCIAL_NETWORKS: { key: SocialNetwork; label: string; icon: ComponentType<IconProps> }[] = [
  { key: "instagram", label: "Instagram", icon: Instagram },
  { key: "facebook", label: "Facebook", icon: Facebook },
  { key: "x", label: "X", icon: XIcon },
  { key: "tiktok", label: "TikTok", icon: TikTokIcon },
]

// The store's social network icons (only the ones it set). Nothing on the platform or if the store has none
export function StoreSocialLinks() {
  const config = useStoreConfig().data
  const links = SOCIAL_NETWORKS.filter((network) => config?.socials?.[network.key])
  if (!config || links.length === 0) return null

  return (
    <nav aria-label={strings.footer.socials} className="flex items-center gap-1">
      {links.map(({ key, label, icon: Icon }) => (
        <a
          key={key}
          href={config.socials[key] ?? undefined}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={strings.footer.socialLink(label, config.name)}
          title={label}
          className="rounded-md p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          <Icon className={key === "x" || key === "tiktok" ? "h-4 w-4" : "h-5 w-5"} />
        </a>
      ))}
    </nav>
  )
}
