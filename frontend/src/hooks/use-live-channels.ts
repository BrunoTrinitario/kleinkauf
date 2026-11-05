import { useEffect, useRef, useState } from "react"
import axios from "axios"
import { api, API_BASE_URL, PLATFORM_PATH } from "@/lib/api"
import { IS_PLATFORM } from "@/lib/platform"

/*
Real time for the panels (docs/backend/observability.md#websocket). One connection per page:
1. Requests a ticket with the JWT (single use, expires in 30 s): the JWT never goes in the URL.
2. Connects to /ws?ticket=, subscribes to the channels and pings every 30 s.
3. If it drops, requests another ticket and reconnects with a growing wait (2 s, 4 s... up to 30 s).
4. If the ticket answers 403 or the server closes with 1008 (the role was lost), it does not retry: "denied".
The WS only brings what is new: each page loads the past over REST.
 */

export type LiveStatus = "connecting" | "live" | "reconnecting" | "denied"

const TICKET_PATH = IS_PLATFORM ? `${PLATFORM_PATH}/observability/ws-ticket` : "/observability/ws-ticket"
const WS_URL = `${API_BASE_URL.replace(/^http/, "ws")}/ws`
const PING_MS = 30_000
const MAX_RETRY_MS = 30_000
// Closed by the server because the user no longer has the role
const ACCESS_REVOKED = 1008

export function useLiveChannels(
  channels: string[],
  onEvent: (channel: string, data: unknown) => void,
  enabled = true
): LiveStatus {
  const [status, setStatus] = useState<LiveStatus>("connecting")
  // The latest onEvent, without reconnecting every time the page re-renders
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
          // An unreadable message does not drop the connection
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
