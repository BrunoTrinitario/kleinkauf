import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react"
import { toast } from "sonner"
import { Check, ImageIcon, Trash2, Upload } from "lucide-react"
import { strings } from "@/lib/strings"
import { productImageUrl } from "@/lib/api"
import { getErrorMessage } from "@/lib/api-error"
import { cn } from "@/lib/utils"
import { applyStoreTheme, isStoreTheme, STORE_THEMES, THEMES, type StoreTheme } from "@/lib/store-themes"
import { useDeleteStoreIcon, useStoreConfig, useUpdateStoreConfig, useUploadStoreIcon } from "@/api/store"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"

const MAX_NAME = 60
const MAX_ICON_BYTES = 1024 * 1024
const ICON_TYPES = ["image/png", "image/jpeg", "image/webp", "image/gif"]

const t = strings.adminStore

// Store settings (/admin/store, ADMINISTRADOR only): name, color theme and icon. The theme previews on the whole
// page while choosing; leaving without saving restores the saved one. The icon is saved on upload
export default function AdminStorePage() {
  const config = useStoreConfig()
  const update = useUpdateStoreConfig()
  const uploadIcon = useUploadStoreIcon()
  const deleteIcon = useDeleteStoreIcon()
  const fileInput = useRef<HTMLInputElement>(null)

  const [name, setName] = useState("")
  const [theme, setTheme] = useState<StoreTheme>("OCEAN")
  const [showIconInHeader, setShowIconInHeader] = useState(true)
  const [showIconAsFavicon, setShowIconAsFavicon] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const saved = config.data
  useEffect(() => {
    if (!saved) return
    setName(saved.customName ?? "")
    setTheme(isStoreTheme(saved.theme) ? saved.theme : "OCEAN")
    setShowIconInHeader(saved.showIconInHeader)
    setShowIconAsFavicon(saved.showIconAsFavicon)
  }, [saved])

  // Theme preview on the whole page. Leaving restores the saved one
  const loaded = !!saved
  useEffect(() => {
    if (loaded) applyStoreTheme(theme)
  }, [theme, loaded])
  const savedTheme = useRef<string | undefined>(undefined)
  savedTheme.current = saved?.theme
  useEffect(() => () => applyStoreTheme(savedTheme.current), [])

  if (!saved) {
    return (
      <Shell>
        <p className="text-sm text-muted-foreground">{config.isError ? t.loadError : t.loading}</p>
      </Shell>
    )
  }

  const dirty =
    name.trim() !== (saved.customName ?? "") ||
    theme !== saved.theme ||
    showIconInHeader !== saved.showIconInHeader ||
    showIconAsFavicon !== saved.showIconAsFavicon

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const clean = name.trim()
    if (clean.length > MAX_NAME) {
      setError(t.nameTooLong(MAX_NAME))
      return
    }
    setError(null)
    try {
      await update.mutateAsync({ name: clean || null, theme, showIconInHeader, showIconAsFavicon })
      toast.success(t.saved)
    } catch (err) {
      setError(getErrorMessage(err, t.saveError))
    }
  }

  const handleIcon = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ""
    if (!file) return
    if (!ICON_TYPES.includes(file.type)) {
      toast.error(t.iconInvalid)
      return
    }
    if (file.size > MAX_ICON_BYTES) {
      toast.error(t.iconTooLarge)
      return
    }
    try {
      await uploadIcon.mutateAsync(file)
      toast.success(t.iconSaved)
    } catch (err) {
      toast.error(getErrorMessage(err, t.iconError))
    }
  }

  const handleDeleteIcon = async () => {
    try {
      await deleteIcon.mutateAsync()
      toast.success(t.iconDeleted)
    } catch (err) {
      toast.error(getErrorMessage(err, t.iconError))
    }
  }

  const iconBusy = uploadIcon.isPending || deleteIcon.isPending

  return (
    <Shell>
      <form onSubmit={handleSubmit} className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>{t.nameTitle}</CardTitle>
            <CardDescription>{t.nameDescription}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Label htmlFor="store-name">{t.nameLabel}</Label>
            <Input
              id="store-name"
              value={name}
              maxLength={MAX_NAME}
              placeholder={saved.customName ? undefined : saved.name}
              onChange={(event) => setName(event.target.value)}
              className="max-w-md"
            />
            <p className="text-xs text-muted-foreground">{t.nameHint}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t.themeTitle}</CardTitle>
            <CardDescription>{t.themeDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            <div role="radiogroup" aria-label={t.themeTitle} className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {STORE_THEMES.map((key) => {
                const swatch = THEMES[key].swatch
                const selected = theme === key
                return (
                  <button
                    key={key}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    onClick={() => setTheme(key)}
                    className={cn(
                      "flex items-center gap-3 rounded-lg border p-3 text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                      selected ? "border-foreground bg-muted" : "border-border hover:bg-muted/60"
                    )}
                  >
                    <span className="flex shrink-0 -space-x-1.5" aria-hidden>
                      {swatch.map((color, index) => (
                        <span
                          key={index}
                          className="h-6 w-6 rounded-full border-2 border-card ring-1 ring-border"
                          style={{ background: color }}
                        />
                      ))}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-sm font-medium">{t.themes[key]}</span>
                    {selected ? <Check className="h-4 w-4 shrink-0" /> : null}
                  </button>
                )
              })}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t.iconTitle}</CardTitle>
            <CardDescription>{t.iconDescription}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="flex flex-wrap items-center gap-4">
              <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-muted">
                {saved.icon ? (
                  <img src={productImageUrl(saved.icon)} alt={t.iconAlt} className="h-full w-full object-contain" />
                ) : (
                  <ImageIcon className="h-6 w-6 text-muted-foreground" />
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                <input
                  ref={fileInput}
                  type="file"
                  accept={ICON_TYPES.join(",")}
                  onChange={handleIcon}
                  className="sr-only"
                  tabIndex={-1}
                />
                <Button type="button" variant="outline" disabled={iconBusy} onClick={() => fileInput.current?.click()}>
                  <Upload className="h-4 w-4" />
                  {saved.icon ? t.iconChange : t.iconUpload}
                </Button>
                {saved.icon ? (
                  <Button type="button" variant="ghost" disabled={iconBusy} onClick={handleDeleteIcon}>
                    <Trash2 className="h-4 w-4" />
                    {t.iconDelete}
                  </Button>
                ) : null}
              </div>
              <p className="w-full text-xs text-muted-foreground">{t.iconHint}</p>
            </div>

            <div className="space-y-4">
              <ToggleRow
                id="icon-header"
                label={t.showInHeader}
                hint={t.showInHeaderHint}
                checked={showIconInHeader}
                onChange={setShowIconInHeader}
              />
              <ToggleRow
                id="icon-favicon"
                label={t.showAsFavicon}
                hint={t.showAsFaviconHint}
                checked={showIconAsFavicon}
                onChange={setShowIconAsFavicon}
              />
              {!saved.icon ? <p className="text-xs text-muted-foreground">{t.noIconNote}</p> : null}
            </div>
          </CardContent>
        </Card>

        {error ? (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        ) : null}
        <div className="flex justify-end">
          <Button type="submit" disabled={!dirty || update.isPending}>
            {update.isPending ? t.saving : t.save}
          </Button>
        </div>
      </form>
    </Shell>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto max-w-3xl flex-1 px-4 py-8">
        <PageHeader title={t.title} highlight={t.highlight} subtitle={t.subtitle} backTo="/admin" />
        {children}
      </main>
      <Footer />
    </div>
  )
}

function ToggleRow({
  id,
  label,
  hint,
  checked,
  onChange,
}: {
  id: string
  label: string
  hint: string
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="space-y-0.5">
        <Label htmlFor={id}>{label}</Label>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </div>
      <Switch id={id} checked={checked} onCheckedChange={onChange} />
    </div>
  )
}
