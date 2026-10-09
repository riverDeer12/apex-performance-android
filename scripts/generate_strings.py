# Regenerates res/values*/strings.xml from the iOS Localizable.xcstrings,
# keeping strings that only exist on Android.
import json, re, sys
xc, res = sys.argv[1], sys.argv[2]
d = json.load(open(xc))

def rn(key):
    key = ' '.join(t for t in key.split(' ') if not t.startswith('%'))
    n = re.sub('_+', '_', re.sub('[^a-z0-9_]', '_', key.lower())).strip('_')
    if n[:1].isdigit(): n = 'err_' + n
    return n

def unit(entry, lang):
    loc = entry.get('localizations', {}).get(lang)
    if not loc or 'stringUnit' not in loc: return None
    return loc['stringUnit'].get('value')

def convert(value, has_args):
    v = value.replace('%lld', '%d').replace('%@', '%s')
    v = re.sub(r'%(\d+)\$lld', r'%\1$d', v)
    v = re.sub(r'%(\d+)\$@', r'%\1$s', v)
    if has_args:
        # Several arguments must be numbered on Android.
        plain = re.findall(r'%[ds]', v)
        if len(plain) > 1:
            i = iter(range(1, 100))
            v = re.sub(r'%([ds])', lambda m: '%%%d$%s' % (next(i), m.group(1)), v)
    v = v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    v = v.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if v.startswith('@') or v.startswith('?'): v = '\\' + v
    return v

existing_re = re.compile(r'^(\s*(?:<!--.*-->\s*)?)<string name="([^"]+)"([^>]*)>(.*)</string>\s*$')

for lang, folder in [('en', 'values'), ('hr', 'values-hr'), ('it', 'values-it')]:
    path = f'{res}/{folder}/strings.xml'
    lines = open(path, encoding='utf-8').read().splitlines()
    header = [l for l in lines[:4] if not l.strip().startswith('<string')]
    old = {}
    pending_comment = None
    for l in lines:
        s = l.strip()
        if s.startswith('<!--') and s.endswith('-->') and not s.startswith('<!-- Generated'):
            pending_comment = s
            continue
        m = re.match(r'\s*<string name="([^"]+)"([^>]*)>(.*)</string>', l)
        if m:
            old[m.group(1)] = (m.group(2), m.group(3), pending_comment)
            pending_comment = None
    new = dict(old)
    for key, entry in d['strings'].items():
        name = rn(key)
        if not name: continue
        value = unit(entry, lang)
        if value is None:
            value = unit(entry, 'hr') if lang != 'hr' else None
        if value is None:
            if name in old: continue
            value = key
        if value == '' : continue
        has_args = '%' in key
        attrs = ''
        conv = convert(value, has_args)
        if not has_args and '%' in conv: attrs = ' formatted="false"'
        comment = old.get(name, (None, None, None))[2]
        new[name] = (attrs, conv, comment)
    out = header[:]
    if out and out[-1].strip() != '<resources>':
        out = [l for l in out if l.strip() != '<resources>'] + ['<resources>']
    for name in sorted(new):
        attrs, value, comment = new[name]
        if comment: out.append('    ' + comment)
        out.append(f'    <string name="{name}"{attrs}>{value}</string>')
    out.append('</resources>')
    open(path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print(folder, len(old), '->', len(new))
