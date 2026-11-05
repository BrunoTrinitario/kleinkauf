import { useRef, type ChangeEvent } from "react"
import { toast } from "sonner"
import { Trash2, Upload } from "lucide-react"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import type { User } from "@/lib/types"
import { useDeleteAvatar, useUploadAvatar } from "@/api/account"
import { UserAvatar } from "@/components/user-avatar"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

// Same rules as users (UserService.setAvatar): checked here first so the user gets the message right away
const MAX_BYTES = 1024 * 1024
const TYPES = ["image/png", "image/jpeg", "image/webp", "image/gif"]

const t = strings.userSettings

// The user's own profile photo: it is saved as soon as it is uploaded, apart from the profile form
export function ProfilePhotoCard({ user }: { user: User }) {
  const upload = useUploadAvatar()
  const remove = useDeleteAvatar()
  const input = useRef<HTMLInputElement>(null)
  const busy = upload.isPending || remove.isPending

  const handleFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ""
    if (!file) return
    if (!TYPES.includes(file.type)) {
      toast.error(t.photoInvalid)
      return
    }
    if (file.size > MAX_BYTES) {
      toast.error(t.photoTooLarge)
      return
    }
    try {
      await upload.mutateAsync(file)
      toast.success(t.photoSaved)
    } catch (err) {
      toast.error(getErrorMessage(err, t.photoError))
    }
  }

  const handleDelete = async () => {
    try {
      await remove.mutateAsync()
      toast.success(t.photoDeleted)
    } catch (err) {
      toast.error(getErrorMessage(err, t.photoError))
    }
  }

  return (
    <Card className="max-w-2xl mb-6">
      <CardHeader>
        <CardTitle>{t.photoTitle}</CardTitle>
        <CardDescription>{t.photoDescription}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap items-center gap-4">
        <UserAvatar name={user.name} avatar={user.avatar} className="h-20 w-20 text-lg" />
        <div className="space-y-2">
          <div className="flex flex-wrap gap-2">
            <input
              ref={input}
              type="file"
              accept={TYPES.join(",")}
              onChange={handleFile}
              className="sr-only"
              tabIndex={-1}
              aria-hidden
            />
            <Button type="button" variant="outline" disabled={busy} onClick={() => input.current?.click()}>
              <Upload className="h-4 w-4" />
              {upload.isPending ? t.photoUploading : user.avatar ? t.photoChange : t.photoUpload}
            </Button>
            {user.avatar ? (
              <Button type="button" variant="ghost" disabled={busy} onClick={handleDelete}>
                <Trash2 className="h-4 w-4" />
                {t.photoDelete}
              </Button>
            ) : null}
          </div>
          <p className="text-xs text-muted-foreground">{t.photoHint}</p>
        </div>
      </CardContent>
    </Card>
  )
}
