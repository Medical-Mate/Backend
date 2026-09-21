# 이벤트 대시보드 — Grafana + Postgres

웹 데모 이벤트를 Grafana 로 봅니다. **한시입니다** — 심사가 끝나면 `demo` 패키지, `analytics` 스키마와 함께 지웁니다.

Prometheus 도 Loki 도 두지 않습니다. 호스트가 2GB 인데 풀스택이 600~900MB 라 안 들어갑니다. Grafana 하나(~150MB)가 Postgres 를 SQL 로 직접 읽습니다.

---

## 1. 읽기 전용 롤 — 먼저 만듭니다

**Grafana 에 앱 계정을 꽂으면 안 됩니다.** 같은 DB 에 `card`·`visit`·`profile` 이 있어서, Grafana 가 뚫리면 그 연결로 환자 테이블까지 열립니다.

마이그레이션에 넣지 않은 이유는 비밀번호가 환경마다 다르고, 저장소에 박히면 안 되기 때문입니다.

```bash
ssh -i ~/.ssh/lightsail-jinryomate.pem ubuntu@54.116.115.128
cd /opt/jinryomate
docker compose -f docker-compose.prod.yml exec -T postgres psql -U jinryomate -d jinryomate
```

```sql
CREATE ROLE grafana_ro LOGIN PASSWORD '여기에 새 비밀번호';

-- analytics 만 봅니다. public 은 주지 않습니다.
GRANT USAGE ON SCHEMA analytics TO grafana_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO grafana_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA analytics GRANT SELECT ON TABLES TO grafana_ro;

-- public 에 기본으로 붙는 권한을 떼어 냅니다.
REVOKE ALL ON SCHEMA public FROM grafana_ro;
```

**확인은 "안 보이는지" 로 합니다.**

```sql
SET ROLE grafana_ro;
SELECT count(*) FROM analytics.demo_event;   -- 되어야 함
SELECT count(*) FROM briefing_card;           -- permission denied 여야 함
RESET ROLE;
```

두 번째가 성공하면 **거기서 멈추고 권한을 다시 보세요.**

---

## 2. compose 에 Grafana 추가

`docker-compose.prod.yml` 은 **저장소에 없고 서버에만 있습니다.** 아래를 `services:` 아래에 붙이고, 왜 그랬는지를 그 파일 주석에 남기세요.

```yaml
  grafana:
    image: grafana/grafana-oss:11.6.0
    restart: unless-stopped
    # 인터넷에 열지 않습니다. 루프백에만 묶고 SSH 터널로 봅니다 —
    # 지금 밖에서 보이는 것은 Caddy 뿐이고, 여기에 로그인 화면을 하나 더
    # 내놓을 이유가 없습니다.
    ports:
      - "127.0.0.1:3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?}
      GF_USERS_ALLOW_SIGN_UP: "false"
      GF_AUTH_ANONYMOUS_ENABLED: "false"
      GF_ANALYTICS_REPORTING_ENABLED: "false"
      GF_ANALYTICS_CHECK_FOR_UPDATES: "false"
      # 데이터소스 비밀번호는 아래 프로비저닝 파일이 참조합니다.
      ANALYTICS_DB_PASSWORD: ${ANALYTICS_DB_PASSWORD:?}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    mem_limit: 192m
    depends_on:
      - postgres
```

`volumes:` 절에 `grafana-data:` 도 추가해야 합니다.

`.env` 에 넣을 것:

```
GRAFANA_ADMIN_PASSWORD=...
ANALYTICS_DB_PASSWORD=...   # 1번에서 만든 grafana_ro 비밀번호
```

> `:?` 를 붙인 이유 — 값이 없으면 컨테이너가 **안 뜹니다.** 기본 비밀번호로 조용히 뜨는 것보다 낫습니다. `admin/admin` 으로 열려 있는 Grafana 는 그 자체가 사고입니다.

---

## 3. 데이터소스 프로비저닝

`/opt/jinryomate/grafana/provisioning/datasources/analytics.yml`

```yaml
apiVersion: 1
datasources:
  - name: analytics
    type: postgres
    url: postgres:5432
    database: jinryomate
    user: grafana_ro
    isDefault: true
    jsonData:
      sslmode: disable          # 같은 docker 네트워크 안입니다
      postgresVersion: 1600
      timescaledb: false
    secureJsonData:
      password: ${ANALYTICS_DB_PASSWORD}
```

화면에서 손으로 넣지 않는 이유는 **컨테이너를 다시 만들면 사라지기 때문**입니다. 파일로 두면 따라옵니다.

---

## 4. 띄우고 보기

```bash
docker compose -f docker-compose.prod.yml up -d grafana
```

```bash
ssh -i ~/.ssh/lightsail-jinryomate.pem -L 3000:127.0.0.1:3000 ubuntu@54.116.115.128
```

터널을 연 채로 브라우저에서 `http://localhost:3000`.

**메모리를 같이 보세요.** 호스트 여유가 949MB 이고 swap 을 이미 건드리는 중입니다.

```bash
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

`ai` 가 192MB 한도에 붙기 시작하면 Grafana 를 먼저 내립니다 — 대시보드보다 서비스가 우선입니다.

---

## 5. 첫 패널 몇 개

```sql
-- 시간대별 문답 시작 (KST)
SELECT date_trunc('hour', occurred_at AT TIME ZONE 'Asia/Seoul') AS time,
       count(*) AS "문답 시작"
FROM analytics.demo_event
WHERE event = 'intake.started' AND $__timeFilter(occurred_at)
GROUP BY 1 ORDER BY 1;
```

```sql
-- 어느 턴에서 그만두나 — 디자인이 제일 원하는 것
SELECT (props->>'turn_no')::int AS "턴", count(*) AS "이탈"
FROM analytics.demo_event
WHERE event = 'intake.abandoned' AND $__timeFilter(occurred_at)
GROUP BY 1 ORDER BY 1;
```

```sql
-- 어느 축을 사람이 자주 고치나 = 추출이 약한 축
SELECT props->>'axis_key' AS "축", count(*) AS "수정"
FROM analytics.demo_event
WHERE event = 'card.axis_edited' AND $__timeFilter(occurred_at)
GROUP BY 1 ORDER BY 2 DESC;
```

```sql
-- AI 응답 지연 p50 · p95
SELECT date_trunc('hour', occurred_at AT TIME ZONE 'Asia/Seoul') AS time,
       percentile_cont(0.5)  WITHIN GROUP (ORDER BY (props->>'latency_ms')::int) AS p50,
       percentile_cont(0.95) WITHIN GROUP (ORDER BY (props->>'latency_ms')::int) AS p95
FROM analytics.demo_event
WHERE event = 'intake.turn_received' AND $__timeFilter(occurred_at)
GROUP BY 1 ORDER BY 1;
```

```sql
-- 막힌 사람 — 빈도 제한이 실사용자를 치고 있나
SELECT event, count(*) FROM analytics.demo_event
WHERE event IN ('guard.rate_limited', 'budget.exhausted', 'upstream.failed')
  AND $__timeFilter(occurred_at)
GROUP BY 1;
```

---

## 6. 심사가 끝나면

```sql
DROP SCHEMA analytics CASCADE;
DROP ROLE grafana_ro;
```

compose 에서 `grafana` 블록과 `grafana-data` 볼륨을 지우고, `.env` 에서 두 비밀번호를 지웁니다. `demo` 패키지를 지울 때 같이 하세요 — **따로 두면 잊습니다.**
