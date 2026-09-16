#!/usr/bin/env python3
"""Capture an HTML mockup and Android screen, align and export comparisons offline."""
import argparse
import math
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent


def run(argv, **kwargs):
    result = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60, **kwargs)
    if result.returncode:
        raise RuntimeError(f"{Path(argv[0]).name}: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout


def android_anchor(xml, text):
    tree = ET.fromstring(xml[xml.index('<?xml'):xml.index('</hierarchy>') + 12])
    nodes = [n for n in tree.iter('node') if text in (n.get('text'), n.get('content-desc'))]
    if len(nodes) != 1:
        raise ValueError(f'Android anchor {text!r} matched {len(nodes)} nodes; choose a unique visible label.')
    bounds = list(map(int, re.findall(r'\d+', nodes[0].get('bounds', ''))))
    if len(bounds) != 4 or bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
        raise ValueError('Android anchor has no visible bounds')
    return {'x': bounds[0], 'y': bounds[1], 'width': bounds[2]-bounds[0], 'height': bounds[3]-bounds[1]}


def glyph_anchor(image_path, region):
    """Find the first contiguous ink-column group, independent of text-box leading."""
    with Image.open(image_path) as source:
        left, top = math.floor(region['x']), math.floor(region['y'])
        crop = source.convert('RGB').crop((left, top,
            math.ceil(region['x']+region['width']), math.ceil(region['y']+region['height'])))
    # Background is estimated from the perimeter, not the glyph-heavy center.
    pixels = crop.load()
    border = [pixels[x, y] for x in range(crop.width) for y in {0, crop.height-1}]
    border += [pixels[x, y] for y in range(crop.height) for x in {0, crop.width-1}]
    bg = tuple(sorted(p[c] for p in border)[len(border)//2] for c in range(3))
    ink = [(x,y) for x in range(crop.width) for y in range(crop.height)
           if max(abs(pixels[x,y][c]-bg[c]) for c in range(3)) >= 80]
    if not ink:
        raise ValueError('No visible glyph in anchor region; choose a high-contrast text anchor')
    columns = {x for x,y in ink}
    first = min(columns)
    last = first
    while last+1 in columns:
        last += 1
    ys = [y for x,y in ink if first <= x <= last]
    if last-first < 2 or max(ys)-min(ys) < 2:
        raise ValueError('Anchor ink is too small to identify reliably')
    return {'x':left+first,'y':top+min(ys),'width':last-first+1,'height':max(ys)-min(ys)+1}


def placement(reference_size, candidate_size, ref_anchor=None, app_anchor=None):
    scale = reference_size[0] / candidate_size[0]
    dx = ref_anchor['x'] - app_anchor['x'] * scale if ref_anchor else 0
    dy = ref_anchor['y'] - app_anchor['y'] * scale if ref_anchor else 0
    return scale, dx, dy


def generate(reference, candidate, out, ref_anchor=None, app_anchor=None, cutoff=45, softness=90, mode='auto'):
    ref = Image.open(reference).convert('RGBA')
    app = Image.open(candidate).convert('RGBA')
    scale, dx, dy = placement(ref.size, app.size, ref_anchor, app_anchor)
    # An inverse affine transform keeps fractional alignment rather than rounding offsets.
    aligned = app.transform(ref.size, Image.Transform.AFFINE,
                            (1 / scale, 0, -dx / scale, 0, 1 / scale, -dy / scale),
                            resample=Image.Resampling.BICUBIC)
    original_luma = app.convert('L')
    if mode == 'auto':
        histogram = original_luma.histogram()
        count = 0
        for median, n in enumerate(histogram):
            count += n
            if count >= app.width * app.height / 2:
                break
        mode = 'light' if median > 127 else 'dark'
    luma = aligned.convert('L')
    contrast = ImageChops.invert(luma) if mode == 'light' else luma
    mask = contrast.point([round(max(0, min(1, (v-cutoff)/softness))*255) for v in range(256)])
    mask = ImageChops.multiply(mask, aligned.getchannel('A'))
    red = Image.new('RGBA', ref.size, (255, 37, 37, 0))
    red.putalpha(mask)
    Image.alpha_composite(ref, red).save(out / 'overlay.png')
    ref.save(out / 'reference.png')
    aligned.save(out / 'aligned.png')
    blend = aligned.copy()
    blend.putalpha(aligned.getchannel('A').point(lambda a: round(a*.5)))
    Image.alpha_composite(ref, blend).save(out / 'blend.png')
    # Only compare pixels actually covered by the aligned candidate.
    difference = ImageChops.difference(ref.convert('RGB'), aligned.convert('RGB'))
    difference = Image.composite(difference, Image.new('RGB', ref.size), aligned.getchannel('A'))
    difference.save(out / 'difference.png')
    side = Image.new('RGB', (ref.width*2, ref.height), '#11151a')
    side.paste(ref.convert('RGB'), (0, 0))
    side.paste(aligned, (ref.width, 0), aligned)
    side.save(out / 'side-by-side.png')
    return {'referenceSize':ref.size,'candidateSize':app.size,'scale':scale,
            'offset':{'x':dx,'y':dy},'backgroundMode':mode,'referenceAnchor':ref_anchor,
            'candidateAnchor':app_anchor,'alignment':'anchor top-left' if ref_anchor else 'screen origin'}


def parser():
    p = argparse.ArgumentParser(description=__doc__, epilog='No prompts. Exit 0: generated; 1: capture/processing failure; 2: invalid arguments.')
    source = p.add_mutually_exclusive_group(required=True)
    source.add_argument('--mockup', type=Path, help='Local HTML to capture in headless Chromium')
    source.add_argument('--reference', type=Path, help='Existing reference image (requires --candidate)')
    p.add_argument('--candidate', type=Path, help='Existing candidate image; otherwise capture Android with adb')
    p.add_argument('--selector', default='body', help='Unique mockup screen element to capture')
    p.add_argument('--anchor-selector', help='Unique DOM anchor; paired with --anchor-text')
    p.add_argument('--anchor-mode', choices=['glyph','box'], default='glyph', help='Anchor visible first glyph (default) or text-box corner')
    p.add_argument('--anchor-text', help='Exact visible Android text or content description')
    p.add_argument('--prepare-script', type=Path, help='Trusted local JS to set browser fixture data before capture')
    p.add_argument('--click', action='append', default=[], help='CSS selector to click before capture; repeatable')
    p.add_argument('--viewport-width', type=int, default=900)
    p.add_argument('--viewport-height', type=int, default=1250)
    p.add_argument('--serial', help='Explicit adb serial; required for Android capture')
    p.add_argument('--adb', default=shutil.which('adb') or str(Path.home()/'Library/Android/sdk/platform-tools/adb'))
    p.add_argument('--node', default=shutil.which('node') or 'node')
    p.add_argument('--out', type=Path, required=True, help='New output directory (refuses to overwrite)')
    p.add_argument('--background', choices=['auto','dark','light'], default='auto')
    p.add_argument('--cutoff', type=int, default=45)
    p.add_argument('--softness', type=int, default=90)
    p.add_argument('--json', action='store_true', help='Machine-readable result/error envelope')
    return p


def main():
    p = parser()
    args = p.parse_args()
    if bool(args.anchor_selector) != bool(args.anchor_text):
        p.error('--anchor-selector and --anchor-text must be supplied together')
    if args.anchor_text and (args.candidate or not args.mockup):
        p.error('Automatic anchors require a live mockup and Android capture')
    if args.reference and not args.candidate:
        p.error('--reference requires --candidate')
    if not args.candidate and not args.serial:
        p.error('--serial is required for Android capture')
    if not 0 <= args.cutoff <= 254 or not 1 <= args.softness <= 255:
        p.error('cutoff must be 0–254; softness 1–255')
    if args.viewport_width < 1 or args.viewport_height < 1:
        p.error('viewport dimensions must be positive')
    try:
        out = args.out.resolve()
        out.mkdir(parents=True, exist_ok=False)
        meta = {'schemaVersion':1, 'warnings':[
            'Screen widths are normalized uniformly; desktop chrome must be excluded.',
            'Text-box anchors are not glyph baselines. Data/state equality is not inferred.',
            'Background mask is contrast-based, not OCR. Difference includes rasterization changes.',
            'Candidate overflow is clipped to the reference. Difference excludes uncovered pixels.']}
        ref_anchor = app_anchor = None
        candidate = args.candidate
        if not candidate:
            adb = [args.adb, '-s', args.serial]
            xml = run(adb+['exec-out','uiautomator','dump','/dev/tty']).decode()
            (out/'android.xml').write_text(xml)
            if args.anchor_text:
                app_anchor = android_anchor(xml, args.anchor_text)
            candidate = out/'candidate.png'
            candidate.write_bytes(run(adb+['exec-out','screencap','-p']))
            meta['android'] = {'serial':args.serial,'anchorText':args.anchor_text}
        else:
            shutil.copyfile(candidate, out/'candidate.png')
        reference = args.reference
        if args.mockup:
            reference = out/'mockup.png'
            env = os.environ.copy()
            # Codex desktop's bundled runtime; normal NODE_PATH/npm resolution also works.
            bundled = Path.home()/'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules'
            if not env.get('NODE_PATH') and bundled.is_dir():
                env['NODE_PATH'] = str(bundled)
            options = {'url':args.mockup.resolve().as_uri(),'selector':args.selector,
                       'anchorSelector':args.anchor_selector,'clicks':args.click,
                       'prepareScript':args.prepare_script.read_text() if args.prepare_script else None,
                       'viewportWidth':args.viewport_width,'viewportHeight':args.viewport_height,
                       'output':str(reference),'metadata':str(out/'browser.json')}
            run([args.node,str(HERE/'capture.cjs')],input=json.dumps(options).encode(),env=env)
            browser = json.loads((out/'browser.json').read_text())
            meta['browser'] = browser
            ref_anchor = browser['anchor']
        if ref_anchor and args.anchor_mode == 'glyph':
            meta['anchorRegions'] = {'reference':ref_anchor,'candidate':app_anchor}
            ref_anchor = glyph_anchor(reference,ref_anchor)
            app_anchor = glyph_anchor(candidate,app_anchor)
        meta.update(generate(reference,candidate,out,ref_anchor,app_anchor,args.cutoff,args.softness,args.background))
        if ref_anchor:
            meta['alignment'] = 'first visible glyph top-left' if args.anchor_mode == 'glyph' else 'text-box top-left'
        meta['warnings'][1] = 'Glyph anchors align visible ink, not font size. Data/state equality is not inferred.' if args.anchor_mode == 'glyph' else meta['warnings'][1]
        meta['outputs'] = {name:str(out/name) for name in ['reference.png','candidate.png','aligned.png','overlay.png','blend.png','difference.png','side-by-side.png']}
        (out/'report.json').write_text(json.dumps(meta,indent=2)+'\n')
        (out/'index.html').write_text('''<!doctype html><meta charset="utf-8"><title>Generated screenshot comparison</title>
<style>body{font:15px system-ui;background:#20252b;color:#eee;margin:24px}img{max-width:100%;height:auto}a{color:#9de3cf}</style>
<h1>Automated screenshot comparison</h1><p>Same screen width; alignment details and limitations in <a href="report.json">report.json</a>.</p>
<h2>Red foreground overlay</h2><img src="overlay.png"><h2>Side by side</h2><img src="side-by-side.png">
<h2>Pixel difference</h2><img src="difference.png">''')
        result = {'ok':True,'schemaVersion':1,'directory':str(out),'report':str(out/'report.json'),'outputs':meta['outputs']}
        print(json.dumps(result) if args.json else f"Comparison generated: {out / 'index.html'}\nAlignment: {meta['alignment']}; scale {meta['scale']:.6f}")
        return 0
    except Exception as e:
        if args.json:
            print(json.dumps({'ok':False,'schemaVersion':1,'error':{'code':'comparison_failed','message':str(e)}}))
        else:
            print(f'error: {e}',file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
