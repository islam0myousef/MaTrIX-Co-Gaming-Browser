(function () {
  if (window.__mxClean) return;
  window.__mxClean = true;

  var KW = /cookie|consent|gdpr|onetrust|cmp|privacy|truste|didomi|quantcast|sp_message|fc-consent|popup|modal|overlay|backdrop|lightbox|interstitial|paywall|adblock|newsletter|subscribe|watch.?ad|reward|offer/i;
  var ACCEPT_REJECT = /^(reject all|reject|decline|deny|refuse|only necessary|necessary only|رفض|رفض الكل|ارفض)/i;
  var CLOSE = /^(close|dismiss|no thanks|not now|skip|continue|×|x|✕|إغلاق|اغلاق|لا شكرا|لا, شكرا|تخطي)$/i;

  function visible(el) {
    var r = el.getBoundingClientRect(), s = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden';
  }
  function clickText(re) {
    var els = document.querySelectorAll('button,a,[role=button],input[type=button],input[type=submit]');
    for (var i = 0; i < els.length; i++) {
      var t = (els[i].innerText || els[i].value || els[i].getAttribute('aria-label') || '').trim();
      if (t && t.length < 40 && re.test(t) && visible(els[i])) { try { els[i].click(); return true; } catch (e) {} }
    }
    return false;
  }
  function isOverlay(el) {
    if (el === document.body || el === document.documentElement) return false;
    var s = getComputedStyle(el);
    if (s.position !== 'fixed' && s.position !== 'sticky' && s.position !== 'absolute') return false;
    if (!visible(el)) return false;
    var r = el.getBoundingClientRect(), vw = innerWidth, vh = innerHeight;
    var z = parseInt(s.zIndex, 10) || 0;
    var big = r.width >= vw * 0.6 && r.height >= vh * 0.25;
    var full = r.width >= vw * 0.9 && r.height >= vh * 0.9;
    var label = (el.id + ' ' + el.className + ' ' + (el.getAttribute('role') || '') + ' ' + (el.getAttribute('aria-modal') || '')).toString();
    var named = KW.test(label) || el.getAttribute('aria-modal') === 'true' || el.getAttribute('role') === 'dialog' || el.getAttribute('role') === 'alertdialog';
    if (s.position === 'absolute' && !(full && z > 10)) return false;
    if (full && z > 0) return true;               // full-screen cover
    if (big && z > 100 && named) return true;     // big named dialog
    if (named && z > 100 && s.position === 'fixed' && (r.top <= 5 || r.bottom >= vh - 5)) return true; // cookie bar
    return false;
  }
  function unlock() {
    [document.documentElement, document.body].forEach(function (e) {
      if (!e) return;
      e.style.setProperty('overflow', 'auto', 'important');
      e.style.setProperty('position', 'static', 'important');
      e.style.setProperty('height', 'auto', 'important');
      e.style.setProperty('pointer-events', 'auto', 'important');
      e.classList.forEach(function (c) { if (/no-?scroll|modal-open|overflow-hidden|scroll-lock|noscroll|locked/i.test(c)) e.classList.remove(c); });
    });
  }
  function sweep() {
    var removed = 0;
    // 1) try rejecting cookies politely first
    clickText(ACCEPT_REJECT);
    // 2) remove overlays
    var all = document.body ? document.body.querySelectorAll('*') : [];
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.dataset && el.dataset.mxSeen) continue;
      if (isOverlay(el)) {
        // optional-ad dialogs: try the close/skip button before removing
        if (/watch|ad\b|reward|إعلان/i.test(el.innerText || '')) {
          var b = el.querySelectorAll('button,a,[role=button]');
          for (var j = 0; j < b.length; j++) { if (CLOSE.test((b[j].innerText || b[j].getAttribute('aria-label') || '').trim())) { try { b[j].click(); } catch (e) {} break; } }
        }
        el.remove(); removed++;
      }
    }
    // 3) known third-party ad/consent iframes
    document.querySelectorAll('iframe').forEach(function (f) {
      if (/doubleclick|googlesyndication|adservice|adnxs|taboola|outbrain|consent|onetrust|didomi|sp_message/i.test(f.src || '') && isOverlay(f)) { f.remove(); removed++; }
    });
    if (removed || true) unlock();
    return removed;
  }
  var timer = null;
  function schedule() { if (timer) return; timer = setTimeout(function () { timer = null; try { sweep(); } catch (e) {} }, 150); }
  try { sweep(); } catch (e) {}
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] });
  // stop watching after 60s to save battery
  setTimeout(function () { try { sweep(); } catch (e) {} }, 60000);
})();
