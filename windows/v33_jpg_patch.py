from pathlib import Path

p=Path('windows/marshall_stock_v30.py')
s=p.read_text(encoding='utf-8')

s=s.replace('APP_NAME = "Marshall Stock PRO v3.2 VISION"','APP_NAME = "Marshall Stock PRO v3.3 VISION JPG"')
s=s.replace('APP_NAME = "Marshall Stock PRO v3.0"','APP_NAME = "Marshall Stock PRO v3.3 VISION JPG"')

# Pillow import for JPG/PNG preview and lightweight image-aware chart reading.
anchor='from tkinter import ttk, filedialog, messagebox\n'
if anchor in s and 'from PIL import Image, ImageTk' not in s:
    s=s.replace(anchor, anchor+'try:\n    from PIL import Image, ImageTk\nexcept Exception:\n    Image = ImageTk = None\n',1)

old='for key,title in [("scan","🚀 초특급 TOP20"),("analysis","🔎 종목 종합분석"),("market","🌍 시장환경"),("theme","🔥 테마 레이더"),("flow","💰 외국인·기관 수급"),("news","📰 뉴스·리서치"),("watch","⭐ 관심종목 LIVE"),("portfolio","💼 10억 가상투자"),("diag","🛠 데이터 상태")]: '
# tolerate source without trailing space
old2='for key,title in [("scan","🚀 초특급 TOP20"),("analysis","🔎 종목 종합분석"),("market","🌍 시장환경"),("theme","🔥 테마 레이더"),("flow","💰 외국인·기관 수급"),("news","📰 뉴스·리서치"),("watch","⭐ 관심종목 LIVE"),("portfolio","💼 10억 가상투자"),("diag","🛠 데이터 상태")]: '
old3='for key,title in [("scan","🚀 초특급 TOP20"),("analysis","🔎 종목 종합분석"),("market","🌍 시장환경"),("theme","🔥 테마 레이더"),("flow","💰 외국인·기관 수급"),("news","📰 뉴스·리서치"),("watch","⭐ 관심종목 LIVE"),("portfolio","💼 10억 가상투자"),("diag","🛠 데이터 상태")]:'
new='for key,title in [("scan","🚀 초특급 TOP20"),("analysis","🔎 종목 종합분석"),("jpg","📷 JPG 차트분석"),("market","🌍 시장환경"),("theme","🔥 테마 레이더"),("flow","💰 외국인·기관 수급"),("news","📰 뉴스·리서치"),("watch","⭐ 관심종목 LIVE"),("portfolio","💼 10억 가상투자"),("diag","🛠 데이터 상태")]:'
if old3 in s:
    s=s.replace(old3,new,1)
else:
    raise SystemExit('v3.3 tab anchor missing')

call_old='self._scan_tab();self._analysis_tab();self._market_tab();self._theme_tab();self._flow_tab();self._news_tab();self._watch_tab();self._portfolio_tab();self._diag_tab()'
call_new='self._scan_tab();self._analysis_tab();self._jpg_tab();self._market_tab();self._theme_tab();self._flow_tab();self._news_tab();self._watch_tab();self._portfolio_tab();self._diag_tab()'
if call_old not in s:
    raise SystemExit('v3.3 init-call anchor missing')
s=s.replace(call_old,call_new,1)

needle='    def _market_tab(self):\n'
if needle not in s:
    raise SystemExit('v3.3 market method anchor missing')

jpg_methods=r'''    def _jpg_tab(self):
        f=self.tabs["jpg"]
        top=tk.Frame(f,bg="#0f172a");top.pack(fill="x",padx=14,pady=12)
        tk.Label(top,text="종목명/코드",bg="#0f172a",fg="#e5e7eb",font=("Malgun Gothic",11,"bold")).pack(side="left")
        self.jpg_q=tk.Entry(top,width=20,font=("Malgun Gothic",12),bg="#111827",fg="white",insertbackground="white",relief="flat")
        self.jpg_q.pack(side="left",padx=(8,10),ipady=7)
        self.btn(top,"📎 JPG/PNG 첨부",self.load_chart_image,True).pack(side="left")
        self.btn(top,"🔍 첨부차트 분석",self.run_jpg_analysis,True).pack(side="left",padx=8)
        self.jpg_file_label=tk.Label(top,text="첨부파일 없음",bg="#0f172a",fg="#94a3b8",font=("Malgun Gothic",10))
        self.jpg_file_label.pack(side="left",padx=10)

        note=tk.Label(f,text="이미지 자체의 방향·압축·변동성 패턴을 보조 판독하고, 종목코드가 있으면 실제 일봉 OHLCV와 교차검증합니다. 이미지에서 가격 숫자를 임의로 OCR해 추정하지 않습니다.",bg="#0f172a",fg="#60a5fa",font=("Malgun Gothic",10))
        note.pack(fill="x",padx=14,pady=(0,8))

        pan=tk.PanedWindow(f,orient="horizontal",bg="#0f172a",sashwidth=6);pan.pack(fill="both",expand=True,padx=14,pady=(0,14))
        left=tk.Frame(pan,bg="#0f172a");right=tk.Frame(pan,bg="#0f172a");pan.add(left,minsize=520);pan.add(right,minsize=560)
        self.jpg_canvas=tk.Canvas(left,bg="#08111f",highlightthickness=1,highlightbackground="#263449")
        self.jpg_canvas.pack(fill="both",expand=True)
        self.jpg_result=tk.Text(right,bg="#0b1220",fg="#e5e7eb",font=("Malgun Gothic",11),wrap="word",relief="flat")
        self.jpg_result.pack(fill="both",expand=True)
        self.jpg_path="";self.jpg_photo=None;self.jpg_image_size=(0,0)
        self.jpg_result.insert("end","[JPG 차트 분석]\n\n1) JPG/PNG 차트 캡처를 첨부하세요.\n2) 가능하면 종목명 또는 6자리 코드를 입력하세요.\n3) '첨부차트 분석'을 누르면 이미지 보조판독 + 실제 시세 교차분석을 합니다.\n")

    def load_chart_image(self):
        path=filedialog.askopenfilename(title="차트 이미지 선택",filetypes=[("차트 이미지","*.jpg *.jpeg *.png *.webp"),("모든 파일","*.*")])
        if not path:return
        if Image is None:
            messagebox.showerror("이미지","Pillow 이미지 모듈을 불러오지 못했습니다.");return
        try:
            img=Image.open(path).convert("RGB")
            self.jpg_path=path;self.jpg_image_size=img.size
            # filename에 6자리 종목코드가 있으면 자동 채움
            m=re.search(r'(?<!\d)(\d{6})(?!\d)',Path(path).name)
            if m and not self.jpg_q.get().strip():self.jpg_q.insert(0,m.group(1))
            self.jpg_file_label.config(text=f"{Path(path).name} · {img.width}×{img.height}")
            self._show_jpg_preview(img)
            self.jpg_result.delete("1.0","end")
            self.jpg_result.insert("end",f"첨부 완료: {Path(path).name}\n크기: {img.width}×{img.height}\n\n'첨부차트 분석'을 눌러주세요.")
        except Exception as e:
            messagebox.showerror("이미지 열기",str(e))

    def _show_jpg_preview(self,img=None):
        if Image is None:return
        try:
            if img is None:img=Image.open(self.jpg_path).convert("RGB")
            self.update_idletasks()
            w=max(420,self.jpg_canvas.winfo_width()-20);h=max(420,self.jpg_canvas.winfo_height()-20)
            cp=img.copy();cp.thumbnail((w,h),Image.LANCZOS)
            self.jpg_photo=ImageTk.PhotoImage(cp)
            self.jpg_canvas.delete("all")
            self.jpg_canvas.create_image(w//2,h//2,image=self.jpg_photo,anchor="center")
        except Exception:pass

    def _image_chart_signal(self,path):
        """Conservative pixel-based trend/shape estimate. No OCR, no fabricated price values."""
        if Image is None:return {"ok":False,"note":"이미지 엔진 없음"}
        img=Image.open(path).convert("RGB")
        img.thumbnail((900,650),Image.LANCZOS)
        w,h=img.size
        if w<120 or h<120:return {"ok":False,"note":"이미지가 너무 작음"}
        # Ignore title/axes zones; focus on chart body.
        x0,x1=int(w*.07),int(w*.94);y0,y1=int(h*.10),int(h*.82)
        pts=[]
        for x in range(x0,x1,max(1,(x1-x0)//180)):
            ys=[]
            for y in range(y0,y1,2):
                r,g,b=img.getpixel((x,y));mx=max(r,g,b);mn=min(r,g,b)
                sat=mx-mn;lum=(r+g+b)/3
                # colored candle/line pixels while rejecting most gray grid/background
                if sat>=45 and 28<=lum<=235:
                    ys.append(y)
            if ys:
                ys.sort();pts.append((x,ys[len(ys)//2]))
        if len(pts)<14:
            return {"ok":False,"note":"색상 기반 차트선/봉을 충분히 분리하지 못했습니다. 실제 시세분석은 계속 가능합니다.","points":len(pts)}
        n=max(4,len(pts)//5)
        ly=sum(y for x,y in pts[:n])/n;ry=sum(y for x,y in pts[-n:])/n
        # y axis is inverted: lower pixel y means higher price.
        move=(ly-ry)/max(1,(y1-y0))*100
        direction="상승형" if move>4 else "하락형" if move<-4 else "횡보/수렴형"
        mid=[y for x,y in pts]
        mean=sum(mid)/len(mid);spread=(sum((y-mean)**2 for y in mid)/len(mid))**0.5/max(1,(y1-y0))*100
        volatility="큼" if spread>16 else "보통" if spread>8 else "낮음"
        # last-zone dispersion relative to preceding zone -> compression/expansion hint
        q=max(4,len(mid)//4);prev=mid[-2*q:-q] if len(mid)>=2*q else mid[:q];last=mid[-q:]
        def sd(a):
            if not a:return 0
            m=sum(a)/len(a);return (sum((z-m)**2 for z in a)/len(a))**0.5
        ps,ls=sd(prev),sd(last)
        shape="변동성 압축" if ps>0 and ls<ps*.72 else "변동성 확대" if ps>0 and ls>ps*1.35 else "변동성 유지"
        confidence=min(88,45+len(pts)//3)
        return {"ok":True,"direction":direction,"move":move,"volatility":volatility,"shape":shape,"confidence":confidence,"points":len(pts)}

    def _jpg_data_report(self,query):
        if not query.strip():return None
        code,name0=resolve(query)
        name,price,chg=basic_quote(code)
        rs=bars(code,260)
        c=[x["close"] for x in rs];h=[x["high"] for x in rs];l=[x["low"] for x in rs];v=[x["volume"] for x in rs]
        cur=price or c[-1]
        ma5=sum(c[-5:])/5;ma20=sum(c[-20:])/20;ma60=sum(c[-60:])/60;ma120=sum(c[-120:])/120
        vol20=sum(v[-20:])/20 if len(v)>=20 else 0;vr=v[-1]/vol20 if vol20 else 1
        rv=50.0
        if len(c)>=15:
            gains=[];loss=[]
            for i in range(-14,0):
                d=c[i]-c[i-1];gains.append(max(d,0));loss.append(max(-d,0))
            ag=sum(gains)/14;al=sum(loss)/14
            rv=100 if al==0 else 100-(100/(1+ag/al))
        hi20=max(h[-21:-1]);lo20=min(l[-21:-1]);hi60=max(h[-61:-1]);lo60=min(l[-61:-1])
        trend=50+(12 if cur>ma20 else -12)+(10 if ma20>ma60 else -8)+(7 if ma60>ma120 else -4)
        if ma5>ma20:trend+=6
        trend=max(0,min(100,trend))
        mom=50+(12 if 45<=rv<=68 else -8 if rv>75 or rv<35 else 3)+(10 if c[-1]>c[-6] else -7)
        volscore=max(20,min(100,40+vr*20))
        breakout=82 if cur>hi20 and vr>=1.3 else 68 if cur>=hi20*.97 else 48
        heat=0
        if rv>72:heat+=25
        if cur>ma20*1.12:heat+=25
        score=max(0,min(100,trend*.35+mom*.22+volscore*.18+breakout*.25-heat*.12))
        grade="A+" if score>=88 else "A" if score>=82 else "A-" if score>=76 else "B+" if score>=70 else "B" if score>=63 else "C"
        state="상승추세" if cur>ma20>ma60 else "중기상승·단기조정" if ma20>ma60 and cur<ma20 else "바닥/전환 확인" if cur<ma20 and ma20<=ma60 else "박스/전환 구간"
        return {"code":code,"name":name,"price":cur,"chg":chg,"ma5":ma5,"ma20":ma20,"ma60":ma60,"ma120":ma120,"rsi":rv,"vr":vr,"hi20":hi20,"lo20":lo20,"hi60":hi60,"lo60":lo60,"score":score,"grade":grade,"state":state}

    def run_jpg_analysis(self):
        if not self.jpg_path:
            messagebox.showwarning("JPG 차트분석","먼저 JPG/PNG 차트 이미지를 첨부해주세요.");return
        self.jpg_result.delete("1.0","end");self.jpg_result.insert("end","이미지와 실제 차트 데이터를 교차분석 중...\n")
        q=self.jpg_q.get().strip();path=self.jpg_path
        def work():
            try:
                vis=self._image_chart_signal(path)
                data=None;err=""
                if q:
                    try:data=self._jpg_data_report(q)
                    except Exception as e:err=str(e)
                lines=[]
                lines.append("[마샬 JPG 차트 교차분석]")
                lines.append(f"첨부: {Path(path).name} · {self.jpg_image_size[0]}×{self.jpg_image_size[1]}")
                lines.append("")
                lines.append("■ 이미지 자체 보조판독")
                if vis.get("ok"):
                    lines.append(f"• 시각 방향: {vis['direction']} (좌→우 높이 변화 {vis['move']:+.1f}%)")
                    lines.append(f"• 차트 변동성: {vis['volatility']}")
                    lines.append(f"• 최근 형태: {vis['shape']}")
                    lines.append(f"• 이미지 판독 신뢰도: {vis['confidence']}% · 감지 포인트 {vis['points']}개")
                else:
                    lines.append("• "+vis.get("note","이미지 보조판독 제한"))
                lines.append("")
                if data:
                    lines.append(f"■ 실제 시세 교차검증 · {data['name']} ({data['code']})")
                    lines.append(f"현재가 {data['price']:,.0f}원 · 등락 {data['chg']:+.2f}%")
                    lines.append(f"차트점수 {data['score']:.1f}/100 · {data['grade']} · {data['state']}")
                    lines.append(f"MA5 {data['ma5']:,.0f} / MA20 {data['ma20']:,.0f} / MA60 {data['ma60']:,.0f} / MA120 {data['ma120']:,.0f}")
                    lines.append(f"RSI14 {data['rsi']:.1f} · 거래량/20일평균 {data['vr']:.2f}배")
                    lines.append(f"20일 지지 후보 {data['lo20']:,.0f}원 · 20일 돌파선 {data['hi20']:,.0f}원")
                    lines.append(f"60일 주요 범위 {data['lo60']:,.0f} ~ {data['hi60']:,.0f}원")
                    lines.append("")
                    agree=""
                    if vis.get("ok"):
                        if vis['direction']=="상승형" and data['price']>data['ma20']:agree="이미지와 실제 데이터가 모두 상승 우위로 일치합니다."
                        elif vis['direction']=="하락형" and data['price']<data['ma20']:agree="이미지와 실제 데이터가 모두 약세/조정 쪽으로 일치합니다."
                        else:agree="이미지 인상과 실제 지표가 완전히 일치하지 않아 실제 OHLCV를 우선합니다."
                    lines.append("■ 마샬 판정")
                    lines.append("• "+(agree or "실제 OHLCV 기준으로 판단합니다."))
                    if data['score']>=82:lines.append("• 강한 후보지만 저항선 돌파 여부와 과열을 함께 확인하세요.")
                    elif data['score']>=70:lines.append("• 상승 준비/추세 유지 후보. 거래량 증가가 확인되면 신뢰도가 높아집니다.")
                    else:lines.append("• 아직 추격보다 지지 확인 또는 구조 개선을 기다리는 편이 낫습니다.")
                else:
                    lines.append("■ 실제 시세 교차검증")
                    if q and err:lines.append("• 종목 데이터 분석 실패: "+err)
                    else:lines.append("• 종목명/6자리 코드를 입력하면 MA·RSI·거래량·지지/저항과 이미지 판독을 합쳐 분석합니다.")
                lines.append("")
                lines.append("※ JPG만으로 정확한 가격·날짜를 임의 추정하지 않습니다. 가격·지표 수치는 실제 네이버 시세 데이터가 있을 때만 표시합니다.")
                text="\n".join(lines)
                self.after(0,lambda:self._fill_jpg_result(text))
            except Exception as e:
                self.after(0,lambda:messagebox.showerror("JPG 차트분석",str(e)))
        threading.Thread(target=work,daemon=True).start()

    def _fill_jpg_result(self,text):
        self.jpg_result.delete("1.0","end");self.jpg_result.insert("end",text);self.refresh_diag()

'''

s=s.replace(needle,jpg_methods+needle,1)
p.write_text(s,encoding='utf-8')
print('v3.3 JPG chart attachment + image-aware analysis patch applied')
