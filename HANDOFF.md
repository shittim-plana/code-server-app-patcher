# Handoff

## 현재 상태

프로젝트 초기 단계. 문서만 작성됨. 구현 없음.

## 목표

URL을 입력하면 해당 도메인 전용 code-server-app APK를 생성하는 도구.

### 핵심 교체 대상

| 파일 | 교체 항목 | 예시 |
|------|----------|------|
| `app/build.gradle.kts` | `applicationId` | `com.codeserver.app.257mari` |
| `app/src/main/AndroidManifest.xml` | `android:host` (intent-filter) | `257mari.vscode.artblnd.net` |
| `app/src/main/res/values/strings.xml` | `app_name` | `Code Server (257mari)` |
| `app/src/main/kotlin/.../MainActivity.kt` | 기본 URL (선택) | `https://257mari.vscode.artblnd.net` |

### 구현 단계

1. **CLI 스크립트** (`patch.sh`) — sed/awk로 파일 교체 → Gradle 빌드 → 서명
2. **템플릿 관리** — code-server-app을 서브모듈 또는 릴리스 zip으로 포함
3. **Android 패처 앱** (선택) — 온디바이스 빌드

### 제약

- code-server-app의 빌드 환경 필요 (JDK 17, Android SDK, Kotlin)
- 온디바이스 빌드 시 Termux + Gradle 또는 AIDE 필요
- 서명 키: 사용자별 생성 또는 공용 키 제공

## 참조

- `code-server-app/` — 원본 앱 (v1.0.0 릴리스됨)
- `code-server-app/app/build.gradle.kts` — applicationId 위치
- `code-server-app/app/src/main/AndroidManifest.xml` — intent-filter 위치
- `code-server-app/scripts/env.sh` — 빌드 환경 설정

## 멀티모델 오케스트레이션

- Opus 4.6[1M]: 오케스트레이터
- Opus 4.5: 섬세한 구현/리뷰 (200k)
- Opus 4.8: Codex 확장판 — 코드 구현 에이전트
