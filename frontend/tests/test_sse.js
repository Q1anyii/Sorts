// splitFrames 单元测试：从 app.js 提取函数本体，覆盖
// ① 一包多帧 ② 跨包半帧 ③ \r\n 行尾 ④ event/data 解析 ⑤ 流末残帧兜底
const fs = require('fs');
const path = require('path');
const src = fs.readFileSync(path.join(__dirname, '..', 'js', 'app.js'), 'utf8');
const m = src.match(/function splitFrames\(buffer\) \{[\s\S]*?\n\}/);
if (!m) { console.error('splitFrames NOT FOUND'); process.exit(1); }
const fn = new Function('return ' + m[0])();
const decode = (s) => Buffer.from(s, 'utf8').toString('utf8');

let pass = 0, fail = 0;
function eq(name, got, want) {
  const g = JSON.stringify(got), w = JSON.stringify(want);
  if (g === w) { pass++; console.log('PASS', name); }
  else { fail++; console.log('FAIL', name, '\n  got ', g, '\n  want', w); }
}

// ① 一包多帧：同 chunk 内两条完整帧
let r = fn('event: delta\ndata: {"content":"你"}\n\nevent: delta\ndata: {"content":"好"}\n\n');
eq('one-chunk-two-frames', r.frames.map(f => [f.event, JSON.parse(f.data).content]), [['delta','你'],['delta','好']]);

// ② 跨包半帧：帧被截断，rest 必须保留待拼接
let r2 = fn('event: delta\ndata: {"content":"');
eq('half-frame-rest', r2.frames.length, 0);
eq('half-frame-rest-kept', r2.rest.includes('data: {"content":"'), true);
// 拼接续包后完整出帧
let r3 = fn(r2.rest + 'abc"}\n\n');
eq('half-frame-joined', r3.frames.map(f => JSON.parse(f.data).content), ['abc']);

// ③ \r\n 行尾
let r4 = fn('event: delta\r\ndata: {"content":"X"}\r\n\r\n');
eq('crlf-frame', r4.frames.map(f => f.event), ['delta']);

// ④ event 缺省为 message + 多行 data 拼接
let r5 = fn('data: {"a":1}\ndata: {"b":2}\n\n');
eq('default-event', r5.frames[0].event, 'message');
eq('multi-data-joined', r5.frames[0].data, '{"a":1}\n{"b":2}');

// ⑤ 流末残帧（无空行结尾）：生产代码会补 '\n\n' 再冲一次
let r6 = fn('event: done\ndata: {"ok":true}');
eq('tail-frag-no-close', r6.frames.length, 0);
let r7 = fn(r6.rest + '\n\n');
eq('tail-frag-flush', r7.frames[0].event, 'done');

// ⑥ done 事件（真实后端 payload）
let r8 = fn('event: done\ndata: {"conversationId":"abc123","reply":"完成","suggestedActions":[{"type":"CREATE_SCHEDULE","label":"创建日程"}]}\n\n');
eq('done-payload', r8.frames[0].event, 'done');
eq('done-conversationId', JSON.parse(r8.frames[0].data).conversationId, 'abc123');

console.log(`\nRESULT: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
