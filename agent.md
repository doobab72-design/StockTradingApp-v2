# Agent Workflow — StockTradingApp

이 프로젝트는 **Developer → Code Reviewer → Tester** 3개의 Agent가 순차적으로 협업하는 구조로 운영됩니다.
모든 코드 변경은 이 파이프라인을 반드시 거쳐야 합니다.

---

## 파이프라인 개요

```
[Developer Agent]
       │  구현 완료 + 커밋
       ▼
[Code Reviewer Agent]
       │  리뷰 통과
       ▼
[Tester Agent]
       │  테스트 통과
       ▼
  GitHub PR Merge
```

---

## Agent 1 — Developer

### 역할
기능 구현, 버그 수정, 리팩터링을 담당합니다.

### 기술 스택 (이 프로젝트 기준)
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `@Singleton`, `@Inject`)
- **비동기**: Coroutines + Flow + StateFlow + `viewModelScope`
- **네트워크**: Retrofit2 + OkHttp3 + Gson
- **로컬 DB**: Room (`@Entity`, `@Dao`, `@Database`)
- **백그라운드**: WorkManager + `@HiltWorker`
- **보안**: EncryptedSharedPreferences (API 키 저장)
- **빌드**: Gradle KTS, `JAVA_HOME=/Users/doobab/dev-tools/jdk-17.0.13+11/Contents/Home`

### 책임 범위
1. **기능 구현**
   - ViewModel → Repository → API/DB 레이어 순서로 작업
   - `StateFlow` + `DashboardUiState`로 단방향 데이터 흐름 유지
   - `CoroutineExceptionHandler`로 모든 코루틴 예외 반드시 처리

2. **버그 수정 시 필수 체크리스트**
   - OkHttp Interceptor 내 `runBlocking` → try-catch 필수 (OkHttp 스레드에서 예외 시 앱 크래시)
   - Hilt Worker 사용 시 → `Application`에서 `Configuration.Provider` 구현 필수
   - KIS API 날짜 파라미터 → `FID_INPUT_DATE_1`, `FID_INPUT_DATE_2` 모두 전달
   - JSON 필드명 불일치 → `@SerializedName` 어노테이션으로 명시적 매핑
   - `nullable` 필드 → `.orEmpty()`, `?.let`, `?:` 방어 처리

3. **커밋 규칙**
   ```
   feat:  새 기능
   fix:   버그 수정
   refactor: 리팩터링 (기능 변경 없음)
   chore: 빌드/설정 변경
   docs:  문서 수정
   ```
   - 커밋 메시지에 **무엇을, 왜** 수정했는지 반드시 기술
   - Co-Author: `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`

4. **빌드 확인**
   ```bash
   JAVA_HOME=/Users/doobab/dev-tools/jdk-17.0.13+11/Contents/Home \
     ./gradlew :app:compileDebugKotlin
   ```

### 작업 완료 조건
- [ ] 빌드 성공 (`BUILD SUCCESSFUL`)
- [ ] Kotlin 컴파일 에러 0개
- [ ] 관련 파일 모두 커밋
- [ ] Code Reviewer Agent에게 변경 파일 목록 전달

---

## Agent 2 — Code Reviewer

### 역할
Developer가 작성한 코드의 품질, 안전성, 아키텍처 일관성을 검토합니다.

### 리뷰 체크리스트

#### 🔐 보안
- [ ] API 키/토큰이 로그에 출력되지 않는가? (`Log.d` 레벨로만)
- [ ] `SecureCredentialManager`를 통해서만 자격증명 접근하는가?
- [ ] `EncryptedSharedPreferences` 외 일반 SharedPreferences에 민감 정보 없는가?
- [ ] 네트워크 요청에 HTTPS만 사용하는가?

#### 🏗️ 아키텍처
- [ ] ViewModel이 View(Compose)에 직접 의존하지 않는가?
- [ ] Repository가 ViewModel에 직접 의존하지 않는가?
- [ ] `StateFlow`를 통한 단방향 데이터 흐름을 지키는가?
- [ ] Hilt 의존성 그래프에 순환 의존이 없는가?
  - 순환 방지 패턴: `KISApiServiceProvider` (지연 초기화 프로바이더)

#### ⚡ 안전성 (이 프로젝트 특이사항)
- [ ] OkHttp Interceptor 내 `runBlocking` 호출에 try-catch가 있는가?
  ```kotlin
  // ✅ 올바른 패턴
  val token = try {
      runBlocking { tokenManager.getValidToken(appKey, appSecret) }
  } catch (e: Exception) {
      Log.e(TAG, "토큰 획득 실패: ${e.message}")
      null
  }
  ```
- [ ] WorkManager 사용 시 `Configuration.Provider` 구현 여부 확인
- [ ] `CoroutineExceptionHandler`가 ViewModel 코루틴에 적용되어 있는가?
- [ ] `nullable` API 응답 필드가 방어 처리되어 있는가? (NPE 방지)

#### 🎯 KIS API 특이사항
- [ ] `getDailyPrice` 호출 시 `FID_INPUT_DATE_1`/`FID_INPUT_DATE_2` 날짜 파라미터 포함
- [ ] API 응답 JSON 키와 DTO 필드명 일치 여부 (`@SerializedName` 확인)
  ```kotlin
  // FHKST01010400 응답은 "output" 키 (output2 아님)
  @SerializedName("output")
  val output2: List<DailyPriceOutput>?
  ```
- [ ] 토큰 403 오류와 네트워크 오류를 구분하여 사용자에게 안내하는가?
- [ ] MACD 계산에 최소 35 거래일(slowPeriod 26 + signalPeriod 9) 데이터 확보하는가?

#### 🎨 코드 품질
- [ ] 함수 길이가 50줄을 넘지 않는가? (넘으면 분리 권장)
- [ ] 매직 넘버에 이름 있는 상수(`companion object const`)를 사용하는가?
- [ ] 로그 태그가 `TAG = "클래스명"` 형식으로 통일되어 있는가?
- [ ] 한국어 주석이 충분히 작성되어 있는가?
- [ ] 중복 코드가 없는가? (DRY 원칙)

#### 📱 UI/UX
- [ ] 에러 메시지가 사용자 액션 가능한 내용을 포함하는가?
  - ❌ "오류가 발생했습니다"
  - ✅ "KIS API 키가 유효하지 않습니다. apiportal.koreainvestment.com에서 확인하세요"
- [ ] 로딩 상태가 `isRefreshing`, `isRecommendationRefreshing` 등으로 반영되는가?
- [ ] Snackbar/에러가 콘텐츠 위에 오버레이되는가? (별도 Box로 가리지 않는가?)

### 리뷰 결과 출력 형식
```
## 코드 리뷰 결과

### ✅ 통과 항목
- ...

### ⚠️ 권장 수정사항 (blocking 아님)
- ...

### 🚫 필수 수정사항 (merge 불가)
- ...

### 판정: APPROVED / REQUEST_CHANGES
```

---

## Agent 3 — Tester

### 역할
실제 기기(ADB)에 APK를 설치하고 logcat으로 런타임 동작을 검증합니다.

### 환경 설정
```bash
export PATH="$PATH:/Users/doobab/dev-tools/android-sdk/platform-tools"
export JAVA_HOME="/Users/doobab/dev-tools/jdk-17.0.13+11/Contents/Home"
DEVICE_SERIAL="R3CY10CSCQX"
PACKAGE_NAME="com.stocktrading.debug"
MAIN_ACTIVITY="com.stocktrading.MainActivity"
```

### 테스트 실행 절차

#### Step 1. 빌드 & 설치
```bash
# APK 빌드
./gradlew :app:assembleDebug

# 기기 설치
adb -s $DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk

# 로그 초기화 후 앱 실행
adb -s $DEVICE_SERIAL logcat -c
adb -s $DEVICE_SERIAL shell am start -n $PACKAGE_NAME/$MAIN_ACTIVITY
```

#### Step 2. 런타임 로그 수집
```bash
# 15~30초 후 로그 수집
sleep 20 && adb -s $DEVICE_SERIAL logcat -d 2>&1 | grep -E \
  "(KISToken|KISAuth|StockRepo|DashboardViewModel|DailyRecommend|WorkScheduler|AndroidRuntime|FATAL)"
```

#### Step 3. 기능별 테스트 시나리오

**TC-01: 앱 정상 실행**
```
기대 로그 없음: AndroidRuntime, FATAL EXCEPTION
기대 로그 있음: KISTokenManager, StockRepository
```

**TC-02: 토큰 발급**
```
기대: I KISTokenManager: 토큰 발급 성공 (만료: YYYY-MM-DD HH:mm:ss)
실패: E KISTokenManager: 토큰 발급 실패: 403 Forbidden
  → 앱키/시크릿 확인 필요
```

**TC-03: 주가 데이터 수집**
```
기대: I StockRepository: [종목코드] N개 주가 데이터 저장 완료
  - N >= 30 이어야 함 (MACD 계산 최소 요건)
  - 15개 종목 중 최소 12개 이상 성공 기대
실패 패턴:
  E StockRepository: [종목코드] API 오류: 500 - null  → 일부 허용 (3개 이하)
  W DailyRecommendationWorker: [종목코드] 데이터 부족 (0개)  → 날짜 파라미터 누락 의심
```

**TC-04: 추천 종목 분석**
```
기대: I DashboardViewModel: 추천 종목 새로고침 완료: N개 (성공: M, 실패: K)
  - 성공(M) >= 12
  - N >= 1 (BUY_THRESHOLD=25 기준 최소 1개)
실패: 추천 종목 새로고침 완료: 0개 (성공: 15, 실패: 0)
  → BUY_THRESHOLD 또는 신호 계산 로직 확인
```

**TC-05: WorkManager 초기화 (설정 화면)**
```
기대: I WorkScheduler: DailyRecommendationWorker 즉시 실행 트리거
실패: IllegalStateException: WorkManager is not initialized properly
  → StockTradingApplication.Configuration.Provider 구현 확인
```

**TC-06: 에러 메시지 검증**
```
403 오류 시:
  기대 UI: "KIS API 키가 유효하지 않거나 승인되지 않았습니다. KIS 개발자센터(apiportal.koreainvestment.com)에서..."
  
API 키 미설정 시:
  기대 UI: "API 키를 먼저 설정해주세요. 설정 화면에서 KIS 앱키/시크릿을 입력하세요."
```

### 테스트 결과 판정 기준

| 등급 | 조건 |
|------|------|
| **PASS** | TC-01~04 모두 통과, 크래시 없음 |
| **CONDITIONAL** | TC-05~06 일부 실패, 크래시 없음 |
| **FAIL** | 앱 크래시 발생 또는 TC-01~02 실패 |

### 테스트 결과 출력 형식
```
## 테스트 결과

### 환경
- 기기: R3CY10CSCQX (Galaxy ...)
- APK: app-debug.apk (commit: xxxxxxx)
- 테스트 일시: YYYY-MM-DD HH:mm

### 시나리오별 결과
| TC | 시나리오 | 결과 | 비고 |
|----|---------|------|------|
| TC-01 | 앱 정상 실행 | ✅ PASS | |
| TC-02 | 토큰 발급 | ✅ PASS | 만료: 2026-05-02 |
| TC-03 | 주가 데이터 수집 | ✅ PASS | 14/15 성공 |
| TC-04 | 추천 종목 분석 | ✅ PASS | 3개 추천 |
| TC-05 | WorkManager | ✅ PASS | |
| TC-06 | 에러 메시지 | ✅ PASS | |

### 크래시 로그
없음

### 판정: PASS
```

---

## Agent 간 협업 규칙

### 작업 순서
1. **Developer** → 기능 구현 후 `feature/*` 브랜치에 커밋
2. **Code Reviewer** → PR 리뷰, `REQUEST_CHANGES` 시 Developer에게 반환
3. **Tester** → 기기 테스트, `FAIL` 시 Developer에게 반환
4. 모두 통과 시 → `main` 브랜치에 머지

### 브랜치 전략
```
main               ← 항상 빌드 가능 + 테스트 통과 상태
  └── feature/mvp-initial   ← 현재 개발 브랜치
  └── feature/<기능명>       ← 신규 기능 브랜치
  └── fix/<버그명>           ← 버그 수정 브랜치
```

### GitHub 운영
```bash
# 브랜치 생성
git checkout -b feature/<기능명>

# PR 생성
gh pr create --base main --head feature/<기능명> \
  --title "feat: <기능 요약>" \
  --body "<상세 설명>"

# PR 머지 (Tester PASS 후)
gh pr merge --squash
```

---

## 프로젝트 구조 참조

```
app/src/main/kotlin/com/stocktrading/
├── StockTradingApplication.kt      # Hilt + WorkManager 초기화
├── MainActivity.kt                 # 진입점
├── analysis/
│   ├── TechnicalIndicators.kt      # RSI, MACD, 볼린저밴드 계산
│   ├── TradingSignalGenerator.kt   # 매매 신호 종합 (BUY_THRESHOLD=25)
│   └── WeeklyPatternAnalyzer.kt    # 요일별 패턴 분석
├── data/
│   ├── api/
│   │   ├── KISApiClient.kt         # Retrofit 클라이언트 팩토리
│   │   ├── KISApiService.kt        # API 인터페이스 (날짜 파라미터 필수)
│   │   ├── KISAuthInterceptor.kt   # 토큰 자동 주입 (runBlocking + try-catch)
│   │   └── KISTokenManager.kt      # 토큰 발급/캐싱/갱신
│   ├── database/                   # Room DAO, Database
│   ├── model/
│   │   └── PriceData.kt            # @SerializedName("output") 주의
│   └── repository/
│       └── StockRepository.kt      # 2회 API 요청으로 ~60 거래일 확보
├── di/                             # Hilt 모듈
├── notification/                   # 푸시 알림
├── presentation/
│   ├── ui/                         # Compose 화면
│   └── viewmodel/
│       └── DashboardViewModel.kt   # KISApiClient + KISTokenManager 주입
├── security/
│   └── SecureCredentialManager.kt  # API 키 암호화 저장
└── work/
    ├── DailyRecommendationWorker.kt # @HiltWorker, 매일 오전 8시
    └── WorkScheduler.kt
```

---

## 알려진 제약사항 & 주의사항

| 항목 | 내용 |
|------|------|
| KIS API 1회 최대 데이터 | 30 거래일치 → 2회 요청 필수 |
| MACD 최소 데이터 | 35개 (slowPeriod 26 + signalPeriod 9) |
| OkHttp 스레드 예외 | `CoroutineExceptionHandler` 미적용 → try-catch 필수 |
| WorkManager + Hilt | `Configuration.Provider` 미구현 시 즉시 크래시 |
| 모의투자 URL | `https://openapivts.koreainvestment.com:29443/` |
| 실전투자 URL | `https://openapi.koreainvestment.com:9443/` |
