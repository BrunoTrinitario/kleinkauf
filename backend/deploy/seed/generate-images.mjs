// Genera las imagenes genericas del seed: una silueta por categoria en 3 variantes de color.
// Las PNG ya estan commiteadas en images/; esto solo hace falta para cambiarlas.
// Uso: node backend/deploy/seed/generate-images.mjs
import { writeFileSync, mkdirSync } from "node:fs"
import { deflateSync } from "node:zlib"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const W = 800
const H = 600
const SS = 2 // supersampling por eje para suavizar bordes
const OUT = join(dirname(fileURLToPath(import.meta.url)), "images")

// Paleta de STYLES.md (stone + cyan) y algunos tonos calidos de muebles
const BG_TOP = [250, 250, 249]
const BG_BOTTOM = [232, 230, 229]
const SHADOW = [214, 211, 209]
const VARIANTS = [
  { main: [59, 166, 241], accent: [193, 225, 247] }, // cyan
  { main: [168, 129, 92], accent: [231, 214, 194] }, // madera
  { main: [68, 64, 60], accent: [168, 162, 158] }, // grafito
]

// --- Primitivas: cada una dice si un punto esta adentro ---
const rect = (x, y, w, h, r = 0) => (px, py) => {
  const dx = Math.max(Math.abs(px - (x + w / 2)) - (w / 2 - r), 0)
  const dy = Math.max(Math.abs(py - (y + h / 2)) - (h / 2 - r), 0)
  return dx * dx + dy * dy <= r * r
}
const ellipse = (cx, cy, rx, ry) => (px, py) => ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2 <= 1
const polygon = (points) => (px, py) => {
  let inside = false
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const [xi, yi] = points[i]
    const [xj, yj] = points[j]
    if (yi > py !== yj > py && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

// --- Siluetas: lista de [forma, "main" | "accent"] en orden de pintado ---
const SHAPES = {
  iluminacion: [
    [ellipse(400, 470, 90, 16), "main"],
    [rect(394, 250, 12, 220), "main"],
    [polygon([[300, 260], [500, 260], [450, 130], [350, 130]]), "accent"],
    [rect(300, 252, 200, 12, 6), "main"],
  ],
  sillas: [
    [rect(290, 110, 26, 360, 8), "main"],
    [rect(290, 290, 220, 34, 10), "accent"],
    [rect(484, 300, 26, 170, 8), "main"],
    [rect(300, 120, 22, 175, 6), "accent"],
  ],
  mesas: [
    [rect(200, 230, 400, 34, 14), "main"],
    [rect(240, 260, 24, 210, 6), "main"],
    [rect(536, 260, 24, 210, 6), "main"],
    [rect(230, 236, 340, 10, 5), "accent"],
  ],
  sofas: [
    [rect(210, 200, 380, 140, 40), "accent"],
    [rect(180, 280, 440, 120, 34), "main"],
    [rect(170, 250, 70, 160, 30), "main"],
    [rect(560, 250, 70, 160, 30), "main"],
    [rect(215, 405, 18, 50, 6), "main"],
    [rect(567, 405, 18, 50, 6), "main"],
  ],
  decoracion: [
    [ellipse(400, 360, 110, 120), "main"],
    [rect(365, 170, 70, 110, 14), "main"],
    [ellipse(400, 175, 52, 16), "accent"],
    [rect(300, 345, 200, 18, 9), "accent"],
  ],
  textiles: [
    [rect(200, 150, 400, 300, 18), "main"],
    [rect(230, 180, 340, 240, 12), "accent"],
    [rect(230, 280, 340, 40), "main"],
    [rect(370, 180, 60, 240), "main"],
  ],
  espejos: [
    [ellipse(400, 290, 150, 200), "main"],
    [ellipse(400, 290, 126, 176), "accent"],
    [polygon([[320, 200], [350, 180], [460, 360], [430, 380]]), "main"],
  ],
  plantas: [
    [ellipse(330, 210, 70, 120), "accent"],
    [ellipse(470, 210, 70, 120), "accent"],
    [ellipse(400, 160, 60, 130), "main"],
    [polygon([[310, 300], [490, 300], [460, 470], [340, 470]]), "main"],
    [rect(296, 278, 208, 30, 10), "accent"],
  ],
  organizacion: [
    [rect(250, 100, 22, 380, 6), "main"],
    [rect(528, 100, 22, 380, 6), "main"],
    [rect(250, 150, 300, 16, 4), "accent"],
    [rect(250, 270, 300, 16, 4), "accent"],
    [rect(250, 390, 300, 16, 4), "accent"],
    [rect(300, 200, 50, 70, 6), "main"],
    [rect(420, 330, 90, 60, 6), "main"],
  ],
}

function render(layers) {
  const pixels = Buffer.alloc(W * H * 3)
  const shadow = ellipse(400, 480, 230, 26)
  for (let y = 0; y < H; y++) {
    const t = y / (H - 1)
    const bg = BG_TOP.map((c, i) => c + (BG_BOTTOM[i] - c) * t)
    for (let x = 0; x < W; x++) {
      const sum = [0, 0, 0]
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const px = x + (sx + 0.5) / SS
          const py = y + (sy + 0.5) / SS
          let color = shadow(px, py) ? SHADOW : bg
          for (const [inside, c] of layers) if (inside(px, py)) color = c
          for (let i = 0; i < 3; i++) sum[i] += color[i]
        }
      }
      const o = (y * W + x) * 3
      for (let i = 0; i < 3; i++) pixels[o + i] = Math.round(sum[i] / (SS * SS))
    }
  }
  return pixels
}

// --- PNG minimo: IHDR + IDAT (filtro 0 por fila) + IEND ---
const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})
const crc32 = (buf) => {
  let c = 0xffffffff
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}
const chunk = (type, data) => {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, "ascii"), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}
function png(pixels) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(W, 0)
  ihdr.writeUInt32BE(H, 4)
  ihdr[8] = 8 // bits por canal
  ihdr[9] = 2 // RGB
  const raw = Buffer.alloc((W * 3 + 1) * H)
  for (let y = 0; y < H; y++) pixels.copy(raw, y * (W * 3 + 1) + 1, y * W * 3, (y + 1) * W * 3)
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ])
}

mkdirSync(OUT, { recursive: true })
for (const [name, shapes] of Object.entries(SHAPES)) {
  VARIANTS.forEach((variant, i) => {
    const layers = shapes.map(([inside, role]) => [inside, variant[role]])
    const file = join(OUT, `${name}-${i + 1}.png`)
    writeFileSync(file, png(render(layers)))
    console.log(file)
  })
}
