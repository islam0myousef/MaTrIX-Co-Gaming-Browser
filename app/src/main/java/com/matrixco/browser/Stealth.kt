package com.matrixco.browser

import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Stealth ad-blocking: بدل ما الطلب المحجوب يفشل (والموقع يكتشف المانع)، بنرجّع رد 200 "سليم" شكلاً
 * (سكريبت فاضي / صورة 1x1 / JSON فاضي / VAST فاضي) + CORS، وبنحاكي كائنات الإعلانات المعروفة،
 * وبنخلّي فحوصات الـ bait (offsetHeight / getComputedStyle ...) ترجّع قيم طبيعية.
 */
object Stealth {
    private const val JS = "application/javascript"
    private val GIF: ByteArray = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)

    private const val NOOP_JS = "(function(){})();"
    private const val ADSENSE_JS = "(window.adsbygoogle=window.adsbygoogle||[]).loaded=true;window.google_ad_status=1;"
    private const val GPT_JS = "(function(w){var g=w.googletag=w.googletag||{};g.cmd=g.cmd||[];g.apiReady=true;g.pubadsReady=true;" +
        "var P=new Proxy(function(){},{get:function(t,k){if(typeof k==='symbol')return k===Symbol.toPrimitive?function(){return''}:undefined;return k==='then'?undefined:P},apply:function(){return P}});" +
        "['pubads','defineSlot','defineOutOfPageSlot','display','enableServices','destroySlots','sizeMapping','companionAds','setConfig'].forEach(function(n){if(!g[n])g[n]=function(){return P}});" +
        "g.cmd.push=function(f){try{typeof f==='function'&&f.call(w)}catch(e){}return 1};g.cmd.forEach&&0})(window);"
    private const val FAB_JS = "(function(w){function F(){this.onDetected=function(){return this};this.onNotDetected=function(f){try{typeof f==='function'&&setTimeout(f,0)}catch(e){}return this};" +
        "this.check=function(){return true};this.setOption=function(){return this};this.emitEvent=function(){return this};this.clearEvent=function(){return this};" +
        "this.on=function(d,f){if(!d&&typeof f==='function')setTimeout(f,0);return this}}" +
        "w.FuckAdBlock=w.BlockAdBlock=F;w.fuckAdBlock=w.blockAdBlock=new F();})(window);"
    private const val VAST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"></VAST>"
    private const val EMPTY_HTML = "<!doctype html><html><head></head><body></body></html>"

    private fun hdr(h: Map<String, String>?, k: String): String =
        (h?.get(k) ?: h?.get(k.lowercase(Locale.ROOT))).orEmpty()

    fun response(uri: Uri, h: Map<String, String>?): WebResourceResponse {
        val full = uri.toString().lowercase(Locale.ROOT)
        val p = uri.path.orEmpty().lowercase(Locale.ROOT)
        val accept = hdr(h, "Accept").lowercase(Locale.ROOT)
        val dest = hdr(h, "Sec-Fetch-Dest").lowercase(Locale.ROOT)
        val origin = hdr(h, "Origin")

        var mime = "text/plain"; var body: ByteArray = ByteArray(0)
        when {
            full.contains("fuckadblock") || full.contains("blockadblock") -> { mime = JS; body = FAB_JS.toByteArray() }
            full.contains("adsbygoogle") || full.contains("show_ads") -> { mime = JS; body = ADSENSE_JS.toByteArray() }
            full.contains("gpt.js") || full.contains("googletag") || full.contains("pubads") -> { mime = JS; body = GPT_JS.toByteArray() }
            p.endsWith(".js") || dest == "script" || accept.contains("javascript") -> { mime = JS; body = NOOP_JS.toByteArray() }
            full.contains("vast") || p.endsWith(".xml") -> { mime = "text/xml"; body = VAST.toByteArray() }
            Regex("\\.(png|jpe?g|gif|webp|ico|bmp|svg)$").containsMatchIn(p) || dest == "image" || accept.startsWith("image/") -> { mime = "image/gif"; body = GIF }
            p.endsWith(".css") || dest == "style" || accept.startsWith("text/css") -> { mime = "text/css" }
            p.endsWith(".json") || accept.contains("json") -> { mime = "application/json"; body = "{}".toByteArray() }
            dest == "iframe" || dest == "document" || accept.contains("text/html") -> { mime = "text/html"; body = EMPTY_HTML.toByteArray() }
        }
        val headers = HashMap<String, String>()
        headers["Access-Control-Allow-Origin"] = if (origin.isNotEmpty()) origin else "*"
        if (origin.isNotEmpty()) headers["Access-Control-Allow-Credentials"] = "true"
        headers["Timing-Allow-Origin"] = "*"
        headers["Cache-Control"] = "no-store"
        return WebResourceResponse(mime, "utf-8", 200, "OK", headers, ByteArrayInputStream(body))
    }

    /** يتحقن عند document-start (وكاحتياطي متأخر — idempotent). مفيهوش علامة الدولار عشان Kotlin raw string. */
    val DOC_START = """
(function(){
'use strict';
var W=window;
try{ if(W.__mxS) return; Object.defineProperty(W,'__mxS',{value:1,enumerable:false}); }catch(e){ return; }
var NS=new WeakMap(), oTS=Function.prototype.toString;
function mask(f,n){ try{ NS.set(f,'function '+n+'() { [native code] }'); }catch(e){} return f; }
try{ var nts=function toString(){ var r=NS.get(this); return r!==undefined?r:oTS.call(this); }; NS.set(nts,'function toString() { [native code] }'); Function.prototype.toString=nts; }catch(e){}
function def(k,v){ try{ if(!(k in W)) Object.defineProperty(W,k,{value:v,writable:true,configurable:true,enumerable:true}); }catch(e){} }

/* كائنات الإعلانات اللي المواقع بتفحصها */
def('canRunAds',true); def('isAdBlockActive',false); def('adblock',false);
def('adBlockDetected',false); def('adBlockEnabled',false); def('adsBlocked',false);
def('adsbygoogle',(function(){ var a=[]; a.loaded=true; return a; })());
var P=new Proxy(function(){},{get:function(t,k){ if(typeof k==='symbol') return k===Symbol.toPrimitive?function(){return '';}:undefined; return k==='then'?undefined:P; },apply:function(){ return P; }});
var cmd=[]; cmd.push=function(f){ try{ if(typeof f==='function') f.call(W); }catch(e){} return 1; };
def('googletag',{cmd:cmd,apiReady:true,pubadsReady:true,pubads:function(){return P;},defineSlot:function(){return P;},defineOutOfPageSlot:function(){return P;},display:function(){return P;},enableServices:function(){return P;},destroySlots:function(){return true;},sizeMapping:function(){return P;}});
function FAB(){ this.onDetected=function(){return this;}; this.onNotDetected=function(f){ try{ if(typeof f==='function') setTimeout(f,0); }catch(e){} return this; }; this.check=function(){return true;}; this.setOption=function(){return this;}; this.emitEvent=function(){return this;}; this.clearEvent=function(){return this;}; this.on=function(d,f){ if(!d&&typeof f==='function') setTimeout(f,0); return this; }; }
def('FuckAdBlock',FAB); def('BlockAdBlock',FAB); def('fuckAdBlock',new FAB()); def('blockAdBlock',new FAB());

/* bait: العناصر اللي بنخبيها لو اتفحصت تبان طبيعية */
var BAIT=/(^|[\s_-])(adsbox|adbanner|ad-banner|banner[_-]?ads?|textads?|text-ad|pub_\d+x\d+|sponsor(ed)?|advert(isement)?s?|googleads?|adsense|ad-?slot|ad-?container|ads?)(?![a-z])/i;
function isBait(el){ try{ if(!el||el.nodeType!==1) return false; return BAIT.test((el.id||'')+' '+(typeof el.className==='string'?el.className:'')); }catch(e){ return false; } }
function patchGet(proto,k,fix){
  try{ var d=Object.getOwnPropertyDescriptor(proto,k); if(!d||!d.get) return; var g=d.get;
    Object.defineProperty(proto,k,{configurable:true,enumerable:d.enumerable,get:mask(function(){ var v=g.call(this); return fix(this,v); },'get '+k)});
  }catch(e){}
}
['offsetHeight','clientHeight'].forEach(function(k){ patchGet(k==='offsetHeight'?HTMLElement.prototype:Element.prototype,k,function(el,v){ return (v===0&&isBait(el))?90:v; }); });
['offsetWidth','clientWidth'].forEach(function(k){ patchGet(k==='offsetWidth'?HTMLElement.prototype:Element.prototype,k,function(el,v){ return (v===0&&isBait(el))?300:v; }); });
patchGet(HTMLElement.prototype,'offsetParent',function(el,v){ return (v===null&&isBait(el)&&document.body)?document.body:v; });
try{
  var oR=Element.prototype.getBoundingClientRect;
  Element.prototype.getBoundingClientRect=mask(function getBoundingClientRect(){ var r=oR.call(this); try{ if(r.width===0&&r.height===0&&isBait(this)) return new DOMRect(r.x,r.y,300,90); }catch(e){} return r; },'getBoundingClientRect');
}catch(e){}
try{
  var oG=W.getComputedStyle;
  W.getComputedStyle=mask(function getComputedStyle(el,ps){
    var cs=oG.apply(W,arguments);
    try{
      if(!ps&&isBait(el)&&(cs.display==='none'||cs.visibility==='hidden'||cs.opacity==='0'||parseFloat(cs.height)===0)){
        return new Proxy(cs,{get:function(t,p){
          if(p==='display') return 'block'; if(p==='visibility') return 'visible'; if(p==='opacity') return '1';
          if(p==='height'&&parseFloat(t.height)===0) return '90px'; if(p==='width'&&parseFloat(t.width)===0) return '300px';
          if(p==='getPropertyValue') return function(n){ n=String(n).toLowerCase(); if(n==='display') return 'block'; if(n==='visibility') return 'visible'; if(n==='opacity') return '1'; return t.getPropertyValue(n); };
          var v=Reflect.get(t,p,t); return typeof v==='function'?v.bind(t):v;
        }});
      }
    }catch(e){}
    return cs;
  },'getComputedStyle');
}catch(e){}
})();
"""
}
