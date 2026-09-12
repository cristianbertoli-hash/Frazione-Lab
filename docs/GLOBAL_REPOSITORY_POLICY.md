# Global Repository Policy

Scope: this policy applies to every current and future project, repository, branch, chat/channel and development stream managed for Cris, including BrainCore, FotoLab, Android USB Transfer, games, Whisper Locale, MEDLAB and related apps.

## Mandatory workflow
1. Before any repository work, inspect repository status, default branch, relevant branches, recent changes and any obvious anomaly/conflict/risk.
2. Do not develop significant changes directly on `main`. Create or use a dedicated branch for each feature, fix, variant, experiment, significant upload or recovery task.
3. Preserve working/stable versions. Never overwrite or delete a known-good branch/version without an explicit reason and a recoverable reference.
4. Use Git LFS for eligible large model/binary files. Avoid unnecessary duplicate model revisions because LFS stores each distinct version.
5. For very large deliverables, prefer appropriate release artifacts or split/reassembly workflows when repository/LFS single-file limits require it.
6. Before pushes involving large files, verify the target branch and file handling rules (`.gitattributes` / LFS tracking).
7. If a repository state, branch history, upload, build, CI result, LFS rule or storage situation looks wrong, report it to Cris before proceeding with risky changes.

## Storage / cost guardrails
- No automatic plan upgrades or spending increases.
- 80% of included storage/bandwidth quota: warn Cris and propose cleanup/optimization or a storage plan before adding more large assets.
- 90%: stop new non-essential large uploads until Cris explicitly chooses the next action.
- Prefer zero-cost solutions first: deduplication, immutable shared models, release artifacts, splitting files, pruning obsolete generated assets when safe.

## Branch discipline
Suggested prefixes: `feature/`, `fix/`, `test/`, `spike/`, `snapshot/`, `stable/`, `ops/`.
Branch names should describe the task and preserve clear separation between stable and experimental work.

## Persistence rule
This is a global operating rule, not a project-specific preference. It applies regardless of which chat/channel starts the work. New repositories should receive an equivalent governance file early in their lifecycle.

Updated: 2026-09-12
