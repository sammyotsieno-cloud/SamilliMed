# Restore Repository to Original GitHub Android (Kotlin/Compose) Version

Plan to revert all recent React SPA modifications and restore the codebase to its original native Android Jetpack Compose state imported from `sammyotsieno-cloud/SamilliMed`.

## User Review & Critical Decisions

> [!IMPORTANT]
> The restoration will completely remove the React web application (`src/`, `package.json`, Vite configuration, node_modules) and reinstate the original Gradle and Android structure.

- **Target Platform**: Original Android Kotlin + Jetpack Compose application.
- **Gradle & AGP Versions**: Restore exact raw versions from the GitHub repository (`sammyotsieno-cloud/SamilliMed`).
- **Web Artifacts**: Completely delete all React files, `dist/`, `node_modules/`, and web configuration.

---

## 1. Overview & Core Concept

- **What It Does**: Reverts the repository to the authentic Android project structure, recreating all Gradle build configuration files, Kotlin source code (`MainActivity.kt`), XML resources, manifests, and wrapper files exactly as tracked on GitHub.
- **Target Audience**: Android developers and maintainers working on native Jetpack Compose for the SamilliMed healthcare application.
- **Key Value**: Returns the workspace to a pristine, unmodified state matching upstream GitHub commits without residual web artifacts.

---

## 2. Restoration Architecture & Steps

### System Architecture Diagram

```
[ Current Workspace (Web React SPA) ]
                  │
                  ▼
  Step 1: Clean Up React & Web Files
    ├── Remove /src, /dist, /node_modules
    ├── Remove package.json, vite.config.ts, tsconfig*.json
    └── Remove index.html
                  │
                  ▼
  Step 2: Fetch Original Files from GitHub
    └── Clone / checkout https://github.com/sammyotsieno-cloud/SamilliMed.git
                  │
                  ▼
  Step 3: Reinstate Original Android Tree
    ├── /.github/workflows/manual-build.yml
    ├── /app/build.gradle.kts
    ├── /app/src/main/AndroidManifest.xml
    ├── /app/src/main/java/org/samillimed/app/MainActivity.kt
    ├── /app/src/main/res/ (drawables, mipmaps, colors)
    ├── /build.gradle.kts
    ├── /gradle.properties
    ├── /gradle/libs.versions.toml
    ├── /gradle/wrapper/ (jar & properties)
    ├── /gradlew & /gradlew.bat
    └── /settings.gradle.kts
                  │
                  ▼
  Step 4: Restore Original README.md & Verification
```

---

## 3. Key Product Decisions & Trade-Offs

- **Exact Upstream Fetch vs. Manual Recreation**:
  - *Chosen Approach*: Clone the upstream `sammyotsieno-cloud/SamilliMed` repository into a temporary folder to cleanly extract binary resources (such as `gradle-wrapper.jar`, `samillimed_icon.png`, and launcher drawables) and exact commit files, then copy them into root.
  - *Why*: Ensures bit-for-bit fidelity with the GitHub repository, avoiding any missing binary gradle wrappers or modified versions.
- **Removal of Web Runtime Files**:
  - *Chosen Approach*: Remove `package.json`, `node_modules`, `src/`, `dist/`, and web configuration files completely.
  - *Why*: Prevents dual-framework conflicts and leaves the root directory as a pure Android project.

---

## 4. Technical Execution Plan

1. **Clean Web Artifacts**:
   - Delete `/src` directory.
   - Delete `/public` directory.
   - Delete `/dist` directory.
   - Delete `package.json`, `package-lock.json`, `tsconfig.json`, `vite.config.ts`, `index.html`.
2. **Fetch and Restore from GitHub Repository**:
   - Fetch the original commit tree from `https://github.com/sammyotsieno-cloud/SamilliMed.git`.
   - Restore all files to the workspace root:
     - Root Gradle files (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`).
     - Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `gradle/libs.versions.toml`).
     - App module (`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/org/samillimed/app/MainActivity.kt`).
     - Resources (`app/src/main/res/*`).
     - GitHub workflow (`.github/workflows/manual-build.yml`).
     - Original `README.md` and `.gitignore`.
3. **Verification**:
   - Verify all restored files are present and match original GitHub contents.
