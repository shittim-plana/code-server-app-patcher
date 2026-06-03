# 구현 계획

## Phase 1: CLI 패치 스크립트

### 목표
`patch.sh` 스크립트로 URL, 앱 이름, applicationId를 입력받아 커스텀 APK 생성.

### 작업

1. **템플릿 준비**
   - code-server-app v1.0.0 릴리스 APK 소스를 `template/` 디렉토리에 복사
   - 또는 git submodule로 연결
   - `.gitignore`에 빌드 산출물 제외

2. **`patch.sh` 구현**
   ```
   입력: --url, --name (선택), --id (선택)
   
   처리:
   1. template/ 복사 → build/
   2. URL에서 도메인 추출 (예: 257mari.vscode.artblnd.net)
   3. 도메인에서 applicationId 생성 (예: com.codeserver.app.257mari)
   4. sed로 파일 교체:
      - build.gradle.kts: applicationId
      - AndroidManifest.xml: android:host
      - strings.xml: app_name
   5. Gradle 빌드
   6. APK 서명
   
   출력: 서명된 APK 파일
   ```

3. **서명 키 관리**
   - 기본: `release.keystore` 포함 (개발/테스트용)
   - 사용자 키: `--keystore` 옵션

### 검증
- 다른 도메인으로 2개 APK 생성 → 동시 설치 확인
- 각 앱에서 "기본으로 열기" 동작 확인

---

## Phase 2: 앱 아이콘 커스텀 (선택)

- `--icon` 옵션으로 PNG 또는 SVG 입력
- ImageMagick 또는 Android `aapt2`로 mipmap 생성
- 기본: </> 블루 아이콘 유지

---

## Phase 3: Android 패처 앱 (선택)

- Termux + Gradle로 온디바이스 빌드
- 또는 GitHub Actions 연동 (URL 입력 → Actions 트리거 → APK 다운로드)
- UI: URL 입력 → 앱 이름 설정 → 빌드 → 설치

---

## 우선순위

Phase 1만 구현하면 핵심 기능 완성. Phase 2, 3은 필요 시.
