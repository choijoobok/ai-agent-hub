# 개발 CLI 중단 호출의 계량 검증 — 2026-11-10

REQ-009/107/116·FEAT-304/211·TC-3046/3047/2114 및 ASM-003의 실행 근거다. API 수준의 제품 계약은 CON-033/034·ARCH-032를 따르며 이 문서는 실험 입력/기대/실제 결과와 재현 소스만 보존한다.

## 결론

**두 CLI 모두 이번 중단 호출의 최종 사용량을 확인하지 못했다.** Codex는 과거 수치를 현재 턴에 재표시하고, Claude는 부분 입력량이 있는데도 오류 결과의 사용량을0으로 반환했다. 이를 원 호출의 최종량이나 실제0으로 채택하면 오집계한다. CLI 수신·저장 관측과 최종량 귀속을 분리한다. 지원되는 모든 버전/모델에서 영원히 불가능하다는 결론은 아니며, 아래 고정 버전/경로/관측 시간의 결과다.

당시 ASM-003의 남은 항목은 ADR-001 §8.1(2)의 **중단 호출 토큰 기록 완전성**이었다. 2026-11-10 ADR-048 A 승인 후 프레임워크 설계 재현과 운영 공급자 수락을 분리했고, [수정30단언·변이2종과 새 기준 대조](orchestration-runtime-verification.md)로 ASM-003을 확인했다. 이 문서의 CLI 최종량 미확인 결과는 그대로 운영 API 후속 수락의 근거다. 이전23단언은 정상 DB 종결 오표기를 놓쳤으며 수정 근거로 대체됐다. 실제 제품 원장/쿼터 통합은 소유 G3 이후 시험한다.

## 범위와 실행

- Codex CLI0.165.0, 요청 모델gpt-6.1-sol/low. app-server JSON 프로토콜, 도구/웹/앱/다중 에이전트/프로젝트 문서 및 훅을 자식 설정에서 차단했다. 요청 모델명은 공급자의 내부 실모델 ID 증명이 아니다.
- Claude Code2.1.322. 사용자의 로그인 완료 안내 후 CLI 로그인 유효성을 확인하고 실제 구독 경로를 호출했다. 요청 별칭sonnet, 수신 모델claude-sonnet-5-5. 안전 모드·도구없음·설정원천없음·MCP없음·스킬없음으로 실행했다. 인증 값은 시험 코드가 읽지 않는다.
- 각 CLI에서 새 세션 중단1회, 별도 세션 정상1회→중단1회, 총6개의 비교 호출. 정상 입력은 `SYNTHETIC_OK`만, 중단 입력은 숫자1~300 생성이다. 두 번째 텍스트 청크를 저장/수락한 직후 중단을 요청했다.
- 종결 이벤트 뒤30초 동안 연결을 유지하며 관측했다. Codex는 해당 시험 세션만 thread/read 후 프로세스 재시작·thread/resume과10초 관측을 추가하고 시험 세션을 보관 처리했다. Claude는 해당 시험 세션 ID의 저장 메시지만 대조했다. 새 모델 호출을 사용량 복구로 수행하지 않았다.
- 최초 Codex 시행3회는 로컬 훅의 안내문이 섞였다. 동일한 오귀속 현상은 관측했지만 완전 격리 입력의 근거로 쓰지 않고 훅을 차단한3회 재시험을 표의 근거로 삼았다. 기존 사용자 설정은 변경하지 않았다.
- 운영 API 호출·Claude 원격 종료 확인·과금 조회는 수행하지 않았다. Claude 저장 조회는 재개 호출 시험이 아니다. 시험 자식 프로세스는 종료했다.

## 입력·기대·실제값

| 경로/입력 | 기대하는 기록 경계 | 실제 관측 |
|---|---|---|
| Codex 새 세션 중단 | ACK/종결과 사용량을 별도로 기록 | 수신14청크. interrupt ACK·interrupted. 종결 뒤30.75초에 usage 이벤트0, 저장 token_count.info=null, 재시작/재개 후10초도 usage0 |
| Codex 정상 대조군 | 정상 종결과 숫자 계량을 보존 | completed, input11328/output10/total11338 |
| Codex 같은 세션 다음 중단 | 이전 정상량을 새 호출로 복제하지 않음 | 수신14청크. 중단 turnId의 last/total이 정상 수치와 완전히 동일. 종결 뒤30.29초, 저장 기록과 재개 이벤트에도 같은값. 새 호출의 최종량 근거 없음 |
| Claude 새 세션 중단 | 부분 관측과 최종량 부재를 분리 | 수신9청크/수락2. message_start input2/cache_creation630/output1, 중단 message_delta도 output1·stop_reason=null. interrupt ACK·message_stop·error_during_execution. 결과 usage 전부0, modelUsage 비어 있음. 이후30.09초에도 새 최종량 없음 |
| Claude 정상 대조군 | 완료 계량/각 범위를 구별 | success/end_turn, input2/cache_creation625/output13. client 추정비용0.002634 USD. 이를 구독 청구액으로 쓰지 않음 |
| Claude 같은 세션 다음 중단 | 누적 모델 비용과 이번 턴을 구별 | 수신11청크/수락2. 시작 input4/cache_creation663/output8, 중단 message_delta도 output8·stop_reason=null. 결과 usage 전부0, modelUsage/비용은 이전 정상과 동일. 이후30.08초와 저장 메시지에도 최종 output 없음 |

수신 청크 수는 토큰 수가 아니며 중단 요청 뒤 공급자가 새로 생성한 양도 아니다. CLI/전송 큐를 포함한다. 두 CLI의 interrupt ACK와 로컬 종결을 원격 처리/과금 종료로 해석하지 않는다. 숫자 동일성은 이 실험의 반례이지 제품의 범용 중복 판정 알고리즘이 아니다.

공식 [Codex app-server 문서](https://learn.chatgpt.com/docs/app-server)는 turn/interrupt의 접수/종결과 thread 사용량 이벤트를 구분한다. 계정 사용량은 계정/일자 범위이므로 이번 호출에 귀속시키는 대안이 아니다. 해당 계정 집계는 조회하지 않았다.

Claude 공식 [스트리밍 문서](https://code.claude.com/docs/en/agent-sdk/streaming-output)는 잘린 스트림에서도 message_stop과 중단 message_delta가 전달될 수 있음을 설명한다. 따라서 그 이벤트 존재만으로 공급자의 완전한 최종 계량을 입증하지 않는다. [계량 문서](https://code.claude.com/docs/en/agent-sdk/cost-tracking)에 따르면 assistant별 output_tokens는 자리표시이고, 스트리밍 입력에서 result.usage는 현재 턴, modelUsage/비용은 누적 범위다. 비용은 클라이언트 추정값이다. 이 설명과 실제 관측을 함께 사용했다.

## 검증 결과와 설계 반영

각 CLI 실행 단언9개씩 총18개, 저장된 관측 재검사10개가 통과했다. 실제 반례에 `Codex turnId만 믿기`·`Claude 오류 usage0을 확정하기` 두 잘못된 판정 규칙을 적용하면 UNKNOWN 경계를 위반함을 확인했다. 이는 제품 TC 실행이나 운영 API 수락 시험 통과 수가 아니다.

UsageObservation/UsageEnvelope의 원천 범위·누적 의미·수신 단계·귀속 검증 결과를 ENT-259에 연결했다. 원천 숫자는 ENT-260의 관측 항목으로 보존할 수 있지만 귀속 미검증이면 유효 head/ledger/쿼터에 반영하지 않는다. 최종량을 모르는 호출은 UNKNOWN, 관련 실적 스냅샷은 INCOMPLETE다. 실제 호출별0과 자리표시0을 구별한다. 정본은 FEAT-211.minimal_producer.attribution이며 TC-3046/3047/2114에 반례를 추가했다.

## 운영 API 수락 검증 인계

구독/시험 연결 확보 후 ARCH-032.capability_contract.operational_usage_acceptance와 TC-3043~3048을 실행한다. provider/모델/SDK/전송 버전과 호출 식별을 고정하고 정상·중단·단절·늦은 확정/정정·중복·동시 호출을 검증한다. CLI 실패를 보정하려고 다른 호출의 값이나 계정 집계 차액을 가져오지 않는다.

OpenAI [background 문서](https://developers.openai.com/api/docs/guides/background)의 취소/조회 기능은 해당 모드의 실증 후보이며, 동기 스트리밍과 동일한 지원을 가정하지 않는다. background를 채택할 경우 제공자의 데이터 보존/배포 조건부터 검토한다. Claude [Messages 스트리밍 문서](https://platform.claude.com/docs/en/build-with-claude/streaming)의 누적 message_delta와 최종 메시지를 실제 API에서 대조한다. 이 문서 확인을 양사의 중단 후 최종량 지원 실증으로 표시하지 않는다.

## 재현

아래3파일을 같은 폴더에 복원하고 Python3로 실행한다. 설치된 CLI 버전/실행파일 경로와 로그인은 고객 환경에 맞춘다. 기존 인증 파일을 복사하거나 값으로 넘기지 않는다. 재현 시 새 합성 요청이 실행되며 정상/중단3회씩의 구독 사용량이 발생한다.

```text
python -X utf8 cancel_usage_probe.py
python -X utf8 claude_usage_probe.py
python -X utf8 check_usage_observations.py <Codex 결과 JSON> <Claude 결과 JSON> <재검사 결과 JSON>
```

프로브는 각 실행 폴더에 정제 이벤트와 결과를 남긴다. 재검사는 라이브 이벤트와 저장 관측을 읽으며, 실제 최종량이 제공되는 새 버전에서는 이번 결론을 재사용하지 말고 원천/귀속 근거를 재검토한다.

### cancel_usage_probe.py

SHA-256: `f41ccf5367cf4a883b678afe3afc4a961530c21e3b33c8a0e78dbf5cfc9796f1`

<!-- reproduce-file: cancel_usage_probe.py -->
```python
"""ASM-003 합성 호출: 사용량의 턴 귀속과 중단 후 복원 가능성을 검증한다."""
import collections, hashlib, json, os, queue, subprocess, threading, time, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RUN = ROOT / ('cancel-usage-' + uuid.uuid4().hex[:8])
WORK = RUN / 'work'
WORK.mkdir(parents=True)
CODEX = Path(os.environ['LOCALAPPDATA']) / 'Programs/OpenAI/Codex/bin/codex.exe'
RESULT = {'cli': '0.165.0', 'requested_model': 'gpt-6.1-sol', 'observation_seconds': 30, 'cases': [], 'checks': []}
CONFIG = {'model_reasoning_effort': 'low', 'features.shell_tool': False,
          'features.apply_patch_freeform': False, 'features.apps': False, 'features.multi_agent': False, 'features.hooks': False}

def check(value, name):
    if not value:
        raise AssertionError(name)
    RESULT['checks'].append(name)
    print('PASS ' + name, flush=True)

class Client:
    def __init__(self, name):
        self.name, self.seq, self.events = name, 0, []
        self.queue = queue.Queue()
        self.counts = collections.Counter()
        args = [str(CODEX), '-c', 'features.shell_tool=false', '-c', 'features.apply_patch_freeform=false',
                '-c', 'features.multi_agent=false', '-c', 'features.apps=false', '-c', 'web_search="disabled"',
                '-c', 'project_doc_max_bytes=0', '-c', 'features.hooks=false', 'app-server', '--stdio']
        self.proc = subprocess.Popen(args, cwd=WORK, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, text=True, encoding='utf-8', creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        def read():
            for line in self.proc.stdout:
                try: self.queue.put(json.loads(line))
                except ValueError: raise RuntimeError('JSON 스트림 파싱 실패')
            self.queue.put({'eof': True})
        threading.Thread(target=read, daemon=True).start()
        self.rpc('initialize', {'clientInfo': {'name': 'aihub_usage_verification', 'version': '2.0'},
                               'capabilities': {'experimentalApi': True}})
        self.send('initialized', {}, notification=True)

    def send(self, method, params, notification=False):
        self.seq += 1
        obj = {'method': method, 'params': params}
        if not notification: obj['id'] = self.seq
        self.proc.stdin.write(json.dumps(obj) + '\n')
        self.proc.stdin.flush()
        return self.seq

    def receive(self, timeout):
        o = self.queue.get(timeout=timeout)
        if o.get('eof'): raise RuntimeError('CLI 조기 종료')
        method, p = o.get('method', ''), o.get('params', {})
        if method:
            self.counts[method] += 1
            safe = {'method': method, 'at': time.monotonic(), 'threadId': p.get('threadId'), 'turnId': p.get('turnId')}
            if method == 'thread/tokenUsage/updated': safe['tokenUsage'] = p['tokenUsage']
            elif method == 'item/agentMessage/delta': safe['delta'] = p.get('delta', '')
            elif method in ('turn/started', 'turn/completed'):
                safe.update(turnId=p['turn']['id'], status=p['turn']['status'], turnKeys=list(p['turn']))
            elif method == 'item/started':
                safe['itemType'] = p.get('item', {}).get('type')
                if safe['itemType'] not in ('userMessage', 'agentMessage', 'reasoning'):
                    raise RuntimeError('예상하지 않은 도구 항목: ' + str(safe['itemType']))
            self.events.append(safe)
            if 'id' in o:
                self.proc.stdin.write(json.dumps({'id': o['id'], 'error': {'code': -32601, 'message': '시험은 도구를 허용하지 않는다'}}) + '\n')
                self.proc.stdin.flush()
                raise RuntimeError('예상하지 않은 서버 요청: ' + method)
        return o

    def rpc(self, method, params):
        ident = self.send(method, params)
        end = time.monotonic() + 75
        while time.monotonic() < end:
            try: o = self.receive(min(1, end - time.monotonic()))
            except queue.Empty: continue
            if o.get('id') == ident:
                if 'error' in o: raise RuntimeError(method + ' RPC 오류 코드 ' + str(o['error'].get('code')))
                return o['result']
        raise TimeoutError(method)

    def drain(self, seconds):
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            try: self.receive(min(1, max(.01, end - time.monotonic())))
            except queue.Empty: pass

    def start_thread(self):
        r = self.rpc('thread/start', {'model': RESULT['requested_model'], 'cwd': str(WORK),
            'sandbox': 'read-only', 'approvalPolicy': 'never', 'ephemeral': False,
            'baseInstructions': 'You generate synthetic test text only. Never use tools, files, network, or skills.',
            'developerInstructions': 'Output only the requested synthetic text. No tools or external data.', 'config': CONFIG})
        return r['thread']

    def turn(self, tid, cancel):
        start = len(self.events)
        ident = self.send('turn/start', {'threadId': tid, 'effort': 'low', 'input': [{'type': 'text',
            'text': 'Print integers 1 through 300, one per line. No tools, explanation or headings.' if cancel else 'Reply with exactly: SYNTHETIC_OK', 'text_elements': []}]})
        turn_id = None
        cancel_id, ack, completed, terminal, deltas = None, False, None, None, 0
        end = time.monotonic() + 120
        while time.monotonic() < (completed + 30 if completed else end):
            try: o = self.receive(1)
            except queue.Empty: continue
            m, p = o.get('method'), o.get('params', {})
            if o.get('id') == ident:
                if 'error' in o: raise RuntimeError('모델 턴 시작 실패')
                turn_id = o['result']['turn']['id']
            if m == 'turn/started' and p.get('threadId') == tid: turn_id = p['turn']['id']
            if m == 'item/agentMessage/delta' and p.get('threadId') == tid:
                deltas += 1
                if cancel and deltas == 2:
                    if not turn_id: raise RuntimeError('중단 턴 식별 없음')
                    cancel_id = self.send('turn/interrupt', {'threadId': tid, 'turnId': turn_id})
            if cancel_id and o.get('id') == cancel_id: ack = 'error' not in o
            if m == 'turn/completed' and p.get('threadId') == tid and p['turn']['id'] == turn_id:
                terminal, completed = p['turn']['status'], time.monotonic()
        events = self.events[start:]
        usage = [e for e in events if e['method'] == 'thread/tokenUsage/updated']
        return {'turn_id': turn_id, 'cancel_requested': cancel, 'cancel_ack': ack, 'terminal': terminal,
                'deltas': deltas, 'post_terminal_seconds': round(time.monotonic()-completed, 2) if completed else None,
                'usage_events': usage, 'event_count': len(events)}

    def close(self):
        (RUN / (self.name + '.json')).write_text(json.dumps({'events': self.events, 'method_counts': self.counts}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        self.proc.terminate()
        try: self.proc.wait(timeout=8)
        except subprocess.TimeoutExpired: self.proc.kill(); self.proc.wait(timeout=3)

def stored_usage(thread):
    p = Path(thread['path'])
    # 이 시험의 thread/start가 반환한 세션 한 건만 읽는다. 자격정보는 읽지 않는다.
    if thread['id'] not in p.name: raise RuntimeError('시험 세션 경로 불일치')
    events, context = [], None
    for line in p.read_text(encoding='utf-8').splitlines():
        obj = json.loads(line)
        kind, payload = obj.get('type'), obj.get('payload', {})
        if kind == 'turn_context': context = payload.get('turn_id')
        if kind == 'event_msg' and payload.get('type') in ('token_count', 'task_started', 'task_complete', 'turn_aborted'):
            safe = {'type': payload['type'], 'turn_id': payload.get('turn_id'), 'context_turn_id': context}
            if payload['type'] == 'token_count': safe['info'] = payload.get('info')
            events.append(safe)
    return events

print('RUN ' + RUN.name, flush=True)
try:
    for name, baseline in [('fresh_cancel', False), ('normal_then_cancel', True)]:
        c = Client(name)
        thread = None
        case = {'name': name}
        RESULT['cases'].append(case)
        try:
            thread = c.start_thread()
            if baseline:
                print('START normal control', flush=True)
                case['normal'] = c.turn(thread['id'], False)
                n = case['normal']
                check(n['terminal'] == 'completed' and any(e['turnId'] == n['turn_id'] and e['tokenUsage']['last']['inputTokens'] > 0 for e in n['usage_events']), name + '_normal_usage_control')
            print('START ' + name, flush=True)
            case['cancelled'] = c.turn(thread['id'], True)
            v = case['cancelled']
            check(v['cancel_ack'] and v['terminal'] == 'interrupted' and v['deltas'] >= 2, name + '_ack_terminal')
            check(v['post_terminal_seconds'] >= 30, name + '_full_30_second_observation')
            r = c.rpc('thread/read', {'threadId': thread['id'], 'includeTurns': True})['thread']
            case['read_turns'] = [{'id': t['id'], 'status': t['status'], 'keys': list(t)} for t in r['turns']]
            case['stored_events_before_restart'] = stored_usage(r)
            check(any(t['id'] == v['turn_id'] and t['status'] == 'interrupted' for t in r['turns']), name + '_read_preserves_interruption')
        finally: c.close()
        c = Client(name + '_restart')
        try:
            r = c.rpc('thread/resume', {'threadId': thread['id'], 'cwd': str(WORK), 'sandbox': 'read-only', 'approvalPolicy': 'never', 'config': CONFIG})
            c.drain(10)
            case['resume_usage_events'] = [e for e in c.events if e['method'] == 'thread/tokenUsage/updated']
            case['resume_turns'] = [{'id': t['id'], 'status': t['status'], 'keys': list(t)} for t in r['thread']['turns']]
            case['stored_events_after_restart'] = stored_usage(r['thread'])
            c.rpc('thread/archive', {'threadId': thread['id']})
            case['test_session_archived'] = True
            check(any(t['id'] == case['cancelled']['turn_id'] and t['status'] == 'interrupted' for t in r['thread']['turns']), name + '_restart_preserves_interruption')
            print('OBSERVED ' + json.dumps({'name': name, 'cancel_usage': len(case['cancelled']['usage_events']), 'resume_usage': len(case['resume_usage_events']), 'stored_token_events': sum(x['type']=='token_count' for x in case['stored_events_after_restart'])}), flush=True)
        finally: c.close()
    RESULT['result'] = 'OBSERVED'
finally:
    RESULT['source_sha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    (RUN / 'result.json').write_text(json.dumps(RESULT, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('RESULT ' + str(RUN / 'result.json'), flush=True)
```

### claude_usage_probe.py

SHA-256: `173e2cb5c543a045a465a047640cbf126747d6e16c7b3c554a4c9901403edab5`

<!-- reproduce-file: claude_usage_probe.py -->
```python
"""Claude CLI의 합성 스트리밍/중단/계량 관측. 자격정보는 CLI만 사용한다."""
import collections, hashlib, json, os, queue, subprocess, threading, time, uuid
from pathlib import Path
ROOT = Path(__file__).resolve().parent
RUN = ROOT / ('claude-usage-' + uuid.uuid4().hex[:8])
WORK = RUN / 'work'
WORK.mkdir(parents=True)
CLI = Path.home() / '.local/bin/claude.exe'
RESULT = {'cli': '2.1.322', 'requested_model': 'sonnet', 'cases': [], 'checks': []}

def check(value, name):
    if not value: raise AssertionError(name)
    RESULT['checks'].append(name)
    print('PASS ' + name, flush=True)

class Client:
    def __init__(self, name):
        self.name, self.session, self.seq = name, str(uuid.uuid4()), 0
        self.events, self.queue, self.counts = [], queue.Queue(), collections.Counter()
        args = [str(CLI), '-p', '--verbose', '--input-format', 'stream-json', '--output-format', 'stream-json',
            '--include-partial-messages', '--safe-mode', '--setting-sources', '', '--tools', '',
            '--permission-mode', 'dontAsk', '--strict-mcp-config', '--mcp-config', '{"mcpServers":{}}',
            '--disable-slash-commands', '--system-prompt', 'Generate only requested synthetic text. Never use tools, files, network or skills.',
            '--model', 'sonnet', '--effort', 'low', '--session-id', self.session]
        self.err = (RUN / (name + '.stderr')).open('w', encoding='utf-8')
        self.proc = subprocess.Popen(args, cwd=WORK, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.err,
            text=True, encoding='utf-8', creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        def read():
            for line in self.proc.stdout:
                try: self.queue.put(json.loads(line))
                except ValueError: self.queue.put({'type': 'invalid_json'})
            self.queue.put({'type': 'eof'})
        threading.Thread(target=read, daemon=True).start()
        init = self.control('initialize')
        end = time.monotonic() + 60
        while time.monotonic() < end:
            try: o = self.receive(1)
            except queue.Empty: continue
            if o.get('type') == 'control_response' and o.get('response', {}).get('request_id') == init:
                check(o['response'].get('subtype') == 'success', name + '_initialize')
                return
        raise TimeoutError('Claude 초기화')

    def send(self, o):
        self.proc.stdin.write(json.dumps(o) + '\n'); self.proc.stdin.flush()

    def control(self, subtype):
        self.seq += 1
        ident = 'probe_' + str(self.seq)
        self.send({'type': 'control_request', 'request_id': ident, 'request': {'subtype': subtype}})
        return ident

    def receive(self, timeout):
        o = self.queue.get(timeout=timeout)
        kind = o.get('type')
        if kind in ('eof', 'invalid_json'): raise RuntimeError('Claude 스트림 종료/파싱 오류: ' + kind)
        self.counts[kind] += 1
        s = {'type': kind, 'at': time.monotonic(), 'uuid': o.get('uuid')}
        if kind == 'system': s.update(subtype=o.get('subtype'), model=o.get('model'))
        elif kind == 'control_response': s['response'] = {k:v for k,v in o.get('response', {}).items() if k in ['subtype','request_id']}
        elif kind == 'stream_event':
            e = o.get('event', {})
            s['event'] = e
            if e.get('type') == 'content_block_start' and e.get('content_block', {}).get('type') == 'tool_use': raise RuntimeError('도구 호출 발생')
        elif kind == 'assistant':
            m = o.get('message', {})
            s['message'] = {k:v for k,v in m.items() if k in ['id','model','usage','stop_reason','stop_sequence']}
            if any(x.get('type') == 'tool_use' for x in m.get('content', [])): raise RuntimeError('도구 호출 발생')
        elif kind == 'result':
            s.update({k:v for k,v in o.items() if k in ['subtype','is_error','usage','modelUsage','total_cost_usd','num_turns','stop_reason','duration_ms','duration_api_ms']})
        elif kind == 'control_request': raise RuntimeError('예상하지 않은 권한/도구 요청')
        self.events.append(s)
        return o

    def turn(self, cancel):
        start = len(self.events)
        self.send({'type':'user', 'session_id':self.session, 'message':{'role':'user', 'content':
            'Print integers 1 through 300, one number per line. No tools or explanation.' if cancel else 'Reply with exactly: SYNTHETIC_OK'}, 'parent_tool_use_id':None})
        deltas, accepted, cancel_id, ack, finished, result = 0, 0, None, False, None, None
        end = time.monotonic() + 120
        while time.monotonic() < (finished + 30 if finished else end):
            try: o = self.receive(1)
            except queue.Empty: continue
            if o['type'] == 'stream_event' and o.get('event', {}).get('type') == 'content_block_delta' and o['event'].get('delta', {}).get('type') == 'text_delta':
                deltas += 1
                if not cancel_id: accepted += 1
                if cancel and deltas == 2: cancel_id = self.control('interrupt')
            if o['type'] == 'control_response' and o.get('response',{}).get('request_id') == cancel_id: ack = o['response'].get('subtype') == 'success'
            if o['type'] == 'result': finished, result = time.monotonic(), self.events[-1]
        e = self.events[start:]
        return {'cancel_requested': cancel, 'cancel_ack': ack, 'deltas': deltas, 'accepted': accepted,
                'post_result_seconds': round(time.monotonic()-finished,2) if finished else None,
                'result': result, 'usage_events': [x for x in e if (x['type']=='stream_event' and ('usage' in x['event'] or 'usage' in x['event'].get('message',{}))) or x['type'] in ('assistant','result')],
                'message_stop_count':sum(x['type']=='stream_event' and x['event'].get('type')=='message_stop' for x in e)}

    def close(self):
        self.proc.stdin.close()
        try: self.proc.wait(timeout=10)
        except subprocess.TimeoutExpired: self.proc.terminate(); self.proc.wait(timeout=5)
        self.err.close()
        (RUN / (self.name + '.json')).write_text(json.dumps({'events':self.events, 'method_counts':self.counts},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

print('RUN ' + RUN.name,flush=True)
try:
    for name, baseline in [('fresh_cancel',False),('normal_then_cancel',True)]:
        c = Client(name)
        case = {'name':name, 'session':c.session}
        RESULT['cases'].append(case)
        try:
            if baseline:
                print('START normal control',flush=True)
                case['normal'] = c.turn(False)
                check(case['normal']['result'] is not None and case['normal']['result'].get('usage',{}).get('output_tokens',0)>0, name+'_normal_usage_control')
            print('START '+name,flush=True)
            case['cancelled'] = c.turn(True)
            v=case['cancelled']
            check(v['cancel_ack'] and v['deltas']>=2 and v['accepted']==2, name+'_interrupt_ack_boundary')
            check(v['result'] is not None and v['message_stop_count']>0, name+'_terminal_observed')
            check(v['post_result_seconds']>=30, name+'_full_30_second_observation')
            print('OBSERVED '+json.dumps({'name':name,'deltas':v['deltas'],'result':v['result']},ensure_ascii=False),flush=True)
        finally: c.close()
        paths=list((Path.home()/'.claude/projects').glob('*/'+c.session+'.jsonl'))
        case['stored_usage']=[]
        for p in paths:
            for line in p.read_text(encoding='utf-8').splitlines():
                o=json.loads(line);m=o.get('message',{})
                if o.get('type')=='assistant' and 'usage' in m:
                    case['stored_usage'].append({k:v for k,v in m.items() if k in ['id','model','usage','stop_reason']})
        case['stored_session_files']=len(paths)
    RESULT['result']='OBSERVED'
finally:
    RESULT['source_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    (RUN/'result.json').write_text(json.dumps(RESULT,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('RESULT '+str(RUN/'result.json'),flush=True)
```

### check_usage_observations.py

SHA-256: `06059fa46de7f6b404018133122c1901ad5ba0a22c96ad2843353d5fb901ce95`

<!-- reproduce-file: check_usage_observations.py -->
```python
"""실제 수신 근거에서 잘못된 최종량 판정 규칙을 반례로 검증한다. 제품 TC 실행이 아니다."""
import json,sys
from pathlib import Path
codex=json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
claude=json.loads(Path(sys.argv[2]).read_text(encoding='utf-8'))
checks=[]
def check(value,name):
    assert value,name
    checks.append(name)
    print('PASS '+name)
fresh,mixed=codex['cases']
check(not fresh['cancelled']['usage_events'] and not fresh['resume_usage_events'],'codex_fresh_no_final_usage_after_wait_and_resume')
baseline=mixed['normal']['usage_events'][-1]['tokenUsage']
cancel=mixed['cancelled']['usage_events'][-1]
check(cancel['turnId']==mixed['cancelled']['turn_id'] and cancel['tokenUsage']==baseline,'codex_same_turn_id_does_not_prove_attribution')
check(mixed['resume_usage_events'][-1]['tokenUsage']==baseline,'codex_resume_replays_old_meter')
cf,cm=claude['cases']
for c in claude['cases']:
    v=c['cancelled']
    start=next(e['event']['message']['usage'] for e in v['usage_events'] if e['type']=='stream_event' and e['event']['type']=='message_start')
    check(start['input_tokens']>0 and v['result']['usage']['input_tokens']==0 and v['result']['is_error'],c['name']+'_claude_error_zero_is_not_actual_zero')
    delta=next(e['event'] for e in v['usage_events'] if e['type']=='stream_event' and e['event']['type']=='message_delta')
    check(delta['delta']['stop_reason'] is None and delta['usage']['output_tokens']==start['output_tokens'],c['name']+'_claude_cut_message_not_final')
check(cm['normal']['result']['modelUsage']==cm['cancelled']['result']['modelUsage'],'claude_cumulative_model_usage_is_previous_normal')
check(cm['normal']['result']['usage']['output_tokens']>0,'claude_normal_control_has_output_usage')

# 두 잘못된 규칙을 실제 입력에 적용하면 계약의 미확인 경계를 깨뜨려야 한다.
mutations=[]
for name,wrong_value in [('trust_codex_turn_id',cancel['tokenUsage']['last']['totalTokens']),('trust_claude_error_zero',cf['cancelled']['result']['usage']['output_tokens'])]:
    try:
        assert wrong_value is None,'최종 귀속 근거가 없는 중단 호출은 UNKNOWN이어야 한다'
    except AssertionError:
        mutations.append(name)
        print('MUTATION_KILLED '+name)
    else: raise AssertionError('잘못된 최종량 규칙이 탐지되지 않았다')
check(len(mutations)==2,'both_unsafe_shortcuts_rejected')
out={'checks':checks,'mutations_killed':mutations,'interpretation':'최종 호출 귀속은 미확인. 원천 숫자 보존과 유효 사용량 선택은 분리한다.'}
Path(sys.argv[3]).write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
```

