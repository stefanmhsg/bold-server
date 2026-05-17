# mase-server Guidance

## Execution Plans

For complex features, significant refactors, migrations, or multi-session work in this directory, look for relevant `PLAN_*.md` files before implementing. Start with [PLAN_MASE_SERVER.md](PLAN_MASE_SERVER.md) for server-wide architecture and use feature-specific plans only when active. The admin reset feature is complete and documented in [README.md](README.md), so no active admin-reset plan remains.

Treat active plans as living documents: update progress, discoveries, decisions, validation results, and outcomes as work proceeds. When creating a new plan, use `PLAN_<SCOPE>.md` and keep it self-contained enough for a future Codex session to resume from the file alone.
