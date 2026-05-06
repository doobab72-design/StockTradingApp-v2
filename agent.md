# Agent Workflow — Universal Coding Template

> 이 파일은 **모든 프로젝트에서 재사용 가능한 범용 Agent 워크플로우**입니다.
> 새 프로젝트 시작 시 이 파일을 복사하고 `## 프로젝트 설정` 섹션만 채우면 됩니다.

---

## ⚠️ 절대 규칙: 코딩 시작 전 반드시 계획 수립

```
❌ 절대 금지: 요청을 받자마자 바로 코드 작성
✅ 반드시 준수: Plan → Confirm → Code → Review → Test 순서
```

**이유**: 계획 없이 바로 코딩하면 아래 문제가 반복됩니다.
- 잘못된 파일 수정 → 전체 롤백 필요
- 의존성 누락으로 빌드 실패
- 사이드 이펙트 미파악으로 다른 기능 파괴
- 작업 범위 과대/과소 산정

---

## 전체 파이프라인

```
      사용자 요청
          │
          ▼
  ┌─────────────┐
  │  Phase 0    │  계획 수립 & 사용자 승인  ← 코딩 시작 전 필수
  │  Planning   │
  └──────┬──────┘
         │ 승인
         ▼
  ┌─────────────┐
  │  Agent 1    │  구현 + 빌드 확인 + 커밋
  │  Developer  │
  └──────┬──────┘
         │ 완료
         ▼
  ┌─────────────┐
  │  Agent 2    │  코드 품질 / 보안 / 아키텍처 리뷰
  │  Reviewer   │
  └──────┬──────┘
         │ APPROVED
         ▼
  ┌─────────────┐
  │  Agent 3    │  실행 환경 테스트 + 결과 검증
  │  Tester     │
  └──────┬──────┘
         │ PASS
         ▼
   GitHub PR Merge
```

---

## Phase 0 — Planning (필수, 건너뛰기 불가)

### 목적
코드를 한 줄도 작성하기 전에 무엇을, 어떻게, 어떤 순서로 할지 명확히 합니다.

### 계획서 작성 항목

```markdown
## 작업 계획서

### 1. 목표
- 한 문장으로 이 작업의 목적을 서술

### 2. 작업 범위
- 추가/수정/삭제할 파일 목록
- 변경하지 않을 파일 (영향 없음 확인)

### 3. 구현 순서
1. [파일명] - 변경 내용 요약
2. [파일명] - 변경 내용 요약
   ...

### 4. 위험 요소 (Risk)
- 예상되는 사이드 이펙트
- 의존성 변경으로 영향받는 부분
- 롤백 필요 시 방법

### 5. 완료 기준
- [ ] 체크리스트 항목 1
- [ ] 체크리스트 항목 2

### 6. 예상 소요 시간
- 구현: X분
- 테스트: X분
```

### 계획 승인 프로세스
1. Agent가 위 계획서를 작성하여 사용자에게 제시
2. 사용자가 **명시적으로 승인** ("진행해", "OK", "해줘" 등)
3. 승인 후에만 코딩 시작
4. 작업 중 범위를 벗어나는 변경 발견 시 → **즉시 중단 후 재계획**

---

## Agent 1 — Developer

### 역할
계획이 승인된 작업만 구현합니다. 계획 범위를 벗어나지 않습니다.

### 보편적 구현 원칙

#### 코드 작성
- 파일 수정 전 반드시 현재 파일 내용 **Read** 먼저
- 한 번에 하나의 논리적 단위만 변경
- 변경 후 즉시 빌드/컴파일 확인
- 함수 단위: 단일 책임 원칙 (50줄 이하 권장)
- 매직 넘버 금지 → 이름 있는 상수 사용

#### 오류 처리
- 모든 외부 I/O (네트워크, 파일, DB)에 예외 처리 필수
- 에러 메시지는 **사용자가 행동할 수 있는** 내용 포함
  - ❌ "오류가 발생했습니다"
  - ✅ "서버 연결 실패. 네트워크 상태를 확인하거나 잠시 후 다시 시도해주세요"

#### 커밋 규칙
```
feat:     새 기능 추가
fix:      버그 수정
refactor: 기능 변경 없는 코드 개선
chore:    빌드/설정/의존성 변경
docs:     문서만 수정
test:     테스트 코드 추가/수정
```
- 메시지에 **무엇을(what) + 왜(why)** 반드시 포함
- 하나의 커밋 = 하나의 논리적 변경

#### 브랜치 전략
```
main              ← 항상 빌드 가능 + 테스트 통과 상태
  └── feature/<기능명>    ← 신규 기능
  └── fix/<버그명>        ← 버그 수정
  └── refactor/<대상>     ← 리팩터링
```

### 작업 완료 조건
- [ ] 빌드/컴파일 오류 0개
- [ ] 계획서의 완료 기준 체크리스트 모두 충족
- [ ] 관련 파일 모두 커밋 (누락 없음)
- [ ] Code Reviewer Agent에게 변경 내역 전달

---

## Agent 2 — Code Reviewer

### 역할
코드가 안전하고, 일관성 있고, 유지보수 가능한지 검토합니다.

### 범용 리뷰 체크리스트

#### 🔐 보안
- [ ] 비밀키/토큰/비밀번호가 코드에 하드코딩되어 있지 않은가?
- [ ] 민감 정보가 로그에 출력되지 않는가?
- [ ] 외부 입력값에 검증(validation)이 있는가?
- [ ] 네트워크 통신에 암호화(HTTPS/TLS)를 사용하는가?

#### 🏗️ 아키텍처
- [ ] 레이어 간 의존성 방향이 올바른가? (UI → ViewModel → Repository → Data)
- [ ] 순환 의존성이 없는가?
- [ ] 새 파일이 기존 아키텍처 패턴과 일관성 있는가?
- [ ] 전역 상태 변경이 최소화되어 있는가?

#### ⚡ 안전성
- [ ] Null 참조 오류(NPE) 발생 가능성이 없는가?
- [ ] 비동기 작업의 예외가 모두 처리되는가?
- [ ] 리소스(파일, 연결, 스트림)가 사용 후 반드시 해제되는가?
- [ ] 무한 루프/재귀 가능성이 없는가?

#### 🎨 코드 품질
- [ ] 함수/변수 이름이 의도를 명확히 드러내는가?
- [ ] 중복 코드가 없는가? (DRY 원칙)
- [ ] 주석이 "무엇"이 아닌 "왜"를 설명하는가?
- [ ] 하나의 함수가 하나의 책임만 갖는가?

#### 🧪 테스트 가능성
- [ ] 순수 함수(pure function)로 작성되어 단위 테스트가 가능한가?
- [ ] 외부 의존성이 주입(DI) 방식으로 교체 가능한가?

### 리뷰 결과 출력 형식

```markdown
## 코드 리뷰 결과

### ✅ 통과 항목
- ...

### ⚠️ 권장 수정 (blocking 아님)
- [파일:라인] 이유 및 개선 방향

### 🚫 필수 수정 (merge 불가)
- [파일:라인] 이유 및 수정 방법

### 판정: APPROVED / REQUEST_CHANGES
```

---

## Agent 3 — Tester

### 역할
실제 실행 환경에서 동작을 검증하고 결과를 기록합니다.

### 테스트 설계 원칙

#### 테스트 케이스 필수 구성
모든 기능에 대해 아래 3가지 경로를 반드시 검증합니다.

```
Happy Path    → 정상 입력, 정상 동작
Edge Case     → 경계값, 빈 값, 최대/최소값
Error Path    → 잘못된 입력, 네트워크 실패, 권한 없음
```

#### 테스트 케이스 작성 형식

```markdown
**TC-[번호]: [기능명]**
- 전제조건: ...
- 입력: ...
- 기대 결과: ...
- 실패 시 의심 포인트: ...
```

#### 테스트 우선순위
```
P0 (Critical)  → 앱 크래시, 데이터 손실, 보안 취약점
P1 (High)      → 핵심 기능 동작 여부
P2 (Medium)    → UI/UX, 성능
P3 (Low)       → 오탈자, 디자인 미세 조정
```

### 테스트 결과 판정 기준

| 판정 | 조건 |
|------|------|
| **PASS** | P0 + P1 모두 통과 |
| **CONDITIONAL** | P0 통과, P1 일부 실패 (사용자와 협의) |
| **FAIL** | P0 하나라도 실패 (즉시 Developer에게 반환) |

### 테스트 결과 출력 형식

```markdown
## 테스트 결과

### 환경
- OS / 기기: ...
- 버전 / 빌드: ...
- 테스트 일시: YYYY-MM-DD HH:mm

### 시나리오별 결과
| TC | 우선순위 | 시나리오 | 결과 | 비고 |
|----|---------|---------|------|------|
| TC-01 | P0 | ... | ✅ PASS | |
| TC-02 | P1 | ... | ❌ FAIL | 오류 내용 |

### 실패 상세
- TC-XX: [재현 방법] → [실제 결과] → [기대 결과]

### 판정: PASS / CONDITIONAL / FAIL
```

---

## Agent 간 협업 규칙

### 반환(Return) 조건
각 Agent는 아래 조건에서 이전 단계로 작업을 반환합니다.

```
Reviewer → Developer:  필수 수정사항(🚫) 발견 시
Tester   → Developer:  P0 실패 발견 시
Tester   → Reviewer:   리뷰에서 놓친 보안/아키텍처 문제 발견 시
```

### 재작업 시 규칙
- 반환된 작업은 **새 브랜치** 없이 같은 브랜치에서 수정
- 수정 후 Phase 0 계획서의 완료 기준 재확인
- Reviewer/Tester는 **수정된 부분만** 재검토 (전체 재검토 불필요)

### GitHub PR 운영
```bash
# 브랜치 생성
git checkout -b feature/<기능명>

# PR 생성
gh pr create --base main --head feature/<기능명> \
  --title "<type>: <기능 요약 (70자 이내)>" \
  --body "$(cat <<'EOF'
## 변경 내용
- ...

## 테스트 방법
- ...

## 관련 이슈
- ...
EOF
)"

# PR 머지 (Tester PASS 후)
gh pr merge --squash
```

---

## 새 프로젝트 시작 가이드

새 프로젝트에서 이 파일을 사용하려면:

1. `agent.md`를 프로젝트 루트에 복사
2. 아래 `## 프로젝트 설정` 섹션을 채움
3. Reviewer 체크리스트에 프로젝트 특이사항 추가
4. Tester 시나리오에 프로젝트별 TC 추가

---

## 프로젝트 설정 (각 프로젝트에서 채울 항목)

```
프로젝트명:     [프로젝트명]
언어/프레임워크: [예: Kotlin/Android, TypeScript/Next.js, Python/FastAPI]
빌드 명령어:    [예: ./gradlew assembleDebug]
실행 명령어:    [예: npm run dev]
테스트 명령어:  [예: ./gradlew test]
주요 외부 의존성: [예: KIS OpenAPI, Firebase, Stripe]
```

---

---
# ─────────────────────────────────────────────
# 아래는 StockTradingApp 프로젝트 특이사항
# 새 프로젝트 사용 시 이 섹션은 교체하세요
# ─────────────────────────────────────────────

## [StockTradingApp] 프로젝트 설정

```
프로젝트명:     StockTradingApp
언어/프레임워크: Kotlin / Android (Jetpack Compose + Hilt)
빌드 명령어:    JAVA_HOME=/Users/doobab/dev-tools/jdk-17.0.13+11/Contents/Home ./gradlew :app:assembleDebug
설치 명령어:    adb -s R3CY10CSCQX install -r app/build/outputs/apk/debug/app-debug.apk
실행 명령어:    adb -s R3CY10CSCQX shell am start -n com.stocktrading.debug/com.stocktrading.MainActivity
테스트 명령어:  adb -s R3CY10CSCQX logcat -d 2>&1 | grep -E "(KISToken|StockRepo|DashboardViewModel|AndroidRuntime|FATAL)"
주요 외부 의존성: KIS 한국투자증권 OpenAPI (https://apiportal.koreainvestment.com)
GitHub:        https://github.com/doobab72-design/StockTradingApp-v2
```

## [StockTradingApp] 기술 스택

| 분류 | 기술 |
|------|------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `@Singleton`) |
| 비동기 | Coroutines + Flow + StateFlow + `viewModelScope` |
| 네트워크 | Retrofit2 + OkHttp3 + Gson |
| 로컬 DB | Room (`@Entity`, `@Dao`, `@Database`) |
| 백그라운드 | WorkManager + `@HiltWorker` |
| 보안 | EncryptedSharedPreferences |

## [StockTradingApp] Developer 추가 규칙

### 필수 패턴 — OkHttp Interceptor 내 코루틴
```kotlin
// ✅ 올바른 패턴 (runBlocking은 OkHttp 스레드에서 실행, 예외 시 앱 크래시)
val token = try {
    runBlocking { tokenManager.getValidToken(appKey, appSecret) }
} catch (e: Exception) {
    Log.e(TAG, "토큰 획득 실패: ${e.message}")
    null
}
```

### 필수 패턴 — WorkManager + Hilt
```kotlin
// ✅ Application 클래스에서 반드시 구현
@HiltAndroidApp
class App : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration get() =
        Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

### KIS API 특이사항
- `getDailyPrice` → `FID_INPUT_DATE_1` + `FID_INPUT_DATE_2` 날짜 파라미터 **필수**
- `FHKST01010400` 응답 JSON 키: `"output"` (output2 아님) → `@SerializedName("output")`
- 1회 최대 30 거래일치 → 2회 요청으로 ~60 거래일 확보
- MACD 계산 최소 데이터: 35개 (slowPeriod 26 + signalPeriod 9)
- 403 → API 키 오류 / 네트워크 오류 구분하여 안내

## [StockTradingApp] Reviewer 추가 체크리스트

- [ ] `KISApiServiceProvider.setService()`가 `getValidToken()` 전에 호출되는가?
- [ ] `DailyPriceResponse.output2`에 `@SerializedName("output")` 있는가?
- [ ] WorkManager Worker가 `@HiltWorker` + `@AssistedInject` 패턴인가?
- [ ] `CoroutineExceptionHandler`가 모든 `viewModelScope.launch`에 적용되었는가?
- [ ] 에러 메시지가 HTTP 상태코드별로 다른 안내문을 제공하는가?

## [StockTradingApp] Tester 시나리오

### 환경 변수
```bash
export PATH="$PATH:/Users/doobab/dev-tools/android-sdk/platform-tools"
export JAVA_HOME="/Users/doobab/dev-tools/jdk-17.0.13+11/Contents/Home"
DEVICE="R3CY10CSCQX"
PKG="com.stocktrading.debug"
```

### TC 목록

| TC | 우선순위 | 시나리오 | 기대 로그 | 실패 시 의심 |
|----|---------|---------|----------|------------|
| TC-01 | P0 | 앱 실행 | AndroidRuntime 없음 | Hilt 초기화 오류 |
| TC-02 | P0 | 토큰 발급 | `토큰 발급 성공` | API 키 오류 / 403 |
| TC-03 | P1 | 주가 수집 | `N개 주가 데이터 저장 완료` (N≥30) | 날짜 파라미터 누락 |
| TC-04 | P1 | 추천 분석 | `추천 종목 새로고침 완료: N개 (성공: M≥12)` | BUY_THRESHOLD 과도 |
| TC-05 | P1 | WorkManager | `DailyRecommendationWorker 즉시 실행 트리거` | Configuration.Provider 누락 |
| TC-06 | P2 | 에러 메시지 | UI에 구체적 안내 문구 | 에러 분기 미처리 |

## [StockTradingApp] 알려진 제약사항

| 항목 | 내용 |
|------|------|
| KIS API 일별 최대 | 30 거래일/회 → 2회 요청 패턴 사용 |
| BUY_THRESHOLD | 25 (60 거래일 기준 캘리브레이션) |
| 모의투자 URL | `https://openapivts.koreainvestment.com:29443/` |
| 실전투자 URL | `https://openapi.koreainvestment.com:9443/` |

## [StockTradingApp] 프로젝트 구조

```
app/src/main/kotlin/com/stocktrading/
├── StockTradingApplication.kt       # HiltWorkerFactory + WorkManager 초기화
├── analysis/
│   ├── TechnicalIndicators.kt       # RSI, MACD, 볼린저밴드
│   └── TradingSignalGenerator.kt    # 매매 신호 종합 (BUY_THRESHOLD=25)
├── data/
│   ├── api/
│   │   ├── KISApiClient.kt          # Retrofit 팩토리 (모의/실전 URL 전환)
│   │   ├── KISApiService.kt         # FID_INPUT_DATE_1/2 파라미터 필수
│   │   ├── KISAuthInterceptor.kt    # runBlocking try-catch 패턴
│   │   └── KISTokenManager.kt       # 토큰 발급/캐싱 (Mutex 동시성 제어)
│   ├── model/PriceData.kt           # @SerializedName("output")
│   └── repository/StockRepository.kt # 2회 API 요청 → ~60 거래일
├── presentation/viewmodel/
│   └── DashboardViewModel.kt        # KISApiClient + KISTokenManager 주입
├── security/SecureCredentialManager.kt
└── work/DailyRecommendationWorker.kt # @HiltWorker, 매일 오전 8시
```
