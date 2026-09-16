# DB 커넥션 반환과 풀 고갈 실습

DB 연결을 빌린 뒤 반환하지 않으면 왜 요청이 실패하는지 확인하는 실습임

**정상 실행 → 반환 누락 재현 → 로그로 실패 위치 확인 → 정상 복구** 순서로 진행함

## 실습 구조

`GET /lab/connection` 요청을 받으면 Controller → Service 순서로 실행됨

Service는 풀에서 연결을 빌리고 `SELECT 1`을 실행한 뒤 결과와 걸린 시간을 반환함
테이블 생성이나 데이터 변경은 하지 않음

| 브랜치 | 연결 처리 | 예상 결과 |
|---|---|---|
| `practice/connection-leak` | try-with-resources로 자동 반환함 | 순차 요청을 반복해도 성공함 |
| `practice/connection-leak-demo` | 의도적으로 반환하지 않음 | 새로 실행한 앱에서 세 번째 요청이 실패함 |

브랜치를 바꾸기 전에 실행 중인 앱을 종료해야 함
브랜치만 바꿔서는 이미 실행 중인 앱의 코드가 바뀌지 않음

## 1. 실행 준비

- Java 21과 로컬 MySQL이 필요함
- 프로젝트 루트에서 명령을 실행함
- 터미널 두 개를 사용함: A는 앱 실행용, B는 요청용임

현재 로컬 프로젝트 위치는 다음과 같음

```bash
cd /Users/sonjuwan/IdeaProjects/junior-backend-book-practice
```

현재 로컬 환경에는 `.env.local`이 준비되어 있음
이 파일은 Git에서 제외되어 있으므로 새로 복제한 환경에서는 본인의 DB 접속 정보로 별도 준비해야 함

| 환경 변수 | 용도 |
|---|---|
| `PRACTICE_DB_URL` | `jdbc:mysql://호스트:포트/스키마` 형태의 DB 접속 주소임 |
| `PRACTICE_DB_USERNAME` | DB 접속 계정임 |
| `PRACTICE_DB_PASSWORD` | DB 접속 비밀번호임 |
| `JAVA_HOME` | 로컬 Java 21 설치 경로임 |

`.env.local`에서는 `export 변수명='값'` 형식으로 설정함
실제 계정과 비밀번호는 README나 Git에 기록하지 않음

## 2. 정상 실행

터미널 A에서 실행함

```bash
git switch practice/connection-leak
source .env.local
./gradlew bootRun --args='--spring.profiles.active=connection-leak'
```

콘솔에 `Started BackendBookPracticeApplication`이 나오면 터미널 B에서 아래 명령을 한 번씩, 총 세 번 실행함

```bash
curl -i http://localhost:8080/lab/connection
```

세 번 모두 HTTP 200과 `value: 1`이 포함된 응답이 나오는지 확인함
응답 시간은 실행마다 달라짐

```json
{"value":1,"connectionWaitMs":0.02,"totalMs":0.8}
```

- `connectionWaitMs`는 연결을 얻는 데 걸린 시간이며 첫 요청에는 풀 초기화나 연결 생성 시간이 포함될 수 있음
- `totalMs`는 서비스 시작부터 연결 반환 직후까지 걸린 시간이며 HTTP 전체 응답 시간은 아님

서버 로그는 `커넥션 획득 시도 → 커넥션 획득 완료 → SELECT 1 실행 완료 → 커넥션 반환 완료` 순서로 나옴
풀에 연결이 2개뿐이어도 요청마다 돌려주므로 계속 사용할 수 있음

## 3. 반환 누락 실험

터미널 A에서 `Ctrl+C`로 앱을 종료한 뒤 누수 브랜치로 전환하고 다시 실행함

```bash
git switch practice/connection-leak-demo
source .env.local
./gradlew bootRun --args='--spring.profiles.active=connection-leak'
```

시작 완료 후 터미널 B에서 같은 요청을 한 번씩, 총 세 번 실행함

```bash
curl -i http://localhost:8080/lab/connection
```

| 요청 | 예상 결과 | 이유 |
|---|---|---|
| 첫 번째 | HTTP 200 | 연결 하나를 빌린 뒤 반환하지 않음 |
| 두 번째 | HTTP 200 | 남은 연결도 빌린 뒤 반환하지 않음 |
| 세 번째 | 약 1초 뒤 HTTP 500 | 빌릴 연결이 없어 기다리다가 실패함 |

세 번째 요청의 서버 로그에서 `실패 단계=커넥션 획득`을 확인함
해당 요청에는 `커넥션 획득 완료`와 `SELECT 1 실행 완료`가 없으므로 SQL 실행 전에 막힌 것임
실패 응답에는 성공 시의 시간 측정 DTO가 나오지 않음

누수 브랜치의 `totalMs`는 연결 반환 없이 서비스 처리에 걸린 시간임
로그도 실제 동작에 맞게 `실습용 커넥션 반환 누락`으로 표시됨

실험을 다시 하려면 앱을 종료하고 재실행함
재시작하면 풀은 새로 만들어지지만 반환 누락 코드가 남아 있으므로 같은 문제가 반복됨

## 4. 정상 복구

터미널 A에서 `Ctrl+C`로 종료한 뒤 실행함

```bash
git switch practice/connection-leak
source .env.local
./gradlew bootRun --args='--spring.profiles.active=connection-leak'
```

터미널 B에서 요청을 세 번 보내 모두 성공하는지 확인함
실습을 마치면 터미널 A에서 `Ctrl+C`로 앱을 종료함

## 풀 설정 위치

`src/main/resources/application-connection-leak.yml`에 설정되어 있음
두 브랜치 모두 `connection-leak` 프로필로 실행해야 이 설정이 적용됨

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 2
      maximum-pool-size: 2
      connection-timeout: 1000
```

- `minimum-idle`: 쉬는 연결 2개를 유지하려는 설정임
- `maximum-pool-size`: 사용 중인 연결과 쉬는 연결을 합쳐 최대 2개로 제한함
- `connection-timeout`: 연결 획득을 최대 1000ms 기다린 뒤 예외가 발생함

둘 다 사용 중이라고 쉬는 연결 2개를 추가로 만드는 것은 아님

## 테스트

정상 브랜치에서 실행함
테스트도 실제 로컬 MySQL과 DB 환경 변수가 필요함

```bash
git switch practice/connection-leak
source .env.local
./gradlew test
```

연결 반환과 풀 대기 시간 측정을 확인하는 테스트 2개임
누수 브랜치는 의도적으로 연결을 반환하지 않으므로 정상 반환을 전제로 한 테스트가 실패할 수 있음
