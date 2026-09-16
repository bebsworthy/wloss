/* Pixel operations shared by the offline browser tool and Node tests. */
(function (root) {
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  function alignment(reference, candidate, scale) {
    return { x: reference.x - candidate.x * scale, y: reference.y - candidate.y * scale };
  }
  function foreground(data, mode, cutoff, feather, color) {
    const out = new Uint8ClampedArray(data.length);
    for (let i = 0; i < data.length; i += 4) {
      const luminance = .2126 * data[i] + .7152 * data[i + 1] + .0722 * data[i + 2];
      const contrast = mode === 'light' ? 255 - luminance : luminance;
      const alpha = mode === 'none' ? 1 : clamp((contrast - cutoff) / Math.max(1, feather), 0, 1);
      out[i] = color[0]; out[i + 1] = color[1]; out[i + 2] = color[2];
      out[i + 3] = data[i + 3] * alpha;
    }
    return out;
  }
  function automaticMode(data) {
    // Median luminance avoids letting a few bright labels dominate dark screens.
    const histogram = new Uint32Array(256);
    let count = 0;
    for (let i = 0; i < data.length; i += 16) {
      if (data[i + 3] < 128) continue;
      histogram[Math.round(.2126 * data[i] + .7152 * data[i + 1] + .0722 * data[i + 2])]++;
      count++;
    }
    let total = 0;
    for (let i = 0; i < 256; i++) { total += histogram[i]; if (total >= count / 2) return i > 127 ? 'light' : 'dark'; }
    return 'dark';
  }
  const api = { alignment, foreground, automaticMode };
  root.ScreenshotCompare = api;
  if (typeof module !== 'undefined') module.exports = api;
})(globalThis);
