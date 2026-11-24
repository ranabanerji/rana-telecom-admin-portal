"use client"

import { useEffect, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { Loader2 } from "lucide-react"
import { useAuth } from "@/components/auth-provider"
import { Logo } from "@/components/logo"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { safeNextPath } from "@/lib/redirect"

export function LoginForm() {
  const { status, login } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const nextPath = safeNextPath(searchParams.get("next"))

  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (status === "authenticated") router.replace(nextPath)
  }, [status, nextPath, router])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(username, password)
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed, please try again")
      setSubmitting(false)
    }
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="items-center text-center">
        <div className="bg-primary text-primary-foreground mx-auto mb-2 flex size-10 items-center justify-center rounded-lg">
          <Logo className="size-5" />
        </div>
        <CardTitle className="text-headline-3">Telecom Demo Admin</CardTitle>
        <CardDescription>Sign in to continue</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="username">Username</Label>
            <Input
              id="username"
              name="username"
              autoComplete="username"
              autoFocus
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {error && (
            <p role="alert" className="text-sm text-red-500">
              {error}
            </p>
          )}
          <Button type="submit" className="w-full" disabled={submitting || !username || !password}>
            {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Log in
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
