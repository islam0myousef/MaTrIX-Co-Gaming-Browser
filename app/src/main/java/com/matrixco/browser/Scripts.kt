package com.matrixco.browser

import org.json.JSONObject

/** سكريبتات تُحقن داخل الصفحات (تايمرات، حجب، تجسس، قراءة، فيديو ...) */
object Scripts {

    private val ADS_SEL = listOf(
        ".adsbygoogle", "ins.adsbygoogle", "[id^='google_ads_']", "[id^='div-gpt-ad']", "[data-ad-slot]",
        "[data-ad-client]", "iframe[src*='doubleclick.net']", "iframe[src*='googlesyndication']",
        "iframe[id^='google_ads']", "[class~='ad-banner']", "[class~='ad-container']", "[class~='ad-slot']",
        "[class~='ads-container']", "[class~='advertisement']", "[class~='sponsored-ad']", "[id~='ad-banner']",
        "[id='ad-container']", "[aria-label='Advertisement']", "[aria-label='advertisement']",
        ".taboola", "[id^='taboola-']", "[id^='outbrain']", "[class*='OUTBRAIN']", "[id^='mgid']", ".mgid_3rd_party",
        "a[href*='doubleclick.net/']", "a[href*='googleadservices.com']", "iframe[src*='adsterra']",
        "iframe[src*='popads']", "iframe[src*='exoclick']", "[class*='popunder']",
        "ytd-ad-slot-renderer", "ytd-promoted-sparkles-web-renderer", "#masthead-ad", "ytd-display-ad-renderer",
        "ytd-in-feed-ad-layout-renderer", "ytm-promoted-sparkles-web-renderer", "ytm-companion-ad-renderer",
        ".ytp-ad-overlay-container", "ytd-banner-promo-renderer", "ytd-statement-banner-renderer"
    ).joinToString(",")

    private val ANNOY_SEL = listOf(
        "#onetrust-consent-sdk", "#onetrust-banner-sdk", ".cookie-consent", "#cookie-banner", "#cookieConsent",
        ".cc-window", ".cc-banner", "#CybotCookiebotDialog", "#cmpbox", "#cmpwrapper", ".fc-consent-root",
        "[class*='cookie-banner']", "[id*='cookie-banner']", "[class*='cookie-notice']",
        "[class*='newsletter-popup']", "[class*='push-notification-prompt']", "[id*='notification-prompt']"
    ).joinToString(",")

    /**
     * Isolated time acceleration engine.
     * It is installed independently from fingerprint protection and remains at
     * 1x speed until the user enables a supported skip mode.
     */
    val TIMER_ENGINE = """
(function(){
  'use strict';
  var W=window;
  if(W.__mxTimer) return;

  var NativeDate=W.Date;
  var nativeSetTimeout=W.setTimeout.bind(W);
  var nativeClearTimeout=W.clearTimeout.bind(W);
  var nativePerformanceNow=(W.performance&&W.performance.now)
    ? W.performance.now.bind(W.performance) : null;

  var speed=1;
  var realBase=NativeDate.now();
  var virtualBase=realBase;
  var perfRealBase=nativePerformanceNow?nativePerformanceNow():0;
  var perfVirtualBase=perfRealBase;
  var timers=Object.create(null);
  var nextId=910000;

  function realNow(){ return NativeDate.now(); }
  function virtualNow(){ return virtualBase+(realNow()-realBase)*speed; }
  function virtualPerformanceNow(){
    if(!nativePerformanceNow) return 0;
    return perfVirtualBase+(nativePerformanceNow()-perfRealBase)*speed;
  }

  function schedule(id){
    var item=timers[id];
    if(!item) return;
    var delay=Math.max(0,(item.due-virtualNow())/speed);
    item.nativeId=nativeSetTimeout(function(){ fire(id); },delay);
  }

  function fire(id){
    var item=timers[id];
    if(!item) return;
    if(item.interval){
      item.due=virtualNow()+item.delay;
      schedule(id);
    }else{
      delete timers[id];
    }
    try{
      item.callback.apply(W,item.args);
    }catch(error){
      nativeSetTimeout(function(){ throw error; },0);
    }
  }

  function makeTimer(interval){
    return function(callback,delay){
      var args=Array.prototype.slice.call(arguments,2);
      if(typeof callback!=='function'){
        var source=String(callback);
        callback=function(){ (0,eval)(source); };
      }
      delay=Number(delay)||0;
      if(delay<0) delay=0;
      if(interval&&delay<4) delay=4;
      var id=++nextId;
      timers[id]={
        callback:callback,
        args:args,
        delay:delay,
        interval:interval,
        due:virtualNow()+delay,
        nativeId:null
      };
      schedule(id);
      return id;
    };
  }

  function clearTimer(id){
    var item=timers[id];
    if(item){
      if(item.nativeId!==null) nativeClearTimeout(item.nativeId);
      delete timers[id];
    }else{
      nativeClearTimeout(id);
    }
  }

  function setSpeed(next){
    next=Number(next);
    if(!isFinite(next)) next=1;
    next=Math.max(1,Math.min(20,next));
    if(next===speed) return;
    var currentVirtual=virtualNow();
    var currentPerf=nativePerformanceNow?virtualPerformanceNow():0;
    realBase=realNow();
    virtualBase=currentVirtual;
    if(nativePerformanceNow){
      perfRealBase=nativePerformanceNow();
      perfVirtualBase=currentPerf;
    }
    speed=next;
    Object.keys(timers).forEach(function(id){
      var item=timers[id];
      if(item.nativeId!==null) nativeClearTimeout(item.nativeId);
      item.nativeId=null;
      schedule(id);
    });
  }

  try{
    W.setTimeout=makeTimer(false);
    W.setInterval=makeTimer(true);
    W.clearTimeout=clearTimer;
    W.clearInterval=clearTimer;
  }catch(error){}

  try{
    W.Date=new Proxy(NativeDate,{
      construct:function(target,args){
        if(args.length===0) return new target(Math.floor(virtualNow()));
        return Reflect.construct(target,args);
      },
      get:function(target,property){
        if(property==='now') return function(){ return Math.floor(virtualNow()); };
        return Reflect.get(target,property,target);
      }
    });
  }catch(error){}

  try{
    if(nativePerformanceNow){
      try{
        Object.defineProperty(W.performance,'now',{
          configurable:true,
          value:virtualPerformanceNow
        });
      }catch(error){
        W.performance.now=virtualPerformanceNow;
      }
    }
  }catch(error){}

  W.__mxTimer={
    setSpeed:setSpeed,
    reset:function(){ setSpeed(1); },
    realNow:realNow,
    getSpeed:function(){ return speed; }
  };
})();
"""

    private val MAIN = """
(function(){
'use strict';
var W=window;
if(W.__mxv){ if(W.__mxSetSkip){ W.__mxSetSkip(%%SKIP%%); } if(W.__mxDark){ W.__mxDark(%%DARK%%); } return; }
W.__mxv=1;
var HOST="%%HOST%%";
var SEL="%%SEL%%";
var YT=%%YT%%, ANNOY=%%ANNOY%%, SPY=%%SPY%%, FPN=%%FP%%, BG=%%BG%%, POL=%%POL%%;
var mode=%%SKIP%%;
var OD=W.Date, oST=W.setTimeout, oSI=W.setInterval, oCT=W.clearTimeout;
var TIMER=W.__mxTimer;
var realNow=(TIMER&&TIMER.realNow)?TIMER.realNow:function(){ return OD.now(); };

/* The timer virtualization engine is installed independently at document start.
   This module only controls its speed; fingerprint settings never affect it. */

/* Network filtering is enforced natively by WebViewClient.shouldInterceptRequest.
   No JavaScript-to-native bridge is exposed to untrusted pages. */

function setF(nf){
  if(TIMER&&TIMER.setSpeed) TIMER.setSpeed(nf);
}
var turbo=false, tEnd=0, announced=false;
function turboOn(f,ms,auto){
  setF(f); turbo=true; tEnd=realNow()+ms;
  if(auto&&!announced){ announced=true; try{ console.log('MX_SKIP_AUTO'); }catch(e){} }
}

/* ===== كشف صفحات الانتظار والنقر التلقائي ===== */
var RX=/(please wait|wait\s*\d+|\d+\s*(seconds?|secs?)\b|redirect(ing)?\s+in|skip\s+(the\s+)?(ad\s+)?in|link\s+(is\s+)?(ready|will)|your (download|link) (will|is)|generating (link|download)|click.{0,12}continue|انتظر|ثانية|ثواني|ثوان|سيتم (تحويلك|التحميل)|جاري (التحميل|تجهيز)|تخطي)/i;
var BTN=/^\s*(get link|get the link|continue( to (link|download|site|website))?|click here( to continue)?|skip( ad| ads| this ad)?|download( now| file)?|free download|create download link|generate( download)? link|go to (link|download)|proceed|show link|احصل على الرابط|الرابط|متابعة|استمرار|تخطي|تخطى|تحميل( الآن| الملف)?|تنزيل|اضغط هنا|انتقل)\s*[>»›→]*\s*$/i;
function vis(e){
  try{
    var r=e.getBoundingClientRect(); if(r.width<8||r.height<8) return false;
    var cs=getComputedStyle(e);
    return cs.visibility!=='hidden'&&cs.display!=='none'&&cs.opacity!=='0';
  }catch(x){ return false; }
}
function sameHost(h){
  if(!h||h.charAt(0)==='#'||/^javascript:/i.test(h)) return true;
  try{
    var u=new URL(h,location.href);
    return u.hostname.replace(/^www\./,'')===location.hostname.replace(/^www\./,'');
  }catch(e){ return false; }
}
function looksLikeWait(){
  var b=document.body; if(!b) return false;
  var t=(b.innerText||'').slice(0,5000);
  if(!RX.test(t)||!/\d/.test(t)) return false;
  var n=document.querySelectorAll('span,div,b,strong,p,h1,h2,h3');
  for(var i=0;i<n.length&&i<900;i++){
    var e=n[i];
    if(e.children.length===0){
      var s=(e.textContent||'').trim();
      if(/^\d{1,2}$/.test(s)&&vis(e)) return true;
    }
  }
  return !!document.querySelector('[id*="count" i],[id*="timer" i],[class*="countdown" i],[class*="timer" i],[id*="wait" i]');
}
function reveal(){
  var els=document.querySelectorAll('a[id*="download" i],a[id*="link" i],button[id*="download" i],button[id*="continue" i],a[class*="download" i],a[class*="get-link" i],button[class*="continue" i],[id*="getlink" i],[id*="skip" i]');
  for(var i=0;i<els.length;i++){
    var e=els[i]; var t=(e.innerText||e.value||e.textContent||'').trim();
    if(!t||t.length>40||!BTN.test(t)) continue;
    try{
      var cs=getComputedStyle(e);
      if(cs.display==='none') e.style.setProperty('display','inline-block','important');
      if(cs.visibility==='hidden') e.style.setProperty('visibility','visible','important');
      e.removeAttribute('disabled'); e.disabled=false;
    }catch(x){}
  }
}
function clickOne(){
  var els=document.querySelectorAll('a,button,input[type=button],input[type=submit],div[role=button],span[role=button]');
  for(var i=0;i<els.length;i++){
    var e=els[i]; if(e.__mxc) continue;
    var t=(e.innerText||e.value||'').trim(); if(!t||t.length>40||!BTN.test(t)) continue;
    if(e.disabled||e.getAttribute('disabled')!==null){ e.removeAttribute('disabled'); e.disabled=false; }
    if(!vis(e)) continue;
    if(e.tagName==='A'&&!sameHost(e.getAttribute('href'))) continue;
    if(e.getAttribute&&e.getAttribute('target')==='_blank') e.removeAttribute('target');
    e.__mxc=1;
    try{ e.click(); }catch(x){}
    return;
  }
}

/* ===== إخفاء الإعلانات + جدار مانع الإعلانات ===== */
function injectCss(){
  try{
    if(document.getElementById('__mxcss')) return;
    var st=document.createElement('style'); st.id='__mxcss';
    st.textContent=SEL+'{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;max-height:0!important;pointer-events:none!important}';
    (document.head||document.documentElement).appendChild(st);
  }catch(e){}
}
var WALL=/(ad ?block|ad-?blocker|disable (your )?ad|whitelist (us|this site)|turn off (your )?ad|مانع الإعلانات|حاجب الإعلانات|مانع الاعلانات|عطّل|عطل (مانع|حاجب))/i;
function unwall(){
  try{
    var b=document.body; if(!b) return;
    var kids=b.children, removed=false;
    for(var i=0;i<kids.length;i++){
      var e=kids[i]; if(e.__mxw||e.tagName==='SCRIPT'||e.tagName==='STYLE') continue;
      var cs=getComputedStyle(e);
      if((cs.position==='fixed'||cs.position==='absolute')&&parseInt(cs.zIndex||'0',10)>=900){
        var r=e.getBoundingClientRect();
        if(r.width>innerWidth*0.55&&r.height>innerHeight*0.25&&WALL.test((e.innerText||'').slice(0,500))){
          e.__mxw=1; e.remove(); removed=true; i--;
        }
      }
    }
    if(removed){
      document.documentElement.style.setProperty('overflow','auto','important');
      b.style.setProperty('overflow','auto','important');
      b.style.setProperty('position','static','important');
    }
  }catch(e){}
}

/* ===== حارس النوافذ المنبثقة ===== */
var lastG=0;
['touchstart','touchend','mousedown','click','keydown'].forEach(function(n){
  W.addEventListener(n,function(ev){ if(ev.isTrusted) lastG=realNow(); },true);
});
if(ANNOY) try{
  var oOpen=W.open;
  W.open=function(){ if(realNow()-lastG>900) return null; return oOpen.apply(W,arguments); };
}catch(e){}

/* ===== يوتيوب ===== */
if(YT){
  try{
    var oJP=JSON.parse;
    JSON.parse=function(){
      var r=oJP.apply(this,arguments);
      try{
        if(r&&typeof r==='object'){
          if(r.adPlacements) r.adPlacements=[];
          if(r.playerAds) r.playerAds=[];
          if(r.adSlots) r.adSlots=[];
          if(r.playerResponse&&r.playerResponse.adPlacements) r.playerResponse.adPlacements=[];
        }
      }catch(e){}
      return r;
    };
  }catch(e){}
  oSI(function(){
    try{
      var p=document.querySelector('.html5-video-player');
      var v=document.querySelector('video');
      if(p&&v&&p.classList.contains('ad-showing')){
        v.muted=true;
        if(isFinite(v.duration)&&v.duration>0) v.currentTime=v.duration;
      }
      var sb=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.videoAdUiSkipButton');
      if(sb) sb.click();
    }catch(e){}
  },400);
}

/* ===== تعتيم المواقع ===== */
W.__mxDark=function(on){
  try{
    var id='__mxdark', old=document.getElementById(id);
    if(!on){ if(old) old.remove(); return; }
    if(old) return;
    var st=document.createElement('style'); st.id=id;
    st.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff}img,video,canvas,picture,iframe,svg image{filter:invert(1) hue-rotate(180deg)!important}';
    (document.head||document.documentElement).appendChild(st);
  }catch(e){}
};


/* ===== كاشف التجسس ===== */
function srcHost(){
  try{
    var st=(new Error()).stack||'', m=st.match(/https?:\/\/([^\/:\s)]+)/g);
    if(m){ for(var i=0;i<m.length;i++){ var h=m[i].replace(/^https?:\/\//,''); if(h!==location.hostname) return h; } }
  }catch(e){}
  return location.hostname;
}
var REP={};
function report(t,d){
  try{
    var h=srcHost(), k=t+'|'+h; if(REP[k]) return; REP[k]=1;
    console.log('MX_SPY:'+JSON.stringify({t:t,s:h,d:d||''}));
  }catch(e){}
}
function blocked(t){ return POL.indexOf(t)>=0; }
W.__mxBlockType=function(t){ if(POL.indexOf(t)<0) POL.push(t); };
if(SPY){
  try{
    /* Canvas perturbation is owned exclusively by HARDEN.
       This layer only reports fingerprint reads; it must never add a second,
       random mutation on top of the deterministic document-start protection. */
    var CP=HTMLCanvasElement.prototype, oTD=CP.toDataURL, oTB=CP.toBlob;
    var CX=CanvasRenderingContext2D.prototype, oGI=CX.getImageData;
    var fpCanvas=function(c){ return !!c && !c.isConnected && c.width>0 && c.height>0 && c.width*c.height<=200000; };
    CP.toDataURL=function(){
      if(fpCanvas(this)) report('canvas_fp','toDataURL');
      return oTD.apply(this,arguments);
    };
    CP.toBlob=function(){
      if(fpCanvas(this)) report('canvas_fp','toBlob');
      return oTB.apply(this,arguments);
    };
    CX.getImageData=function(){
      if(fpCanvas(this.canvas)) report('canvas_fp','getImageData');
      return oGI.apply(this,arguments);
    };
  }catch(e){}
  try{
    var AC=W.OfflineAudioContext||W.webkitOfflineAudioContext;
    if(AC){
      var oSR=AC.prototype.startRendering;
      AC.prototype.startRendering=function(){
        report('audio_fp','startRendering');
        if(blocked('audio_fp')) return Promise.reject(new DOMException('blocked','NotAllowedError'));
        return oSR.apply(this,arguments);
      };
    }
  }catch(e){}
  try{
    var RP=W.RTCPeerConnection||W.webkitRTCPeerConnection;
    if(RP){
      var FakeRP=function(){
        report('webrtc','new RTCPeerConnection');
        if(blocked('webrtc')) throw new DOMException('blocked','NotAllowedError');
        return new (Function.prototype.bind.apply(RP,[null].concat([].slice.call(arguments))))();
      };
      FakeRP.prototype=RP.prototype;
      W.RTCPeerConnection=FakeRP;
    }
  }catch(e){}
  try{
    var CB=W.navigator.clipboard;
    if(CB&&CB.readText){
      var oRT=CB.readText.bind(CB);
      CB.readText=function(){
        report('clipboard_read','readText');
        if(blocked('clipboard_read')) return Promise.reject(new DOMException('blocked','NotAllowedError'));
        return oRT();
      };
    }
  }catch(e){}
  try{
    var G=W.navigator.geolocation;
    if(G){
      var oGP=G.getCurrentPosition.bind(G), oWP=G.watchPosition.bind(G);
      G.getCurrentPosition=function(ok,err){ report('geolocation','getCurrentPosition'); if(blocked('geolocation')){ if(err) err({code:1,message:'denied'}); return; } return oGP.apply(G,arguments); };
      G.watchPosition=function(ok,err){ report('geolocation','watchPosition'); if(blocked('geolocation')){ if(err) err({code:1,message:'denied'}); return 0; } return oWP.apply(G,arguments); };
    }
  }catch(e){}
  try{
    var MD=W.navigator.mediaDevices;
    if(MD&&MD.getUserMedia){
      var oUM=MD.getUserMedia.bind(MD);
      MD.getUserMedia=function(){ report('media','getUserMedia'); return oUM.apply(MD,arguments); };
    }
  }catch(e){}
  try{
    var oAEL=EventTarget.prototype.addEventListener;
    EventTarget.prototype.addEventListener=function(t){
      try{
        if(t==='devicemotion'||t==='deviceorientation'){ report('sensor',t); }
        else if((t==='keydown'||t==='keypress'||t==='keyup')&&(this===document||this===W||this===document.body)){
          if(srcHost()!==location.hostname) report('key_listener',t);
        }
      }catch(e){}
      return oAEL.apply(this,arguments);
    };
  }catch(e){}
  var chkIframes=function(){
    try{
      var f=document.querySelectorAll('iframe');
      for(var i=0;i<f.length;i++){
        var e=f[i]; if(e.__mxi) continue;
        var src=e.getAttribute('src')||''; if(!/^https?:/i.test(src)) continue;
        var u=new URL(src,location.href); if(u.hostname===location.hostname) continue;
        var r=e.getBoundingClientRect(), cs=getComputedStyle(e);
        if(r.width<=2||r.height<=2||cs.display==='none'||cs.visibility==='hidden'){
          e.__mxi=1;
          try{ console.log('MX_SPY:'+JSON.stringify({t:'hidden_iframe',s:u.hostname,d:src.slice(0,80)})); }catch(x){}
          if(blocked('hidden_iframe')) e.remove();
        }
      }
      var names=['FS','hj','_lrq','clarity','mouseflow','smartlook','__insp','_ln'];
      for(var j=0;j<names.length;j++){ if(W[names[j]]&&!REP['sr'+names[j]]){ REP['sr'+names[j]]=1; console.log('MX_SPY:'+JSON.stringify({t:'session_replay',s:location.hostname,d:names[j]})); } }
    }catch(e){}
  };
  oSI(chkIframes,2500);
}
try{ Object.defineProperty(W.navigator,'globalPrivacyControl',{get:function(){return true;}}); }catch(e){}
if(FPN){
  try{ Object.defineProperty(W.navigator,'hardwareConcurrency',{get:function(){return 4;}}); }catch(e){}
  try{ Object.defineProperty(W.navigator,'deviceMemory',{get:function(){return 4;}}); }catch(e){}
}
if(BG){
  try{
    Object.defineProperty(document,'hidden',{get:function(){return false;}});
    Object.defineProperty(document,'visibilityState',{get:function(){return 'visible';}});
    document.addEventListener('visibilitychange',function(e){ e.stopImmediatePropagation(); },true);
  }catch(e){}
}
/* ===== FPS + اكتشاف الفيديو ===== */
var fpsRun=false;
W.__mxFps=function(on){
  if(!on){ fpsRun=false; return; }
  if(fpsRun) return; fpsRun=true;
  var n=0,t=realNow();
  (function loop(){
    n++; var now=realNow();
    if(now-t>=1000){ try{ console.log('MX_FPS:'+n); }catch(e){} n=0; t=now; }
    if(fpsRun) requestAnimationFrame(loop);
  })();
};
var VSEEN={};
function scanVideos(){
  try{
    var v=document.querySelectorAll('video,source,audio');
    for(var i=0;i<v.length;i++){
      var u=v[i].currentSrc||v[i].src||''; if(!/^https?:/i.test(u)||VSEEN[u]) continue;
      VSEEN[u]=1; console.log('MX_VIDEO:'+u);
    }
  }catch(e){}
}

/* ===== المشغّل ===== */
var ticks=0, sc=0;
function tick(){
  ticks++;
  if(ticks%3===0) scanVideos();
  if(turbo&&realNow()>tEnd){ setF(1); turbo=false; }
  if(ANNOY&&ticks<70) unwall();
  if(mode!==0){
    if(!turbo&&mode===2) turboOn(12,45000,false);
    else if(!turbo&&ticks%2===0&&ticks<60&&looksLikeWait()) turboOn(10,35000,true);
    if(turbo){ reveal(); clickOne(); }
  }
  if(ticks>260&&!turbo&&sc){ clearInterval(sc); try{ oCT(sc); }catch(e){} sc=0; }
}
function startScan(){ ticks=0; if(!sc){ sc=oSI(tick,700); } }
W.__mxSetSkip=function(m){
  mode=m;
  if(m===2){ turboOn(12,45000,false); startScan(); }
  else if(m===0&&turbo){ setF(1); turbo=false; }
  else if(m===1){ startScan(); }
};

injectCss();
try{
  new MutationObserver(function(){ if(!document.getElementById('__mxcss')) injectCss(); })
    .observe(document.documentElement,{childList:true});
}catch(e){}
W.__mxDark(%%DARK%%);
if(mode!==0) startScan(); else { sc=oSI(function(){ if(ANNOY&&++ticks<70) unwall(); else { clearInterval(sc); } },700); }
})();
"""

    /** حماية البصمة والواجهات الحساسة — بتتحقن في بداية كل صفحة قبل سكريبتات الموقع */
    val HARDEN = """
(function(){
if(window.__mxH) return;
try{ Object.defineProperty(window,'__mxH',{value:1}); }catch(e){}
var W=window, N=navigator;
function def(o,k,v){ try{ Object.defineProperty(o,k,{get:function(){return v;},configurable:true}); }catch(e){} }
function kill(o,k){ try{ Object.defineProperty(o,k,{value:undefined,configurable:true,writable:true}); }catch(e){ try{ o[k]=undefined; }catch(e2){} } }
var tabSeed=(%%SEED%%)>>>0;
if(!tabSeed) tabSeed=0x6D2B79F5;
/* Derive an origin-specific seed so the same tab does not reuse identical
   perturbation patterns across unrelated sites. */
var originHash=2166136261;
try{ var origin=String(W.location.origin||W.location.hostname); for(var oi=0;oi<origin.length;oi++){ originHash^=origin.charCodeAt(oi); originHash=Math.imul(originHash,16777619); } }catch(e){}
var seed=(Math.imul((tabSeed^originHash)>>>0, 2246822519)>>>0)||0x6D2B79F5;
function mix(n){ n=(n+seed+0x6D2B79F5)|0; n=Math.imul(n^(n>>>15),n|1); n^=n+Math.imul(n^(n>>>7),n|61); return (n^(n>>>14))>>>0; }
function unitNoise(i){ return mix(i) / 4294967295; }
var rnd=function(n){ return Math.floor(unitNoise(n+0x51ED270B)*n); };

/* 1) إشارات الخصوصية + توحيد مواصفات الجهاز */
def(N,'globalPrivacyControl',true);
def(N,'doNotTrack','1');
def(N,'hardwareConcurrency',4);
def(N,'deviceMemory',8);
/* Keep real screen geometry and color depth: fixed spoofed dimensions
   create contradictions with the viewport and can break games/layouts. */

/* 2) الواجهات الحساسة: بطارية، بلوتوث، USB، Serial، HID، NFC، MIDI، حساسات الحركة */
['getBattery','bluetooth','usb','serial','hid','getInstalledRelatedApps','wakeLock'].forEach(function(k){ kill(N,k); try{ kill(Navigator.prototype,k); }catch(e){} });
try{ N.requestMIDIAccess=function(){ return Promise.reject(new DOMException('Permission denied','SecurityError')); }; }catch(e){}
['NDEFReader','Accelerometer','Gyroscope','Magnetometer','AbsoluteOrientationSensor','RelativeOrientationSensor','LinearAccelerationSensor','GravitySensor','AmbientLightSensor','Bluetooth','USB','Serial','HID'].forEach(function(k){ kill(W,k); });
try{
  var oAEL=EventTarget.prototype.addEventListener;
  EventTarget.prototype.addEventListener=function(t){
    if(t==='deviceorientation'||t==='deviceorientationabsolute'||t==='devicemotion') return;
    return oAEL.apply(this,arguments);
  };
  ['ondeviceorientation','ondeviceorientationabsolute','ondevicemotion'].forEach(function(k){ try{ Object.defineProperty(W,k,{get:function(){return null;},set:function(){},configurable:true}); }catch(e){} });
}catch(e){}

/* 3) الصلاحيات: أي حاجة حساسة بترجع denied وبدون ما تتسأل */
try{
  var P=N.permissions;
  if(P&&P.query){
    var oQ=P.query.bind(P);
    var DENY={'midi':1,'camera':1,'microphone':1,'geolocation':1,'notifications':1,'clipboard-read':1,'clipboard-write':1,'persistent-storage':1,'background-sync':1,'background-fetch':1,'accelerometer':1,'gyroscope':1,'magnetometer':1,'ambient-light-sensor':1,'bluetooth':1,'nfc':1,'push':1,'screen-wake-lock':1,'display-capture':1,'idle-detection':1,'window-placement':1,'storage-access':1,'local-fonts':1,'payment-handler':1,'speaker-selection':1};
    P.query=function(d){
      try{
        var n=d&&d.name;
        if(n&&DENY[n]){ return Promise.resolve({name:n,state:'denied',onchange:null,addEventListener:function(){},removeEventListener:function(){},dispatchEvent:function(){return false;}}); }
      }catch(e){}
      return oQ(d);
    };
  }
}catch(e){}

/* 4) الكاميرا والمايك: مفيش أجهزة ظاهرة لحد ما يتمنح الإذن */
try{
  var MD=N.mediaDevices;
  if(MD&&MD.enumerateDevices){ MD.enumerateDevices=function(){ return Promise.resolve([]); }; }
}catch(e){}

/* 5) WebGL: اسم كارت الشاشة والمصنّع قيمة عامة موحدة */
function patchGL(proto){
  if(!proto) return;
  var o=proto.getParameter;
  proto.getParameter=function(p){
    if(p===37445) return 'Google Inc. (Generic)';
    if(p===37446) return 'ANGLE (Generic, Generic GPU, OpenGL ES 3.0)';
    return o.apply(this,arguments);
  };
}
try{ patchGL(W.WebGLRenderingContext&&WebGLRenderingContext.prototype); }catch(e){}
try{ patchGL(W.WebGL2RenderingContext&&WebGL2RenderingContext.prototype); }catch(e){}

/* 6) Canvas: per-document deterministic noise.
   Repeated reads in one page get the same perturbation, reducing API instability. */
function jitter(d,w,h){
  var n=w*h, count=Math.min(7,Math.max(3,Math.floor(n/12000)+3));
  for(var i=0;i<count;i++){
    var pixel=mix(i*7919+ n*31) % n, k=pixel*4;
    if(k+2<d.length){
      d[k]=d[k]^1;
      if((mix(i+101)&1)===1) d[k+1]=d[k+1]^1;
      if((mix(i+211)&1)===1) d[k+2]=d[k+2]^1;
    }
  }
}
try{
  var CX=CanvasRenderingContext2D.prototype, oGI=CX.getImageData;
  var CP=HTMLCanvasElement.prototype, oTD=CP.toDataURL, oTB=CP.toBlob;
  /* Only small, detached canvases are treated as likely fingerprint probes.
     Connected/game canvases are left untouched to avoid rendering regressions. */
  function fpCanvas(c){
    return !!c && !c.isConnected && c.width>0 && c.height>0 && c.width*c.height<=200000;
  }
  CX.getImageData=function(x,y,w,h){
    var r=oGI.apply(this,arguments);
    try{ if(fpCanvas(this.canvas)&&r&&r.data) jitter(r.data,r.width,r.height); }catch(e){}
    return r;
  };
  function noisy(c){
    try{
      if(!fpCanvas(c)) return c;
      var n=document.createElement('canvas'); n.width=c.width; n.height=c.height;
      var x=n.getContext('2d',{willReadFrequently:true}); if(!x) return c;
      x.drawImage(c,0,0);
      var id=oGI.call(x,0,0,n.width,n.height);
      jitter(id.data,n.width,n.height);
      x.putImageData(id,0,0);
      return n;
    }catch(e){ return c; }
  }
  CP.toDataURL=function(){
    return oTD.apply(noisy(this),arguments);
  };
  CP.toBlob=function(){
    return oTB.apply(noisy(this),arguments);
  };
}catch(e){}

/* 7) Audio fingerprinting: deterministic, tiny noise on analyser output only.
   Do not mutate AudioBuffer.getChannelData(): it is live playback data and changing
   it can alter real sound. Analyser arrays are caller-owned snapshots. */
try{
  var AN=W.AnalyserNode&&AnalyserNode.prototype;
  if(AN){
    var oFF=AN.getFloatFrequencyData;
    if(oFF) AN.getFloatFrequencyData=function(a){
      oFF.apply(this,arguments);
      try{ for(var i=0;i<a.length;i+=7){ if(isFinite(a[i])) a[i]+=(unitNoise(i+809)-0.5)*0.002; } }catch(e){}
    };
    var oBF=AN.getByteFrequencyData;
    if(oBF) AN.getByteFrequencyData=function(a){
      oBF.apply(this,arguments);
      try{ for(var i=0;i<a.length;i+=11){ a[i]=Math.max(0,Math.min(255,a[i]+(Math.floor(unitNoise(i+1201)*3)-1))); } }catch(e){}
    };
    var oFT=AN.getFloatTimeDomainData;
    if(oFT) AN.getFloatTimeDomainData=function(a){
      oFT.apply(this,arguments);
      try{ for(var i=0;i<a.length;i+=13){ if(isFinite(a[i])) a[i]+=(unitNoise(i+1601)-0.5)*0.000002; } }catch(e){}
    };
    var oBT=AN.getByteTimeDomainData;
    if(oBT) AN.getByteTimeDomainData=function(a){
      oBT.apply(this,arguments);
      try{ for(var i=0;i<a.length;i+=17){ a[i]=Math.max(0,Math.min(255,a[i]+(Math.floor(unitNoise(i+1901)*3)-1))); } }catch(e){}
    };
  }
}catch(e){}

/* 8) الخطوط: أي خط مش قياسي بيتقاس كأنه مش موجود */
var STD={'serif':1,'sans-serif':1,'monospace':1,'cursive':1,'fantasy':1,'system-ui':1,'arial':1,'helvetica':1,'times new roman':1,'times':1,'courier new':1,'courier':1,'roboto':1,'noto sans':1};
function famKey(s){ return String(s).split(',')[0].trim().replace(/['"]/g,'').toLowerCase(); }
try{
  var oMT=CanvasRenderingContext2D.prototype.measureText;
  CanvasRenderingContext2D.prototype.measureText=function(t){
    try{
      var f=this.font;
      var m=/^(.*?\d+(?:\.\d+)?(?:px|pt|em|rem|%)(?:\/\S+)?)\s+(.+)$/.exec(f);
      if(m && !STD[famKey(m[2])]){
        var parts=m[2].split(','); var last=parts[parts.length-1].trim().replace(/['"]/g,'').toLowerCase();
        this.font=m[1]+' '+(STD[last]?last:'sans-serif');
        var r=oMT.call(this,t); this.font=f; return r;
      }
    }catch(e){}
    return oMT.call(this,t);
  };
}catch(e){}
try{
  var hide=function(el){
    var s=el&&el.style; if(!s||!s.fontFamily) return null;
    if(STD[famKey(s.fontFamily)]) return null;
    var l=parseFloat(s.left), tp=parseFloat(s.top);
    if(s.position==='absolute' && (l<-500||tp<-500||s.visibility==='hidden')) return s;
    return null;
  };
  ['offsetWidth','offsetHeight'].forEach(function(k){
    var d=Object.getOwnPropertyDescriptor(HTMLElement.prototype,k);
    if(!d||!d.get) return;
    var g=d.get;
    Object.defineProperty(HTMLElement.prototype,k,{configurable:true,get:function(){
      try{
        var s=hide(this);
        if(s){ var old=s.fontFamily, parts=old.split(','); var last=parts[parts.length-1].trim().replace(/['"]/g,'').toLowerCase(); s.fontFamily=STD[last]?last:'sans-serif'; var v=g.call(this); s.fontFamily=old; return v; }
      }catch(e){}
      return g.call(this);
    }});
  });
}catch(e){}
})();
"""

    /** Build the anti-fingerprinting script with a native per-tab seed. */
    fun harden(seed: Long): String = HARDEN.replace("%%SEED%%", (seed and 0xffffffffL).toString())

    fun main(
        host: String, skipMode: Int, dark: Boolean, youtube: Boolean,
        cosmetic: Boolean, annoy: Boolean, spy: Boolean, fp: Boolean, bg: Boolean, blockedTypes: List<String>
    ): String {
        val sel = listOfNotNull(if (cosmetic) ADS_SEL else null, if (annoy) ANNOY_SEL else null).joinToString(",")
        val pol = blockedTypes.joinToString(",") { "\"" + it.replace("\"", "") + "\"" }
        return MAIN
            .replace("%%HOST%%", host.replace("\"", ""))
            .replace("%%SEL%%", if (sel.isEmpty()) "#__mx_none_sel" else sel)
            .replace("%%SKIP%%", skipMode.toString())
            .replace("%%DARK%%", dark.toString())
            .replace("%%YT%%", (youtube && cosmetic).toString())
            .replace("%%ANNOY%%", annoy.toString())
            .replace("%%SPY%%", spy.toString())
            .replace("%%FP%%", fp.toString())
            .replace("%%BG%%", bg.toString())
            .replace("%%POL%%", "[$pol]")
    }

    /** إخفاء عناصر من قوائم التصفية — بيقسّم المجموعة لو فيها selector غلط بدل ما يبوّظ الباقي */
    fun cosmetic(sels: List<String>): String {
        val arr = org.json.JSONArray(sels).toString()
        return "(function(S){try{var o=document.getElementById('__mxfl');if(o)o.remove();var st=document.createElement('style');st.id='__mxfl';(document.head||document.documentElement).appendChild(st);var sh=st.sheet;if(!sh)return;" +
            "function add(a){if(!a.length)return;try{sh.insertRule(a.join(',')+'{display:none!important}',sh.cssRules.length)}catch(e){if(a.length>1){var m=a.length>>1;add(a.slice(0,m));add(a.slice(m))}}}" +
            "for(var i=0;i<S.length;i+=100)add(S.slice(i,i+100))}catch(e){}})($arr)"
    }

    /** وضع الكمبيوتر: يفرض عرض الصفحة ويصغّرها لتناسب الشاشة كلها */
    fun desktopViewport(width: Int) =
        "(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}" +
            "m.setAttribute('content','width=$width,minimum-scale=0.1,maximum-scale=5,user-scalable=yes');})()"

    const val FORCE_SKIP = "(function(){if(window.__mxSetSkip){window.__mxSetSkip(2);return 1}return 0})()"

    fun fps(on: Boolean) = "window.__mxFps&&window.__mxFps($on)"

    /** نص الصفحة (أو وضع القراءة) للقراءة بصوت عالٍ / للذكاء الاصطناعي */
    const val TEXT = "(function(){var r=document.getElementById('__mxread');return (r?r.innerText:(document.body?document.body.innerText:'')).slice(0,12000)})()"

    const val SELECTION = "(function(){return String(window.getSelection?window.getSelection():'').slice(0,6000)})()"

    fun user(id: Long, code: String, type: String): String {
        return if (type == "css") {
            "(function(){var i='__mxus$id';if(document.getElementById(i))return;var s=document.createElement('style');s.id=i;s.textContent=" +
                JSONObject.quote(code) + ";(document.head||document.documentElement).appendChild(s);})()"
        } else {
            "(function(){if(window.__mxu$id)return;window.__mxu$id=1;try{\n" + code + "\n}catch(e){console.log('MX_USERERR:'+e)}})()"
        }
    }

    val READER = """
(function(){
  var old=document.getElementById('__mxread');
  if(old){ old.remove(); document.documentElement.style.overflow=''; return 'off'; }
  var m=new Map(); var ps=document.querySelectorAll('p');
  for(var i=0;i<ps.length;i++){ var t=(ps[i].innerText||'').trim().length; if(t<40) continue; var k=ps[i].parentElement; m.set(k,(m.get(k)||0)+t); }
  var best=null,bs=0; m.forEach(function(v,k){ if(v>bs){ bs=v; best=k; } });
  if(!best||bs<300) return 'none';
  var w=document.createElement('div'); w.id='__mxread';
  var dark=false, fs=19;
  function paint(){ w.style.background=dark?'#111':'#fafafa'; w.style.color=dark?'#ddd':'#222'; w.style.fontSize=fs+'px'; }
  w.style.cssText='position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147483647;overflow:auto;padding:64px 18px 60px;line-height:1.9;font-family:Georgia,serif;';
  var bar=document.createElement('div');
  bar.style.cssText='position:fixed;left:0;right:0;top:0;height:46px;display:flex;gap:8px;align-items:center;justify-content:center;background:rgba(0,0,0,.85);z-index:2147483647;font:16px sans-serif;';
  function btn(t,f){ var b=document.createElement('button'); b.textContent=t; b.style.cssText='background:#e81e28;color:#fff;border:0;border-radius:8px;padding:6px 14px;font-size:15px;'; b.onclick=f; bar.appendChild(b); }
  btn('A+',function(){ fs=Math.min(34,fs+2); paint(); });
  btn('A-',function(){ fs=Math.max(13,fs-2); paint(); });
  btn('🌙',function(){ dark=!dark; paint(); });
  btn('✕',function(){ w.remove(); bar.remove(); document.documentElement.style.overflow=''; });
  var h=document.createElement('h1'); h.textContent=document.title; h.style.cssText='font-size:26px;line-height:1.4;margin:0 0 18px';
  w.appendChild(h);
  var els=best.querySelectorAll('p,h2,h3,blockquote,li');
  for(var j=0;j<els.length;j++){
    var tx=(els[j].innerText||'').trim(); if(tx.length<2) continue;
    var tag=els[j].tagName.toLowerCase(); if(tag==='li') tag='p';
    var n=document.createElement(tag); n.textContent=tx; n.style.margin='0 0 16px'; w.appendChild(n);
  }
  paint();
  document.body.appendChild(w); document.body.appendChild(bar);
  document.documentElement.style.overflow='hidden';
  return 'on';
})()
"""
}
