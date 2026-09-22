"""Generate a self-contained HTML report from the setup JSONL log."""
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3] / 'run-reports'
source = ROOT / 'raw/codex-setup-run.jsonl'
data = source.read_bytes()
encoding = 'utf-16' if data.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
rows = [json.loads(line) for line in data.decode(encoding).splitlines() if line.strip()]
esc = lambda value: html.escape(str(value))

def display(text):
    # Repair known console mojibake for display only; raw events stay intact.
    for broken, fixed in [('ΓÇÖ', '’'), ('ΓÇ£', '“'), ('ΓÇ¥', '”'), ('ΓÇô', '–'), ('ΓÇö', '—')]:
        text = text.replace(broken, fixed)
    return esc(text)

items = {}
for line, row in enumerate(rows, 1):
    if 'item' in row:
        item = row['item']
        record = items.setdefault(item['id'], {'lines': [], 'item': {}})
        record['lines'].append(line)
        record['item'].update(item)
commands = [r['item'] for r in items.values() if r['item']['type'] == 'command_execution']
failed = [c for c in commands if c.get('exit_code') not in (None, 0) or c.get('status') == 'failed']
usage = next((r['usage'] for r in reversed(rows) if 'usage' in r), {})
thread = next((r['thread_id'] for r in rows if 'thread_id' in r), 'Unknown')
cards = []
purposes = {
    'item_0': 'Announce workspace inspection and the intended JavaFX setup.',
    'item_1': 'Discover project instructions and existing files before scaffolding.',
    'item_2': 'Read the project versions and the setup skill filesystem requirements.',
    'item_3': 'Check the run log and installed Java/Gradle versions. Java reported 25.0.2; Gradle native initialization failed.',
    'item_5': 'Explain the selected skill, package, and intended scaffold.',
    'item_6': 'Retry Gradle with a workspace-local user home; Gradle 9.1.0 started successfully. Reading the growing run log duplicated earlier output.',
    'item_8': 'Create the application scaffold, build configuration, CI, and documentation (15 added files). Contents are not included in this event.',
    'item_9': 'Create the test directory and generate the wrapper. Plugin resolution failed before wrapper generation.',
    'item_10': 'Report scaffold progress and the remaining GitHub Pages publishing step.',
    'item_11': 'Verify required empty files and inspect daemon diagnostics. Both files were zero bytes.',
    'item_12': 'Generate the wrapper in a temporary minimal project to avoid application plugin resolution, then copy it back. Wrapper task succeeded.',
    'item_13': 'Inspect cached dependencies and test Plugin Portal connectivity. The command exited zero, but the connectivity test returned False.',
    'item_14': 'Report successful wrapper generation and the network verification blocker.',
    'item_15': 'Check required files, parse XML, and inspect the wrapper JAR. Output confirms 19 files, test directory, and XML checks, but the overall command exited 1; its cause is not established by the log.',
    'item_16': 'Update wrapper properties after inspection. The event records success but does not include a diff, so the exact change is unavailable.',
    'item_17': 'Summarize delivered files, the unresolved build verification, and the launch command.',
}
for index, record in enumerate(items.values(), 1):
    item = record['item']
    kind = item['type']
    failure = item.get('exit_code') not in (None, 0) or item.get('status') == 'failed'
    title = kind.replace('_', ' ').title()
    purpose = purposes.get(item['id'], 'Look up JavaFX plugin and Shadow compatibility information.' if '44154552' in item['id'] else 'Consult the SE-EDU Java conventions referenced by the setup skill.')
    body = '<p class="muted"><strong>Purpose / observed result:</strong> ' + esc(purpose) + '</p>'
    if kind == 'agent_message':
        body += '<div class="message">' + display(item.get('text', '')) + '</div>'
    elif kind == 'command_execution':
        body += '<h3>Command</h3><pre>' + display(item.get('command', '')) + '</pre>'
        body += '<details' + (' open' if failure else '') + '><summary>Command output · exit ' + esc(item.get('exit_code')) + '</summary><pre>' + display(item.get('aggregated_output', '') or '(No output)') + '</pre></details>'
    elif kind == 'file_change':
        body += '<table><thead><tr><th>Action</th><th>Path</th></tr></thead><tbody>' + ''.join('<tr><td>' + esc(c['kind']) + '</td><td><code>' + esc(c['path']) + '</code></td></tr>' for c in item.get('changes', [])) + '</tbody></table>'
    elif kind == 'web_search':
        body += '<p>' + display(item.get('query', '')) + '</p><p class="muted">Search event completed; retrieved source contents and their accuracy cannot be verified from this log.</p>'
    else:
        body += '<pre>' + esc(json.dumps(item, ensure_ascii=False, indent=2)) + '</pre>'
    status = item.get('status', 'recorded')
    raw = [rows[n - 1] for n in record['lines']]
    body += '<details class="raw"><summary>Raw events · lines ' + ', '.join(map(str, record['lines'])) + '</summary><pre>' + esc(json.dumps(raw, ensure_ascii=False, indent=2)) + '</pre></details>'
    cards.append(f'<article data-kind="{esc(kind)}" data-failed="{str(failure).lower()}"><header><h2><span>{index:02}</span> {title}</h2><b class="{"bad" if failure else "badge"}">{esc(status)}</b></header>{body}</article>')

page = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Codex setup run report</title>
<style>
:root{color-scheme:light;--ink:#192b3c;--muted:#526477;--line:#dce3eb}*{box-sizing:border-box}body{margin:0;background:#f3f6fa;color:var(--ink);font:16px/1.6 system-ui,sans-serif}main{max-width:1120px;margin:auto;padding:44px 24px}h1{font-size:36px;margin:0}h2{font-size:19px;margin:0}h3{font-size:14px;margin-bottom:6px}p{margin:10px 0} .muted,small{color:var(--muted)}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:24px 0}.stat,article,.overview{background:white;border:1px solid var(--line);border-radius:12px;padding:20px}.stat strong{display:block;font-size:28px}.stat span{color:var(--muted);font-size:14px}.overview{border-left:5px solid #bc7a19}.controls{display:flex;gap:12px;flex-wrap:wrap;margin:26px 0 12px}input,select,button{font:inherit;padding:9px 12px;border:1px solid #aebccb;border-radius:7px;background:white;color:var(--ink)}input{flex:1;min-width:200px}button,summary{cursor:pointer}article{margin:16px 0}article header{display:flex;justify-content:space-between;gap:12px;align-items:center}h2 span{color:#73869a;margin-right:8px}.badge,.bad{font-size:12px;padding:3px 9px;border-radius:20px;background:#e8f3ef;color:#23604c}.bad{background:#ffe7e4;color:#992f24}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f5f7fa;padding:14px;border-radius:7px;font:13px/1.6 ui-monospace,Consolas,monospace;max-height:600px;overflow:auto}.message{white-space:pre-wrap;overflow-wrap:anywhere;margin-top:16px}details{margin-top:14px}summary{color:#315d89}.raw{font-size:13px}table{width:100%;border-collapse:collapse;margin-top:16px;font-size:14px}td,th{text-align:left;padding:8px;border-bottom:1px solid var(--line);overflow-wrap:anywhere}td code{word-break:break-word}footer{margin-top:24px;font-size:13px;color:var(--muted)}[hidden]{display:none!important}@media(max-width:650px){main{padding:24px 14px}.stats{grid-template-columns:repeat(2,1fr)}h1{font-size:28px}article header{align-items:flex-start}article{padding:15px}}@media print{.controls{display:none}body{background:white}main{max-width:none;padding:0}pre{max-height:none}article{break-inside:avoid}}
</style></head><body><main><p class="muted">EXECUTION REPORT</p><h1>Codex setup run</h1><p class="muted">Source: codex-setup-run.jsonl · Thread: THREAD</p>
<div class="stats">STATS</div>
<section class="overview"><h2>Run outcome</h2><p>The turn completed. The agent reported creating the HotShop JavaFX scaffold with Java 25 and Gradle 9.1.0. Wrapper generation succeeded; build verification was blocked by an OpenJFX plugin download failure.</p><p class="muted">This summarizes the recorded run, not a new verification of the project.</p></section>
<p class="muted">USAGE</p>
<div class="controls"><input id="search" aria-label="Search report" placeholder="Search messages, commands, paths, output…"><select id="filter" aria-label="Filter entries"><option value="all">All entries</option><option value="agent_message">Agent messages</option><option value="command_execution">Commands</option><option value="file_change">File changes</option><option value="web_search">Web searches</option><option value="failed">Failures</option></select><button id="expand">Expand details</button><button id="collapse">Collapse details</button></div><p id="count" class="muted" aria-live="polite"></p>
CARDS
<details><summary>Complete original event stream (24 events)</summary><pre>RAW</pre></details>
<footer>Start/completion events are grouped by item ID. No timestamps were recorded, so duration is unavailable. Known console character corruption is repaired in display text; raw event values are preserved. This report works offline.</footer></main>
<script>
const articles=[...document.querySelectorAll('article')],search=document.getElementById('search'),filter=document.getElementById('filter');function update(){let n=0;for(const a of articles){a.hidden=!(a.textContent.toLowerCase().includes(search.value.toLowerCase())&&(filter.value==='all'||a.dataset.kind===filter.value||(filter.value==='failed'&&a.dataset.failed==='true')));if(!a.hidden)n++}document.getElementById('count').textContent=`${n} of ${articles.length} entries shown`}search.addEventListener('input',update);filter.addEventListener('change',update);for(const [id,open] of [['expand',true],['collapse',false]])document.getElementById(id).addEventListener('click',()=>articles.filter(a=>!a.hidden).forEach(a=>a.querySelectorAll('details').forEach(d=>d.open=open)));update();
</script></body></html>'''
stats = [(len(rows), 'Raw events'), (len(items), 'Activity entries'), (len(commands), 'Commands'), (len(failed), 'Failed commands')]
values = {'THREAD': esc(thread), 'STATS': ''.join(f'<div class="stat"><strong>{n}</strong><span>{label}</span></div>' for n, label in stats), 'USAGE': ' · '.join(esc(k.replace('_', ' ').capitalize()) + ': ' + f'{v:,}' for k, v in usage.items()), 'CARDS': '\n'.join(cards), 'RAW': esc(json.dumps(rows, ensure_ascii=False, indent=2))}
import re
page = re.sub(r'THREAD|STATS|USAGE|CARDS|RAW', lambda m: values[m.group()], page)
page = page.replace('Complete original event stream (24 events)', f'Complete original event stream ({len(rows)} events)')
overview = '''<section class="overview"><h2>User prompt and task</h2><p>The original user prompt is absent from this JSONL. Based on the agent messages, the inferred request was to use <code>-javafx-project</code> to scaffold HotShop with Java 25 and Gradle 9.1.0. This is a reconstruction, not a verbatim prompt.</p><h2>Goal assessment: partially achieved</h2><p>The log records scaffold creation and successful wrapper generation. It does not demonstrate a successful compilation, JUnit run, Checkstyle run, packaging, application launch, CI run, or published website. The final structural check printed successful file and XML checks but returned exit code 1.</p><h2>Workflow improvements</h2><ul><li>Restore access to the Gradle Plugin Portal, then run the wrapper build, test, Checkstyle, and packaging tasks and record their results.</li><li>Investigate the final inspection command's nonzero exit separately; do not classify the entire command as passed based on its printed message.</li><li>Clarify the setup skill's test-file allow-list so actual JUnit tests can accompany its CI requirement, and define whether publishing Pages is part of completion.</li><li>Record the original prompt and timestamps alongside events, and avoid reading the growing JSONL into itself to reduce duplicated context.</li><li>Keep the workspace-local Gradle user home workaround explicit when reproducing this environment.</li></ul><p class="muted">Purposes below are brief interpretations of recorded actions, not private reasoning. Token counts are reported totals; cached input is a subset of input and reasoning output is a subset of output, not additional usage.</p></section>'''
page = page.replace('<div class="controls">', overview + '<div class="controls">')
css = re.search(r'<style>(.*?)</style>', page, re.S).group(1)
for old, new in [('color-scheme:light','color-scheme:dark'),('#192b3c','#e6edf7'),('#526477','#a7b5c9'),('#dce3eb','#30415a'),('#f3f6fa','#0c1422'),('background:white','background:#152237'),('#f5f7fa','#0e192a'),('#315d89','#8bc7ff'),('#aebccb','#405670')]:
    css = css.replace(old, new)
(ROOT / 'report.css').write_text(css, encoding='utf-8')
page = re.sub(r'<style>.*?</style>', '<link rel="stylesheet" href="../report.css">', page, flags=re.S)
target = ROOT / 'html/codex-setup-run.html'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(page, encoding='utf-8')
print(f'Created {target}: {len(rows)} events, {len(items)} entries, {len(failed)} failed commands')
