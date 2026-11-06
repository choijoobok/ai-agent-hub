import {createRequire} from 'node:module';
import {dirname,join} from 'node:path';
import {readFileSync} from 'node:fs';
const frontend=createRequire(new URL('../../../sources/frontend/package.json',import.meta.url));
/** 설치된 브라우저 번들을 오프라인 산출물에 포함한다. 제품 의존성은 바꾸지 않는다. */
export function richTextBrowserAssets(){return [{name:'mockup-marked.js',path:join(dirname(frontend.resolve('marked')),'marked.umd.js')},{name:'mockup-purify.js',path:frontend.resolve('dompurify/purify.min.js')}];}
export function richTextBrowserScripts(){return richTextBrowserAssets().map(asset=>readFileSync(asset.path,'utf8').replace(/<\/script/gi,'<\\/script')).join('\n');}
