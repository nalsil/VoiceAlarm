# AdMob 배너 광고 설정 가이드

이 문서는 VoiceAlarm 앱에 Google AdMob 배너 광고를 설정하는 방법을 설명합니다.

## 목차

1. [AdMob 계정 생성](#1-admob-계정-생성)
2. [앱 등록](#2-앱-등록)
3. [광고 단위 생성](#3-광고-단위-생성)
4. [ID 확인 및 적용](#4-id-확인-및-적용)
5. [테스트 및 배포](#5-테스트-및-배포)
6. [주의사항](#6-주의사항)

---

## 1. AdMob 계정 생성

### 1.1 AdMob 사이트 접속

1. 웹 브라우저에서 [https://admob.google.com](https://admob.google.com) 접속
2. Google 계정으로 로그인

### 1.2 AdMob 계정 설정 (신규 사용자)

1. **국가/지역 선택**: 대한민국 선택
2. **이용약관 동의**: AdMob 이용약관 및 개인정보처리방침 동의
3. **계정 생성 완료**

---

## 2. 앱 등록

### 2.1 앱 추가

1. AdMob 대시보드에서 왼쪽 메뉴의 **"앱"** 클릭
2. **"앱 추가"** 버튼 클릭

### 2.2 플랫폼 선택

- **Android** 선택

### 2.3 앱 정보 입력

| 항목 | 입력값 |
|------|--------|
| 앱 이름 | VoiceAlarm (또는 원하는 이름) |
| Google Play 게시 여부 | 예/아니오 선택 |
| 패키지 이름 | `com.nalsil.voicealarm` |

### 2.4 앱 추가 완료

- **"앱 추가"** 버튼 클릭하여 완료
- 앱 ID가 생성됨 (예: `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`)

---

## 3. 광고 단위 생성

### 3.1 광고 단위 추가

1. 등록한 앱 선택
2. 왼쪽 메뉴에서 **"광고 단위"** 클릭
3. **"광고 단위 추가"** 버튼 클릭

### 3.2 광고 형식 선택

- **"배너"** 선택 (앱 하단에 표시되는 직사각형 광고)

### 3.3 광고 단위 설정

| 항목 | 설정값 |
|------|--------|
| 광고 단위 이름 | `main_banner` (또는 원하는 이름) |
| 광고 유형 | 배너 |

### 3.4 광고 단위 생성 완료

- **"광고 단위 만들기"** 클릭
- 광고 단위 ID가 생성됨 (예: `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ`)

---

## 4. ID 확인 및 적용

### 4.1 획득한 ID 종류

AdMob에서 두 가지 ID를 제공합니다:

| ID 유형 | 형식 예시 | 용도 |
|--------|----------|------|
| **앱 ID** | `ca-app-pub-1234567890123456~1234567890` | AndroidManifest.xml에 설정 |
| **광고 단위 ID** | `ca-app-pub-1234567890123456/1234567890` | 코드에서 배너 광고 로드 시 사용 |

### 4.2 AndroidManifest.xml 수정

`app/src/main/AndroidManifest.xml` 파일에서 앱 ID를 실제 ID로 교체합니다:

```xml
<!-- 테스트 ID (개발 중) -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713" />

<!-- 실제 ID (배포 시) - 위의 테스트 ID를 아래 형식으로 교체 -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
```

### 4.3 MainActivity.kt 수정

`app/src/main/java/com/nalsil/voicealarm/MainActivity.kt` 파일에서 광고 단위 ID를 교체합니다:

```kotlin
// 테스트 ID (개발 중)
private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

// 실제 ID (배포 시) - 위의 테스트 ID를 아래 형식으로 교체
private const val BANNER_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ"
```

---

## 5. 테스트 및 배포

### 5.1 테스트 단계

개발 및 테스트 중에는 반드시 **테스트 ID**를 사용합니다:

| 항목 | 테스트 ID |
|------|----------|
| 앱 ID | `ca-app-pub-3940256099942544~3347511713` |
| 배너 광고 단위 ID | `ca-app-pub-3940256099942544/6300978111` |

테스트 ID 사용 시:
- 실제 광고 대신 "Test Ad" 라벨이 붙은 테스트 광고가 표시됨
- 계정 정지 위험 없음
- 수익 발생하지 않음

### 5.2 배포 단계

Google Play Store에 앱을 배포하기 전:

1. **AndroidManifest.xml**의 앱 ID를 실제 ID로 교체
2. **MainActivity.kt**의 광고 단위 ID를 실제 ID로 교체
3. AdMob 대시보드에서 앱 상태가 "준비됨"인지 확인

### 5.3 테스트 기기 등록 (선택사항)

실제 ID로 테스트할 때 테스트 기기를 등록하면 계정 정지 위험 없이 테스트할 수 있습니다:

```kotlin
// MainActivity.kt의 onCreate에서
MobileAds.initialize(this) {}

// 테스트 기기 설정 (Logcat에서 기기 ID 확인 후 추가)
val configuration = RequestConfiguration.Builder()
    .setTestDeviceIds(listOf("YOUR_TEST_DEVICE_ID"))
    .build()
MobileAds.setRequestConfiguration(configuration)
```

---

## 6. 주의사항

### 6.1 정책 준수

AdMob 정책을 반드시 준수해야 합니다:

- **클릭 유도 금지**: 사용자에게 광고 클릭을 유도하면 안 됨
- **자체 클릭 금지**: 본인의 광고를 클릭하면 안 됨
- **부적절한 콘텐츠 금지**: 성인 콘텐츠, 불법 콘텐츠 등 금지
- **광고 배치 규정**: 실수로 클릭하기 쉬운 위치에 배치 금지

### 6.2 수익 수령

AdMob에서 수익을 받으려면:

1. AdMob 대시보드에서 **결제** > **결제 정보** 설정
2. 결제 수단 등록 (은행 계좌 등)
3. 세금 정보 제출 (미국 세금 양식)
4. 최소 지급 기준액 달성 시 수익 지급 (보통 $100)

### 6.3 문제 해결

| 문제 | 해결 방법 |
|------|----------|
| 광고가 표시되지 않음 | 인터넷 연결 확인, 앱 ID/광고 단위 ID 확인 |
| "광고를 로드할 수 없음" 오류 | 새 광고 단위는 활성화까지 최대 1시간 소요 |
| 테스트 광고만 표시됨 | 실제 ID로 교체했는지 확인 |
| 계정 정지됨 | AdMob 정책 위반 여부 확인, 이의 제기 가능 |

---

## 참고 링크

- [Google AdMob 공식 문서](https://developers.google.com/admob/android/quick-start)
- [AdMob 정책 센터](https://support.google.com/admob/answer/6128543)
- [배너 광고 구현 가이드](https://developers.google.com/admob/android/banner)
- [테스트 광고 ID 목록](https://developers.google.com/admob/android/test-ads)

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2025-01-30 | 최초 작성 |
