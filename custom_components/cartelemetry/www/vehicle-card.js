/******************************************************************************
Copyright (c) Microsoft Corporation.

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
***************************************************************************** */
/* global Reflect, Promise, SuppressedError, Symbol, Iterator */


function __decorate(decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
}

typeof SuppressedError === "function" ? SuppressedError : function (error, suppressed, message) {
    var e = new Error(message);
    return e.name = "SuppressedError", e.error = error, e.suppressed = suppressed, e;
};

/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */
const t$2=globalThis,e$2=t$2.ShadowRoot&&(void 0===t$2.ShadyCSS||t$2.ShadyCSS.nativeShadow)&&"adoptedStyleSheets"in Document.prototype&&"replace"in CSSStyleSheet.prototype,s$2=Symbol(),o$4=new WeakMap;let n$3 = class n{constructor(t,e,o){if(this._$cssResult$=true,o!==s$2)throw Error("CSSResult is not constructable. Use `unsafeCSS` or `css` instead.");this.cssText=t,this.t=e;}get styleSheet(){let t=this.o;const s=this.t;if(e$2&&void 0===t){const e=void 0!==s&&1===s.length;e&&(t=o$4.get(s)),void 0===t&&((this.o=t=new CSSStyleSheet).replaceSync(this.cssText),e&&o$4.set(s,t));}return t}toString(){return this.cssText}};const r$4=t=>new n$3("string"==typeof t?t:t+"",void 0,s$2),i$3=(t,...e)=>{const o=1===t.length?t[0]:e.reduce((e,s,o)=>e+(t=>{if(true===t._$cssResult$)return t.cssText;if("number"==typeof t)return t;throw Error("Value passed to 'css' function must be a 'css' function result: "+t+". Use 'unsafeCSS' to pass non-literal values, but take care to ensure page security.")})(s)+t[o+1],t[0]);return new n$3(o,t,s$2)},S$1=(s,o)=>{if(e$2)s.adoptedStyleSheets=o.map(t=>t instanceof CSSStyleSheet?t:t.styleSheet);else for(const e of o){const o=document.createElement("style"),n=t$2.litNonce;void 0!==n&&o.setAttribute("nonce",n),o.textContent=e.cssText,s.appendChild(o);}},c$2=e$2?t=>t:t=>t instanceof CSSStyleSheet?(t=>{let e="";for(const s of t.cssRules)e+=s.cssText;return r$4(e)})(t):t;

/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */const{is:i$2,defineProperty:e$1,getOwnPropertyDescriptor:h$1,getOwnPropertyNames:r$3,getOwnPropertySymbols:o$3,getPrototypeOf:n$2}=Object,a$1=globalThis,c$1=a$1.trustedTypes,l$1=c$1?c$1.emptyScript:"",p$1=a$1.reactiveElementPolyfillSupport,d$1=(t,s)=>t,u$1={toAttribute(t,s){switch(s){case Boolean:t=t?l$1:null;break;case Object:case Array:t=null==t?t:JSON.stringify(t);}return t},fromAttribute(t,s){let i=t;switch(s){case Boolean:i=null!==t;break;case Number:i=null===t?null:Number(t);break;case Object:case Array:try{i=JSON.parse(t);}catch(t){i=null;}}return i}},f$1=(t,s)=>!i$2(t,s),b$1={attribute:true,type:String,converter:u$1,reflect:false,useDefault:false,hasChanged:f$1};Symbol.metadata??=Symbol("metadata"),a$1.litPropertyMetadata??=new WeakMap;let y$1 = class y extends HTMLElement{static addInitializer(t){this._$Ei(),(this.l??=[]).push(t);}static get observedAttributes(){return this.finalize(),this._$Eh&&[...this._$Eh.keys()]}static createProperty(t,s=b$1){if(s.state&&(s.attribute=false),this._$Ei(),this.prototype.hasOwnProperty(t)&&((s=Object.create(s)).wrapped=true),this.elementProperties.set(t,s),!s.noAccessor){const i=Symbol(),h=this.getPropertyDescriptor(t,i,s);void 0!==h&&e$1(this.prototype,t,h);}}static getPropertyDescriptor(t,s,i){const{get:e,set:r}=h$1(this.prototype,t)??{get(){return this[s]},set(t){this[s]=t;}};return {get:e,set(s){const h=e?.call(this);r?.call(this,s),this.requestUpdate(t,h,i);},configurable:true,enumerable:true}}static getPropertyOptions(t){return this.elementProperties.get(t)??b$1}static _$Ei(){if(this.hasOwnProperty(d$1("elementProperties")))return;const t=n$2(this);t.finalize(),void 0!==t.l&&(this.l=[...t.l]),this.elementProperties=new Map(t.elementProperties);}static finalize(){if(this.hasOwnProperty(d$1("finalized")))return;if(this.finalized=true,this._$Ei(),this.hasOwnProperty(d$1("properties"))){const t=this.properties,s=[...r$3(t),...o$3(t)];for(const i of s)this.createProperty(i,t[i]);}const t=this[Symbol.metadata];if(null!==t){const s=litPropertyMetadata.get(t);if(void 0!==s)for(const[t,i]of s)this.elementProperties.set(t,i);}this._$Eh=new Map;for(const[t,s]of this.elementProperties){const i=this._$Eu(t,s);void 0!==i&&this._$Eh.set(i,t);}this.elementStyles=this.finalizeStyles(this.styles);}static finalizeStyles(s){const i=[];if(Array.isArray(s)){const e=new Set(s.flat(1/0).reverse());for(const s of e)i.unshift(c$2(s));}else void 0!==s&&i.push(c$2(s));return i}static _$Eu(t,s){const i=s.attribute;return  false===i?void 0:"string"==typeof i?i:"string"==typeof t?t.toLowerCase():void 0}constructor(){super(),this._$Ep=void 0,this.isUpdatePending=false,this.hasUpdated=false,this._$Em=null,this._$Ev();}_$Ev(){this._$ES=new Promise(t=>this.enableUpdating=t),this._$AL=new Map,this._$E_(),this.requestUpdate(),this.constructor.l?.forEach(t=>t(this));}addController(t){(this._$EO??=new Set).add(t),void 0!==this.renderRoot&&this.isConnected&&t.hostConnected?.();}removeController(t){this._$EO?.delete(t);}_$E_(){const t=new Map,s=this.constructor.elementProperties;for(const i of s.keys())this.hasOwnProperty(i)&&(t.set(i,this[i]),delete this[i]);t.size>0&&(this._$Ep=t);}createRenderRoot(){const t=this.shadowRoot??this.attachShadow(this.constructor.shadowRootOptions);return S$1(t,this.constructor.elementStyles),t}connectedCallback(){this.renderRoot??=this.createRenderRoot(),this.enableUpdating(true),this._$EO?.forEach(t=>t.hostConnected?.());}enableUpdating(t){}disconnectedCallback(){this._$EO?.forEach(t=>t.hostDisconnected?.());}attributeChangedCallback(t,s,i){this._$AK(t,i);}_$ET(t,s){const i=this.constructor.elementProperties.get(t),e=this.constructor._$Eu(t,i);if(void 0!==e&&true===i.reflect){const h=(void 0!==i.converter?.toAttribute?i.converter:u$1).toAttribute(s,i.type);this._$Em=t,null==h?this.removeAttribute(e):this.setAttribute(e,h),this._$Em=null;}}_$AK(t,s){const i=this.constructor,e=i._$Eh.get(t);if(void 0!==e&&this._$Em!==e){const t=i.getPropertyOptions(e),h="function"==typeof t.converter?{fromAttribute:t.converter}:void 0!==t.converter?.fromAttribute?t.converter:u$1;this._$Em=e;const r=h.fromAttribute(s,t.type);this[e]=r??this._$Ej?.get(e)??r,this._$Em=null;}}requestUpdate(t,s,i,e=false,h){if(void 0!==t){const r=this.constructor;if(false===e&&(h=this[t]),i??=r.getPropertyOptions(t),!((i.hasChanged??f$1)(h,s)||i.useDefault&&i.reflect&&h===this._$Ej?.get(t)&&!this.hasAttribute(r._$Eu(t,i))))return;this.C(t,s,i);} false===this.isUpdatePending&&(this._$ES=this._$EP());}C(t,s,{useDefault:i,reflect:e,wrapped:h},r){i&&!(this._$Ej??=new Map).has(t)&&(this._$Ej.set(t,r??s??this[t]),true!==h||void 0!==r)||(this._$AL.has(t)||(this.hasUpdated||i||(s=void 0),this._$AL.set(t,s)),true===e&&this._$Em!==t&&(this._$Eq??=new Set).add(t));}async _$EP(){this.isUpdatePending=true;try{await this._$ES;}catch(t){Promise.reject(t);}const t=this.scheduleUpdate();return null!=t&&await t,!this.isUpdatePending}scheduleUpdate(){return this.performUpdate()}performUpdate(){if(!this.isUpdatePending)return;if(!this.hasUpdated){if(this.renderRoot??=this.createRenderRoot(),this._$Ep){for(const[t,s]of this._$Ep)this[t]=s;this._$Ep=void 0;}const t=this.constructor.elementProperties;if(t.size>0)for(const[s,i]of t){const{wrapped:t}=i,e=this[s];true!==t||this._$AL.has(s)||void 0===e||this.C(s,void 0,i,e);}}let t=false;const s=this._$AL;try{t=this.shouldUpdate(s),t?(this.willUpdate(s),this._$EO?.forEach(t=>t.hostUpdate?.()),this.update(s)):this._$EM();}catch(s){throw t=false,this._$EM(),s}t&&this._$AE(s);}willUpdate(t){}_$AE(t){this._$EO?.forEach(t=>t.hostUpdated?.()),this.hasUpdated||(this.hasUpdated=true,this.firstUpdated(t)),this.updated(t);}_$EM(){this._$AL=new Map,this.isUpdatePending=false;}get updateComplete(){return this.getUpdateComplete()}getUpdateComplete(){return this._$ES}shouldUpdate(t){return  true}update(t){this._$Eq&&=this._$Eq.forEach(t=>this._$ET(t,this[t])),this._$EM();}updated(t){}firstUpdated(t){}};y$1.elementStyles=[],y$1.shadowRootOptions={mode:"open"},y$1[d$1("elementProperties")]=new Map,y$1[d$1("finalized")]=new Map,p$1?.({ReactiveElement:y$1}),(a$1.reactiveElementVersions??=[]).push("2.1.2");

/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */
const t$1=globalThis,i$1=t=>t,s$1=t$1.trustedTypes,e=s$1?s$1.createPolicy("lit-html",{createHTML:t=>t}):void 0,h="$lit$",o$2=`lit$${Math.random().toFixed(9).slice(2)}$`,n$1="?"+o$2,r$2=`<${n$1}>`,l=document,c=()=>l.createComment(""),a=t=>null===t||"object"!=typeof t&&"function"!=typeof t,u=Array.isArray,d=t=>u(t)||"function"==typeof t?.[Symbol.iterator],f="[ \t\n\f\r]",v=/<(?:(!--|\/[^a-zA-Z])|(\/?[a-zA-Z][^>\s]*)|(\/?$))/g,_=/-->/g,m=/>/g,p=RegExp(`>|${f}(?:([^\\s"'>=/]+)(${f}*=${f}*(?:[^ \t\n\f\r"'\`<>=]|("|')|))|$)`,"g"),g=/'/g,$=/"/g,y=/^(?:script|style|textarea|title)$/i,x=t=>(i,...s)=>({_$litType$:t,strings:i,values:s}),b=x(1),E=Symbol.for("lit-noChange"),A=Symbol.for("lit-nothing"),C=new WeakMap,P=l.createTreeWalker(l,129);function V(t,i){if(!u(t)||!t.hasOwnProperty("raw"))throw Error("invalid template strings array");return void 0!==e?e.createHTML(i):i}const N=(t,i)=>{const s=t.length-1,e=[];let n,l=2===i?"<svg>":3===i?"<math>":"",c=v;for(let i=0;i<s;i++){const s=t[i];let a,u,d=-1,f=0;for(;f<s.length&&(c.lastIndex=f,u=c.exec(s),null!==u);)f=c.lastIndex,c===v?"!--"===u[1]?c=_:void 0!==u[1]?c=m:void 0!==u[2]?(y.test(u[2])&&(n=RegExp("</"+u[2],"g")),c=p):void 0!==u[3]&&(c=p):c===p?">"===u[0]?(c=n??v,d=-1):void 0===u[1]?d=-2:(d=c.lastIndex-u[2].length,a=u[1],c=void 0===u[3]?p:'"'===u[3]?$:g):c===$||c===g?c=p:c===_||c===m?c=v:(c=p,n=void 0);const x=c===p&&t[i+1].startsWith("/>")?" ":"";l+=c===v?s+r$2:d>=0?(e.push(a),s.slice(0,d)+h+s.slice(d)+o$2+x):s+o$2+(-2===d?i:x);}return [V(t,l+(t[s]||"<?>")+(2===i?"</svg>":3===i?"</math>":"")),e]};class S{constructor({strings:t,_$litType$:i},e){let r;this.parts=[];let l=0,a=0;const u=t.length-1,d=this.parts,[f,v]=N(t,i);if(this.el=S.createElement(f,e),P.currentNode=this.el.content,2===i||3===i){const t=this.el.content.firstChild;t.replaceWith(...t.childNodes);}for(;null!==(r=P.nextNode())&&d.length<u;){if(1===r.nodeType){if(r.hasAttributes())for(const t of r.getAttributeNames())if(t.endsWith(h)){const i=v[a++],s=r.getAttribute(t).split(o$2),e=/([.?@])?(.*)/.exec(i);d.push({type:1,index:l,name:e[2],strings:s,ctor:"."===e[1]?I:"?"===e[1]?L:"@"===e[1]?z:H}),r.removeAttribute(t);}else t.startsWith(o$2)&&(d.push({type:6,index:l}),r.removeAttribute(t));if(y.test(r.tagName)){const t=r.textContent.split(o$2),i=t.length-1;if(i>0){r.textContent=s$1?s$1.emptyScript:"";for(let s=0;s<i;s++)r.append(t[s],c()),P.nextNode(),d.push({type:2,index:++l});r.append(t[i],c());}}}else if(8===r.nodeType)if(r.data===n$1)d.push({type:2,index:l});else {let t=-1;for(;-1!==(t=r.data.indexOf(o$2,t+1));)d.push({type:7,index:l}),t+=o$2.length-1;}l++;}}static createElement(t,i){const s=l.createElement("template");return s.innerHTML=t,s}}function M(t,i,s=t,e){if(i===E)return i;let h=void 0!==e?s._$Co?.[e]:s._$Cl;const o=a(i)?void 0:i._$litDirective$;return h?.constructor!==o&&(h?._$AO?.(false),void 0===o?h=void 0:(h=new o(t),h._$AT(t,s,e)),void 0!==e?(s._$Co??=[])[e]=h:s._$Cl=h),void 0!==h&&(i=M(t,h._$AS(t,i.values),h,e)),i}class R{constructor(t,i){this._$AV=[],this._$AN=void 0,this._$AD=t,this._$AM=i;}get parentNode(){return this._$AM.parentNode}get _$AU(){return this._$AM._$AU}u(t){const{el:{content:i},parts:s}=this._$AD,e=(t?.creationScope??l).importNode(i,true);P.currentNode=e;let h=P.nextNode(),o=0,n=0,r=s[0];for(;void 0!==r;){if(o===r.index){let i;2===r.type?i=new k(h,h.nextSibling,this,t):1===r.type?i=new r.ctor(h,r.name,r.strings,this,t):6===r.type&&(i=new Z(h,this,t)),this._$AV.push(i),r=s[++n];}o!==r?.index&&(h=P.nextNode(),o++);}return P.currentNode=l,e}p(t){let i=0;for(const s of this._$AV) void 0!==s&&(void 0!==s.strings?(s._$AI(t,s,i),i+=s.strings.length-2):s._$AI(t[i])),i++;}}class k{get _$AU(){return this._$AM?._$AU??this._$Cv}constructor(t,i,s,e){this.type=2,this._$AH=A,this._$AN=void 0,this._$AA=t,this._$AB=i,this._$AM=s,this.options=e,this._$Cv=e?.isConnected??true;}get parentNode(){let t=this._$AA.parentNode;const i=this._$AM;return void 0!==i&&11===t?.nodeType&&(t=i.parentNode),t}get startNode(){return this._$AA}get endNode(){return this._$AB}_$AI(t,i=this){t=M(this,t,i),a(t)?t===A||null==t||""===t?(this._$AH!==A&&this._$AR(),this._$AH=A):t!==this._$AH&&t!==E&&this._(t):void 0!==t._$litType$?this.$(t):void 0!==t.nodeType?this.T(t):d(t)?this.k(t):this._(t);}O(t){return this._$AA.parentNode.insertBefore(t,this._$AB)}T(t){this._$AH!==t&&(this._$AR(),this._$AH=this.O(t));}_(t){this._$AH!==A&&a(this._$AH)?this._$AA.nextSibling.data=t:this.T(l.createTextNode(t)),this._$AH=t;}$(t){const{values:i,_$litType$:s}=t,e="number"==typeof s?this._$AC(t):(void 0===s.el&&(s.el=S.createElement(V(s.h,s.h[0]),this.options)),s);if(this._$AH?._$AD===e)this._$AH.p(i);else {const t=new R(e,this),s=t.u(this.options);t.p(i),this.T(s),this._$AH=t;}}_$AC(t){let i=C.get(t.strings);return void 0===i&&C.set(t.strings,i=new S(t)),i}k(t){u(this._$AH)||(this._$AH=[],this._$AR());const i=this._$AH;let s,e=0;for(const h of t)e===i.length?i.push(s=new k(this.O(c()),this.O(c()),this,this.options)):s=i[e],s._$AI(h),e++;e<i.length&&(this._$AR(s&&s._$AB.nextSibling,e),i.length=e);}_$AR(t=this._$AA.nextSibling,s){for(this._$AP?.(false,true,s);t!==this._$AB;){const s=i$1(t).nextSibling;i$1(t).remove(),t=s;}}setConnected(t){ void 0===this._$AM&&(this._$Cv=t,this._$AP?.(t));}}class H{get tagName(){return this.element.tagName}get _$AU(){return this._$AM._$AU}constructor(t,i,s,e,h){this.type=1,this._$AH=A,this._$AN=void 0,this.element=t,this.name=i,this._$AM=e,this.options=h,s.length>2||""!==s[0]||""!==s[1]?(this._$AH=Array(s.length-1).fill(new String),this.strings=s):this._$AH=A;}_$AI(t,i=this,s,e){const h=this.strings;let o=false;if(void 0===h)t=M(this,t,i,0),o=!a(t)||t!==this._$AH&&t!==E,o&&(this._$AH=t);else {const e=t;let n,r;for(t=h[0],n=0;n<h.length-1;n++)r=M(this,e[s+n],i,n),r===E&&(r=this._$AH[n]),o||=!a(r)||r!==this._$AH[n],r===A?t=A:t!==A&&(t+=(r??"")+h[n+1]),this._$AH[n]=r;}o&&!e&&this.j(t);}j(t){t===A?this.element.removeAttribute(this.name):this.element.setAttribute(this.name,t??"");}}class I extends H{constructor(){super(...arguments),this.type=3;}j(t){this.element[this.name]=t===A?void 0:t;}}class L extends H{constructor(){super(...arguments),this.type=4;}j(t){this.element.toggleAttribute(this.name,!!t&&t!==A);}}class z extends H{constructor(t,i,s,e,h){super(t,i,s,e,h),this.type=5;}_$AI(t,i=this){if((t=M(this,t,i,0)??A)===E)return;const s=this._$AH,e=t===A&&s!==A||t.capture!==s.capture||t.once!==s.once||t.passive!==s.passive,h=t!==A&&(s===A||e);e&&this.element.removeEventListener(this.name,this,s),h&&this.element.addEventListener(this.name,this,t),this._$AH=t;}handleEvent(t){"function"==typeof this._$AH?this._$AH.call(this.options?.host??this.element,t):this._$AH.handleEvent(t);}}class Z{constructor(t,i,s){this.element=t,this.type=6,this._$AN=void 0,this._$AM=i,this.options=s;}get _$AU(){return this._$AM._$AU}_$AI(t){M(this,t);}}const B=t$1.litHtmlPolyfillSupport;B?.(S,k),(t$1.litHtmlVersions??=[]).push("3.3.3");const D=(t,i,s)=>{const e=s?.renderBefore??i;let h=e._$litPart$;if(void 0===h){const t=s?.renderBefore??null;e._$litPart$=h=new k(i.insertBefore(c(),t),t,void 0,s??{});}return h._$AI(t),h};

/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */const s=globalThis;class i extends y$1{constructor(){super(...arguments),this.renderOptions={host:this},this._$Do=void 0;}createRenderRoot(){const t=super.createRenderRoot();return this.renderOptions.renderBefore??=t.firstChild,t}update(t){const r=this.render();this.hasUpdated||(this.renderOptions.isConnected=this.isConnected),super.update(t),this._$Do=D(r,this.renderRoot,this.renderOptions);}connectedCallback(){super.connectedCallback(),this._$Do?.setConnected(true);}disconnectedCallback(){super.disconnectedCallback(),this._$Do?.setConnected(false);}render(){return E}}i._$litElement$=true,i["finalized"]=true,s.litElementHydrateSupport?.({LitElement:i});const o$1=s.litElementPolyfillSupport;o$1?.({LitElement:i});(s.litElementVersions??=[]).push("4.2.2");

/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */
const t=t=>(e,o)=>{ void 0!==o?o.addInitializer(()=>{customElements.define(t,e);}):customElements.define(t,e);};

/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */const o={attribute:true,type:String,converter:u$1,reflect:false,hasChanged:f$1},r$1=(t=o,e,r)=>{const{kind:n,metadata:i}=r;let s=globalThis.litPropertyMetadata.get(i);if(void 0===s&&globalThis.litPropertyMetadata.set(i,s=new Map),"setter"===n&&((t=Object.create(t)).wrapped=true),s.set(r.name,t),"accessor"===n){const{name:o}=r;return {set(r){const n=e.get.call(this);e.set.call(this,r),this.requestUpdate(o,n,t,true,r);},init(e){return void 0!==e&&this.C(o,void 0,t,e),e}}}if("setter"===n){const{name:o}=r;return function(r){const n=this[o];e.call(this,r),this.requestUpdate(o,n,t,true,r);}}throw Error("Unsupported decorator location: "+n)};function n(t){return (e,o)=>"object"==typeof o?r$1(t,e,o):((t,e,o)=>{const r=e.hasOwnProperty(o);return e.constructor.createProperty(o,t),r?Object.getOwnPropertyDescriptor(e,o):void 0})(t,e,o)}

/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */function r(r){return n({...r,state:true,attribute:false})}

let CarCardEditor = class CarCardEditor extends i {
    setConfig(config) {
        this._config = Object.assign({}, config);
    }
    _valueChanged(ev) {
        const target = ev.target;
        if (!target)
            return;
        const key = target.getAttribute("data-key") || target.name;
        const value = target.value || target.checked;
        if (!key)
            return;
        // Handle nested objects
        if (key.includes(".")) {
            const parts = key.split(".");
            const obj = Object.assign({}, this._config);
            let current = obj;
            for (let i = 0; i < parts.length - 1; i++) {
                if (!current[parts[i]]) {
                    current[parts[i]] = {};
                }
                current = current[parts[i]];
            }
            current[parts[parts.length - 1]] = value;
            this._config = obj;
        }
        else {
            this._config = Object.assign(Object.assign({}, this._config), { [key]: value });
        }
        // Fire config changed event
        const event = new CustomEvent("config-changed", {
            bubbles: true,
            composed: true,
            detail: { config: this._config },
        });
        this.dispatchEvent(event);
    }
    _handleSelectChange(ev, key) {
        const target = ev.target;
        this._config = Object.assign(Object.assign({}, this._config), { [key]: target.value });
        const event = new CustomEvent("config-changed", {
            bubbles: true,
            composed: true,
            detail: { config: this._config },
        });
        this.dispatchEvent(event);
    }
    _handleEntityChanged(key, category, value) {
        const v = value || "";
        if (category) {
            const categoryObj = Object.assign({}, (this._config[category] || {}));
            categoryObj[key] = v;
            this._config = Object.assign(Object.assign({}, this._config), { [category]: categoryObj });
        }
        else {
            this._config = Object.assign(Object.assign({}, this._config), { [key]: v });
        }
        const event = new CustomEvent("config-changed", {
            bubbles: true,
            composed: true,
            detail: { config: this._config },
        });
        this.dispatchEvent(event);
    }
    render() {
        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q;
        if (!this.hass || !this._config) {
            return b `<div class="loading">Загрузка...</div>`;
        }
        return b `
      <div class="editor">
        <div class="section">
          <div class="section-title">Основные настройки</div>

          <div class="field">
            <label>Тип транспорта</label>
            <select
              .value=${this._config.vehicle || "car"}
              @change=${(e) => this._handleSelectChange(e, "vehicle")}
            >
              <option value="car" ?selected=${this._config.vehicle === "car"}>
                Легковая
              </option>
              <option
                value="truck"
                ?selected=${this._config.vehicle === "truck"}
              >
                Грузовая
              </option>
              <option
                value="motorcycle"
                ?selected=${this._config.vehicle === "motorcycle"}
              >
                Мотоцикл
              </option>
            </select>
          </div>

          <div class="field">
            <label>Устройство</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${this._config.device || ""}
              .include-filters=${["device_tracker"]}
              .allow-custom-entity=${true}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("device", null, (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Название</label>
            <input
              type="text"
              .value=${this._config.name || ""}
              @input=${this._valueChanged}
              data-key="name"
              placeholder="Мой автомобиль"
            />
          </div>

          <div class="field">
            <label>Ссылка на картинку (необязательно)</label>
            <input
              type="text"
              .value=${this._config.image_url || ""}
              @input=${this._valueChanged}
              data-key="image_url"
              placeholder="/local/.../my-car.png или https://…"
            />
            <div class="hint">Если не указана — используется встроенный силуэт.</div>
          </div>
        </div>

        <div class="section">
          <div class="section-title">Сенсоры</div>

          <div class="field">
            <label>Температура двигателя</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_a = this._config.sensors) === null || _a === void 0 ? void 0 : _a.temperature) || ""}
              .include-filters=${["sensor", "number"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("temperature", "sensors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Уровень топлива</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_b = this._config.sensors) === null || _b === void 0 ? void 0 : _b.fuel) || ""}
              .include-filters=${["sensor", "number"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("fuel", "sensors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Напряжение АКБ</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_c = this._config.sensors) === null || _c === void 0 ? void 0 : _c.battery) || ""}
              .include-filters=${["sensor", "number"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("battery", "sensors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Пробег</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_d = this._config.sensors) === null || _d === void 0 ? void 0 : _d.mileage) || ""}
              .include-filters=${["sensor", "number"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("mileage", "sensors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>
        </div>

        <div class="section">
          <div class="section-title">Управление</div>

          <div class="field">
            <label>Замки дверей</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_e = this._config.controls) === null || _e === void 0 ? void 0 : _e.lock) || ""}
              .include-filters=${["lock"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("lock", "controls", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Двигатель</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_f = this._config.controls) === null || _f === void 0 ? void 0 : _f.engine) || ""}
              .include-filters=${["switch", "input_boolean"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("engine", "controls", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Свет</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_g = this._config.controls) === null || _g === void 0 ? void 0 : _g.lights) || ""}
              .include-filters=${["switch", "input_boolean", "light"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("lights", "controls", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Клаксон / сигнал (кнопка)</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_h = this._config.controls) === null || _h === void 0 ? void 0 : _h.horn) || ""}
              .include-filters=${["button", "scene", "script"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("horn", "controls", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>
        </div>

        <div class="section">
          <div class="section-title">Двери и отсеки</div>

          <div class="field">
            <label>Левая дверь</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_j = this._config.doors) === null || _j === void 0 ? void 0 : _j.left) || ""}
              .include-filters=${["binary_sensor"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("left", "doors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Правая дверь</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_k = this._config.doors) === null || _k === void 0 ? void 0 : _k.right) || ""}
              .include-filters=${["binary_sensor"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("right", "doors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Багажник</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_l = this._config.doors) === null || _l === void 0 ? void 0 : _l.trunk) || ""}
              .include-filters=${["binary_sensor"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("trunk", "doors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Капот</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_m = this._config.doors) === null || _m === void 0 ? void 0 : _m.hood) || ""}
              .include-filters=${["binary_sensor"]}
              @value-changed=${(e) => { var _a; return this._handleEntityChanged("hood", "doors", (_a = e.detail) === null || _a === void 0 ? void 0 : _a.value); }}
            ></ha-entity-picker>
          </div>
        </div>

        <div class="section">
          <div class="section-title">Позиции на изображении</div>
          <div class="section-subtitle">Перетащите ползунки или введите значения 0-100%</div>

          ${this._renderPositionFields()}
        </div>

        <div class="section">
          <div class="section-title">Спидометр</div>

          <div class="field">
            <label>Сущность скорости</label>
            <ha-entity-picker
              .hass=${this.hass}
              .value=${((_o = this._config.speedometer) === null || _o === void 0 ? void 0 : _o.entity) || ""}
              .include-filters=${["sensor", "number"]}
              .allow-custom-entity=${true}
              @value-changed=${(e) => { var _a; return this._handleSpeedometerChange("entity", ((_a = e.detail) === null || _a === void 0 ? void 0 : _a.value) || ""); }}
            ></ha-entity-picker>
          </div>

          <div class="field">
            <label>Макс. скорость</label>
            <input
              type="number"
              .value=${String(((_p = this._config.speedometer) === null || _p === void 0 ? void 0 : _p.max) || 220)}
              @input=${(e) => this._handleSpeedometerChange("max", parseInt(e.target.value, 10))}
              min="100"
              max="400"
              placeholder="220"
            />
          </div>

          <div class="field">
            <label>Единица измерения</label>
            <input
              type="text"
              .value=${((_q = this._config.speedometer) === null || _q === void 0 ? void 0 : _q.unit) || "км/ч"}
              @input=${(e) => this._handleSpeedometerChange("unit", e.target.value)}
              placeholder="км/ч"
            />
          </div>
        </div>
      </div>
    `;
    }
    _handleSpeedometerChange(key, value) {
        const speedometer = Object.assign({}, (this._config.speedometer || {}));
        speedometer[key] = value;
        this._config = Object.assign(Object.assign({}, this._config), { speedometer });
        const event = new CustomEvent("config-changed", {
            bubbles: true,
            composed: true,
            detail: { config: this._config },
        });
        this.dispatchEvent(event);
    }
    _renderPositionFields() {
        const items = [
            { key: "temperature", label: "🌡 Температура", defaults: { x: 45, y: 30 } },
            { key: "fuel", label: "⛽ Топливо", defaults: { x: 45, y: 50 } },
            { key: "battery", label: "🔋 АКБ", defaults: { x: 30, y: 50 } },
            { key: "mileage", label: "📏 Пробег", defaults: { x: 48, y: 66 } },
            { key: "lock", label: "🔒 Замки", defaults: { x: 60, y: 50 } },
            { key: "engine", label: "🚗 Двигатель", defaults: { x: 83, y: 64 } },
            { key: "lights", label: "💡 Свет", defaults: { x: 95, y: 50 } },
            { key: "horn", label: "📯 Гудок", defaults: { x: 73, y: 43 } },
            { key: "left", label: "🚪 Левая дверь", defaults: { x: 30, y: 35 } },
            { key: "right", label: "🚪 Правая дверь", defaults: { x: 60, y: 35 } },
            { key: "trunk", label: "📦 Багажник", defaults: { x: 10, y: 50 } },
            { key: "hood", label: "🔧 Капот", defaults: { x: 85, y: 50 } },
        ];
        return b `
      <div class="position-grid">
        ${items.map((item) => {
            var _a, _b, _c, _d, _e, _f;
            const currentX = (_c = (_b = (_a = this._config.binding_overrides) === null || _a === void 0 ? void 0 : _a[item.key]) === null || _b === void 0 ? void 0 : _b.x) !== null && _c !== void 0 ? _c : item.defaults.x;
            const currentY = (_f = (_e = (_d = this._config.binding_overrides) === null || _d === void 0 ? void 0 : _d[item.key]) === null || _e === void 0 ? void 0 : _e.y) !== null && _f !== void 0 ? _f : item.defaults.y;
            return b `
            <div class="position-row">
              <div class="position-label">${item.label}</div>
              <div class="position-inputs">
                <label class="position-input">
                  <span>X</span>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    .value=${String(currentX)}
                    @input=${(e) => this._handlePositionChange(item.key, "x", e.target.value)}
                  />
                  <span class="position-value">${currentX}%</span>
                </label>
                <label class="position-input">
                  <span>Y</span>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    .value=${String(currentY)}
                    @input=${(e) => this._handlePositionChange(item.key, "y", e.target.value)}
                  />
                  <span class="position-value">${currentY}%</span>
                </label>
              </div>
            </div>
          `;
        })}
      </div>
    `;
    }
    _handlePositionChange(key, axis, value) {
        const numValue = parseInt(value, 10);
        if (isNaN(numValue))
            return;
        const overrides = Object.assign({}, (this._config.binding_overrides || {}));
        if (!overrides[key]) {
            overrides[key] = {};
        }
        overrides[key][axis] = numValue;
        this._config = Object.assign(Object.assign({}, this._config), { binding_overrides: overrides });
        const event = new CustomEvent("config-changed", {
            bubbles: true,
            composed: true,
            detail: { config: this._config },
        });
        this.dispatchEvent(event);
    }
    static get styles() {
        return i$3 `
      .editor {
        padding: 16px;
      }

      .section {
        margin-bottom: 24px;
      }

      .section-title {
        font-size: 14px;
        font-weight: 600;
        color: var(--primary-text-color, #fff);
        margin-bottom: 12px;
        padding-bottom: 8px;
        border-bottom: 1px solid var(--divider-color, rgba(255, 255, 255, 0.1));
      }

      .field {
        margin-bottom: 12px;
      }

      .field label {
        display: block;
        font-size: 12px;
        color: var(--secondary-text-color, #aaa);
        margin-bottom: 4px;
      }

      .field select,
      .field input {
        width: 100%;
        padding: 8px 12px;
        border: 1px solid var(--divider-color, rgba(255, 255, 255, 0.2));
        border-radius: 4px;
        background: var(--card-background-color, #1c1c1c);
        color: var(--primary-text-color, #fff);
        font-size: 14px;
      }

      .field select:focus,
      .field input:focus {
        outline: none;
        border-color: var(--primary-color, #03a9f4);
      }

      .loading {
        text-align: center;
        padding: 16px;
        color: var(--secondary-text-color, #aaa);
      }

      .section-subtitle {
        font-size: 11px;
        color: var(--secondary-text-color, #aaa);
        margin-bottom: 12px;
      }

      .position-grid {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .position-row {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .position-label {
        font-size: 12px;
        font-weight: 500;
        color: var(--primary-text-color, #fff);
      }

      .position-inputs {
        display: flex;
        gap: 16px;
      }

      .position-input {
        display: flex;
        align-items: center;
        gap: 8px;
        flex: 1;
      }

      .position-input span:first-child {
        font-size: 11px;
        color: var(--secondary-text-color, #aaa);
        min-width: 16px;
      }

      .position-input input[type="range"] {
        flex: 1;
        height: 4px;
        -webkit-appearance: none;
        background: var(--divider-color, rgba(255, 255, 255, 0.2));
        border-radius: 2px;
        border: none;
        padding: 0;
      }

      .position-input input[type="range"]::-webkit-slider-thumb {
        -webkit-appearance: none;
        width: 14px;
        height: 14px;
        background: var(--primary-color, #03a9f4);
        border-radius: 50%;
        cursor: pointer;
      }

      .position-value {
        font-size: 11px;
        color: var(--primary-color, #03a9f4);
        min-width: 32px;
        text-align: right;
        font-family: monospace;
      }
    `;
    }
};
__decorate([
    n({ attribute: false })
], CarCardEditor.prototype, "hass", void 0);
__decorate([
    n({ attribute: false })
], CarCardEditor.prototype, "lovelace", void 0);
__decorate([
    r()
], CarCardEditor.prototype, "_config", void 0);
CarCardEditor = __decorate([
    t("cartelemetry-vehicle-card-editor")
], CarCardEditor);

// Binding templates for different vehicle types
const BINDING_TEMPLATES = {
    car: {
        image_file: "car-silhouette.png",
        sensors: {
            temperature: { x: 45, y: 30, icon: "🌡", color: "#ef5350" },
            fuel: { x: 45, y: 50, icon: "⛽", color: "#ffb74d" },
            battery: { x: 30, y: 50, icon: "🔋", color: "#66bb6a" },
            mileage: { x: 48, y: 66, icon: "📏", color: "#42a5f5" },
        },
        doors: {
            left: { x: 30, y: 35, icon: "🚪" },
            right: { x: 60, y: 35, icon: "🚪" },
            trunk: { x: 10, y: 50, icon: "📦" },
            hood: { x: 85, y: 50, icon: "🔧" },
        },
        controls: {
            lock: { x: 60, y: 50, icon: "🔒" },
            engine: { x: 83, y: 64, icon: "🚗" },
            lights: { x: 95, y: 50, icon: "💡" },
            horn: { x: 73, y: 43, icon: "📯" },
        },
    },
    truck: {
        image_file: "truck-old.png",
        sensors: {
            temperature: { x: 15, y: 25, icon: "🌡", color: "#ef5350" },
            fuel: { x: 80, y: 40, icon: "⛽", color: "#ffb74d" },
            battery: { x: 10, y: 45, icon: "🔋", color: "#66bb6a" },
            mileage: { x: 50, y: 65, icon: "📏", color: "#42a5f5" },
        },
        doors: {
            left: { x: 20, y: 40, icon: "🚪" },
            right: { x: 80, y: 40, icon: "🚪" },
            trunk: { x: 90, y: 45, icon: "📦" },
            hood: { x: 10, y: 30, icon: "🔧" },
        },
        controls: {
            lock: { x: 50, y: 10, icon: "🔒" },
            engine: { x: 50, y: 80, icon: "🚗" },
            lights: { x: 10, y: 15, icon: "💡" },
            horn: { x: 90, y: 15, icon: "📯" },
        },
    },
    motorcycle: {
        image_file: "motorcycle-classic.png",
        sensors: {
            temperature: { x: 40, y: 45, icon: "🌡", color: "#ef5350" },
            fuel: { x: 55, y: 35, icon: "⛽", color: "#ffb74d" },
            battery: { x: 35, y: 55, icon: "🔋", color: "#66bb6a" },
            mileage: { x: 60, y: 55, icon: "📏", color: "#42a5f5" },
        },
        doors: {},
        controls: {
            lock: { x: 50, y: 15, icon: "🔒" },
            engine: { x: 45, y: 50, icon: "🚗" },
            lights: { x: 25, y: 25, icon: "💡" },
            horn: { x: 70, y: 25, icon: "📯" },
        },
    },
};
let CarCard = class CarCard extends i {
    static getConfigEditor() {
        return document.createElement("cartelemetry-vehicle-card-editor");
    }
    static getConfigElement() {
        return document.createElement("cartelemetry-vehicle-card-editor");
    }
    static getStubConfig() {
        return {
            type: "custom:cartelemetry-vehicle-card",
            vehicle: "car",
            device: "device_tracker.my_car",
            name: "My Car",
            sensors: {
                temperature: "sensor.car_cabin_temperature",
                fuel: "sensor.car_fuel",
                battery: "sensor.car_battery",
                mileage: "sensor.car_range",
            },
            controls: {
                lock: "lock.car_doors_lock",
                engine: "switch.car_drl",
                lights: "switch.car_fog",
                horn: "button.car_doors_lock",
            },
            doors: {
                left: "binary_sensor.car_driver_door",
                right: "binary_sensor.car_passenger_door",
                trunk: "binary_sensor.car_trunk",
                hood: "binary_sensor.car_bonnet",
            },
            // Позиции иконок поверх картинки авто (x/y в процентах от картинки).
            // image_url — ссылка на свою картинку (PNG/JPG). В примере — стандартный
            // силуэт; замените на свою картинку, например /local/community/cartelemetry-card/my-car.png
            image_url: "/local/community/cartelemetry-card/assets/car-silhouette.png",
            binding_overrides: {
                temperature: { x: 45, y: 30 },
                fuel: { x: 45, y: 50 },
                battery: { x: 30, y: 50 },
                mileage: { x: 48, y: 66 },
                lock: { x: 60, y: 50 },
                engine: { x: 83, y: 64 },
                lights: { x: 95, y: 50 },
                horn: { x: 73, y: 43 },
                left: { x: 30, y: 35 },
                right: { x: 60, y: 35 },
                trunk: { x: 10, y: 50 },
                hood: { x: 85, y: 50 },
            },
        };
    }
    setConfig(config) {
        if (!config.vehicle) {
            throw new Error("You need to define a vehicle type (car, truck, motorcycle)");
        }
        if (!BINDING_TEMPLATES[config.vehicle]) {
            throw new Error(`Unknown vehicle type: ${config.vehicle}. Use car, truck, or motorcycle`);
        }
        this.config = config;
        this._template = BINDING_TEMPLATES[config.vehicle];
        this.requestUpdate();
    }
    getCardSize() {
        return 6;
    }
    getGridOptions() {
        return {
            rows: 4,
            columns: 6,
            min_rows: 4,
            max_rows: 6,
        };
    }
    _getEntityState(entityId) {
        if (!this.hass || !entityId)
            return "unavailable";
        const entity = this.hass.states[entityId];
        return entity ? entity.state : "unavailable";
    }
    _getEntityAttribute(entityId, attribute) {
        var _a;
        if (!this.hass || !entityId)
            return undefined;
        const entity = this.hass.states[entityId];
        return (_a = entity === null || entity === void 0 ? void 0 : entity.attributes) === null || _a === void 0 ? void 0 : _a[attribute];
    }
    _callService(domain, service, entityId, data = {}) {
        if (!this.hass)
            return;
        this.hass.callService(domain, service, Object.assign({ entity_id: entityId }, data));
    }
    _toggleEntity(entityId) {
        if (!entityId)
            return;
        this._callService("homeassistant", "toggle", entityId);
    }
    _pressButton(entityId) {
        if (!entityId)
            return;
        this._callService("button", "press", entityId);
    }
    _getImageUrl() {
        // A direct image link overrides the bundled vehicle image entirely.
        if (this.config.image_url)
            return this.config.image_url;
        const base = this.config.image_base || "/local/community/cartelemetry-card/assets";
        return `${base}/${this._template.image_file}`;
    }
    _formatSensorValue(entityId, sensorType) {
        const state = this._getEntityState(entityId);
        if (state === "unavailable")
            return "--";
        // Prefer the entity's own unit (our sensors: soc %, range km, temps °C…);
        // fall back to type-based formatting for entities without a unit.
        const unit = this._getEntityAttribute(entityId, "unit_of_measurement") || "";
        const value = parseFloat(state);
        if (unit) {
            return isNaN(value) ? `${state} ${unit}` : `${value} ${unit}`;
        }
        switch (sensorType) {
            case "temperature":
                return isNaN(value) ? state : `${Math.round(value)}°C`;
            case "fuel":
                return isNaN(value) ? state : `${Math.round(value)}%`;
            case "battery":
                return isNaN(value) ? state : `${value.toFixed(1)}%`;
            case "mileage":
                return isNaN(value) ? state : `${Math.round(value).toLocaleString()} км`;
            default:
                return state;
        }
    }
    _isDoorOpen(entityId) {
        return this._getEntityState(entityId) === "on";
    }
    _renderSensorIndicator(sensorType, entityId, binding) {
        if (!entityId)
            return b ``;
        const value = this._formatSensorValue(entityId, sensorType);
        const color = binding.color || "#fff";
        return b `
      <div
        class="sensor-indicator"
        style="left: ${binding.x}%; top: ${binding.y}%; --indicator-color: ${color}"
        title="${sensorType}: ${value}"
      >
        <span class="sensor-icon">${binding.icon || "●"}</span>
        <span class="sensor-value">${value}</span>
      </div>
    `;
    }
    _renderDoorIndicator(doorType, entityId, binding) {
        if (!entityId)
            return b ``;
        const isOpen = this._isDoorOpen(entityId);
        const statusClass = isOpen ? "open" : "closed";
        return b `
      <div
        class="door-indicator ${statusClass}"
        style="left: ${binding.x}%; top: ${binding.y}%"
        title="${doorType}: ${isOpen ? "открыта" : "закрыта"}"
      >
        <span class="door-icon">${binding.icon || "🚪"}</span>
      </div>
    `;
    }
    _renderControlButton(controlType, entityId, binding) {
        if (!entityId)
            return b ``;
        const isActive = this._getEntityState(entityId) === "on";
        const statusClass = isActive ? "active" : "inactive";
        const domain = (entityId.split(".")[0] || "").toLowerCase();
        // binary_sensor → read-only indicator (lights/signal state), no click.
        if (domain === "binary_sensor") {
            return b `
        <div
          class="control-button ${statusClass} control-readonly"
          style="left: ${binding.x}%; top: ${binding.y}%"
          title="${controlType}"
        >
          <span class="control-icon">${binding.icon || "🔘"}</span>
        </div>
      `;
        }
        const handleClick = () => {
            if (controlType === "horn" || domain === "button"
                || domain === "scene" || domain === "script") {
                this._pressButton(entityId);
            }
            else {
                this._toggleEntity(entityId);
            }
        };
        return b `
      <div
        class="control-button ${statusClass}"
        style="left: ${binding.x}%; top: ${binding.y}%"
        @click=${handleClick}
        title="${controlType}"
      >
        <span class="control-icon">${binding.icon || "🔘"}</span>
      </div>
    `;
    }
    _renderBindingIndicators() {
        if (!this._template)
            return b ``;
        const config = this.config;
        const overrides = config.binding_overrides || {};
        const sensorsHtml = Object.entries(this._template.sensors).map(([type, binding]) => {
            var _a, _b, _c;
            const entityId = ((_a = config.sensors) === null || _a === void 0 ? void 0 : _a[type]) || "";
            const override = overrides[type] || {};
            const finalBinding = Object.assign(Object.assign({}, binding), { x: (_b = override.x) !== null && _b !== void 0 ? _b : binding.x, y: (_c = override.y) !== null && _c !== void 0 ? _c : binding.y });
            return this._renderSensorIndicator(type, entityId, finalBinding);
        });
        const doorsHtml = Object.entries(this._template.doors).map(([type, binding]) => {
            var _a, _b, _c;
            const entityId = ((_a = config.doors) === null || _a === void 0 ? void 0 : _a[type]) || "";
            const override = overrides[type] || {};
            const finalBinding = Object.assign(Object.assign({}, binding), { x: (_b = override.x) !== null && _b !== void 0 ? _b : binding.x, y: (_c = override.y) !== null && _c !== void 0 ? _c : binding.y });
            return this._renderDoorIndicator(type, entityId, finalBinding);
        });
        const controlsHtml = Object.entries(this._template.controls).map(([type, binding]) => {
            var _a, _b, _c;
            const entityId = ((_a = config.controls) === null || _a === void 0 ? void 0 : _a[type]) || "";
            const override = overrides[type] || {};
            const finalBinding = Object.assign(Object.assign({}, binding), { x: (_b = override.x) !== null && _b !== void 0 ? _b : binding.x, y: (_c = override.y) !== null && _c !== void 0 ? _c : binding.y });
            return this._renderControlButton(type, entityId, finalBinding);
        });
        return b `${sensorsHtml}${doorsHtml}${controlsHtml}`;
    }
    _renderSensorBar() {
        if (!this.config.sensors)
            return b ``;
        const sensors = this.config.sensors;
        const items = [];
        if (sensors.temperature) {
            items.push(b `
        <div class="sensor-bar-item">
          <span class="sensor-bar-icon">🌡</span>
          <span class="sensor-bar-value">${this._formatSensorValue(sensors.temperature, "temperature")}</span>
        </div>
      `);
        }
        if (sensors.fuel) {
            items.push(b `
        <div class="sensor-bar-item">
          <span class="sensor-bar-icon">⛽</span>
          <span class="sensor-bar-value">${this._formatSensorValue(sensors.fuel, "fuel")}</span>
        </div>
      `);
        }
        if (sensors.battery) {
            items.push(b `
        <div class="sensor-bar-item">
          <span class="sensor-bar-icon">🔋</span>
          <span class="sensor-bar-value">${this._formatSensorValue(sensors.battery, "battery")}</span>
        </div>
      `);
        }
        if (sensors.mileage) {
            items.push(b `
        <div class="sensor-bar-item">
          <span class="sensor-bar-icon">📏</span>
          <span class="sensor-bar-value">${this._formatSensorValue(sensors.mileage, "mileage")}</span>
        </div>
      `);
        }
        return b `<div class="sensor-bar">${items}</div>`;
    }
    _renderControlBar() {
        if (!this.config.controls)
            return b ``;
        const controls = this.config.controls;
        const items = [];
        if (controls.lock) {
            const isActive = this._getEntityState(controls.lock) === "on";
            items.push(b `
        <button
          class="control-bar-button ${isActive ? "active" : ""}"
          @click=${() => this._toggleEntity(controls.lock)}
          title="Замки"
        >
          <span class="control-bar-icon">${isActive ? "🔒" : "🔓"}</span>
          <span class="control-bar-label">Замки</span>
        </button>
      `);
        }
        if (controls.engine) {
            const isActive = this._getEntityState(controls.engine) === "on";
            items.push(b `
        <button
          class="control-bar-button ${isActive ? "active" : ""}"
          @click=${() => this._toggleEntity(controls.engine)}
          title="Двигатель"
        >
          <span class="control-bar-icon">🚗</span>
          <span class="control-bar-label">Двигатель</span>
        </button>
      `);
        }
        if (controls.lights) {
            const isActive = this._getEntityState(controls.lights) === "on";
            items.push(b `
        <button
          class="control-bar-button ${isActive ? "active" : ""}"
          @click=${() => this._toggleEntity(controls.lights)}
          title="Свет"
        >
          <span class="control-bar-icon">${isActive ? "💡" : "🔅"}</span>
          <span class="control-bar-label">Свет</span>
        </button>
      `);
        }
        if (controls.horn) {
            const domain = (controls.horn.split(".")[0] || "").toLowerCase();
            const isActive = this._getEntityState(controls.horn) === "on";
            items.push(b `
        <button
          class="control-bar-button ${isActive ? "active" : ""}"
          @click=${() => {
                if (domain !== "binary_sensor")
                    this._pressButton(controls.horn);
            }}
          title="Клаксон"
        >
          <span class="control-bar-icon">📯</span>
          <span class="control-bar-label">Гудок</span>
        </button>
      `);
        }
        return b `<div class="control-bar">${items}</div>`;
    }
    _renderDoorStatus() {
        if (!this.config.doors)
            return b ``;
        const doors = this.config.doors;
        const items = [];
        if (doors.left) {
            const isOpen = this._isDoorOpen(doors.left);
            items.push(b `
        <div class="door-status-item ${isOpen ? "open" : "closed"}">
          <span class="door-status-icon">🚪</span>
          <span class="door-status-label">Левая: ${isOpen ? "открыта" : "закрыта"}</span>
        </div>
      `);
        }
        if (doors.right) {
            const isOpen = this._isDoorOpen(doors.right);
            items.push(b `
        <div class="door-status-item ${isOpen ? "open" : "closed"}">
          <span class="door-status-icon">🚪</span>
          <span class="door-status-label">Правая: ${isOpen ? "открыта" : "закрыта"}</span>
        </div>
      `);
        }
        if (doors.trunk) {
            const isOpen = this._isDoorOpen(doors.trunk);
            items.push(b `
        <div class="door-status-item ${isOpen ? "open" : "closed"}">
          <span class="door-status-icon">📦</span>
          <span class="door-status-label">Багажник: ${isOpen ? "открыт" : "закрыт"}</span>
        </div>
      `);
        }
        if (doors.hood) {
            const isOpen = this._isDoorOpen(doors.hood);
            items.push(b `
        <div class="door-status-item ${isOpen ? "open" : "closed"}">
          <span class="door-status-icon">🔧</span>
          <span class="door-status-label">Капот: ${isOpen ? "открыт" : "закрыт"}</span>
        </div>
      `);
        }
        return b `<div class="door-status-bar">${items}</div>`;
    }
    _renderSpeedometer() {
        if (!this.config.speedometer)
            return b ``;
        const config = this.config.speedometer;
        const speed = parseFloat(this._getEntityState(config.entity)) || 0;
        const maxSpeed = config.max || 220;
        const unit = config.unit || "км/ч";
        const zones = config.zones || [
            { from: 0, to: 60, color: "#66bb6a" },
            { from: 60, to: 120, color: "#ffb74d" },
            { from: 120, to: maxSpeed, color: "#ef5350" },
        ];
        // SVG parameters
        const width = 200;
        const height = 120;
        const cx = width / 2;
        const cy = height - 10;
        const radius = 85;
        const startAngle = -180;
        const endAngle = 0;
        const totalAngle = endAngle - startAngle;
        // Calculate needle angle
        const speedPercent = Math.min(speed / maxSpeed, 1);
        const needleAngle = startAngle + speedPercent * totalAngle;
        // Helper to convert angle to coordinates
        const polarToCartesian = (angle, r) => {
            const rad = (angle * Math.PI) / 180;
            return {
                x: cx + r * Math.cos(rad),
                y: cy + r * Math.sin(rad),
            };
        };
        // Create arc path
        const createArc = (fromAngle, toAngle, r) => {
            const start = polarToCartesian(fromAngle, r);
            const end = polarToCartesian(toAngle, r);
            const largeArc = toAngle - fromAngle > 180 ? 1 : 0;
            return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 1 ${end.x} ${end.y}`;
        };
        // Generate tick marks
        const ticks = [];
        const tickCount = maxSpeed / 20;
        for (let i = 0; i <= tickCount; i++) {
            const tickAngle = startAngle + (i / tickCount) * totalAngle;
            const tickPercent = i / tickCount;
            const innerR = tickPercent % 0.5 === 0 ? radius - 15 : radius - 10;
            const outerR = radius - 5;
            const inner = polarToCartesian(tickAngle, innerR);
            const outer = polarToCartesian(tickAngle, outerR);
            const labelR = radius - 25;
            const label = polarToCartesian(tickAngle, labelR);
            const isMajor = tickPercent % 0.5 === 0;
            const strokeColor = isMajor ? "#fff" : "#666";
            const strokeWidth = isMajor ? 2 : 1;
            ticks.push(b `
        <line
          x1="${inner.x}" y1="${inner.y}"
          x2="${outer.x}" y2="${outer.y}"
          stroke="${strokeColor}"
          stroke-width="${strokeWidth}"
        />
        ${isMajor ? b `
          <text
            x="${label.x}" y="${label.y}"
            fill="#aaa"
            font-size="10"
            text-anchor="middle"
            dominant-baseline="middle"
          >${Math.round(tickPercent * maxSpeed)}</text>
        ` : ""}
      `);
        }
        // Zone arcs
        const zoneArcs = zones.map((zone) => {
            const fromAngle = startAngle + (zone.from / maxSpeed) * totalAngle;
            const toAngle = startAngle + (zone.to / maxSpeed) * totalAngle;
            return b `
        <path
          d="${createArc(fromAngle, toAngle, radius)}"
          fill="none"
          stroke="${zone.color}"
          stroke-width="8"
          stroke-linecap="round"
          opacity="0.6"
        />
      `;
        });
        // Needle tip
        const needleTip = polarToCartesian(needleAngle, radius - 20);
        const needleBaseLeft = polarToCartesian(needleAngle + 90, 5);
        const needleBaseRight = polarToCartesian(needleAngle - 90, 5);
        // Get current zone color
        const currentZone = zones.find((z) => speed >= z.from && speed < z.to) || zones[zones.length - 1];
        return b `
      <div class="speedometer-container">
        <svg class="speedometer-svg" viewBox="0 0 ${width} ${height}">
          <!-- Background arc -->
          <path
            d="${createArc(startAngle, endAngle, radius)}"
            fill="none"
            stroke="#333"
            stroke-width="8"
            stroke-linecap="round"
          />

          <!-- Zone arcs -->
          ${zoneArcs}

          <!-- Tick marks -->
          ${ticks}

          <!-- Needle -->
          <polygon
            points="${needleTip.x},${needleTip.y} ${needleBaseLeft.x},${needleBaseLeft.y} ${needleBaseRight.x},${needleBaseRight.y}"
            fill="${currentZone.color}"
            filter="drop-shadow(0 2px 4px rgba(0,0,0,0.5))"
          />

          <!-- Center circle -->
          <circle cx="${cx}" cy="${cy}" r="8" fill="#333" stroke="#555" stroke-width="2"/>
          <circle cx="${cx}" cy="${cy}" r="4" fill="${currentZone.color}"/>
        </svg>

        <div class="speedometer-digital">
          <span class="speedometer-value" style="color: ${currentZone.color}">${Math.round(speed)}</span>
          <span class="speedometer-unit">${unit}</span>
        </div>
      </div>
    `;
    }
    render() {
        if (!this.config || !this._template) {
            return b `<div class="error">Configuration error</div>`;
        }
        const deviceName = this.config.name || this.config.vehicle;
        const deviceState = this.config.device
            ? this._getEntityState(this.config.device)
            : "online";
        const isOnline = deviceState !== "unavailable" &&
            deviceState !== "offline" &&
            deviceState !== "off";
        return b `
      <ha-card>
        <div class="card-header">
          <span class="card-title">${deviceName}</span>
          <span class="status-indicator ${isOnline ? "online" : "offline"}">
            ${isOnline ? "●" : "○"}
          </span>
        </div>

        <div class="vehicle-image-container">
          <img
            class="vehicle-image"
            src="${this._getImageUrl()}"
            alt="${this.config.vehicle}"
          />
          ${this._renderBindingIndicators()}
        </div>

        ${this._renderSpeedometer()}
        ${this._renderSensorBar()} ${this._renderControlBar()} ${this._renderDoorStatus()}
      </ha-card>
    `;
    }
    static get styles() {
        return i$3 `
      :host {
        --card-primary-color: var(--ha-card-primary-color, #03a9f4);
        --card-background-color: var(--ha-card-background-color, #1c1c1c);
        --card-text-color: var(--ha-card-text-color, #fff);
        --card-border-radius: var(--ha-card-border-radius, 12px);
      }

      ha-card {
        background-color: var(--card-background-color);
        border-radius: var(--card-border-radius);
        overflow: hidden;
      }

      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      }

      .card-title {
        font-size: 18px;
        font-weight: 600;
        color: var(--card-text-color);
        text-transform: capitalize;
      }

      .status-indicator {
        font-size: 12px;
        padding: 4px 8px;
        border-radius: 12px;
      }

      .status-indicator.online {
        color: #66bb6a;
      }

      .status-indicator.offline {
        color: #ef5350;
      }

      .vehicle-image-container {
        position: relative;
        width: 100%;
        padding: 16px;
        box-sizing: border-box;
      }

      .vehicle-image {
        width: 100%;
        height: auto;
        display: block;
        filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.3));
      }

      .sensor-indicator,
      .door-indicator,
      .control-button {
        position: absolute;
        transform: translate(-50%, -50%);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: transform 0.2s ease;
      }

      .sensor-indicator:hover,
      .door-indicator:hover,
      .control-button:hover {
        transform: translate(-50%, -50%) scale(1.2);
        z-index: 10;
      }

      .control-readonly,
      .control-readonly:hover {
        cursor: default;
        transform: translate(-50%, -50%);
        z-index: auto;
      }

      .sensor-indicator {
        background: rgba(0, 0, 0, 0.7);
        border-radius: 8px;
        padding: 4px 8px;
        border: 2px solid var(--indicator-color, #fff);
      }

      .sensor-icon {
        font-size: 14px;
        line-height: 1;
      }

      .sensor-value {
        font-size: 10px;
        color: #fff;
        white-space: nowrap;
      }

      .door-indicator {
        width: 28px;
        height: 28px;
        background: rgba(0, 0, 0, 0.7);
        border-radius: 50%;
        border: 2px solid #fff;
      }

      .door-indicator.open {
        border-color: #ef5350;
        animation: pulse 1.5s infinite;
      }

      .door-indicator.closed {
        border-color: #66bb6a;
      }

      .door-icon {
        font-size: 14px;
      }

      .control-button {
        width: 32px;
        height: 32px;
        background: rgba(0, 0, 0, 0.7);
        border-radius: 50%;
        border: 2px solid #fff;
        cursor: pointer;
      }

      .control-button.active {
        background: var(--card-primary-color);
        border-color: var(--card-primary-color);
      }

      .control-button:hover {
        box-shadow: 0 0 12px rgba(3, 169, 244, 0.5);
      }

      .control-icon {
        font-size: 16px;
      }

      .sensor-bar {
        display: flex;
        justify-content: space-around;
        padding: 12px 16px;
        background: rgba(0, 0, 0, 0.3);
        border-top: 1px solid rgba(255, 255, 255, 0.1);
      }

      .sensor-bar-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
      }

      .sensor-bar-icon {
        font-size: 18px;
      }

      .sensor-bar-value {
        font-size: 12px;
        color: var(--card-text-color);
        font-weight: 500;
      }

      .control-bar {
        display: flex;
        justify-content: space-around;
        padding: 12px 16px;
        background: rgba(0, 0, 0, 0.2);
        border-top: 1px solid rgba(255, 255, 255, 0.1);
      }

      .control-bar-button {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        background: transparent;
        border: none;
        cursor: pointer;
        padding: 8px;
        border-radius: 8px;
        transition: background 0.2s ease;
      }

      .control-bar-button:hover {
        background: rgba(255, 255, 255, 0.1);
      }

      .control-bar-button.active {
        background: rgba(3, 169, 244, 0.3);
      }

      .control-bar-icon {
        font-size: 24px;
      }

      .control-bar-label {
        font-size: 10px;
        color: var(--card-text-color);
        text-transform: capitalize;
      }

      .door-status-bar {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-around;
        padding: 12px 16px;
        background: rgba(0, 0, 0, 0.3);
        border-top: 1px solid rgba(255, 255, 255, 0.1);
      }

      .door-status-item {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 4px 8px;
        border-radius: 4px;
      }

      .door-status-item.open {
        background: rgba(239, 83, 80, 0.2);
      }

      .door-status-item.closed {
        background: rgba(102, 187, 106, 0.2);
      }

      .door-status-icon {
        font-size: 14px;
      }

      .door-status-label {
        font-size: 11px;
        color: var(--card-text-color);
      }

      .speedometer-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 16px;
        background: rgba(0, 0, 0, 0.3);
        border-top: 1px solid rgba(255, 255, 255, 0.1);
      }

      .speedometer-svg {
        width: 100%;
        max-width: 200px;
        height: auto;
      }

      .speedometer-digital {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin-top: -30px;
      }

      .speedometer-value {
        font-size: 36px;
        font-weight: 700;
        line-height: 1;
        text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
      }

      .speedometer-unit {
        font-size: 12px;
        color: var(--secondary-text-color, #aaa);
        text-transform: uppercase;
      }

      @keyframes pulse {
        0%,
        100% {
          opacity: 1;
        }
        50% {
          opacity: 0.5;
        }
      }

      .error {
        padding: 16px;
        text-align: center;
        color: #ef5350;
      }
    `;
    }
};
__decorate([
    n({ attribute: false })
], CarCard.prototype, "hass", void 0);
__decorate([
    r()
], CarCard.prototype, "config", void 0);
__decorate([
    r()
], CarCard.prototype, "_template", void 0);
CarCard = __decorate([
    t("cartelemetry-vehicle-card")
], CarCard);
// Register the card
window.customCards = window.customCards || [];
window.customCards.push({
    type: "cartelemetry-vehicle-card",
    name: "CARTelemetry Vehicle Card",
    description: "Vehicle dashboard with sensors, doors and controls (car/truck/motorcycle)",
});

export { CarCard };
