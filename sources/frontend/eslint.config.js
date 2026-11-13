import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
// eslint-plugin-import 는 peer 가 eslint 9 에서 멈춰 있다. 유지되는 포크를 쓴다.
import importX from 'eslint-plugin-import-x';
import { createTypeScriptImportResolver } from 'eslint-import-resolver-typescript';
import fs from 'node:fs';
import path from 'node:path';

// features 목록을 손으로 적지 않는다. 적으면 새 기능을 추가할 때 빠뜨리고, 빠진 기능은
// 규칙의 대상이 아니게 되어 아무 경고 없이 다른 기능을 import 할 수 있다 - frontend/packages.md.
const featuresDir = path.join(import.meta.dirname, 'src/features');
const FEATURES = fs.existsSync(featuresDir)
  ? fs
      .readdirSync(featuresDir, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
  : [];

export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommendedTypeChecked],
    languageOptions: {
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    settings: {
      // 없으면 '@/features/...' 별칭을 해석하지 못해 경계 규칙이 조용히 통과한다.
      'import-x/resolver-next': [createTypeScriptImportResolver({ project: './tsconfig.app.json' })],
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'import-x': importX,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // features 끼리 직접 import 하지 않는다. 공유가 필요하면 shared 로 올린다 — frontend/packages.md.
      //
      // 존이 기능마다 하나씩인 이유는 '자기 자신'을 열어 두어야 하기 때문이다.
      // target·from 을 둘 다 './src/features' 로 두면 auth 안에서 auth 를 import 하는 것까지
      // 막힌다 — 규칙이 뜻하는 것은 '기능 사이'이지 '기능 안'이 아니다.
      'import-x/no-restricted-paths': [
        'error',
        {
          zones: [
            ...FEATURES.map((feature) => ({
              target: `./src/features/${feature}`,
              from: './src/features',
              except: [`./${feature}`],
              message:
                'features 끼리 직접 import 할 수 없다. 두 번째 화면이 쓰는 순간 shared/ 로 올린다 — frontend/packages.md',
            })),
            ...['admin', 'portal', 'agent'].flatMap((product) => [
              {
                target: './src/modules/system',
                from: `./src/modules/${product}`,
                message: `system은 ${product} 모듈을 참조할 수 없다 — ARCH-022.`,
              },
              {
                target: `./src/modules/${product}`,
                from: './src/modules/system',
                except: ['./public'],
                message: `${product}는 system public 계약만 참조할 수 있다 — ARCH-022.`,
              },
            ]),
          ],
        },
      ],

      // HTTP 층의 입구는 하나다 — frontend/packages.md.
      //
      // 같은 헬퍼가 '@/shared/api' 와 '@/shared/api/request' 두 경로로 들어오면 새 화면이
      // 무엇을 따라야 할지 알 수 없다. 배럴이 유일한 입구이며, 배럴에 없는 것은
      // 바깥에서 쓸 것이 아니다(추가하려면 index.ts 를 고친다).
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/shared/api/*'],
              message: "HTTP 층은 '@/shared/api' 배럴만 쓴다 — frontend/packages.md.",
            },
          ],
        },
      ],

      // 호출 URL 은 features/{도메인}/api/endpoints.ts 에만 둔다 — frontend/packages.md.
      // 그 선언이 SYS_MENU_API 시드의 입력이라 '선언 = 사용' 이 보장돼야 한다.
      'no-restricted-syntax': [
        'error',
        {
          // String.raw 로 쓴다 — 일반 문자열이면 \/ 가 / 로 접혀 셀렉터가 깨진다.
          selector: String.raw`Literal[value=/^\/api\//]`,
          message:
            "'/api/...' 리터럴은 endpoints.ts 에만 둔다. 화면은 그 상수만 쓴다 — frontend/packages.md.",
        },
        {
          // axios baseURL 이 '/api' 라서 실제 선언은 모듈/계층 마디부터 시작한다. 위 규칙만
          // 두면 '/common/auth/me' 를 화면에 직접 써도 통과한다 — 게이트가 조용히 비게 된다.
          selector: String.raw`Literal[value=/^\/[^/]+\/(public|common|service)\//]`,
          message:
            "모듈/계층 API 경로 리터럴은 endpoints.ts 에만 둔다 — frontend/packages.md.",
        },

        // 화면 문구를 소스에 박지 않는다 - sys/code.md · sys/message.md.
        //
        // 명칭의 단일 원본은 SYS_MESSAGE 이고, 여기 한글이 남으면 원본이 둘이 된다.
        // 어긋났을 때 어느 쪽이 맞는지 화면만 봐서는 알 수 없다.
        //
        // 개발자용 로그(console.error·throw)는 영어로 쓴다. 예외 목록을 만들면 그 목록이
        // 늘어나면서 게이트가 조용히 비게 된다 - 예외 자체를 두지 않는 쪽을 택했다.
        {
          selector: String.raw`Literal[value=/[가-힣]/]`,
          message:
            '한글 문자열 리터럴을 둘 수 없다. MSG 키를 만들고 label() 로 쓴다 - sys/code.md.',
        },
        {
          selector: String.raw`JSXText[value=/[가-힣]/]`,
          message: 'JSX 안의 한글도 문구다. MSG 키를 만들고 label() 로 쓴다 - sys/code.md.',
        },
        {
          selector: String.raw`TemplateElement[value.raw=/[가-힣]/]`,
          message:
            '템플릿 문자열의 한글도 문구다. 자리표는 label(key, params) 가 채운다 - sys/code.md.',
        },

        // 키 자체도 리터럴로 쓰지 않는다 - sys/message.md.
        //
        // label('shell.logout') 은 동작한다. 그래서 위험하다 - 그 키는 generateMessageSeed
        // 의 입력이 아니라서 SYS_MESSAGE_KEY 에 행이 생기지 않고, 관리 화면에 나타나지
        // 않아 번역할 방법이 없다. 화면에는 키 문자열이 그대로 보인다(sys/code.md).
        {
          selector: "CallExpression[callee.name='label'] > Literal",
          message:
            "label() 에 리터럴을 넘길 수 없다. MSG 에 상수를 추가한다 - sys/message.md.",
        },
      ],
    },
  },
  {
    // endpoints 선언 자체는 위 금지의 예외다.
    files: ['**/api/endpoints.ts'],
    rules: { 'no-restricted-syntax': 'off' },
  },
  {
    // 배럴의 안쪽. 여기서 배럴을 부르면 순환이다 — 금지의 대상은 이 폴더 밖이다.
    files: ['src/shared/api/**'],
    rules: { 'no-restricted-imports': 'off' },
  },
  {
    // 번들이 닿지 않을 때의 정적 문구는 여기에만 둔다 -.
    // 내용이 고정적이고 몇 개뿐이라 DB 번들과 벌어질 여지가 없다.
    files: ['src/locales/**'],
    rules: { 'no-restricted-syntax': 'off' },
  },
  {
    // 테스트 제목과 픽스처는 화면에 나가지 않는다.
    files: ['**/*.test.ts', '**/*.test.tsx'],
    rules: { 'no-restricted-syntax': 'off' },
  },
);
