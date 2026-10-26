/* Runs exclusively on insta4dl.com via origin-scoped AndroidX WebView bridge.
   Streams blob downloads to Android Downloads with acknowledgments (avoids one huge base64 payload). */
(function () {
  'use strict';
  if (window.__insta4dlBridgeInstalled || !window.Insta4DLNative) return;
  window.__insta4dlBridgeInstalled = true;

  var waiting = new Map();
  var active = new Set();
  var seq = 0;
  var maxBytes = 700 * 1024 * 1024;
  var timeoutMs = 30000;

  window.Insta4DLNative.onmessage = function (event) {
    var result;
    try { result = JSON.parse(event.data); } catch (_) { return; }
    var pending = waiting.get(result.seq);
    if (!pending) return;
    clearTimeout(pending.timer);
    waiting.delete(result.seq);
    if (result.ok) pending.resolve();
    else pending.reject(new Error(result.error || 'Android download failed'));
  };

  function send(payload) {
    return new Promise(function (resolve, reject) {
      var key = ++seq;
      var timer = setTimeout(function () {
        waiting.delete(key);
        reject(new Error('Android download timed out'));
      }, timeoutMs);
      waiting.set(key, { resolve: resolve, reject: reject, timer: timer });
      payload.seq = key;
      try { window.Insta4DLNative.postMessage(JSON.stringify(payload)); }
      catch (error) { clearTimeout(timer); waiting.delete(key); reject(error); }
    });
  }

  function base64(bytes) {
    var binary = '';
    for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  }

  async function saveBlob(url, name) {
    if (!url || !url.startsWith('blob:') || active.has(url)) return;
    if (active.size >= 3) { alert('Please finish the current downloads first.'); return; }
    active.add(url);
    var id = 'blob_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10);
    var started = false;
    try {
      var response = await fetch(url);
      if (!response.ok) throw new Error('Blob could not be opened');
      var blob = await response.blob();
      if (!blob.size || blob.size > maxBytes) throw new Error('Unsupported file size');
      await send({ op: 'start', id: id, name: name || 'insta4dl-download',
        mime: blob.type || 'application/octet-stream', size: blob.size });
      started = true;
      var chunkSize = 64 * 1024;
      for (var offset = 0; offset < blob.size; offset += chunkSize) {
        var arrayBuffer = await blob.slice(offset, offset + chunkSize).arrayBuffer();
        await send({ op: 'chunk', id: id, base64: base64(new Uint8Array(arrayBuffer)) });
      }
      await send({ op: 'end', id: id });
    } catch (error) {
      if (started) { try { await send({ op: 'abort', id: id }); } catch (_) {} }
      alert('Download failed: ' + error.message);
    } finally {
      active.delete(url);
    }
  }

  window.__insta4dlSaveBlobURL = saveBlob;

  document.addEventListener('click', function (event) {
    var target = event.target;
    var anchor = target && target.closest ? target.closest('a[href^="blob:"]') : null;
    if (!anchor) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    saveBlob(anchor.href, anchor.getAttribute('download'));
  }, true);

  var nativeAnchorClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.href && this.href.startsWith('blob:')) {
      saveBlob(this.href, this.getAttribute('download'));
      return;
    }
    return nativeAnchorClick.apply(this, arguments);
  };
})();
