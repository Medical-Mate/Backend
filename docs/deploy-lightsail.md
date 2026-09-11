# AWS Lightsail 배포

인스턴스 **한 대**에 백엔드 · Postgres · AI · Caddy 를 전부 올립니다.

```
              Lightsail 2GB (ap-northeast-2)
           ┌──────────────────────────────────┐
인터넷─443→│ Caddy ──→ 백엔드 :8080            │
           │             ├──→ AI :8000         │
           │             └──→ Postgres         │
           └──────────────┼───────────────────┘
                          ↓ 최소 권한 IAM 사용자
                    AWS Bedrock
```

**인터넷에 열리는 것은 Caddy 뿐입니다.** AI 와 Postgres 는 호스트 포트를 열지 않아 밖에서 보이지 않습니다.

## 왜 한 대인가

AWS 신규 계정 크레딧이 **$120 / 182일**입니다. **Bedrock 토큰 비용도 같은 크레딧에서 나갑니다.**

| | 6개월 서버 비용 | Bedrock 에 남는 크레딧 |
|---|---|---|
| **Lightsail $12** | $36~72 | **$48~84** |
| EC2 t4g.small $19.74 | $118 | $2 |

EC2 는 $120 ÷ $19.74 = 6.1개월로 딱 맞아떨어져 **AI 비용이 들어갈 자리가 없습니다.**

## 메모리 배분 (2,048MB)

| | 컨테이너 한도 | 실사용 |
|---|---|---|
| 백엔드 (`-Xmx320m`) | 640MB | ~500MB |
| AI (FastAPI + boto3) | 512MB | ~300MB |
| Postgres (`shared_buffers=128MB`) | 400MB | ~250MB |
| Caddy | 64MB | ~30MB |
| OS | — | ~120MB |
| **합계** | 1,616MB | **~1,200MB (59%)** |

**1GB 로는 안 됩니다.** 셋을 합치면 ~990MB 로 한도에 붙어 발표 중 OOM 위험이 있습니다.

---

## 처음 올릴 때

### 1. 인스턴스와 고정 IP

```bash
aws lightsail create-instances \
  --instance-names jinryomate \
  --availability-zone ap-northeast-2a \
  --blueprint-id ubuntu_24_04 \
  --bundle-id medium_3_0

aws lightsail allocate-static-ip --static-ip-name jinryomate-ip
aws lightsail attach-static-ip --static-ip-name jinryomate-ip --instance-name jinryomate
```

**고정 IP 를 반드시 붙이세요.** 인스턴스를 재시작하면 기본 IP 가 바뀌는데, 그러면 `sslip.io` 주소와 **카카오 콘솔에 등록한 웹훅 주소가 전부 깨집니다.**

### 2. 방화벽

```bash
aws lightsail put-instance-public-ports --instance-name jinryomate \
  --port-infos fromPort=80,toPort=80,protocol=TCP \
               fromPort=443,toPort=443,protocol=TCP \
               fromPort=22,toPort=22,protocol=TCP,cidrs=<내 IP>/32
```

**80 번을 닫으면 안 됩니다.** Let's Encrypt 의 ACME 인증이 그 포트를 씁니다. 닫으면 인증서 발급이 실패합니다.

SSH(22)는 본인 IP 로 제한하세요. 환자 데이터가 있는 서버입니다.

### 3. 서버 준비

```bash
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker ubuntu

# 스왑 2GB — 컨테이너 넷이 2GB 를 나눠 쓰므로 안전망을 둡니다.
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 4. 설정 파일

`deploy/docker-compose.prod.yml` 과 `deploy/Caddyfile` 을 서버에 두고, 같은 자리에 `.env` 를 만듭니다.

`deploy/.env.example` 을 복사해 채우세요. **`.env` 는 커밋되지 않습니다.**

`PUBLIC_HOST` 는 고정 IP 의 점을 하이픈으로 바꾼 주소입니다.

```
13.125.7.42  →  13-125-7-42.sslip.io
```

비밀값은 서버에서 직접 만드세요.

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 24   # POSTGRES_PASSWORD
openssl rand -base64 32   # AI_INTERNAL_TOKEN
```

### 5. 띄우기

```bash
echo $GHCR_TOKEN | docker login ghcr.io -u <github-id> --password-stdin
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

AI 이미지가 준비되면 함께 띄웁니다.

```bash
docker compose -f docker-compose.prod.yml --profile ai up -d
```

### 6. 확인

```bash
curl https://<PUBLIC_HOST>/api/health
```

Swagger 는 `https://<PUBLIC_HOST>/swagger-ui.html` 입니다.

### 7. 카카오 콘솔

주소가 바뀌었으므로 **두 곳을 고쳐야 합니다.**

| | 값 |
|---|---|
| 연결 해제 웹훅 | `https://<PUBLIC_HOST>/webhooks/kakao/unlink` (POST) |
| 로그인 Redirect URI | 앱 설정에 맞게 |

---

## 다시 배포할 때

`main` 에 머지되면 GitHub Actions 가 이미지를 GHCR 에 올립니다. 서버에서는 받아서 갈아끼우기만 합니다.

```bash
docker compose -f docker-compose.prod.yml pull backend
docker compose -f docker-compose.prod.yml up -d backend
```

**AI 만 바꿀 때도 백엔드는 건드리지 않습니다.**

```bash
docker compose -f docker-compose.prod.yml --profile ai pull ai
docker compose -f docker-compose.prod.yml --profile ai up -d ai
```

### 롤백

```bash
docker compose -f docker-compose.prod.yml stop backend
docker run ... ghcr.io/medical-mate/backend:<이전 커밋 SHA>
```

`latest` 외에 커밋 SHA 태그도 올리는 이유가 이것입니다.

---

## 백업

Lightsail 스냅샷이 디스크 전체를 뜹니다.

```bash
aws lightsail create-instance-snapshot \
  --instance-name jinryomate \
  --instance-snapshot-name jinryomate-$(date +%Y%m%d)
```

DB 만 따로 뜨려면 이쪽이 빠릅니다.

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U jinryomate jinryomate | gzip > backup-$(date +%Y%m%d).sql.gz
```

**발표 전날에는 반드시 한 번 떠두세요.**

---

## Bedrock 인증 — 왜 키를 쓰는가

**Lightsail 은 인스턴스에 IAM 역할을 붙일 수 없습니다.** EC2 만 됩니다. 그래서 액세스 키를 서버에 두되 피해 범위를 좁힙니다.

- `bedrock:InvokeModel` **하나만** 가진 전용 IAM 사용자
- 키는 `.env` 에만. 저장소에 들어가지 않습니다
- 예산 알림 $30 · $60 · $100

유출돼도 피해가 "남이 우리 크레딧으로 토큰을 쓰는 것" 에 그치고 예산 알림으로 탐지됩니다. **환자 데이터나 다른 AWS 리소스에는 닿지 못합니다.**

---

## 알아둘 것

**단일 장애점입니다.** 한 대가 죽으면 전부 죽습니다. 데모 규모에서는 수용 가능하고, 스냅샷으로 복구가 빠릅니다.

**`sslip.io` 는 발표용 주소로는 예쁘지 않습니다.** 도메인을 사면 고정 IP 를 그대로 가리키게 하고 `PUBLIC_HOST` 만 바꾸면 됩니다. Caddy 가 인증서를 새로 발급합니다.

**Render 는 새 서버가 검증될 때까지 그대로 둡니다.** 무료 DB 가 **2026-10-04** 에 삭제되므로 그 전에 이전을 끝내야 합니다.

## 이전 체크리스트

- [ ] `curl https://<PUBLIC_HOST>/api/health` 200
- [ ] Swagger 에 엔드포인트 26개
- [ ] Flyway V1~V8 적용 확인 (`docker compose logs backend | grep -i flyway`)
- [ ] 카카오 로그인 → 토큰 발급까지 실제로 해보기
- [ ] 카카오 콘솔 웹훅 주소 변경
- [ ] 앱·AI 담당자에게 새 주소 공지
- [ ] 예산 알림 설정 확인
- [ ] DB 덤프 한 번
- [ ] Render 서비스 정지
