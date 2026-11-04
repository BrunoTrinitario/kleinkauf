// Store color themes (StoreTheme in products). The light ones change the primary color (buttons, active links),
// the focus color and the accent (highlights, savings, featured); backgrounds, grays and text stay as in
// index.css. NIGHT is the dark mode: it also changes the neutrals and turns on the .dark class (used by the ui/
// components). OCEAN is the index.css palette: without a custom theme nothing changes
export const STORE_THEMES = ["OCEAN", "FOREST", "TERRACOTTA", "GRAPE", "ROSE", "GRAPHITE", "NIGHT"] as const
export type StoreTheme = (typeof STORE_THEMES)[number]

interface Accent {
  primary: string // buttons (with white text: contrast of 4.5:1 or more)
  signal: string // focus ring and charts
  edge: string // accent text
  wash: string // highlight background
}

interface ThemeDefinition {
  accent: Accent
  dark?: boolean
  // Background, cards, text and borders (dark mode only)
  neutrals?: Record<string, string>
  // The three swatch colors on the settings page
  swatch: [string, string, string]
}

const light = (accent: Accent): ThemeDefinition => ({ accent, swatch: [accent.primary, accent.signal, accent.wash] })

const NIGHT_MUTED = "color-mix(in srgb, #fafaf9 7%, #0c0a09)"

export const THEMES: Record<StoreTheme, ThemeDefinition> = {
  OCEAN: light({ primary: "#1677bd", signal: "#3ba6f1", edge: "#3398e1", wash: "#c1e1f7" }),
  FOREST: light({ primary: "#2f7d4f", signal: "#4caf74", edge: "#2f8a55", wash: "#d3efdf" }),
  TERRACOTTA: light({ primary: "#b4532a", signal: "#e07a4f", edge: "#c25d33", wash: "#f7dccf" }),
  GRAPE: light({ primary: "#6d3fc0", signal: "#9b72e6", edge: "#7a4dd0", wash: "#e5dbf9" }),
  ROSE: light({ primary: "#be3a6a", signal: "#e8709b", edge: "#cc4a7a", wash: "#f8d6e3" }),
  GRAPHITE: light({ primary: "#1c1917", signal: "#57534e", edge: "#44403c", wash: "#e7e5e4" }),
  NIGHT: {
    dark: true,
    accent: { primary: "#1a73ba", signal: "#5bb4f5", edge: "#7cc3f7", wash: "#11314b" },
    neutrals: {
      "--background": "#0c0a09",
      "--foreground": "#f5f5f4",
      "--card": "#1c1917",
      "--card-foreground": "#f5f5f4",
      "--popover": "#1c1917",
      "--popover-foreground": "#f5f5f4",
      "--secondary": "#1c1917",
      "--secondary-foreground": "#f5f5f4",
      "--muted": NIGHT_MUTED,
      "--muted-foreground": "#a8a29e",
      "--accent": NIGHT_MUTED,
      "--accent-foreground": "#f5f5f4",
      // Almost black in light mode (errors and counters); red in dark mode: readable as text and under white text
      "--destructive": "#dc2626",
      "--border": "#292524",
      "--input": "#44403c",
      "--chart-2": "#a8a29e",
      "--chart-3": "#78716c",
      "--chart-4": "#44403c",
      "--chart-5": "#e7e5e4",
      "--sidebar": "#1c1917",
      "--sidebar-foreground": "#f5f5f4",
      "--sidebar-accent": NIGHT_MUTED,
      "--sidebar-accent-foreground": "#f5f5f4",
      "--sidebar-border": "#292524",
    },
    swatch: ["#0c0a09", "#1a73ba", "#f5f5f4"],
  },
}

const accentVariables = (accent: Accent): Record<string, string> => ({
  "--primary": accent.primary,
  "--sidebar-primary": accent.primary,
  "--ring": accent.signal,
  "--sidebar-ring": accent.signal,
  "--chart-1": accent.signal,
  "--color-cyan-signal": accent.signal,
  "--color-cyan-edge": accent.edge,
  "--color-sky-wash": accent.wash,
})

const variablesOf = (theme: ThemeDefinition) => ({ ...accentVariables(theme.accent), ...theme.neutrals })

// Every variable some theme changes: when switching themes, the ones the new theme does not set go back to index.css
const ALL_VARIABLES = [...new Set(Object.values(THEMES).flatMap((theme) => Object.keys(variablesOf(theme))))]

export const isStoreTheme = (value: string | null | undefined): value is StoreTheme =>
  !!value && (STORE_THEMES as readonly string[]).includes(value)

// The store's last saved theme, kept in the browser (each subdomain has its own). Applied before the first paint,
// so a dark store does not start out light until its settings arrive
const SAVED_THEME_KEY = "kk-store-theme"

export function rememberStoreTheme(theme: string) {
  try {
    localStorage.setItem(SAVED_THEME_KEY, theme)
  } catch {
    // Without localStorage only the startup theme is lost
  }
}

export function applyRememberedStoreTheme() {
  try {
    applyStoreTheme(localStorage.getItem(SAVED_THEME_KEY))
  } catch {
    // Same as above
  }
}

// Applies the theme on <html> (overriding :root). An unknown theme or OCEAN go back to the index.css values
export function applyStoreTheme(theme: string | null | undefined) {
  const root = document.documentElement
  const definition = isStoreTheme(theme) && theme !== "OCEAN" ? THEMES[theme] : null
  const variables = definition ? variablesOf(definition) : {}
  for (const name of ALL_VARIABLES) {
    if (name in variables) root.style.setProperty(name, variables[name])
    else root.style.removeProperty(name)
  }
  const dark = !!definition?.dark
  root.classList.toggle("dark", dark)
  // Dark scrollbars, native controls and autofill
  root.style.colorScheme = dark ? "dark" : ""
}
