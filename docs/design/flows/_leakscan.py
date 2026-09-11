import re, sys, glob

PATTERNS = [
    (r'\bR-[A-Z]+\d+', 'ruling id'),
    (r'\bF\d{2}\b', 'feature id'),
    (r'§', 'section ref'),
    (r'\d+(?:[.,]\d+)?\s*ms\b', 'ms value'),
    (r'EWMA|α', 'algorithm param'),
    (r'recognizer v\d|transparent-v\d|v1\.4', 'engine/recognizer version'),
    (r'haptic|double-tick|thock|PRIMITIVE_', 'haptic spec'),
    (r'\bspring\b|\bripple\b|\bease[- ]?out\b|\bbounce\b|\bstagger\b|\bshimmer\b|\bletterpress\b|\bodometer\b|\bdetent\b|\bmorph', 'motion vocab'),
    (r'\bbreathes?\b|\bpulses?\b', 'ambient-motion vocab'),
    (r'\bprototype\b|\bwireframe\b|\bannotation\b|\bmock \d|\bpin-ref\b|\bopen pattern\b', 'design-doc phrase'),
    (r'\[v1\.x\]|\(v1\.x\)|\(R-|\(F\d{2}', 'inline spec tag'),
    (r'in \d+(?:\.\d+)? ?s\b|under 1 ?s|nothing to wait for', 'speed claim (R-D15)'),
    (r'one-tap|\bone tap\b|two taps|taps? to done|\d quick swipes', 'ease count (R-D15)'),
    (r'5-second|zero-?tap|zero effort', 'ease pitch (R-D15)'),
    (r'works offline|\bsaves? instantly\b|fastest fix', 'pre-emptive capability pitch (R-D15)'),
]
ALLOW = [
    ('03-checkin-f07.html', 'transparent-v'),
    ('04-weigh-in-trend-f06-f08.html', 'residuals'),
    ('04-weigh-in-trend-f06-f08.html', '3σ'),
    ('07-onboarding-f01.html', 'works offline'),
]

fails = 0
for f in sorted(glob.glob('0*.html')):
    lines = open(f).read().split('\n')
    n = len(lines)
    i = 0
    count = 0
    while i < n:
        ln = lines[i]
        if re.match(r'^    <div class="phone(?! wf)', ln):
            # find end: 4-space </div> whose next non-empty line is the caption / flowunit close
            j = i + 1
            end = None
            while j < n:
                if re.match(r'^    </div>\s*$', lines[j]):
                    k = j + 1
                    while k < n and not lines[k].strip():
                        k += 1
                    if k < n and (re.match(r'^    <div class="caption', lines[k]) or re.match(r'^  </div>', lines[k]) or re.match(r'^  <div class="flowunit', lines[k]) or re.match(r'^<div class="review', lines[k])):
                        end = j
                        break
                j += 1
            if end is None:
                print(f"  !! {f}: phone at line {i+1}: end not found")
                break
            count += 1
            chunk = ' '.join(' '.join(lines[i:end+1]).split())
            txt = re.sub(r'<[^>]+>', ' ', chunk)
            for pat, label in PATTERNS:
                for m in re.finditer(pat, txt, re.I):
                    if any(a[0] == f and a[1].lower() in m.group(0).lower() for a in ALLOW):
                        continue
                    print(f"{f} phone#{count} (line {i+1}) [{label}]: …{txt[max(0,m.start()-45):m.end()+45]}…")
                    fails += 1
            i = end
        i += 1
    print(f"  # {f}: {count} phones scanned")
print(f"\n{fails} hits")
sys.exit(1 if fails else 0)
