
import { useEffect, useState } from "react"
import { toast } from "sonner"
import { useAuth } from "@/lib/auth-context"
import { useDeleteAccount, useMe, useUpdateProfile } from "@/api/account"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Headline } from "@/components/highlight"
import { Header } from "@/components/header"
import type { ProfileFormState } from "@/lib/types"
import { isValidName, isValidPassword } from "@/lib/validation"
import { PasswordRequirements } from "@/components/password-requirements"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"


export default function UserSettingsPage() {
	const { user, logout } = useAuth()
	const [profile, setProfile] = useState<ProfileFormState | null>(null)
	const [error, setError] = useState<string | null>(null)
	const [success, setSuccess] = useState<string | null>(null)
	const [isDeleteOpen, setIsDeleteOpen] = useState(false)
	const [deleteError, setDeleteError] = useState<string | null>(null)
	const me = useMe(!!user?.token)
	const updateProfile = useUpdateProfile()
	const deleteAccount = useDeleteAccount()
	const isLoading = me.isPending
	const isSaving = updateProfile.isPending
	const isDeleting = deleteAccount.isPending

	// El formulario arranca con lo que devuelve /auth/me (y se vuelve a llenar despues de guardar)
	useEffect(() => {
		if (me.data) {
			setProfile({
				id: Number(me.data.id),
				name: me.data.name ?? "",
				email: me.data.email ?? "",
				password: "",
			})
		}
	}, [me.data])

	useEffect(() => {
		if (me.isError) {
			setError(getErrorMessage(me.error, strings.userSettings.loadError))
		}
	}, [me.isError, me.error])

	const handleSave = async () => {
		if (!profile || !user?.token) {
			return
		}

		setError(null)
		setSuccess(null)

		if (!isValidName(profile.name)) {
			setError(strings.validation.nameInvalid)
			return
		}
		// Contrasena vacia = no se cambia
		if (profile.password !== "" && !isValidPassword(profile.password)) {
			setError(strings.validation.passwordReqsError)
			return
		}

		try {
			// La contrasena solo se manda si se escribio una nueva
			await updateProfile.mutateAsync({
				name: profile.name.trim(),
				password: profile.password === "" ? undefined : profile.password,
			})
			setSuccess(strings.userSettings.saveSuccess)
		} catch (err) {
			setError(getErrorMessage(err, strings.userSettings.saveError))
		}
	}

	// 409: es el unico ADMINISTRADOR de la tienda (el backend no la deja sin administrador)
	const handleDeleteAccount = async () => {
		setDeleteError(null)
		try {
			await deleteAccount.mutateAsync()
			setIsDeleteOpen(false)
			toast.success(strings.userSettings.deletedToast)
			// A la home, no al login: logout navega despues de que la ruta protegida reacciona
			logout("/")
		} catch (err) {
			setDeleteError(getErrorMessage(err, strings.userSettings.deleteError, { conflict: strings.userSettings.lastAdmin }))
		}
	}

	return (
		<div className="min-h-screen bg-background">
			<Header />
			<main className="container mx-auto px-4 py-8">
				<div className="mb-8">
					<h1 className="text-heading-sm text-foreground">
            <Headline text={strings.userSettings.title} highlight="cuenta" />
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
									<p className="text-sm text-cyan-edge">{success}</p>
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
									<Input type="email" value={profile.email} disabled readOnly />
									<p className="text-xs text-muted-foreground">{strings.adminUserEdit.emailLocked}</p>
								</div>
								<div className="grid gap-2">
									<label className="text-sm font-medium">{strings.userSettings.passwordLabel}</label>
									<Input
										type="password"
										autoComplete="new-password"
										placeholder={strings.userSettings.passwordPlaceholder}
										value={profile.password}
										onChange={(event) =>
											setProfile((prev) =>
												prev ? { ...prev, password: event.target.value } : prev
											)
										}
									/>
									{profile.password !== "" ? <PasswordRequirements password={profile.password} /> : null}
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

				<Card className="max-w-2xl mt-6">
					<CardHeader>
						<CardTitle>{strings.userSettings.deleteTitle}</CardTitle>
					</CardHeader>
					<CardContent className="space-y-4">
						<p className="text-sm text-muted-foreground">{strings.userSettings.deleteDescription}</p>
						{deleteError ? <p className="text-sm text-destructive">{deleteError}</p> : null}
						<Button
							variant="outline"
							className="text-destructive hover:text-destructive"
							onClick={() => setIsDeleteOpen(true)}
							disabled={isDeleting}
						>
							{isDeleting ? strings.userSettings.deleting : strings.userSettings.deleteButton}
						</Button>
					</CardContent>
				</Card>

				<AlertDialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
					<AlertDialogContent>
						<AlertDialogHeader>
							<AlertDialogTitle>{strings.userSettings.deleteQuestion}</AlertDialogTitle>
							<AlertDialogDescription>{strings.userSettings.deleteDescription}</AlertDialogDescription>
						</AlertDialogHeader>
						<AlertDialogFooter>
							<AlertDialogCancel>{strings.userSettings.deleteCancel}</AlertDialogCancel>
							<AlertDialogAction
								className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
								onClick={() => {
									setIsDeleteOpen(false)
									handleDeleteAccount()
								}}
							>
								{strings.userSettings.deleteConfirm}
							</AlertDialogAction>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialog>
			</main>
		</div>
	)
}
