# 校验 index.html 模板中引用的 JS 标识符都存在于 app.js 的 setup return 中
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$html = [IO.File]::ReadAllText((Join-Path $root 'index.html'), (New-Object System.Text.UTF8Encoding($false)))
$js   = [IO.File]::ReadAllText((Join-Path $root 'js\app.js'), (New-Object System.Text.UTF8Encoding($false)))

# 提取 setup return 段（从最后一个 "return {" 到 app.mount 之前的 "};"）
$start = $js.LastIndexOf('return {')
$mount = $js.IndexOf("app.mount('#app')")
$seg = $js.Substring($start, $mount - $start)
$end = $seg.LastIndexOf('};')
if ($start -lt 0 -or $end -lt 0) { Write-Output 'RETURN_BLOCK_NOT_FOUND'; exit 1 }
$returnBlock = $seg.Substring(0, $end)

# 从模板提取标识符：{{ expr }} / v-if / v-else / @click / :class / v-model / v-html 中的属性访问链头
$tokens = [System.Collections.Generic.HashSet[string]]::new()
foreach ($mm in [regex]::Matches($html, '\{\{\s*([A-Za-z_$][\w$]*)|@[a-z]+="([A-Za-z_$][\w$]*)' +
  '|v-if="([A-Za-z_$][\w$]*)|v-else-if="([A-Za-z_$][\w$]*)|v-for="[^"]*?\(?([A-Za-z_$][\w$]*)[,\)]' +
  '|v-model="([A-Za-z_$][\w$]*)')) {
  for ($g = 1; $g -lt $mm.Groups.Count; $g++) {
    $v = $mm.Groups[$g].Value
    if ($v) { [void]$tokens.Add($v) }
  }
}
# v-html 里的调用
foreach ($mm in [regex]::Matches($html, 'v-html="([A-Za-z_$][\w$]*)')) { [void]$tokens.Add($mm.Groups[1].Value) }
# 模板内直接出现的函数调用（内联表达式如 formatTime(s.plannedStartTime)）
foreach ($mm in [regex]::Matches($html, '([A-Za-z_$][\w$]*)\s*\(')) { [void]$tokens.Add($mm.Groups[1].Value) }

$missing = @()
foreach ($t in $tokens) {
  if ($t -in @('v-if','v-else','v-show','function','true','false','null','new','Math')) { continue }
  if (-not $returnBlock.Contains($t)) { $missing += $t }
}
if ($missing.Count -eq 0) { Write-Output "CONTRACT_OK (checked $($tokens.Count) identifiers)" }
else { Write-Output "MISSING: $($missing -join ', ')" }
