(() => {
  const $ = (s) => document.querySelector(s);

  const PRESETS = {
    maximum: { label: 'Maximum quality', quality: 0.96 },
    balanced: { label: 'High quality · smaller', quality: 0.90 },
    compact: { label: 'Compact file', quality: 0.84 }
  };

  function addExportControls() {
    const format = $('#exportFormat');
    const actions = document.querySelector('.publish-actions');
    if (!format || !actions) return;

    if (![...format.options].some((o) => o.value === 'pdf')) {
      format.add(new Option('PDF · Optimized', 'pdf'));
    }

    if (!$('#exportQuality')) {
      const quality = document.createElement('select');
      quality.id = 'exportQuality';
      quality.title = 'Export quality';
      quality.innerHTML = `
        <option value="maximum">Maximum quality</option>
        <option value="balanced" selected>High quality · smaller</option>
        <option value="compact">Compact file</option>`;
      format.insertAdjacentElement('afterend', quality);
    }
  }

  function download(blob, name) {
    const a = document.createElement('a');
    const url = URL.createObjectURL(blob);
    a.href = url;
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function ascii(text) {
    return new TextEncoder().encode(text);
  }

  function concat(chunks) {
    const total = chunks.reduce((n, part) => n + part.length, 0);
    const out = new Uint8Array(total);
    let offset = 0;
    for (const part of chunks) {
      out.set(part, offset);
      offset += part.length;
    }
    return out;
  }

  function jpegToPdf(jpeg, pixelWidth, pixelHeight) {
    // Use an A4-width physical scale while preserving the artwork aspect ratio.
    const pageWidth = 595.28;
    const pageHeight = pageWidth * pixelHeight / pixelWidth;
    const content = ascii(`q\n${pageWidth.toFixed(2)} 0 0 ${pageHeight.toFixed(2)} 0 0 cm\n/Im0 Do\nQ\n`);

    const objects = [
      [ascii('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n')],
      [ascii('2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n')],
      [ascii(`3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth.toFixed(2)} ${pageHeight.toFixed(2)}] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n`)],
      [ascii(`4 0 obj\n<< /Type /XObject /Subtype /Image /Width ${pixelWidth} /Height ${pixelHeight} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.length} >>\nstream\n`), jpeg, ascii('\nendstream\nendobj\n')],
      [ascii(`5 0 obj\n<< /Length ${content.length} >>\nstream\n`), content, ascii('endstream\nendobj\n')]
    ];

    const header = ascii('%PDF-1.4\n');
    const chunks = [header];
    const offsets = [0];
    let cursor = header.length;

    for (const object of objects) {
      offsets.push(cursor);
      for (const part of object) {
        chunks.push(part);
        cursor += part.length;
      }
    }

    const xrefOffset = cursor;
    let xref = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    for (let i = 1; i <= objects.length; i++) {
      xref += `${String(offsets[i]).padStart(10, '0')} 00000 n \n`;
    }
    xref += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
    chunks.push(ascii(xref));

    return new Blob([concat(chunks)], { type: 'application/pdf' });
  }

  function mergedBlob(canvas, mime, quality) {
    return new Promise((resolve, reject) => {
      // v3-elements overrides toBlob for the base canvas so all visible editor
      // elements are merged without selection handles before export.
      canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('Export failed')), mime, quality);
    });
  }

  async function exportCurrent() {
    const canvas = $('#publisherCanvas');
    const format = $('#exportFormat')?.value || 'png';
    const presetName = $('#exportQuality')?.value || 'balanced';
    const preset = PRESETS[presetName] || PRESETS.balanced;
    const template = $('#templateSelect')?.value || 'design';
    if (!canvas) return;

    const status = $('#outputStatus');
    if (status) status.textContent = `${format.toUpperCase()} · ${canvas.width} × ${canvas.height} · ${preset.label}`;

    try {
      if (format === 'pdf') {
        const jpg = await mergedBlob(canvas, 'image/jpeg', preset.quality);
        const pdf = jpegToPdf(new Uint8Array(await jpg.arrayBuffer()), canvas.width, canvas.height);
        download(pdf, `publisher-${template}-${canvas.width}x${canvas.height}-optimized.pdf`);
        return;
      }

      const mime = format === 'jpeg' ? 'image/jpeg' : format === 'webp' ? 'image/webp' : 'image/png';
      const ext = format === 'jpeg' ? 'jpg' : format;
      const blob = await mergedBlob(canvas, mime, preset.quality);
      download(blob, `publisher-${template}-${canvas.width}x${canvas.height}.${ext}`);
    } catch (error) {
      console.error(error);
      if (status) status.textContent = 'Export failed';
    }
  }

  window.addEventListener('load', () => {
    addExportControls();
    const button = $('#exportImage');
    const format = $('#exportFormat');
    const quality = $('#exportQuality');
    if (!button || !format) return;

    button.onclick = exportCurrent;
    const refreshStatus = () => {
      const canvas = $('#publisherCanvas');
      const preset = PRESETS[quality?.value || 'balanced'] || PRESETS.balanced;
      const status = $('#outputStatus');
      if (status && canvas) status.textContent = `${format.value.toUpperCase()} · ${canvas.width} × ${canvas.height} · ${preset.label}`;
    };
    format.addEventListener('change', refreshStatus);
    quality?.addEventListener('change', refreshStatus);
    refreshStatus();
  });
})();
