/** Stand-in for the workerd-only "cloudflare:workers" module in unit tests. */
export class DurableObject<Env = unknown> {
  constructor(
    protected ctx: DurableObjectState,
    protected env: Env
  ) {}
}
