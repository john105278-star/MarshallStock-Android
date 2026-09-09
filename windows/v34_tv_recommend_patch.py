from pathlib import Path

p=Path('windows/marshall_stock_v30.py')
s=p.read_text(encoding='utf-8')

s=s.replace('APP_NAME = "Marshall Stock PRO v3.3 VISION JPG"','APP_NAME = "Marshall Stock PRO v3.4 VISION TRADING"')
s=s.replace('APP_NAME = "Marshall Stock PRO v3.2 VISION"','APP_NAME = "Marshall Stock PRO v3.4 VISION TRADING"')
s=s.replace('APP_NAME = "Marshall Stock PRO v3.0"','APP_NAME = "Marshall Stock PRO v3.4 VISION TRADING"')

# Add standard-library imports used by the TradingView launcher and parallel enrichment.
if 'import webbrowser' not in s:
    s=s.replace('import json, re, threading, statistics, math, urllib.request, urllib.parse, hashlib, time, os\n',
                'import json, re, threading, statistics, math, urllib.request, urllib.parse, hashlib, time, os, webbrowser, concurrent.futures\n',1)

# Add recommendation + TradingView tabs.
old='for key,title in [("scan","🚀 초특급 TOP20"),("analysis","🔎 종목 종합분석"),("jpg","📷 JPG 차트분석"),("market","🌍 시장환경"),("theme","🔥 테마 레이더"),("flow","💰 외국인·기관 수급"),("news","📰 뉴스·리서치"),("watch","⭐ 관심종목 LIVE"),("portfolio","💼 10억 가상투자"),("diag","🛠 데이터 상태")]:'
new='for key,title in [("scan","🚀 초특급 TOP20"),("recommend","🎯 마샬 추천 TOP20"),("analysis","🔎 종목 종합분석"),("tv","📈 TradingView 차트"),("jpg","📷 JPG 차트분석"),("market","🌍 시장환경"),("theme","🔥 테마 레이더"),("flow","💰 외국인·기관 수급"),("news","📰 뉴스·리서치"),("watch","⭐ 관심종목 LIVE"),("portfolio","💼 10억 가상투자"),("diag","🛠 데이터 상태")]:'
if old not in s:
    raise SystemExit('v3.4 tab anchor missing')
s=s.replace(old,new,1)

old_calls='self._scan_tab();self._analysis_tab();self._jpg_tab();self._market_tab();self._theme_tab();self._flow_tab();self._news_tab();self._watch_tab();self._portfolio_tab();self._diag_tab()'
new_calls='self._scan_tab();self._recommend_tab();self._analysis_tab();self._tradingview_tab();self._jpg_tab();self._market_tab();self._theme_tab();self._flow_tab();self._news_tab();self._watch_tab();self._portfolio_tab();self._diag_tab()'
if old_calls not in s:
    raise SystemExit('v3.4 init call anchor missing')
s=s.replace(old_calls,new_calls,1)

# UI methods are inserted before the JPG tab so all business logic remains untouched.
needle='    def _jpg_tab(self):\n'
if needle not in s:
    raise SystemExit('v3.4 jpg method anchor missing')

methods=r'''    def _recommend_tab(self):
        f=self.tabs["recommend"]
        top=tk.Frame(f,bg="#0f172a");top.pack(fill="x",padx=14,pady=12)
        self.btn(top,"🎯 추천종목 TOP20 정밀스캔",self.refresh_recommend,True).pack(side="left")
        tk.Label(top,text="차트 40% · 외국인/기관 25% · 뉴스 20% · 시장신호 10% · 시장환경 5%",bg="#0f172a",fg="#60a5fa",font=("Malgun Gothic",10,"bold")).pack(side="left",padx=14)
        self.recommend_status=tk.Label(top,text="대기 중",bg="#0f172a",fg="#fbbf24",font=("Malgun Gothic",10,"bold"));self.recommend_status.pack(side="right")

        pan=tk.PanedWindow(f,orient="horizontal",bg="#0f172a",sashwidth=6);pan.pack(fill="both",expand=True,padx=14,pady=(0,14))
        left=tk.Frame(pan,bg="#0f172a");right=tk.Frame(pan,bg="#0f172a");pan.add(left,minsize=850);pan.add(right,minsize=520)
        self.recommend_tree=self.tree(left,["순위","종목","코드","현재가","등락%","종합","차트","수급","뉴스","신호","등급"],[55,170,75,105,80,75,70,70,70,70,65])
        self.recommend_tree.pack(fill="both",expand=True)
        self.recommend_tree.bind("<<TreeviewSelect>>",self.recommend_selected)
        self.recommend_text=tk.Text(right,bg="#0b1220",fg="#e5e7eb",font=("Malgun Gothic",11),wrap="word",relief="flat")
        self.recommend_text.pack(fill="both",expand=True)
        self.recommend_cache=[]
        self.recommend_text.insert("end","[마샬 추천 TOP20]\n\n단순 상승률 순위가 아니라 차트 흐름, 외국인·기관 수급, 최근 뉴스의 방향성과 빈도, 거래량/검색/NXT/골든크로스 등 시장신호를 함께 점수화합니다.\n\n점수가 높아도 추격매수 위험과 시장 급변 가능성은 별도로 확인하세요.")

    def refresh_recommend(self):
        self.recommend_tree.delete(*self.recommend_tree.get_children())
        self.recommend_text.delete("1.0","end");self.recommend_text.insert("end","시장 전체를 스캔하고 뉴스·수급을 교차검증 중입니다...\n")
        self.recommend_status.config(text="정밀스캔 중...")
        def progress(msg,pct=0):
            try:self.after(0,lambda:self.recommend_status.config(text=msg))
            except Exception:pass
        def work():
            try:
                r=marshall_recommend_scan(progress,20)
                self.after(0,lambda:self.fill_recommend(r))
            except Exception as e:
                self.after(0,lambda:messagebox.showerror("추천 TOP20",str(e)))
                self.after(0,lambda:self.recommend_status.config(text="분석 실패"))
        threading.Thread(target=work,daemon=True).start()

    def fill_recommend(self,rows):
        self.recommend_cache=rows[:20]
        self.recommend_tree.delete(*self.recommend_tree.get_children())
        for i,d in enumerate(self.recommend_cache,1):
            sd=d.get("score_detail",{})
            self.recommend_tree.insert("","end",iid=str(i-1),values=(i,d.get("name",""),d.get("code",""),f"{d.get('price',0):,.0f}",f"{d.get('change',0):+.2f}",f"{d.get('final',0):.1f}",f"{sd.get('chart',0):.0f}",f"{sd.get('flow',0):.0f}",f"{sd.get('news',0):.0f}",f"{sd.get('signal',0):.0f}",d.get("grade","")))
        self.recommend_status.config(text=f"완료 · {len(self.recommend_cache)}종목")
        if self.recommend_cache:
            self.recommend_tree.selection_set("0");self.recommend_tree.focus("0");self.recommend_selected()

    def recommend_selected(self,event=None):
        sel=self.recommend_tree.selection()
        if not sel:return
        try:d=self.recommend_cache[int(sel[0])]
        except Exception:return
        sd=d.get("score_detail",{});nd=d.get("news_detail",{});fd=d.get("flow_detail",{})
        m=d.get("metrics",{}) or {}
        score=float(d.get("final",0))
        verdict="최상위 강도" if score>=88 else "강한 후보" if score>=82 else "상승준비 우수" if score>=76 else "관심 후보" if score>=70 else "관찰"
        risk=[]
        blob=" ".join(_marshall_flat_strings(d))
        if "이격과열" in blob:risk.append("이격도 과열 신호 포함")
        if "NXT하락" in blob:risk.append("NXT 하락 신호 확인")
        if d.get("change",0)>=12:risk.append("당일 급등폭이 커 추격 위험")
        flow_hits=fd.get("hits",[])
        headlines=nd.get("titles",[])[:8]
        metric_lines=[]
        for k,v in list(m.items())[:24]:
            if isinstance(v,(int,float,str)) and len(str(v))<40:metric_lines.append(f"  · {k}: {v}")
        txt=(f"[마샬 정밀 추천 분석]\n\n{d.get('name')} ({d.get('code')})\n현재가 {d.get('price',0):,.0f}원 / 등락 {d.get('change',0):+.2f}%\n종합점수 {score:.1f}/100 · {d.get('grade','')} · {verdict}\n\n"
             f"[점수 구성]\n  차트 흐름      {sd.get('chart',0):.1f} / 100   × 40%\n  외국인·기관    {sd.get('flow',0):.1f} / 100   × 25%\n  뉴스·재료      {sd.get('news',0):.1f} / 100   × 20%\n  시장신호       {sd.get('signal',0):.1f} / 100   × 10%\n  시장환경       {sd.get('market',0):.1f} / 100   ×  5%\n\n"
             f"[수급 판독]\n" + ("\n".join("  · "+x for x in flow_hits) if flow_hits else "  · 외국인/기관 TOP20 교집합 없음") +
             f"\n\n[뉴스 판독]\n  긍정 {nd.get('positive',0)}건 · 부정 {nd.get('negative',0)}건 · 분석기사 {nd.get('count',0)}건\n" +
             ("\n".join("  · "+x for x in headlines) if headlines else "  · 종목 뉴스 제목을 충분히 수집하지 못함") +
             "\n\n[차트 세부]\n" + ("\n".join(metric_lines) if metric_lines else "  · 기본 차트 점수 사용") +
             "\n\n[리스크]\n" + ("\n".join("  · "+x for x in risk) if risk else "  · 현재 수집 신호상 특이 과열 경고 없음") +
             "\n\n※ 추천점수는 후보 선별용이며 수익을 보장하지 않습니다. 실제 진입은 지지·저항과 당일 수급 변화를 함께 확인하세요.")
        self.recommend_text.delete("1.0","end");self.recommend_text.insert("end",txt)

    def _tradingview_tab(self):
        f=self.tabs["tv"]
        top=tk.Frame(f,bg="#0f172a");top.pack(fill="x",padx=14,pady=12)
        tk.Label(top,text="종목명/코드",bg="#0f172a",fg="#e5e7eb",font=("Malgun Gothic",11,"bold")).pack(side="left")
        self.tv_q=tk.Entry(top,width=22,font=("Malgun Gothic",12),bg="#111827",fg="white",insertbackground="white",relief="flat");self.tv_q.pack(side="left",padx=8,ipady=7)
        self.btn(top,"📈 TradingView 차트 열기",self.open_tradingview_chart,True).pack(side="left",padx=5)
        self.btn(top,"🧠 마샬 지표분석",self.tv_analyze,True).pack(side="left",padx=5)
        tk.Label(top,text="TradingView 공식 차트 + 마샬 계산지표를 함께 확인",bg="#0f172a",fg="#60a5fa",font=("Malgun Gothic",10)).pack(side="left",padx=12)
        body=tk.Frame(f,bg="#0f172a");body.pack(fill="both",expand=True,padx=14,pady=(0,14))
        self.tv_text=tk.Text(body,bg="#0b1220",fg="#e5e7eb",font=("Malgun Gothic",12),wrap="word",relief="flat");self.tv_text.pack(fill="both",expand=True)
        self.tv_text.insert("end","[TradingView + 마샬 차트]\n\n종목명 또는 6자리 코드를 입력하세요.\n\n• TradingView 차트 열기: 공식 Advanced Chart 화면을 엽니다.\n• 차트에서 RSI, MACD, 이동평균, 볼린저밴드 등 원하는 지표를 추가할 수 있습니다.\n• 마샬 지표분석: 같은 종목의 실제 일봉을 바탕으로 MA5/20/60/120, RSI, 거래량, 20·60일 고저점과 지지·저항을 계산합니다.\n\n한국 종목은 KRX:6자리코드 형식으로 연결합니다.")
        self.tv_q.bind("<Return>",lambda e:self.open_tradingview_chart())

    def open_tradingview_chart(self):
        q=self.tv_q.get().strip()
        if not q:
            messagebox.showwarning("TradingView","종목명 또는 6자리 코드를 입력해주세요.");return
        try:code,name=resolve(q)
        except Exception as e:
            messagebox.showerror("TradingView",str(e));return
        symbol=f"KRX:{code}"
        # TradingView's full chart is launched from inside this tab. This avoids brittle scraping of their JS canvas.
        url="https://www.tradingview.com/chart/?symbol="+urllib.parse.quote(symbol,safe=':')
        try:webbrowser.open_new(url)
        except Exception as e:messagebox.showerror("TradingView",str(e))
        self.tv_text.delete("1.0","end");self.tv_text.insert("end",f"TradingView 차트를 열었습니다.\n\n종목: {name} ({code})\n심볼: {symbol}\n\nTradingView 차트에서 지표를 추가한 뒤, 이 프로그램의 '마샬 지표분석' 결과와 교차 확인하세요.\n\n참고: TradingView의 KRX 데이터 실시간/지연 여부는 사용자의 TradingView 데이터 권한에 따라 달라질 수 있습니다.")

    def tv_analyze(self):
        q=self.tv_q.get().strip()
        if not q:
            messagebox.showwarning("마샬 지표분석","종목을 입력해주세요.");return
        self.tv_text.delete("1.0","end");self.tv_text.insert("end","실제 일봉과 기술지표 계산 중...\n")
        def work():
            try:
                d=self._jpg_data_report(q)
                code=d['code'];name=d['name']
                extra=[]
                try:
                    nd=_marshall_news_score(code);fd=_marshall_flow_for_code(code,_marshall_flow_map())
                    extra=[nd,fd]
                except Exception:extra=[]
                def fill():
                    self.tv_text.delete("1.0","end")
                    txt=(f"[마샬 실시간 차트 보조분석]\n\n{name} ({code})\n현재가 {d['price']:,.0f}원 / {d['chg']:+.2f}%\n차트점수 {d['score']:.1f}/100 · {d['grade']} · {d['state']}\n\n"
                         f"MA5     {d['ma5']:,.0f}\nMA20    {d['ma20']:,.0f}\nMA60    {d['ma60']:,.0f}\nMA120   {d['ma120']:,.0f}\nRSI(14) {d['rsi']:.1f}\n거래량비 {d['vr']:.2f}배\n\n"
                         f"20일 저점 {d['lo20']:,.0f} / 20일 고점 {d['hi20']:,.0f}\n60일 저점 {d['lo60']:,.0f} / 60일 고점 {d['hi60']:,.0f}\n")
                    if extra:
                        nd,fd=extra
                        txt+=f"\n뉴스점수 {nd['score']:.1f} / 수급점수 {fd['score']:.1f}\n"
                    txt+="\nTradingView에서 실제 캔들 위치와 보조지표를 보면서 위 수치를 교차 확인하세요."
                    self.tv_text.insert("end",txt)
                self.after(0,fill)
            except Exception as e:self.after(0,lambda:messagebox.showerror("차트 분석",str(e)))
        threading.Thread(target=work,daemon=True).start()

'''
s=s.replace(needle,methods+needle,1)

# Recommendation scoring engine: appended before the main guard so it overrides scan_market at runtime
# without rewriting the existing core scanner.
engine=r'''
# ===== Marshall v3.4 recommendation enrichment =====
_MARSHALL_BASE_SCAN = scan_market

def _marshall_clamp(v,lo=0,hi=100):
    try:return max(lo,min(hi,float(v)))
    except Exception:return 50.0

def _marshall_flat_strings(node):
    out=[]
    if isinstance(node,dict):
        for k,v in node.items():
            out.append(str(k));out.extend(_marshall_flat_strings(v))
    elif isinstance(node,(list,tuple,set)):
        for v in node:out.extend(_marshall_flat_strings(v))
    elif isinstance(node,str):out.append(node)
    return out

def _marshall_rank_bonus(rank):
    try:r=int(rank)
    except Exception:return 0
    return 16 if r<=5 else 12 if r<=10 else 7 if r<=20 else 3 if r<=30 else 0

def _marshall_flow_map():
    mp={}
    def one(src):
        label,url,w,kind=src
        try:
            rows=stock_rows(url,30)
            return [(x.get('code',''),label,i,kind) for i,x in enumerate(rows,1)]
        except Exception:return []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        for arr in ex.map(one,FLOW_URLS):
            for code,label,rank,kind in arr:
                if code:mp.setdefault(code,[]).append((label,rank,kind))
    return mp

def _marshall_flow_for_code(code,mp):
    hits=mp.get(code,[]);score=50.0;labels=[];foreign_buy=False;inst_buy=False;foreign_sell=False;inst_sell=False
    for label,rank,kind in hits:
        b=_marshall_rank_bonus(rank)
        if kind=='buy':score+=b
        elif kind=='sell':score-=b
        labels.append(f"{label} {rank}위")
        if '외국인' in label and kind=='buy':foreign_buy=True
        if '기관' in label and kind=='buy':inst_buy=True
        if '외국인' in label and kind=='sell':foreign_sell=True
        if '기관' in label and kind=='sell':inst_sell=True
    if foreign_buy and inst_buy:score+=10;labels.append('외국인+기관 쌍끌이 매수 보너스')
    if foreign_sell and inst_sell:score-=10;labels.append('외국인+기관 동반 순매도 경고')
    return {'score':_marshall_clamp(score),'hits':labels,'foreign_buy':foreign_buy,'institution_buy':inst_buy}

def _marshall_news_score(code):
    url=f"https://finance.naver.com/item/news_news.naver?code={code}&page=1&sm=title_entity_id.basic&clusterId="
    try:rows=links_from(url,'news',30)
    except Exception:rows=[]
    titles=[]
    for x in rows:
        t=(x.get('title') or '').strip()
        if t and t not in titles:titles.append(t)
    pos=neg=0
    for t in titles:
        sv=sentiment(t)
        if sv>0:pos+=1
        elif sv<0:neg+=1
    if not titles:score=45.0
    else:score=50+pos*6.0-neg*7.5+min(8,len(titles)*0.7)
    # Repeated fresh coverage is attention, not automatically bullish; direction still comes from sentiment.
    return {'score':_marshall_clamp(score),'positive':pos,'negative':neg,'count':len(titles),'titles':titles[:12]}

def marshall_recommend_scan(progress=None,topn=20):
    if progress:progress('1/4 기본 시장·차트 후보 스캔 중...',5)
    base=_MARSHALL_BASE_SCAN(progress,40)
    pool=list(base[:32])
    if not pool:return []
    if progress:progress('2/4 외국인·기관 수급 교차검증 중...',56)
    fmap=_marshall_flow_map()
    if progress:progress('3/4 종목별 최신 뉴스 분석 중...',68)
    news_map={}
    def nwork(d):
        code=str(d.get('code',''))
        return code,_marshall_news_score(code)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        for code,nd in ex.map(nwork,pool):news_map[code]=nd
    if progress:progress('4/4 마샬 종합점수 계산 중...',90)
    out=[]
    for d0 in pool:
        d=dict(d0);code=str(d.get('code',''))
        chart=_marshall_clamp(d.get('chart') or (d.get('metrics') or {}).get('score') or 50)
        fd=_marshall_flow_for_code(code,fmap);flow=fd['score']
        nd=news_map.get(code,{'score':45,'positive':0,'negative':0,'count':0,'titles':[]});news=nd['score']
        raw_signal=d.get('signal',0)
        try:signal=_marshall_clamp(50+float(raw_signal)*2.2)
        except Exception:signal=50
        env=d.get('market_env') or {};market=_marshall_clamp(env.get('score',50))
        total=chart*.40+flow*.25+news*.20+signal*.10+market*.05
        blob=' '.join(_marshall_flat_strings(d))
        penalty=0
        if '이격과열' in blob:penalty+=5
        if 'NXT하락' in blob:penalty+=3
        try:
            if float(d.get('change',0))>=15:penalty+=3
        except Exception:pass
        total=_marshall_clamp(total-penalty)
        d['final']=total;d['grade']=grade(total);d['flow_detail']=fd;d['news_detail']=nd
        d['score_detail']={'chart':chart,'flow':flow,'news':news,'signal':signal,'market':market,'penalty':penalty}
        out.append(d)
    out.sort(key=lambda x:(x.get('final',0),x.get('score_detail',{}).get('flow',0),x.get('score_detail',{}).get('chart',0)),reverse=True)
    if progress:progress('추천 TOP20 완료',100)
    return out[:topn]

# Existing 초특급 TOP20 also receives the improved score so both recommendation screens stay consistent.
def scan_market(progress=None,topn=40):
    return marshall_recommend_scan(progress,min(topn,20))
# ===== end v3.4 recommendation enrichment =====
'''
marker='if __name__'
pos=s.rfind(marker)
if pos<0:raise SystemExit('v3.4 main guard missing')
s=s[:pos]+engine+'\n\n'+s[pos:]

p.write_text(s,encoding='utf-8')
print('v3.4 TradingView + recommendation patch applied')
