
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import axios from "axios"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { Header } from "@/components/header"
import type { ProfileFormState } from "@/lib/types"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"


export default function UserSettingsPage() {
	const navigate = useNavigate()
	const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
	const [profile, setProfile] = useState<ProfileFormState | null>(null)
	const [isLoading, setIsLoading] = useState(true)
	const [isSaving, setIsSaving] = useState(false)
	const [error, setError] = useState<string | null>(null)
	const [success, setSuccess] = useState<string | null>(null)

	const apiBasePublic =
		import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

	useEffect(() => {
		if (isAuthLoading) {
			return
		}
		if (!isAuthenticated) {
			navigate("/login")
			return
		}
	}, [isAuthenticated, isAuthLoading, navigate])

	useEffect(() => {
		if (!user?.token) {
			return
		}

		let isMounted = true

		const loadProfile = async () => {
			try {
				setError(null)
				const response = await axios.get(`${apiBasePublic}/api/v1/auth/me`, {
					headers: {
						Authorization: `Bearer ${user.token}`,
					},
				})
				if (!isMounted) {
					return
				}

				const data = response.data as ProfileFormState
				setProfile({
					id: Number(data.id),
					name: data.name ?? "",
					email: data.email ?? "",
					password: "",
				})
			} catch {
				if (isMounted) {
					setError(strings.userSettings.loadError)
				}
			} finally {
				if (isMounted) {
					setIsLoading(false)
				}
			}
		}

		loadProfile()

		return () => {
			isMounted = false
		}
	}, [apiBasePublic, user?.token])

	const handleSave = async () => {
		if (!profile || !user?.token) {
			return
		}

		setIsSaving(true)
		setError(null)
		setSuccess(null)

		try {
			await axios.post(
				`${apiBasePublic}/api/v1/auth/update`,
				{
					name: profile.name,
					email: profile.email,
					password: profile.password,
				},
				{
					headers: {
						Authorization: `Bearer ${user.token}`,
					},
				}
			)
				setSuccess(strings.userSettings.saveSuccess)
		} catch {
				setError(strings.userSettings.saveError)
		} finally {
			setIsSaving(false)
		}
	}

	if (isAuthLoading || !isAuthenticated) {
		return null
	}

	return (
		<div className="min-h-screen bg-background">
			<Header />
			<main className="container mx-auto px-4 py-8">
				<div className="mb-8">
					<h1 className="text-3xl font-bold tracking-tight text-foreground">
							{strings.userSettings.title}
					</h1>
					<p className="text-muted-foreground mt-2">
							{strings.userSettings.subtitle}
					</p>
				</div>

				<Card className="max-w-2xl">
					<CardHeader>
							<CardTitle>{strings.userSettings.profileTitle}</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4">
						{isLoading ? (
								<p className="text-sm text-muted-foreground">{strings.userSettings.loading}</p>
						) : profile ? (
							<>
								{error ? (
									<p className="text-sm text-destructive">{error}</p>
								) : null}
								{success ? (
									<p className="text-sm text-emerald-600">{success}</p>
								) : null}
								<div className="grid gap-2">
									<label className="text-sm font-medium">{strings.userSettings.nameLabel}</label>
									<Input
										value={profile.name}
										onChange={(event) =>
											setProfile((prev) =>
												prev ? { ...prev, name: event.target.value } : prev
											)
										}
									/>
								</div>
								<div className="grid gap-2">
									<label className="text-sm font-medium">{strings.userSettings.emailLabel}</label>
									<Input
										type="email"
										value={profile.email}
										onChange={(event) =>
											setProfile((prev) =>
												prev ? { ...prev, email: event.target.value } : prev
											)
										}
									/>
								</div>
								<div className="grid gap-2">
									<label className="text-sm font-medium">{strings.userSettings.passwordLabel}</label>
									<Input
										type="password"
										value={profile.password}
										onChange={(event) =>
											setProfile((prev) =>
												prev ? { ...prev, password: event.target.value } : prev
											)
										}
									/>
								</div>
								<Button onClick={handleSave} disabled={isSaving}>
									{isSaving ? strings.userSettings.saving : strings.userSettings.save}
								</Button>
							</>
						) : (
							<p className="text-sm text-muted-foreground">{strings.userSettings.noData}</p>
						)}
					</CardContent>
				</Card>
			</main>
		</div>
	)
}
