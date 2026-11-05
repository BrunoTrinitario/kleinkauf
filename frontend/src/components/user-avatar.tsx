import { avatarUrl } from "@/lib/api"
import { cn } from "@/lib/utils"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"

interface UserAvatarProps {
  name?: string | null
  avatar?: string | null
  className?: string
}

// First letter of the first two words: "Ana María Pérez" -> "AM"
const initials = (name?: string | null) =>
  (name ?? "")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join("")

// The user's photo, or their initials while it loads, when it fails or when there is none.
// The key remounts it when the photo changes: Radix keeps the "loaded" state after the image is removed, and the
// initials would never come back
export function UserAvatar({ name, avatar, className }: UserAvatarProps) {
  return (
    <Avatar key={avatar ?? "none"} className={cn("h-8 w-8", className)}>
      {avatar ? <AvatarImage src={avatarUrl(avatar)} alt="" className="object-cover" /> : null}
      {/* Highlight colors of the store theme: visible on light and dark cards alike */}
      <AvatarFallback className="bg-sky-wash text-xs font-medium text-cyan-edge">
        {initials(name) || "?"}
      </AvatarFallback>
    </Avatar>
  )
}
