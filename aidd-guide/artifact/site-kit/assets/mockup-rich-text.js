/** 목업 공통 콘텐츠 표시. 원문을 바꾸지 않고 실행 가능한 HTML·외부 자원을 제외한다. */
function mockupRichTextStyle(property,value){
  const colors=/^(?:#[0-9a-f]{3,4}|#[0-9a-f]{6}|#[0-9a-f]{8}|transparent|currentcolor|black|white|red|green|blue|gray|grey|yellow|orange|purple|navy|teal|maroon|silver|lime|olive|aqua|fuchsia)$/i;
  if(['color','background-color'].includes(property)){
    const rgb=value.match(/^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*(0|1|0?\.\d+))?\s*\)$/);
    return colors.test(value)||rgb&&rgb.slice(1,4).every(part=>Number(part)<=255)&&(!rgb[4]||Number(rgb[4])<=1)?value:'';
  }
  if(property==='font-size'){const match=value.match(/^(\d+(?:\.\d+)?)px$/);return match&&Number(match[1])>=8&&Number(match[1])<=48?value:'';}
  const allowed={'font-weight':['normal','bold','100','200','300','400','500','600','700','800','900'],'font-style':['normal','italic'],'text-align':['left','center','right','justify'],'text-decoration':['none','underline','line-through'],'white-space':['normal','pre','pre-wrap']};
  return Object.hasOwn(allowed,property)&&allowed[property].includes(value)?value:'';
}
function mockupRichTextPreview(text,format,doc){
  const fragment=doc.createDocumentFragment();text=String(text??'');
  if(format==='TEXT'||typeof DOMPurify==='undefined'||format==='MARKDOWN'&&typeof marked==='undefined'){
    const content=doc.createElement('div');content.style.whiteSpace='pre-wrap';content.textContent=text;fragment.append(content);return fragment;
  }
  if(!['MARKDOWN','HTML'].includes(format))throw new Error('지원하지 않는 콘텐츠 작성 형식입니다.');
  let html=text;
  if(format==='MARKDOWN'){
    const escape=text=>String(text).replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
    const renderer=new marked.Renderer();renderer.html=token=>escape(token.text);renderer.image=token=>escape(token.text||'이미지 생략');
    html=marked.parse(text,{gfm:true,async:false,renderer});
  }
  const clean=DOMPurify.sanitize(html,{ALLOWED_TAGS:['div','span','p','h1','h2','h3','h4','h5','h6','ul','ol','li','pre','code','blockquote','strong','em','b','i','u','s','sub','sup','del','a','table','caption','thead','tbody','tfoot','tr','th','td','hr','br','dl','dt','dd'],ALLOWED_ATTR:['href','title','align',...(format==='HTML'?['style']:[])],ALLOW_DATA_ATTR:false,ALLOW_ARIA_ATTR:false,RETURN_DOM_FRAGMENT:true});
  for(const element of clean.querySelectorAll('[style]')){
    const safe=[];for(let index=0;index<element.style.length;index++){const property=element.style.item(index),value=mockupRichTextStyle(property,element.style.getPropertyValue(property).trim().toLowerCase());if(value)safe.push([property,value]);}
    element.removeAttribute('style');for(const [property,value]of safe)element.style.setProperty(property,value);
  }
  for(const link of clean.querySelectorAll('a')){const href=link.getAttribute('href')||'';if(!/^(?:https?:\/\/|mailto:|#)/i.test(href))link.removeAttribute('href');link.setAttribute('rel','noopener noreferrer');}
  fragment.append(clean);return fragment;
}
function bindRichTextMockup(root){root.addEventListener('click',event=>{if(event.target.closest('a')?.closest('[data-rich-text-preview],[data-prompt-preview]'))event.preventDefault();});}
