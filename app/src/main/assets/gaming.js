(function () {
  if (window.__mxGaming) return;
  window.__mxGaming = true;
  function stop(v) {
    try {
      if (v.__mxStopped) return;
      v.__mxStopped = true;
      if (!v.paused && !v.__mxUserPlayed) { v.pause(); }
      v.autoplay = false;
      v.removeAttribute('autoplay');
      v.addEventListener('play', function () {
        if (!v.__mxUserPlayed && !(navigator.userActivation && navigator.userActivation.isActive)) { v.pause(); }
      }, true);
      v.addEventListener('click', function () { v.__mxUserPlayed = true; }, true);
      v.addEventListener('touchend', function () { v.__mxUserPlayed = true; }, true);
    } catch (e) {}
  }
  function scan() { var l = document.getElementsByTagName('video'); for (var i = 0; i < l.length; i++) stop(l[i]); }
  scan();
  try { new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true }); } catch (e) {}
})();
