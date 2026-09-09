import json, re, threading, statistics, math, urllib.request, urllib.parse
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from pathlib import Path
try:
    from PIL import Image, ImageTk
except Exception:
    Image = ImageTk = None

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130 Safari/537.36"


def http_text(url, timeout=10):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json,text/plain,*/*"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", errors="ignore")


def http_json(url):
    return json.loads(http_text(url))


def num(v):
    if v is None: return 0.0
    if isinstance(v, (int,float)): return float(v)
    s = str(v).replace(",","").replace("%","").replace("+","").strip()
    try: return float(s)
    except: return 0.0


def deep_find_candidates(node, out):
    if isinstance(node, dict):
        code = str(node.get("itemCode") or node.get("itemcode") or node.get("stockCode") or node.get("code") or "")
        name = str(node.get("stockName") or node.get("itemName") or node.get("itemname") or node.get("name") or "")
        if re.fullmatch(r"\d{6}", code) and name:
            out.append((code,name))
        for v in node.values(): deep_find_candidates(v,out)
    elif isinstance(node, list):
        for v in node: deep_find_candidates(v,out)


def resolve(query):
    q = query.strip()
    if re.fullmatch(r"\d{6}", q): return q, q
    urls = [
        "https://m.stock.naver.com/front-api/search/autoComplete?query=" + urllib.parse.quote(q) + "&target=stock,index,marketindicator,coin,ipo",
        "https://ac.stock.naver.com/ac?q=" + urllib.parse.quote(q) + "&q_enc=utf-8&target=stock"
    ]
    for u in urls:
        try:
            data = http_json(u); arr=[]; deep_find_candidates(data,arr)
            if arr: return arr[0]
        except Exception: pass
    raise RuntimeError("종목명을 찾지 못했습니다. 6자리 종목코드로 입력해 보세요.")


def basic_quote(code):
    urls=[f"https://m.stock.naver.com/api/stock/{code}/basic",f"https://m.stock.naver.com/front-api/stock/domestic/basic?code={code}&endType=stock"]
    for u in urls:
        try:
            j=http_json(u)
            if isinstance(j,dict):
                name=j.get("stockName") or j.get("itemName") or j.get("itemname") or code
                price=num(j.get("closePrice") or j.get("nowPrice") or j.get("currentPrice"))
                chg=num(j.get("fluctuationsRatio") or j.get("prevChangeRate") or j.get("changeRate"))
                if price>0: return str(name),price,chg
        except Exception: pass
    return code,0,0


def bars(code, count=260):
    url=f"https://fchart.stock.naver.com/sise.nhn?symbol={code}&timeframe=day&count={count}&requestType=0"
    txt=http_text(url)
    rows=[]
    for m in re.finditer(r'<item\s+data="([^"]+)"',txt):
        x=m.group(1).split("|")
        if len(x)>=6:
            try:
                d,o,h,l,c,v=x[:6]
                rows.append({"date":d,"open":float(o),"high":float(h),"low":float(l),"close":float(c),"volume":float(v)})
            except: pass
    rows.sort(key=lambda z:z["date"])
    if len(rows)<80: raise RuntimeError("차트 가격 데이터가 부족합니다.")
    return rows


def sma(a,n): return sum(a[-n:])/n if len(a)>=n else 0

def ema_series(a,n):
    if not a:return []
    k=2/(n+1); out=[a[0]]
    for x in a[1:]: out.append(x*k+out[-1]*(1-k))
    return out

def rsi(a,n=14):
    if len(a)<=n:return 50
    gains=[];loss=[]
    for i in range(-n,0):
        d=a[i]-a[i-1];gains.append(max(d,0));loss.append(max(-d,0))
    ag=sum(gains)/n; al=sum(loss)/n
    if al==0:return 100
    rs=ag/al;return 100-(100/(1+rs))

def cmo(a,n=9):
    if len(a)<=n:return 0
    up=dn=0
    for i in range(-n,0):
        d=a[i]-a[i-1]
        if d>0:up+=d
        else:dn-=d
    return 100*(up-dn)/(up+dn) if up+dn else 0

def atr(h,l,c,n=14):
    if len(c)<=n:return 0
    tr=[]
    for i in range(1,len(c)):
        tr.append(max(h[i]-l[i],abs(h[i]-c[i-1]),abs(l[i]-c[i-1])))
    return sum(tr[-n:])/n

def adx(h,l,c,n=14):
    if len(c)<n+2:return 0
    tr=[];pdm=[];mdm=[]
    for i in range(1,len(c)):
        up=h[i]-h[i-1]; dn=l[i-1]-l[i]
        pdm.append(up if up>dn and up>0 else 0); mdm.append(dn if dn>up and dn>0 else 0)
        tr.append(max(h[i]-l[i],abs(h[i]-c[i-1]),abs(l[i]-c[i-1])))
    trn=sum(tr[-n:]); p=sum(pdm[-n:]); m=sum(mdm[-n:])
    if trn==0:return 0
    pdi=100*p/trn; mdi=100*m/trn
    return 100*abs(pdi-mdi)/(pdi+mdi) if pdi+mdi else 0

def slope_pct(a,n=10):
    if len(a)<n+20:return 0
    ma_now=sum(a[-20:])/20; ma_old=sum(a[-20-n:-n])/20
    return (ma_now/ma_old-1)*100 if ma_old else 0

def cluster_levels(levels,current):
    if not levels:return []
    levels=sorted(x for x in levels if x>0)
    groups=[]
    for x in levels:
        if not groups or abs(x-statistics.mean(groups[-1]))/max(x,1)>0.018:groups.append([x])
        else:groups[-1].append(x)
    vals=[statistics.mean(g) for g in groups if len(g)>=2]
    vals.sort(key=lambda x:abs(x-current))
    return vals[:8]

def levels(rows,current):
    r=rows[-120:]; lows=[]; highs=[]
    for i in range(2,len(r)-2):
        if r[i]["low"]<=min(r[j]["low"] for j in range(i-2,i+3)):lows.append(r[i]["low"])
        if r[i]["high"]>=max(r[j]["high"] for j in range(i-2,i+3)):highs.append(r[i]["high"])
    sup=sorted([x for x in cluster_levels(lows,current) if x<current],reverse=True)[:3]
    res=sorted([x for x in cluster_levels(highs,current) if x>current])[:3]
    low60=min(x["low"] for x in r[-60:]); high60=max(x["high"] for x in r[-60:])
    if low60<current and all(abs(low60-x)/current>.02 for x in sup):sup.append(low60)
    if high60>current and all(abs(high60-x)/current>.02 for x in res):res.append(high60)
    return sup[:3],res[:3]

def fmt_price(x):
    if not x:return "-"
    return f"{int(round(x/100)*100):,}원"

def analyze_rows(name,code,rows,live_price=0,live_chg=0):
    c=[x["close"] for x in rows];h=[x["high"] for x in rows];l=[x["low"] for x in rows];v=[x["volume"] for x in rows]
    cur=live_price or c[-1]; ma5=sma(c,5);ma10=sma(c,10);ma20=sma(c,20);ma60=sma(c,60);ma120=sma(c,120)
    rv=rsi(c); cm=cmo(c); ad=adx(h,l,c); at=atr(h,l,c)
    e12=ema_series(c,12);e26=ema_series(c,26);mac=e12[-1]-e26[-1];sig=ema_series([e12[i]-e26[i] for i in range(min(len(e12),len(e26)))],9)[-1]
    vol20=sma(v,20); vr=v[-1]/vol20 if vol20 else 1
    sl=slope_pct(c,10)
    ma_spread=(max(ma5,ma10,ma20)-min(ma5,ma10,ma20))/cur*100 if cur else 99
    hi20=max(h[-21:-1]);hi60=max(h[-61:-1]);lo20=min(l[-21:-1]);lo60=min(l[-61:-1])
    dist_hi=(hi60/cur-1)*100 if cur else 0
    box_range=(max(h[-25:])/min(l[-25:])-1)*100
    last=rows[-1]; body=abs(last["close"]-last["open"]); rng=max(last["high"]-last["low"],1)
    body_ratio=body/rng
    breakout=cur>hi20 and vr>=1.3
    reclaim=cur>ma20 and c[-2]<=sma(c[:-1],20)
    sup,res=levels(rows,cur)

    trend=50
    trend += 10 if cur>ma20 else -10
    trend += 10 if ma20>ma60 else -7
    trend += 8 if ma60>ma120 else -4
    trend += max(-10,min(10,sl*3))
    if ma_spread<2.2: trend+=6
    trend=max(0,min(100,trend))

    volume=max(0,min(100,45+min(vr,4)*18))
    mom=50
    if 45<=rv<=68:mom+=18
    elif rv>75:mom-=10
    elif rv<35:mom-=8
    mom+=max(-14,min(14,cm/4))
    mom+=10 if mac>sig else -5
    mom+=6 if ad>=22 else 0
    mom=max(0,min(100,mom))

    structure=50
    if box_range<=18:structure+=12
    if ma_spread<2.5:structure+=12
    if cur>ma20:structure+=8
    if c[-1]>c[-10]:structure+=6
    structure=max(0,min(100,structure))

    br=45
    if 0<=dist_hi<=8:br+=20
    elif dist_hi<=15:br+=12
    if vr>=1.5:br+=15
    if breakout:br+=15
    if reclaim:br+=8
    br=max(0,min(100,br))

    heat=0
    if rv>72:heat+=3
    if cur>ma20*1.12:heat+=3
    ret10=(cur/c[-11]-1)*100 if len(c)>11 else 0
    if ret10>15:heat+=2
    if vr>3.5:heat+=1
    heat=min(10,heat)

    total=trend*.27+volume*.15+mom*.18+structure*.15+br*.20+(100-heat*10)*.05
    grade="A+" if total>=88 else "A" if total>=82 else "A-" if total>=76 else "B+" if total>=70 else "B" if total>=63 else "C"

    if breakout: state="거래량을 동반한 단기 돌파 시도"
    elif ma_spread<2.3 and box_range<18: state="이평선 수렴과 박스권 압축 후 방향 선택 구간"
    elif cur>ma20 and sl>0: state="상승추세를 유지하는 눌림/재상승 구간"
    elif cur<ma20 and ma20<ma60: state="중기 약세에서 바닥 확인이 필요한 구간"
    else: state="박스권 또는 추세 전환 확인 구간"

    pos=[]; neg=[]
    if cur>ma20:pos.append("현재가가 20일선 위")
    if ma20>ma60:pos.append("20일선이 60일선 위")
    if ma_spread<2.3:pos.append("5·10·20일선 수렴")
    if vr>=1.5:pos.append(f"거래량이 20일 평균의 {vr:.2f}배")
    if mac>sig:pos.append("MACD가 시그널보다 우위")
    if cm>-10:pos.append("CMO가 중립 이상")
    if rv>72:neg.append("RSI 과열권")
    if cur<ma20:neg.append("현재가가 20일선 아래")
    if mac<sig:neg.append("MACD 모멘텀 둔화")
    if vr<0.8:neg.append("거래량 부족")
    if heat>=5:neg.append("단기 과열 부담")

    first_res=res[0] if res else hi20; first_sup=sup[0] if sup else lo20
    target2=res[1] if len(res)>1 else hi60
    fail=first_sup if first_sup else ma20
    scenario=f"{fmt_price(first_res)}을 거래량 증가와 함께 돌파하면 {fmt_price(target2)} 재도전 가능성을 높게 봅니다."
    risk=f"반대로 {fmt_price(fail)}을 종가 기준으로 이탈하면 단기 상승 시나리오의 신뢰도를 낮춥니다."

    report=[]
    report.append(f"[마샬 차트 종합분석]  {name} ({code})")
    report.append(f"현재가 {fmt_price(cur)}  /  등락 {live_chg:+.2f}%")
    report.append(f"종합점수 {total:.1f}/100 · {grade}등급")
    report.append("")
    report.append(f"■ 현재 위치\n{state}입니다.")
    report.append("")
    report.append("■ 좋은 점\n- "+("\n- ".join(pos) if pos else "뚜렷한 강점 신호는 아직 제한적입니다."))
    report.append("")
    report.append("■ 주의할 점\n- "+("\n- ".join(neg) if neg else "과열 부담은 크지 않습니다."))
    report.append("")
    report.append(f"■ 핵심 가격\n1차 지지 {fmt_price(first_sup)}\n2차 지지 {fmt_price(sup[1]) if len(sup)>1 else fmt_price(lo60)}\n1차 저항 {fmt_price(first_res)}\n2차 저항 {fmt_price(target2)}\n60일 전고점 {fmt_price(hi60)}")
    report.append("")
    report.append(f"■ 시나리오\n{scenario}\n{risk}")
    report.append("")
    report.append(f"■ 점수 세부\n추세 {trend:.0f} · 거래량 {volume:.0f} · 모멘텀 {mom:.0f} · 구조 {structure:.0f} · 돌파가능성 {br:.0f} · 과열도 {heat}/10")
    report.append(f"RSI {rv:.1f} · CMO {cm:.1f} · ADX {ad:.1f} · 거래량비 {vr:.2f}배 · 20일선 기울기 {sl:+.2f}%")
    report.append(f"MA5 {fmt_price(ma5)} · MA20 {fmt_price(ma20)} · MA60 {fmt_price(ma60)} · MA120 {fmt_price(ma120)}")
    report.append("")
    report.append("※ 차트 이미지를 불러온 경우 이미지는 비교용으로 표시하며, 핵심 해석은 종목의 실제 OHLCV 데이터로 계산합니다. 투자 판단 보조용이며 수익을 보장하지 않습니다.")
    return "\n".join(report)


class App:
    def __init__(self,root):
        self.root=root;root.title("MarshallStock Windows v2.1 · 마샬 차트 해석")
        root.geometry("1380x860");root.configure(bg="#08101d")
        self.image_path=None;self.photo=None
        self.build()
    def build(self):
        left=tk.Frame(self.root,bg="#101827",width=300);left.pack(side="left",fill="y")
        main=tk.Frame(self.root,bg="#08101d");main.pack(side="right",fill="both",expand=True)
        tk.Label(left,text="마샬 차트 해석",font=("맑은 고딕",22,"bold"),fg="white",bg="#101827").pack(padx=18,pady=(22,6),anchor="w")
        tk.Label(left,text="Windows v2.1",font=("맑은 고딕",11),fg="#34d399",bg="#101827").pack(padx=18,anchor="w")
        tk.Label(left,text="종목명 또는 6자리 코드",font=("맑은 고딕",10),fg="#94a3b8",bg="#101827").pack(padx=18,pady=(24,5),anchor="w")
        self.q=tk.Entry(left,font=("맑은 고딕",14));self.q.pack(fill="x",padx=18,ipady=7)
        self.q.insert(0,"LS ELECTRIC")
        tk.Button(left,text="차트 자동 해석",command=self.go,bg="#dc2626",fg="white",font=("맑은 고딕",13,"bold"),relief="flat").pack(fill="x",padx=18,pady=(12,6),ipady=8)
        tk.Button(left,text="차트 JPG 불러오기",command=self.load_image,bg="#2563eb",fg="white",font=("맑은 고딕",12),relief="flat").pack(fill="x",padx=18,pady=6,ipady=7)
        tk.Button(left,text="분석내용 복사",command=self.copy_report,bg="#475569",fg="white",font=("맑은 고딕",12),relief="flat").pack(fill="x",padx=18,pady=6,ipady=7)
        self.status=tk.Label(left,text="종목을 입력하고 분석을 누르세요.",wraplength=250,justify="left",font=("맑은 고딕",10),fg="#cbd5e1",bg="#101827")
        self.status.pack(fill="x",padx=18,pady=20,anchor="w")
        tk.Label(left,text="분석요소\n• 5·10·20·60·120일선\n• RSI / CMO / MACD / ADX\n• 거래량 20일 평균비\n• 박스권·이평선 수렴\n• 지지·저항·전고점\n• 돌파/실패 조건\n• 과열도·종합등급",justify="left",font=("맑은 고딕",10),fg="#94a3b8",bg="#101827").pack(padx=18,anchor="w")
        pan=ttk.Panedwindow(main,orient="vertical");pan.pack(fill="both",expand=True,padx=12,pady=12)
        imgf=tk.Frame(pan,bg="#0f172a",height=300);self.img=tk.Label(imgf,text="JPG 차트를 불러오면 여기에 표시됩니다.",fg="#94a3b8",bg="#0f172a",font=("맑은 고딕",12));self.img.pack(fill="both",expand=True,padx=8,pady=8)
        txtf=tk.Frame(pan,bg="#0f172a"); self.txt=tk.Text(txtf,bg="#0b1220",fg="#e5e7eb",insertbackground="white",font=("맑은 고딕",12),wrap="word",relief="flat",padx=18,pady=16);self.txt.pack(fill="both",expand=True)
        pan.add(imgf,weight=2);pan.add(txtf,weight=5)
    def set_status(self,s): self.root.after(0,lambda:self.status.config(text=s))
    def go(self):
        q=self.q.get().strip()
        if not q:return
        self.set_status("실제 시세와 일봉 데이터를 읽는 중...")
        self.txt.delete("1.0","end");self.txt.insert("end","분석 중입니다...\n")
        threading.Thread(target=self.work,args=(q,),daemon=True).start()
    def work(self,q):
        try:
            code,resolved_name=resolve(q); self.set_status(f"{resolved_name} ({code}) · 가격 데이터 분석 중")
            name,price,chg=basic_quote(code)
            if name==code and resolved_name!=code:name=resolved_name
            r=bars(code,280); rep=analyze_rows(name,code,r,price,chg)
            self.root.after(0,lambda:self.show_report(rep))
            self.set_status("분석 완료 · 지지/저항과 돌파조건을 확인하세요.")
        except Exception as e:
            self.root.after(0,lambda:messagebox.showerror("분석 오류",str(e)))
            self.set_status("분석 실패 · 인터넷 연결 또는 종목코드를 확인하세요.")
    def show_report(self,rep):
        self.txt.delete("1.0","end");self.txt.insert("end",rep)
    def load_image(self):
        p=filedialog.askopenfilename(title="차트 이미지 선택",filetypes=[("이미지","*.jpg *.jpeg *.png *.webp"),("모든 파일","*.*")])
        if not p:return
        self.image_path=p
        if Image is None:
            self.img.config(text=Path(p).name+"\n(Pillow 미탑재로 미리보기 불가)");return
        try:
            im=Image.open(p);im.thumbnail((1020,300));self.photo=ImageTk.PhotoImage(im);self.img.config(image=self.photo,text="")
            self.set_status("차트 이미지를 불러왔습니다. 종목을 입력하고 '차트 자동 해석'을 누르세요.")
        except Exception as e:messagebox.showerror("이미지 오류",str(e))
    def copy_report(self):
        s=self.txt.get("1.0","end").strip()
        if not s:return
        self.root.clipboard_clear();self.root.clipboard_append(s);self.set_status("분석내용을 클립보드에 복사했습니다.")

if __name__=="__main__":
    root=tk.Tk();App(root);root.mainloop()
