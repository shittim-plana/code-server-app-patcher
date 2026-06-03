# Code Server App Patcher

URL을 입력하면 해당 도메인 전용 [code-server-app](https://github.com/shittim-plana/code-server-app) APK를 생성하는 도구.

## 왜 필요한가

code-server-app은 런타임에 URL을 변경할 수 있지만, **"기본으로 열기"**(도메인 링크 연결)는 AndroidManifest에 하드코딩해야 한다. 여러 code-server 인스턴스를 각각 별도 앱으로 쓰려면 `applicationId`도 달라야 한다.

Patcher는 이 커스텀 빌드 과정을 자동화한다.

## 기능

- **URL 입력** → 해당 도메인 전용 APK 생성
- **applicationId 자동 생성** → 도메인 기반 (예: `com.codeserver.app.257mari`)
- **앱 이름 커스텀** → 도메인 또는 사용자 지정
- **기본으로 열기** → AndroidManifest intent-filter에 도메인 등록
- **아이콘 커스텀** (선택) → 기본은 </> 블루
- **서명** → 빌드 후 자동 서명

## 사용 방식

### CLI (개발자용)

```bash
./patch.sh \
  --url "https://my-codeserver.example.com" \
  --name "My Code Server" \
  --id "com.codeserver.app.myserver"
```

### 앱 (일반 사용자용) — 예정

Android 앱에서 URL 입력 → 온디바이스 빌드 → APK 설치.

## 동작 원리

1. `code-server-app` 템플릿 프로젝트를 복사
2. 다음을 교체:
   - `applicationId` (build.gradle.kts)
   - `android:host` (AndroidManifest.xml intent-filter)
   - `app_name` (strings.xml)
   - 기본 URL (SharedPreferences 초기값 또는 하드코딩)
3. Gradle 빌드
4. APK 서명

## 프로젝트 구조 (예정)

```
code-server-app-patcher/
├── template/              # code-server-app 소스 (서브모듈 또는 복사)
├── patch.sh               # CLI 패치 스크립트
├── patcher-app/           # Android 패처 앱 (예정)
├── scripts/
│   └── apply-patch.sh     # 실제 파일 교체 로직
└── README.md
```

## 관련 프로젝트

| 리포 | 역할 |
|------|------|
| [code-server-app](https://github.com/shittim-plana/code-server-app) | 원본 앱 (System WebView) |
| **code-server-app-patcher** | 도메인별 커스텀 APK 생성 도구 |
| web-app-keeper (예정) | 범용 웹앱 래퍼 템플릿 |

## 라이선스

ISC
