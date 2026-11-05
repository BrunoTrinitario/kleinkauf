import { useEffect, useRef, useState } from "react"
import axios from "axios"
import { api, API_BASE_URL, PLATFORM_PATH } from "@/lib/api"
import { IS_PLATFORM } from "@/lib/platform"

/*
Tiempo real de los paneles (docs/backend/observability.md#websocket). Una conexion por pagina:
1. Pide un ticket con el JWT (sirve una vez y vence en 30 s): el JWT nunca va en la URL.
2. Conecta a /ws?ticket=, se suscribe a los canales y manda ping cada 30 s.
3. Si se corta, pide otro ticket y reconecta con espera creciente (2 s, 4 s... hasta 30 s).
4. Si el ticket responde 403 o el servidor cierra con 1008 (perdio el rol), no reintenta: "denied".
El WS solo trae lo nuevo: lo anterior lo carga cada pagina por REST.
 */

export type LiveStatus = "connecting" | "live" | "reconnecting" | "denied"

const TICKET_PATH = IS_PLATFORM ? `${PLATFORM_PATH}/observability/ws-ticket` : "/observability/ws-ticket"
const WS_URL = `${API_BASE_URL.replace(/^http/, "ws")}/ws`
const PING_MS = 30_000
const MAX_RETRY_MS = 30_000
// Cerrado por el servidor porque ya no tiene el rol
const ACCESS_REVOKED = 1008

export function useLiveChannels(
  channels: string[],
  onEvent: (channel: string, data: unknown) => void,
  enabled = true
): LiveStatus {
  const [status, setStatus] = useState<LiveStatus>("connecting")
  // El ultimo onEvent sin reconectar cada vez que la pagina se vuelve a dibujar
  const onEventRef = useRef(onEvent)
  onEventRef.current = onEvent
  const channelsKey = channels.join(",")

  useEffect(() => {
    if (!enabled) return
    let socket: WebSocket | null = null
    let ping: ReturnType<typeof setInterval> | undefined
    let retry: ReturnType<typeof setTimeout> | undefined
    let attempts = 0
    let stopped = false

    const scheduleRetry = () => {
      if (stopped) return
      setStatus("reconnecting")
      const wait = Math.min(2_000 * 2 ** attempts, MAX_RETRY_MS)
      attempts += 1
      retry = setTimeout(connect, wait)
    }

    const connect = async () => {
      let ticket: string
      try {
        ticket = (await api.post<{ ticket: string }>(TICKET_PATH)).data.ticket
      } catch (error) {
        if (stopped) return
        if (axios.isAxiosError(error) && error.response?.status === 403) {
          setStatus("denied")
          return
        }
        scheduleRetry()
        return
      }
      if (stopped) return

      socket = new WebSocket(`${WS_URL}?ticket=${encodeURIComponent(ticket)}`)
      socket.onopen = () => {
        attempts = 0
        setStatus("live")
        for (const channel of channelsKey.split(",")) {
          socket?.send(JSON.stringify({ type: "subscribe", channel }))
        }
        ping = setInterval(() => socket?.send(JSON.stringify({ type: "ping" })), PING_MS)
      }
      socket.onmessage = (message) => {
        try {
          const parsed = JSON.parse(String(message.data)) as { type: string; channel?: string; data?: unknown }
          if (parsed.type === "event" && parsed.channel) {
            onEventRef.current(parsed.channel, parsed.data)
          }
        } catch {
          // Un mensaje ilegible no corta la conexion
        }
      }
      socket.onclose = (event) => {
        clearInterval(ping)
        socket = null
        if (stopped) return
        if (event.code === ACCESS_REVOKED) {
          setStatus("denied")
          return
        }
        scheduleRetry()
      }
    }

    setStatus("connecting")
    connect()
    return () => {
      stopped = true
      clearInterval(ping)
      clearTimeout(retry)
      socket?.close()
    }
  }, [channelsKey, enabled])

  return status
}
