# ADR-0003: 트레이스는 앱에서 전부 보내고, Alloy에서 tail sampling 한다

- 상태: 채택 (2026-11-15)
- 관련: #518, manifest#35

## 맥락
- 요청 단위로 지연 원인을 보려고 Grafana Cloud Tempo를 붙인다. 환경(prod/dev)마다 앱 1대, Alloy 1대가 같은 노드에 있고, Alloy 메모리 limit은 180Mi였다.
- 트래픽은 평시 분당 수 건, 한계 초당 약 25건(ADR-0002 이전 측정)이다. 트레이스 1건당 span 수와 크기는 아직 실측 전인 **추정치**다. span 8개는 요청 경로(HTTP 서버 1 + JDBC 쿼리 2(N+1 제거 후 기준) + Redis 1~3 + RabbitMQ 발행 1)를 센 값이고, span 1.5KB는 OTLP span(속성 10~20개)의 일반적인 크기다. dev 배포 뒤 Alloy의 `otelcol_receiver_accepted_spans` 지표와 파드 메모리로 실측해 이 문서를 갱신한다.
- 샘플링 방식은 둘이다. head 방식은 앱이 요청 시작 시점에 확률로 정해서 가볍지만 에러·느린 요청도 같은 확률로 버린다. tail 방식은 수집기가 트레이스를 끝까지 모아 본 뒤 정하므로 문제 요청을 전부 남길 수 있지만 대기하는 동안 메모리를 쓴다.
- 일반적인 대규모 구성(head 1차 → gateway collector에서 traceID 해싱 → tail 수집기)을 기준으로 우리 규모에 필요한 것만 추렸다.

## 결정
- 앱은 샘플링 없이(확률 1.0) 모든 트레이스를 같은 네임스페이스의 Alloy(`alloy:4318`, OTLP HTTP)로 보낸다. 계측은 Java agent가 아니라 `micrometer-tracing-bridge-otel`(in-process)로 한다.
- 앱은 `/actuator`, `/ws`, `/swagger-ui`, `/v3/api-docs` 요청을 관측에서 제외한다(`ObservationConfig`). k8s 프로브와 Synthetic 체크가 1~2분마다 호출하는 노이즈다.
- Alloy는 `otlp 수신 → memory_limiter(150Mi) → tail_sampling → batch → Tempo` 순으로 처리한다. tail 정책은 `composite`로 초당 400 span 상한 안에서 아래와 같이 배분한다.

| 정책 | 조건 | 수집 | 상한 배분 |
|---|---|---|---|
| errors | span status ERROR | 100% | 30% |
| http5xx | `outcome=SERVER_ERROR` | 100% | 20% |
| slow | 트레이스 1초 초과 | 100% | 25% |
| baseline | 나머지 | 10% | 25% |

- `decision_wait`는 10초, 동시에 들고 있는 트레이스는 2000개까지다. Alloy limit은 256Mi로 올린다.

### 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| head 샘플링만(prod 10%) | 가장 가볍지만 에러·느린 요청의 90%를 못 본다. 트레이스를 붙이는 목적과 어긋난다 |
| gateway collector + traceID 해싱 | 앱 1대·Alloy 1대라 한 트레이스의 span이 흩어질 경로가 없다. HPA나 두 번째 노드가 생기면 그때 넣는다 |
| spanmetrics 커넥터(샘플링 전 RED 메트릭) | RED 메트릭은 Micrometer가 앱에서 100% 기준으로 이미 수집한다. Alloy 메모리만 더 쓴다 |
| head 50% 1차 방어 | 초당 200 span은 네트워크 비용이 무의미하다. 부하테스트 때만 `TRACING_SAMPLING_PROBABILITY`로 내린다 |
| decision_wait 30초(기본값) | 비동기 경로가 RabbitMQ(커밋 후 발행) 하나라 대부분 1초 안에 도착한다. 대기가 길수록 메모리가 는다 |

## 결과
- 상한(초당 400 span) 이하에서는 문제 요청(에러·5xx·1초 초과)이 전부, 정상 요청은 10%가 Tempo에 남는다.
- 상한을 넘으면 정책별 배분(errors 30% = 초당 120 span, http5xx 20%, slow 25%, baseline 25%)까지만 남고 나머지는 버려진다. 예를 들어 초당 120건이 전부 에러(초당 960 span)면 에러 트레이스의 약 1/8만 남는다. 이 상한은 장애 때 Tempo 한도와 Alloy 메모리를 지키려고 둔 것이고, 그 구간의 원인 분석은 남은 샘플과 로그·메트릭으로 한다.
- Alloy 메모리는 평시 거의 0, 한계 트래픽에서 약 10MB(25 rps × 10초 × 8 span × 1.5KB × Go 객체 오버헤드 3배, 위 추정치 기준)를 더 쓴다. 앱은 bridge와 exporter 큐(기본 2048 span)로 10~20MB가 늘 것으로 본다(Micrometer Tracing 일반 경험치). 둘 다 dev에서 실측해 갱신한다.
- 감수한 것: Prometheus exemplar는 앱이 붙이므로 tail에서 버려진 정상 요청의 exemplar는 Tempo에 없다. p95 패널의 점을 눌렀을 때 "trace not found"가 날 수 있다. 에러·느린 요청의 점은 항상 열린다.
- 감수한 것: 부하테스트급 트래픽(초당 120건)에서는 memory_limiter가 걸려 그 구간 span 일부가 빠진다. OOM은 나지 않는다.

## 이후 작업 시 주의사항
- 앱 파드를 2대 이상으로 늘리면 같은 트레이스의 span이 Alloy 1대로 모이지 않을 수 있다(현재는 네임스페이스당 Alloy 1대라 괜찮다). Alloy를 늘리는 순간 `loadbalancing exporter`가 필요하다.
- 새 비동기 경로(스케줄러, 지연 큐)를 넣을 때 span이 10초 뒤에 도착하면 트레이스가 조각난다. 조각은 traceId로 찾을 수 있지만 tail 정책은 먼저 온 조각만 보고 판단한다.
- 부하테스트 전에 `TRACING_SAMPLING_PROBABILITY`를 0.1로 내리고 끝나면 되돌린다.
- Tempo 전송 자격은 `grafana-cloud-creds` 시크릿의 `tempo-url`·`tempo-username`(선택 `tempo-token`)이다. 키가 없으면 Alloy 파드가 뜨지 않는다.
