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
var oPN=(W.performance&&W.performance.now)?W.performance.now.bind(W.performance):null;
var F=1, rBase=OD.now(), vBase=rBase, pBase=oPN?oPN():0, pvBase=pBase;
function vnow(){ return vBase+(OD.now()-rBase)*F; }
function pnow(){ return pvBase+(oPN()-pBase)*F; }

/* ===== محرك الوقت: كل المؤقتات تمر من هنا ===== */
var timers={}, seq=900000;
function schedule(id){
  var t=timers[id]; if(!t) return;
  var wait=Math.max(0,(t.due-vnow())/F);
  t.real=oST(function(){ fire(id); }, wait);
}
function fire(id){
  var t=timers[id]; if(!t) return;
  if(t.iv){ t.due=vnow()+t.delay; schedule(id); } else { delete timers[id]; }
  try{ t.fn.apply(W,t.args); }catch(e){ oST(function(){ throw e; },0); }
}
function mk(iv){
  return function(fn,d){
    var args=[].slice.call(arguments,2);
    if(typeof fn!=='function'){ var src=String(fn); fn=function(){ (0,eval)(src); }; }
    d=+d||0; if(d<0) d=0; if(iv&&d<4) d=4;
    var id=++seq;
    timers[id]={fn:fn,args:args,delay:d,iv:iv,due:vnow()+d,real:0};
    schedule(id);
    return id;
  };
}
try{
  W.setTimeout=mk(false); W.setInterval=mk(true);
  W.clearTimeout=W.clearInterval=function(id){
    var t=timers[id];
    if(t){ oCT(t.real); delete timers[id]; } else { oCT(id); }
  };
}catch(e){}
try{
  W.Date=new Proxy(OD,{
    construct:function(t,a){ if(a.length===0) return new t(Math.floor(vnow())); return Reflect.construct(t,a); },
    get:function(t,p){ if(p==='now') return function(){ return Math.floor(vnow()); }; return t[p]; }
  });
}catch(e){}
try{ if(oPN) W.performance.now=pnow; }catch(e){}

/* ===== قطع طلبات fetch/XHR/Beacon للنطاقات المحجوبة (يظهر كفشل شبكة حقيقي) ===== */
var MB=W.__mxb;
function blk(u){
  try{
    if(!MB) return false;
    var a=new URL(String(u),location.href);
    if(a.protocol!=='http:'&&a.protocol!=='https:') return false;
    if(a.hostname===location.hostname) return false;
    return MB.blocked(a.href)===true;
  }catch(e){ return false; }
}
try{
  var oF=W.fetch;
  if(oF){
    W.fetch=function(i){
      var u=(typeof i==='string')?i:((i&&i.url)?i.url:String(i));
      if(blk(u)) return Promise.reject(new TypeError('Failed to fetch'));
      return oF.apply(this,arguments);
    };
  }
}catch(e){}
try{
  var XP=W.XMLHttpRequest.prototype, oXo=XP.open, oXs=XP.send;
  XP.open=function(m,u){ this.__mxu=u; return oXo.apply(this,arguments); };
  XP.send=function(){
    var x=this;
    if(x.__mxu&&blk(x.__mxu)){
      oST(function(){
        try{ x.dispatchEvent(new Event('error')); x.dispatchEvent(new Event('loadend')); }catch(e){}
      },0);
      return;
    }
    return oXs.apply(this,arguments);
  };
}catch(e){}
try{
  var oB=W.navigator.sendBeacon;
  if(oB) W.navigator.sendBeacon=function(u){ if(blk(u)) return false; return oB.apply(this,arguments); };
}catch(e){}

function setF(nf){
  if(nf===F) return;
  vBase=vnow(); rBase=OD.now();
  pvBase=oPN?pnow():0; pBase=oPN?oPN():0;
  F=nf;
  for(var k in timers){ var t=timers[k]; oCT(t.real); schedule(k); }
}
var turbo=false, tEnd=0, announced=false;
function turboOn(f,ms,auto){
  setF(f); turbo=true; tEnd=OD.now()+ms;
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
  W.addEventListener(n,function(ev){ if(ev.isTrusted) lastG=OD.now(); },true);
});
if(ANNOY) try{
  var oOpen=W.open;
  W.open=function(){ if(OD.now()-lastG>900) return null; return oOpen.apply(W,arguments); };
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
    var CP=HTMLCanvasElement.prototype, oTD=CP.toDataURL, oTB=CP.toBlob;
    var CX=CanvasRenderingContext2D.prototype, oGI=CX.getImageData;
    var noisy=function(c){
      try{
        var n=document.createElement('canvas'); n.width=c.width; n.height=c.height;
        var x=n.getContext('2d'); x.drawImage(c,0,0);
        for(var i=0;i<3;i++){ x.fillStyle='rgba('+(Math.random()*255|0)+',0,0,0.01)'; x.fillRect(Math.random()*n.width|0,Math.random()*n.height|0,1,1); }
        return n;
      }catch(e){ return c; }
    };
    var fpCanvas=function(c){ return !c.isConnected&&c.width*c.height<=200000; };
    CP.toDataURL=function(){
      if(fpCanvas(this)){ report('canvas_fp','toDataURL'); if(blocked('canvas_fp')||FPN) return oTD.apply(noisy(this),arguments); }
      return oTD.apply(this,arguments);
    };
    CP.toBlob=function(){
      if(fpCanvas(this)){ report('canvas_fp','toBlob'); if(blocked('canvas_fp')||FPN) return oTB.apply(noisy(this),arguments); }
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
  var n=0,t=OD.now();
  (function loop(){
    n++; var now=OD.now();
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
  if(turbo&&OD.now()>tEnd){ setF(1); turbo=false; }
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
