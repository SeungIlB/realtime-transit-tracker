# realtime-transit-tracker

사용자가 선택한 노선과 승·하차 지점을 기준으로 접근 중인 첫 번째·두 번째 차량을 보여주는 실시간 대중교통 출발 타이밍 서비스입니다.

## 현재 상태

현재는 백엔드·프런트엔드 프레임과 PostgreSQL·Redis 기반 인프라를 구성하고, 공식 API 응답을 검증하는 단계입니다.

## 개발 기준 문서

- [개발 가이드](./DEVELOPMENT_GUIDE.md): 제품 범위, 도메인 모델, API 설계, 구현 순서, 테스트 및 완료 조건
- [클래스 다이어그램](./CLASS_DIAGRAM.md): Spring 계층, 도메인 모델, 공급자 어댑터와 조회 흐름
- [테이블 다이어그램](./DATABASE_ERD.md): PostgreSQL ERD, 제약조건, 인덱스와 보존 정책
- [외부 API 연동 가이드](./API_INTEGRATION_GUIDE.md): GBIS와 전국 초정밀버스 API 역할, 필드 매핑 및 검증 절차

문서 안에서 아이디어와 구현 상세가 충돌할 경우 `개발 결정 사항`과 뒤쪽의 상세 설계를 우선합니다.

## 확정된 MVP

```text
버스: 버스 번호 → 승차 정류장 → 하차 정류장
지하철: 호선 → 승차역 → 하차역
```

시스템은 선택된 승·하차 지점으로 방향, 정차 가능 여부와 접근 중인 차량을 판별하여 첫 번째·두 번째 후보를 반환합니다. 현재 위치 기반 도보시간과 `지금 출발` 판정은 MVP 이후 범위입니다.

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

필요하면 `.env.example`을 `.env`로 복사하고 값을 변경합니다. 기본 PostgreSQL·Redis 값은 별도 설정 없이 동작합니다. 실제 API 키는 `.env`에만 저장합니다.

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

## 검증 명령

```powershell
cd backend
.\gradlew.bat test

cd ..\frontend
npm run lint
npm run test
npm run build
```
