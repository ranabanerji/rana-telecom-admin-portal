/** Read RFC 9457 details without hiding the fallback if a proxy returns HTML. */
export async function responseError(response: Response, fallback: string): Promise<Error> {
  try {
    const problem = await response.json()
    const fields =
      problem.errors && typeof problem.errors === "object"
        ? Object.entries(problem.errors)
            .map(([field, message]) => `${field}: ${message}`)
            .join("; ")
        : null
    return new Error(fields || problem.detail || fallback)
  } catch {
    return new Error(fallback)
  }
}
