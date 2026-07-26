---
applyTo: "**/*.gradle.kts,gradle/libs.versions.toml,.github/workflows/*.yml"
---

- Use the version catalog.
- Add dependencies only for a current requirement.
- Java/Kotlin bytecode target stays 17 unless a recorded decision changes it.
- Preserve the verified AGP built-in Kotlin configuration.
- Run the narrowest applicable Gradle verification task.
- Avoid unrelated upgrades.