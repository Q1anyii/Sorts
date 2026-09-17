// renderMarkdown 单元测试：从 app.js 提取函数，stub document/hljs，覆盖
// ① 基础 Markdown（标题/加粗/行内代码） ② 代码块窗口 + 语言标签 ③ XSS：原始 HTML 转义
// ④ XSS：javascript: 伪协议链接拦截 ⑤ 依赖缺失退化（marked 未加载 → 纯文本转义）
const fs = require('fs');
const path = require('path');

// —— stub 浏览器环境 ——
const marked = require(path.join(__dirname, '..', 'js', 'vendor', 'marked.min.js'));
// 真实浏览器中 div.textContent=... 后 innerHTML 返回实体编码文本；stub 需模拟该行为
const stubHtmlEncode = (s) => String(s == null ? '' : s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
const stubDoc = {
  createElement() {
    return {
      _text: '',
      set textContent(v) { this._text = String(v == null ? '' : v); },
      get innerHTML() { return stubHtmlEncode(this._text); },
      set innerHTML(v) { this._text = String(v); },
      querySelectorAll: () => [],
    };
  },
};
global.document = stubDoc;
global.marked = marked;
global.hljs = undefined; // 无 DOM 时高亮跳过（生产环境有真实浏览器）

const src = fs.readFileSync(path.join(__dirname, '..', 'js', 'app.js'), 'utf8');
function grab(name) {
  const m = src.match(new RegExp('function ' + name + '\\([\\s\\S]*?\\n\\}'));
  if (!m) { console.error(name + ' NOT FOUND'); process.exit(1); }
  return new Function('return ' + m[0])();
}
const escapeHtml = grab('escapeHtml');
const sanitizeLink = grab('sanitizeLink');
// renderMarkdown 内部以自由变量引用上述模块级函数，注入全局供 eval 解析
global.escapeHtml = escapeHtml;
global.sanitizeLink = sanitizeLink;
const renderMarkdown = grab('renderMarkdown');

let pass = 0, fail = 0;
function eq(name, got, want) {
  const ok = got === want;
  if (ok) { pass++; console.log('PASS', name); }
  else { fail++; console.log('FAIL', name, '\n  got :', String(got).slice(0, 220), '\n  want:', String(want).slice(0, 220)); }
}
function has(name, hay, needle) { eq(name, hay.includes(needle), true); }
function notHas(name, hay, needle) { eq(name, hay.includes(needle), false); }

// ① 基础 Markdown
const md1 = renderMarkdown('# 标题\n\n**加粗** 与 `行内代码`');
has('h1', md1, '<h1');
has('strong', md1, '<strong>加粗</strong>');
has('inline-code', md1, '<code');
notHas('raw-hash', md1, '# 标题');

// ② 代码块窗口
const md2 = renderMarkdown('```js\nconst a = 1;\n```');
has('code-window', md2, 'code-block-wrapper');
has('code-lang', md2, 'code-block-lang');
has('lang-js', md2, 'language-js');
has('copy-btn', md2, 'data-copy-code');
has('code-text', md2, 'const a = 1;');

// ③ XSS：原始 HTML 转义
const md3 = renderMarkdown('<script>alert(1)</script>');
notHas('no-script-tag', md3, '<script>');
has('script-escaped', md3, '&lt;script&gt;');

// ④ XSS：javascript: 链接拦截
const md4 = renderMarkdown('[点击](javascript:alert(1))');
notHas('no-js-href', md4, 'href="javascript:');
has('js-href-blocked', md4, 'href="#"');
// 正常链接保留 target/rel
const md5 = renderMarkdown('[官网](https://example.com)');
has('safe-link-target', md5, 'target="_blank" rel="noopener noreferrer"');
has('safe-link-href', md5, 'href="https://example.com"');

// ⑤ 依赖缺失退化（marked 未加载）
const saved = global.marked;
global.marked = undefined;
const md6 = renderMarkdown('<b>纯文本</b>');
notHas('fallback-no-html', md6, '<b>');
has('fallback-escaped', md6, '&lt;b&gt;');
global.marked = saved;

// ⑥ 空白/空值保护
eq('empty-string', renderMarkdown(''), '');
eq('null-value', renderMarkdown(null), '');

// ⑦ 连续空行压缩（3+ 换行 → 2）
const md7 = renderMarkdown('第一段\n\n\n\n\n第二段');
notHas('no-triple-newline', md7, '\n\n\n');

// ⑧ sanitizeLink 单元
eq('sanitize-js', sanitizeLink('javascript:evil()'), '#');
eq('sanitize-data', sanitizeLink('data:text/html,x'), '#');
eq('sanitize-vb', sanitizeLink('vbscript:x'), '#');
eq('sanitize-https', sanitizeLink('https://ok.com'), 'https://ok.com');
eq('sanitize-mailto', sanitizeLink('mailto:a@b.c'), 'mailto:a@b.c');

// ⑨ escapeHtml 单元
eq('escape-quotes', escapeHtml('a"b\'c'), 'a&quot;b&#39;c');

console.log(`\nRESULT: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
