# realtime-transit-tracker

사용자가 직접 선택한 승차 구간을 기준으로 접근 중인 첫 번째·두 번째 차량과 탑승 가능성을 보여주는 실시간 대중교통 출발 타이밍 서비스입니다.

## 현재 상태

백엔드와 웹 MVP가 연결되어 있으며, 노선·정류장 데이터가 없을 때 공식 외부 API를 호출해 자동 동기화합니다. TAGO 전국 버스의 지역별 노선·정류장·도착·차량 위치와 서울 지하철의 노선·역·실시간 도착정보를 지원합니다. 국가철도공단 역별 시간표를 결합해 급행·특급 열차의 하차역 정차 여부를 판정하며, GBIS와 각 외부 API에 맞는 활용신청 승인이 필요합니다.

노선·역·정류장과 정차 패턴은 마스터 데이터로 유지합니다. 차량 위치, 도착 예측과 외부 API 원본 관측은 저장 시점부터 최대 2시간 보존하며, 백엔드 유지보수 스케줄러가 10분마다 만료 데이터를 순차 삭제합니다.

사용자의 위치 관측값은 탑승 판단용으로만 저장하고 10분 후 만료시킵니다. 종료되거나 만료된 여정과 연결되지 않은 익명 프로필은 2시간 뒤 삭제합니다. 브라우저의 익명 식별자는 탭 세션 동안만 유지되며, 여정 조회·위치 등록·종료 요청은 해당 식별자가 일치해야 합니다. 장소 검색어는 OpenStreetMap Nominatim으로 직접 전송되므로 상세한 개인 주소 입력은 피해야 합니다.

## 개발 기준 문서

- [개발 가이드](./DEVELOPMENT_GUIDE.md): 제품 범위, 도메인 모델, API 설계, 구현 순서, 테스트 및 완료 조건
- [클래스 다이어그램](./CLASS_DIAGRAM.md): Spring 계층, 도메인 모델, 공급자 어댑터와 조회 흐름
- [테이블 다이어그램](./DATABASE_ERD.md): PostgreSQL ERD, 제약조건, 인덱스와 보존 정책
- [외부 API 연동 가이드](./API_INTEGRATION_GUIDE.md): GBIS와 전국 버스 API 역할, 필드 매핑 및 검증 절차

문서 안에서 아이디어와 구현 상세가 충돌할 경우 `개발 결정 사항`과 뒤쪽의 상세 설계를 우선합니다.

## 사용 방식과 제품 범위

```text
버스: 버스 번호 → 승차 정류장 → 하차 정류장
지하철: 호선 → 승차역 → 하차역
```

목적지를 입력하면 Kakao Mobility 대중교통 경로를 확인할 수 있습니다. 사용자가 경로의 실시간 계산을 요청하면 최대 3개의 탑승·환승 구간을 내부 노선과 정류장에 정확히 매칭하고, 현재 위치와 각 구간의 실시간 차량 후보를 바탕으로 전체 성공 확률을 근사합니다. 자동 매칭이 불명확하거나 뒤쪽 구간의 차량이 아직 실시간 조회 범위에 없으면 결과를 추측하지 않고 직접 구간 계산 또는 재확인을 안내합니다.

처음 대중교통을 탈 때는 현재 위치와 승차 정류장을 선택해 사용할 수 있습니다. 이미 다른 교통수단으로 이동 중이라면 예정된 환승 지점을 출발 위치로 설정하고, 다음에 탈 노선과 승차 구간을 선택해 탑승 가능성을 확인할 수 있습니다.

시스템은 선택된 승·하차 지점으로 운행 방향과 정차 가능 여부를 판별하고, 접근 중인 첫 번째·두 번째 차량을 비교합니다. 현재 위치 또는 직접 검색한 출발 위치까지의 이동시간, 이동 속도별 성공 확률, 추천 행동과 데이터 신뢰도를 함께 제공합니다.

## 개발 시작 순서

1. 공식 API와 이용약관 확인
2. 검증 대상 버스 노선 1개와 지하철 노선 1개 선정
3. 인증키를 제외한 원본 응답 fixture 수집
4. 차량 정규화와 첫 번째·두 번째 후보 판별 구현
5. 통합 백엔드 API 구현
6. 웹 MVP와 현장 검증

## 확정 기술 스택

- 프런트엔드: React + TypeScript + Vite
- 백엔드: Java 21 + Spring Boot + Gradle
- 데이터베이스: PostgreSQL + Flyway
- 데이터 접근: MyBatis 4.1 + XML Mapper
- 캐시·호출량 제어: Redis 8 + Spring Cache

## 로컬 실행

### 1. 환경변수

필요하면 `.env.example`을 `.env`로 복사하고 값을 변경합니다. 기본 PostgreSQL·Redis 값은 별도 설정 없이 동작합니다. 백엔드 외부 연동에는 `GBIS_SERVICE_KEY`, `TAGO_SERVICE_KEY`, `SEOUL_SUBWAY_SERVICE_KEY`, `KRIC_SERVICE_KEY`를 사용하며 실제 API 키는 `.env`에만 저장합니다. 기존 배포의 `NATIONAL_PRECISION_BUS_SERVICE_KEY`도 TAGO 키의 대체 환경변수로 계속 인식합니다. `KRIC_SERVICE_KEY`가 없으면 일반열차는 기존 정차 패턴을 사용하고 급행·특급은 정차 여부를 `UNKNOWN`으로 보존하며, 목적지가 선택된 추천 후보에서는 제외합니다. 공휴일 시간표가 필요한 날짜는 `KRIC_HOLIDAY_DATES=2026-09-25,2026-10-03`처럼 쉼표로 구분해 지정합니다.

### 2. PostgreSQL과 Redis

```powershell
docker compose up -d --wait
```

### 3. 백엔드

```powershell
cd backend
.\gradlew.bat bootRun
```

백엔드는 `http://localhost:8080`, 상태 API는 `http://localhost:8080/api/v1/system/health`에서 확인할 수 있습니다. 기동 시 Flyway가 PostgreSQL 스키마를 적용하고 Redis를 캐시와 외부 API 일일 호출량 제어에 사용합니다.

### 4. 프런트엔드

```powershell
cd frontend
npm install
npm run dev
```

프런트엔드는 `http://localhost:5173`에서 실행되며 `/api` 요청을 로컬 Spring Boot 서버로 프록시합니다.

## 무료 배포

배포 구성은 Vercel(프런트엔드), Render(백엔드와 Redis), Neon(PostgreSQL)입니다. Render Redis는 캐시와 외부 API 호출량 제한 전용이며 무료 인스턴스 재시작 시 데이터가 사라져도 영구 데이터에는 영향이 없습니다.

### 1. Neon PostgreSQL

Neon에서 프로젝트를 만든 뒤 `Connect` 화면의 접속 정보를 준비합니다. Render에 입력할 값은 다음과 같습니다.

- `DB_URL`: `jdbc:postgresql://<host>/<database>?sslmode=require` 형식의 JDBC URL
- `DB_USERNAME`: Neon role 이름
- `DB_PASSWORD`: Neon role 비밀번호

Flyway가 백엔드 최초 기동 시 스키마를 자동 생성합니다.

### 2. Render 백엔드와 Redis

저장소를 GitHub에 올리고 Render에서 `New > Blueprint`를 선택해 루트의 `render.yaml`을 적용합니다. 최초 생성 화면에서 다음 비밀 환경변수를 입력합니다.

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `CORS_ALLOWED_ORIGINS`: 최초에는 예정된 Vercel 주소, 배포 후에는 실제 주소(예: `https://example.vercel.app`)
- `GBIS_SERVICE_KEY`
- `TAGO_SERVICE_KEY` 또는 기존 `NATIONAL_PRECISION_BUS_SERVICE_KEY`
- `SEOUL_SUBWAY_SERVICE_KEY`
- `SEOUL_SUBWAY_REFERENCE_SERVICE_KEY`
- `KRIC_SERVICE_KEY`

`SPRING_DATA_REDIS_URL`은 Blueprint가 Render Key Value의 내부 접속 주소로 자동 연결합니다. 백엔드 상태는 배포 주소의 `/actuator/health`에서 확인합니다.

### 3. Vercel 프런트엔드

Vercel에서 같은 GitHub 저장소를 가져오고 Root Directory를 `frontend`로 지정합니다. 환경변수 `VITE_API_BASE_URL`에는 Render 백엔드 주소를 경로 없이 입력합니다(예: `https://realtime-transit-backend.onrender.com`). `VITE_NAVER_MAP_NCP_KEY_ID`에는 NAVER Cloud Maps의 Web Dynamic Map용 `ncpKeyId`를 입력하고, Maps 애플리케이션에 실제 Vercel 도메인을 Web 서비스 URL로 등록합니다. 카카오 REST 키는 프론트가 아닌 Render 백엔드의 `KAKAO_REST_API_KEY`에 등록합니다. 배포 후 실제 Vercel 주소가 달라졌다면 Render의 `CORS_ALLOWED_ORIGINS`도 같은 주소로 수정하고 백엔드를 재배포합니다.

무료 Render 웹 서비스는 사용하지 않을 때 정지하므로 첫 요청은 시간이 걸릴 수 있습니다. CORS에는 쉼표로 구분한 정확한 프런트엔드 주소만 등록하고 API 키나 DB 비밀번호는 저장소 파일에 기록하지 않습니다.

## 검증 명령

```powershell
cd backend
.\gradlew.bat test

cd ..\frontend
npm run lint
npm run test
npm run build
```
