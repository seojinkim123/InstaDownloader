# Instagram Downloader App - Product Requirements Document (PRD)

## 1. 개요

### 1.1 제품 비전
인스타그램 게시물(이미지, 비디오)을 쉽고 빠르게 다운로드할 수 있는 안드로이드 애플리케이션

### 1.2 목표
- 공개/비공개 인스타그램 게시물 다운로드 지원
- 직관적이고 사용하기 쉬운 UI 제공
- 다운로드한 미디어의 체계적 관리

## 2. 핵심 기능

### 2.1 Tab 1: URL 다운로더
**기능 설명**: 인스타그램 게시물 URL을 통한 다운로드

**주요 기능**:
- URL 입력 필드 (붙여넣기 지원)
- 서버 API 연동을 통한 미디어 URL 추출
- 미디어 프리뷰 (이미지/비디오)
- 개별 또는 일괄 다운로드
- 다운로드 진행률 표시

**API 연동**:
```
GET /api/extract?url={instagram_post_url}
Response: {
  "url": [
    "https://scontent-ssn1-1.cdninstagram.com/...",
    "https://scontent-ssn1-1.cdninstagram.com/..."
  ]
}
```

### 2.2 Tab 2: 웹뷰 브라우저
**기능 설명**: 웹뷰를 통한 직접 인스타그램 접근 및 다운로드

**주요 기능**:
- 인스타그램 웹뷰 구현
- 사용자 로그인 지원
- 비공개 계정 접근 가능
- HTML 파싱을 통한 이미지/비디오 URL 추출
- 게시물 선택 및 다운로드 기능
- 웹뷰 네비게이션 (뒤로가기, 새로고침)

**기술 요구사항**:
- WebView 구현
- JavaScript 인젝션을 통한 HTML 분석
- 쿠키/세션 관리

### 2.3 Tab 3: 갤러리 (폰 갤러리 연동)
**기능 설명**: 폰 갤러리에 저장된 InstaDownloader 미디어 조회 및 관리

**주요 기능**:
- MediaStore API를 통한 폰 갤러리 연동
- Downloads/InstaDownloader 폴더의 미디어 파일 표시
- 그리드 뷰로 썸네일 표시
- 미디어 상세 보기 (기본 갤러리 앱 연동)
- 파일 삭제 기능
- 외부 앱으로 공유 기능 (시스템 공유 메뉴 활용)
- 날짜별/타입별 필터링
- Room DB에 메타데이터 저장 (원본 URL, 다운로드 날짜)

## 3. 기술 명세

### 3.1 아키텍처
- **플랫폼**: Android (Java/Kotlin)
- **네트워킹**: Retrofit2 + OkHttp3
- **이미지 로딩**: Glide
- **로컬 저장소**: Room Database
- **파일 관리**: Android Storage Access Framework

### 3.2 데이터 모델
```kotlin
// Room Entity - 메타데이터만 저장
@Entity(tableName = "downloaded_media")
data class DownloadedMedia(
    @PrimaryKey val id: String,
    val fileName: String,
    val filePath: String, // MediaStore Uri 또는 파일 경로
    val originalUrl: String,
    val downloadDate: Long,
    val mediaType: String, // "image" or "video"
    val fileSize: Long
)
``` 

### 3.3 권한 요구사항
- `INTERNET`: 네트워크 접근
- `READ_EXTERNAL_STORAGE`: 갤러리 파일 읽기 (API < 33)
- `READ_MEDIA_IMAGES`: 이미지 읽기 (API >= 33)
- `READ_MEDIA_VIDEO`: 비디오 읽기 (API >= 33)
- `WRITE_EXTERNAL_STORAGE`: 파일 저장 (API < 29)
- MediaStore API 사용으로 MANAGE_EXTERNAL_STORAGE 불필요

## 4. UI/UX 설계

### 4.1 네비게이션 구조
```
Bottom Navigation (3 tabs)
├── URL Downloader
│   ├── URL Input Field
│   ├── Extract Button

│   └── Media Preview & Download
├── Web Browser
│   ├── WebView
│   ├── Navigation Controls
│   └── Download Overlay
└── Gallery
    ├── Media Grid
    ├── Filter Options
    └── Detail View
```


### 4.2 주요 화면 플로우

**Tab 1 플로우**:
1. URL 입력 → 2. API 호출 → 3. 미디어 프리뷰 → 4. 다운로드 선택 → 5. 다운로드 완료
2. /Users/kimseojin/Desktop/InstaDownloader/app/design_references/KakaoTalk_Photo_2025-08-17-19-18-08 003.jpeg
3. /Users/kimseojin/Desktop/InstaDownloader/app/design_references/KakaoTalk_Photo_2025-08-17-19-18-08 002.jpeg
를 참고해서 만들것 
  
**Tab 2 플로우**:
1. 웹뷰 로드 → 2. 로그인 → 3. 게시물 탐색 → 4. 다운로드 버튼 표시 → 5. 다운로드
 2. /Users/kimseojin/Desktop/InstaDownloader/app/design_references/KakaoTalk_Photo_2025-08-17-19-18-08 001.jpeg
      /Users/kimseojin/Desktop/InstaDownloader/app/design_references/KakaoTalk_Photo_2025-08-17-19-25-56.jpeg
      를 참고해서 만들것


**Tab 3 플로우**:
1. 갤러리 보기 → 2. 미디어 선택 → 3. 상세 보기/공유/삭제
   /Users/kimseojin/Desktop/InstaDownloader/app/design_references/KakaoTalk_Photo_2025-08-17-19-18-08 004.jpeg
## 5. 성능 요구사항

### 5.1 응답 시간
- URL 파싱: 3초 이내
- 이미지 다운로드: 10MB당 30초 이내
- 갤러리 로딩: 1초 이내 (최대 100개 아이템)

### 5.2 저장 공간
- 앱 크기: 50MB 이하
- 다운로드 파일: 사용자 설정 가능한 최대 용량

### 5.3 메모리 사용
- 백그라운드 메모리: 100MB 이하
- 이미지 캐시: 200MB 이하

## 6. 보안 고려사항

### 6.1 사용자 데이터 보호
- 로그인 정보는 로컬에 암호화 저장
- HTTPS 통신만 허용
- 개인정보 수집 최소화

### 6.2 저작권 준수
- 다운로드 시 저작권 고지사항 표시
- 개인 사용 목적임을 명시
- 상업적 이용 금지 안내

## 7. 개발 우선순위

### 7.1 Phase 1 (MVP)
- Tab 1: URL 다운로더 기본 기능
- 기본 UI 구현
- 이미지 다운로드 기능

### 7.2 Phase 2
- Tab 2: 웹뷰 브라우저 구현
- Tab 3: 갤러리 기본 기능
- 비디오 다운로드 지원

### 7.3 Phase 3
- 고급 갤러리 기능 (필터링, 검색)
- 다운로드 성능 최적화
- UI/UX 개선

## 8. 테스트 계획

### 8.1 기능 테스트
- 각 탭별 핵심 기능 검증
- 다양한 인스타그램 URL 형식 테스트
- 웹뷰 호환성 테스트

### 8.2 성능 테스트
- 대용량 파일 다운로드 테스트
- 동시 다운로드 테스트
- 메모리 누수 검증

### 8.3 디바이스 호환성
- 다양한 안드로이드 버전 테스트
- 다양한 화면 크기 대응 테스트

## 9. 릴리즈 계획

### 9.1 내부 테스트
- 알파 버전: 핵심 기능 구현 완료 후
- 베타 버전: 전체 기능 구현 완료 후

### 9.2 배포 전략
- Google Play Store 출시
- 사이드로딩용 APK 제공 고려

## 10. 위험 요소 및 대응 방안

### 10.1 기술적 위험
- **인스타그램 API 변경**: 여러 파싱 방법 준비
- **웹뷰 호환성**: 다양한 디바이스 테스트 강화

### 10.2 법적 위험
- **저작권 문제**: 면책조항 명시 및 개인 사용 제한
- **플랫폼 정책**: Google Play 정책 준수 검토

### 10.3 사용자 경험
- **다운로드 실패**: 재시도 로직 및 오류 메시지 개선
- **성능 저하**: 백그라운드 다운로드 및 캐싱 최적화