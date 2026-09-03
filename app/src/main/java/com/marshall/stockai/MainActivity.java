package com.marshall.stockai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(8,15,28), PANEL=Color.rgb(17,24,39), TEXT=Color.rgb(229,231,235), MUTED=Color.rgb(148,163,184);
    private static final int RED=Color.rgb(220,38,38), GREEN=Color.rgb(22,163,74), BLUE=Color.rgb(37,99,235), AMBER=Color.rgb(217,119,6), PURPLE=Color.rgb(124,58,237), CYAN=Color.rgb(8,145,178);
    private static final double START_CASH=1_000_000_000d;
    private static final long LIVE_REFRESH_MS=15_000L;

    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final StockEngine engine=new StockEngine();
    private final MarketHub market=new MarketHub();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private final DecimalFormat money=new DecimalFormat("#,###");
    private int screenToken=0;

    static class LoadingUi { ProgressBar bar; TextView text; int token; }
    static class Holding { String code="",name=""; int qty; double avg; }
    static class SmartPick { StockEngine.Candidate c; double score; String reason=""; }

    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("marshall_stock_standalone",MODE_PRIVATE);showHome();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);executor.shutdownNow();super.onDestroy();}

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density);}
    private LinearLayout page(String title,boolean homeButton){
        handler.removeCallbacksAndMessages(null);screenToken++;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(15),dp(14),dp(15),dp(30));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.HORIZONTAL);h.setGravity(Gravity.CENTER_VERTICAL);
        if(homeButton){Button back=smallButton("← 메인",BLUE);back.setOnClickListener(v->showHome());h.addView(back,new LinearLayout.LayoutParams(dp(88),dp(48)));}
        TextView t=label(title,23,TEXT,true);t.setPadding(dp(10),0,0,0);h.addView(t,new LinearLayout.LayoutParams(0,-2,1));root.addView(h);addSpace(root,12);setContentView(scroll);return root;
    }
    private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setLineSpacing(0,1.18f);return v;}
    private Button smallButton(String text,int color){Button b=new Button(this);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;}
    private Button menuButton(String title,String sub,int color){Button b=smallButton(title+(sub.isEmpty()?"":"\n"+sub),color);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setPadding(dp(17),dp(10),dp(17),dp(10));b.setTextSize(15);b.setMinHeight(dp(68));return b;}
    private void addSpace(LinearLayout root,int h){root.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void addCard(LinearLayout root,View view){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(13),dp(12),dp(13),dp(12));card.setBackgroundColor(PANEL);card.addView(view);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));root.addView(card,lp);}

    private void showHome(){
        LinearLayout root=page("마샬 주식 AI",false);
        root.addView(label("Android v1.0 · 통합 투자분석 + 가상투자",14,GREEN,true));
        root.addView(label("네이버 공개 시세·차트·수급·뉴스 기반 · 휴대폰 독립 실행",12,MUTED,false));addSpace(root,14);
        Button dash=menuButton("시장 대시보드","KOSPI·KOSDAQ · 개인/외국인/기관 흐름",CYAN);dash.setOnClickListener(v->showMarketDashboard());root.addView(dash);addSpace(root,6);
        Button search=menuButton("종목 종합분석","일봉/주봉/월봉 · 수급 · 뉴스 · 지지/저항",RED);search.setOnClickListener(v->showSearch());root.addView(search);addSpace(root,6);
        Button watch=menuButton("관심종목 LIVE","현재가 · 등락률 · 거래량 · 15초 자동 갱신",BLUE);watch.setOnClickListener(v->showWatchlist());root.addView(watch);addSpace(root,6);
        Button sim=menuButton("10억 가상투자","매수/매도 · 평균단가 · 평가/실현손익",PURPLE);sim.setOnClickListener(v->showPortfolio());root.addView(sim);addSpace(root,6);
        Button top=menuButton("당일 거래 이슈 TOP10","거래대금·거래량·차트·외국인/기관·뉴스",GREEN);top.setOnClickListener(v->loadRadar(false,false));root.addView(top);addSpace(root,6);
        Button setup=menuButton("상승가능성 TOP20","주봉 준비패턴 · 수급 · 뉴스 촉매",AMBER);setup.setOnClickListener(v->loadRadar(true,false));root.addView(setup);addSpace(root,6);
        Button picks=menuButton("마샬 추천주","TOP10 + TOP20 교집합 및 강도 재평가",Color.rgb(190,24,93));picks.setOnClickListener(v->loadSmartPicks(false));root.addView(picks);addSpace(root,6);
        Button sector=menuButton("상승 기대 섹터","업종/테마 강도 + 실시간 뉴스 노출 결합",Color.rgb(13,148,136));sector.setOnClickListener(v->showSectors());root.addView(sector);addSpace(root,6);
        Button info=menuButton("분석 기준 · 데이터 안내","점수 구성과 데이터 제한 확인",Color.DKGRAY);info.setOnClickListener(v->showInfo());root.addView(info);
    }

    private LoadingUi showLoading(String title,String message){LinearLayout root=page(title,true);LoadingUi u=new LoadingUi();u.token=screenToken;u.bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);u.bar.setMax(100);u.bar.setProgress(2);root.addView(u.bar,new LinearLayout.LayoutParams(-1,dp(14)));addSpace(root,11);u.text=label(message,14,MUTED,false);u.text.setGravity(Gravity.CENTER);root.addView(u.text);return u;}
    private StockEngine.Progress progress(LoadingUi u){return (pct,msg)->runOnUiThread(()->{if(u.token==screenToken){u.bar.setProgress(Math.max(0,Math.min(100,pct)));u.text.setText(msg);}});}

    private void showMarketDashboard(){
        LoadingUi u=showLoading("시장 대시보드","지수와 투자자 흐름을 조회합니다...");
        executor.submit(()->{MarketHub.IndexQuote k=market.indexQuote("KOSPI"),d=market.indexQuote("KOSDAQ");MarketHub.InvestorPulse p=market.investorPulse();runOnUiThread(()->{if(u.token!=screenToken)return;LinearLayout root=page("시장 대시보드",true);addIndexCard(root,"KOSPI",k);addIndexCard(root,"KOSDAQ",d);if(p.ok){addCard(root,label("투자자 흐름 · "+p.date+"\n개인 "+flowMoney(p.personal)+"\n외국인 "+flowMoney(p.foreigner)+"\n기관 "+flowMoney(p.institution),15,TEXT,true));}else addCard(root,label("투자자별 순매수 데이터는 공급 화면 구조에 따라 일시적으로 읽지 못할 수 있습니다. 개별 종목 분석의 외국인·기관 순매수 랭킹은 별도로 계속 반영됩니다.",13,MUTED,false));Button s=smallButton("상승 기대 섹터 보기",GREEN);s.setOnClickListener(v->showSectors());root.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));});});
    }
    private void addIndexCard(LinearLayout root,String title,MarketHub.IndexQuote q){String text=q.ok?title+"  "+String.format(Locale.KOREA,"%,.2f  %+.2f%%",q.price,q.changePct):title+"  조회 실패";addCard(root,label(text,19,q.changePct>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));}
    private String flowMoney(double v){return (v>=0?"+":"")+money.format(v);}

    private void showSearch(){LinearLayout root=page("종목 종합분석",true);EditText q=input("종목명 또는 6자리 코드");root.addView(q,new LinearLayout.LayoutParams(-1,dp(56)));Spinner period=new Spinner(this);period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"일봉","주봉","월봉"}));root.addView(period,new LinearLayout.LayoutParams(-1,dp(54)));Button go=smallButton("분석 시작",RED);go.setOnClickListener(v->{String x=q.getText().toString().trim();if(x.isEmpty()){toast("종목명을 입력하세요.");return;}analyze(x,String.valueOf(period.getSelectedItem()));});root.addView(go,new LinearLayout.LayoutParams(-1,dp(54)));}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine(true);return e;}

    private void analyze(String query,String period){LoadingUi u=showLoading("종목 종합분석","차트·수급·뉴스 데이터를 결합하는 중...");executor.submit(()->{try{StockEngine.Analysis a=engine.analyze(query,period,progress(u));runOnUiThread(()->{if(u.token==screenToken)showAnalysis(a);});}catch(Exception e){runOnUiThread(()->showError("종목 분석 오류",e));}});}
    private void showAnalysis(StockEngine.Analysis a){
        LinearLayout root=page(a.name+" · "+a.period,true);root.addView(label(money.format(a.current)+"원",29,TEXT,true));root.addView(label(String.format(Locale.KOREA,"%+.2f%% · %s · %s",a.changePct,a.code,a.market),14,a.changePct>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));addSpace(root,10);
        StockEngine.Score s=a.score;if(s!=null){double composite=clamp(s.technical100*0.62+(a.flow==null?50:a.flow.score)*0.23+(a.news==null?50:a.news.score)*0.15,0,100);addCard(root,label("종합점수 "+f1(composite)+" / 100   ·   "+s.grade+"\n추세 "+f1(s.trend)+" · 모멘텀 "+f1(s.momentum)+" · 거래량 "+f1(s.volume)+"\n신규접근매력 "+f1(s.attractiveness)+" · 과열도 "+f1(s.overheat),15,TEXT,true));addCard(root,label("RSI "+f1(s.rsi)+" · ADX "+f1(s.adx)+" · CMO "+f1(s.cmo)+"\nMACD "+String.format(Locale.KOREA,"%.2f",s.macd)+" / Signal "+String.format(Locale.KOREA,"%.2f",s.signal)+"\n20일 평균 대비 거래량 "+String.format(Locale.KOREA,"%.2f배",s.volRatio),13,MUTED,false));}
        addCard(root,label("지지  "+joinPrices(a.supports)+"\n저항  "+joinPrices(a.resistances),14,TEXT,false));
        if(a.flow!=null)addCard(root,label("[수급]\n"+a.flow.summary+"\n수급점수 "+f1(a.flow.score)+"/100",14,TEXT,false));
        if(a.news!=null)addCard(root,label("[뉴스]\n"+a.news.summary+"\n뉴스점수 "+f1(a.news.score)+"/100",14,TEXT,false));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);Button add=smallButton("관심 +",BLUE);add.setOnClickListener(v->{addWatch(a.code,a.name);toast("관심종목에 추가했습니다.");});actions.addView(add,new LinearLayout.LayoutParams(0,dp(52),1));Button buy=smallButton("가상매수",PURPLE);buy.setOnClickListener(v->showBuyPrefilled(a.code,a.name,a.current));actions.addView(buy,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(actions);addSpace(root,8);addCard(root,label(a.report,14,TEXT,false));
    }

    private void loadRadar(boolean setup20,boolean force){String key=setup20?"setup20":"top10";if(!force){List<StockEngine.Candidate> cached=loadCache(key);if(cached!=null&&!cached.isEmpty()){showRadarList(cached,setup20,true);return;}}LoadingUi u=showLoading(setup20?"상승가능성 TOP20":"당일 거래 이슈 TOP10",setup20?"주봉·수급·뉴스를 분석합니다...":"거래대금·거래량·차트·수급·뉴스를 분석합니다...");executor.submit(()->{try{List<StockEngine.Candidate> list=setup20?engine.setup20(progress(u)):engine.top10(progress(u));saveCache(key,list);runOnUiThread(()->{if(u.token==screenToken)showRadarList(list,setup20,false);});}catch(Exception e){runOnUiThread(()->showError(setup20?"TOP20 오류":"TOP10 오류",e));}});}
    private void showRadarList(List<StockEngine.Candidate> list,boolean setup20,boolean cached){LinearLayout root=page(setup20?"상승가능성 TOP20":"당일 거래 이슈 TOP10",true);root.addView(label(cached?"오늘 저장된 분석 결과":"방금 새로 계산한 결과",12,MUTED,false));Button refresh=smallButton("새로 분석",RED);refresh.setOnClickListener(v->loadRadar(setup20,true));root.addView(refresh,new LinearLayout.LayoutParams(-1,dp(48)));addSpace(root,8);if(list==null||list.isEmpty()){root.addView(label("결과가 없습니다.",15,MUTED,false));return;}for(int i=0;i<list.size();i++){StockEngine.Candidate c=list.get(i);int rank=i+1;String line=rank+"위  "+c.name+"  "+f1(c.finalScore)+"점\n"+money.format(c.current)+"원  "+String.format(Locale.KOREA,"%+.2f%%",c.changePct)+"\n"+(setup20?"주봉":"기술")+" "+f1(c.secondaryScore)+" · 수급 "+f1(c.flowScore)+" · 뉴스 "+f1(c.newsScore)+"\n"+c.reason;Button b=menuButton(line,"",setup20?AMBER:GREEN);b.setOnClickListener(v->showRadarDetail(c,rank,setup20));root.addView(b);addSpace(root,5);}}
    private void showRadarDetail(StockEngine.Candidate c,int rank,boolean setup20){LinearLayout root=page((rank>0?rank+"위 · ":"")+c.name,true);addCard(root,label(c.detail.isEmpty()?c.reason:c.detail,14,TEXT,false));LinearLayout row=new LinearLayout(this);Button add=smallButton("관심 +",BLUE);add.setOnClickListener(v->{addWatch(c.code,c.name);toast("관심종목 추가");});row.addView(add,new LinearLayout.LayoutParams(0,dp(52),1));Button buy=smallButton("가상매수",PURPLE);buy.setOnClickListener(v->showBuyPrefilled(c.code,c.name,c.current));row.addView(buy,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(row);Button full=smallButton(setup20?"주봉 상세분석":"일봉 상세분석",RED);full.setOnClickListener(v->analyze(c.code,setup20?"주봉":"일봉"));root.addView(full,new LinearLayout.LayoutParams(-1,dp(52)));}

    private void loadSmartPicks(boolean force){
        List<StockEngine.Candidate> a=force?null:loadCache("top10"),b=force?null:loadCache("setup20");
        if(a!=null&&b!=null){showSmartPicks(combinePicks(a,b),true);return;}
        LoadingUi u=showLoading("마샬 추천주","TOP10과 TOP20을 함께 계산해 교집합을 찾습니다...");
        executor.submit(()->{try{List<StockEngine.Candidate> top=engine.top10(progress(u));saveCache("top10",top);List<StockEngine.Candidate> setup=engine.setup20(progress(u));saveCache("setup20",setup);List<SmartPick> picks=combinePicks(top,setup);runOnUiThread(()->{if(u.token==screenToken)showSmartPicks(picks,false);});}catch(Exception e){runOnUiThread(()->showError("추천주 분석 오류",e));}});
    }
    private List<SmartPick> combinePicks(List<StockEngine.Candidate> top,List<StockEngine.Candidate> setup){Map<String,StockEngine.Candidate> sm=new HashMap<>();for(StockEngine.Candidate c:setup)sm.put(c.code,c);ArrayList<SmartPick> out=new ArrayList<>();for(StockEngine.Candidate c:top){SmartPick p=new SmartPick();p.c=c;StockEngine.Candidate s=sm.get(c.code);if(s!=null){p.score=clamp(c.finalScore*0.55+s.finalScore*0.45+8,0,100);p.reason="당일 이슈 + 주봉 상승준비 동시 포착";}else{p.score=c.finalScore*0.92;p.reason="당일 이슈 강도 우수";}out.add(p);}for(StockEngine.Candidate s:setup){boolean exists=false;for(SmartPick p:out)if(p.c.code.equals(s.code)){exists=true;break;}if(!exists&&s.finalScore>=78){SmartPick p=new SmartPick();p.c=s;p.score=s.finalScore*0.90;p.reason="중기 상승준비 점수 상위";out.add(p);}}out.sort((x,y)->Double.compare(y.score,x.score));if(out.size()>10)return new ArrayList<>(out.subList(0,10));return out;}
    private void showSmartPicks(List<SmartPick> list,boolean cached){LinearLayout root=page("마샬 추천주",true);addCard(root,label("당일 거래이슈 TOP10과 상승가능성 TOP20을 교차해, 두 목록에 동시에 포착되는 종목을 우선합니다. 추천은 매수 보장이 아닌 분석 우선순위입니다.",13,MUTED,false));Button r=smallButton("전체 재분석",RED);r.setOnClickListener(v->loadSmartPicks(true));root.addView(r,new LinearLayout.LayoutParams(-1,dp(48)));addSpace(root,7);int rank=1;for(SmartPick p:list){StockEngine.Candidate c=p.c;Button x=menuButton(rank+"위  "+c.name+"  "+f1(p.score)+"점\n"+money.format(c.current)+"원  "+String.format(Locale.KOREA,"%+.2f%%",c.changePct)+"\n"+p.reason,"",Color.rgb(190,24,93));x.setOnClickListener(v->showRadarDetail(c,0,false));root.addView(x);addSpace(root,5);rank++;}}

    private void showSectors(){LoadingUi u=showLoading("상승 기대 섹터","업종/테마 강도와 실시간 뉴스 노출을 결합합니다...");executor.submit(()->{try{List<MarketHub.Sector> s=market.sectors();runOnUiThread(()->{if(u.token!=screenToken)return;LinearLayout root=page("상승 기대 섹터",true);addCard(root,label("섹터점수 = 업종/테마 등락강도 + 거래대금 신호(제공 시) + 실시간 뉴스 제목 노출. 개별 종목 매수 전에는 TOP10/TOP20과 상세 차트를 함께 확인하세요.",13,MUTED,false));if(s.isEmpty()){root.addView(label("섹터 데이터를 읽지 못했습니다.",14,MUTED,false));return;}for(int i=0;i<s.size();i++){MarketHub.Sector x=s.get(i);String t=(i+1)+"위  "+x.name+"  "+f1(x.score)+"점\n등락 "+String.format(Locale.KOREA,"%+.2f%%",x.changeRate)+" · 뉴스노출 "+x.newsHits+"건\n"+x.note;addCard(root,label(t,15,x.changeRate>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));}});}catch(Exception e){runOnUiThread(()->showError("섹터 분석 오류",e));}});}

    private void showWatchlist(){
        Set<String> set=new HashSet<>(prefs.getStringSet("watchlist",new HashSet<>()));
        if(set.isEmpty()){LinearLayout root=page("관심종목 LIVE",true);root.addView(label("관심종목이 없습니다. 종목 상세분석이나 TOP 목록에서 ‘관심 +’를 눌러 추가하세요.",15,MUTED,false));return;}
        LoadingUi u=showLoading("관심종목 LIVE","현재가·거래량을 갱신합니다...");
        executor.submit(()->{ArrayList<MarketHub.Quote> qs=new ArrayList<>();for(String row:set){String[] p=row.split("\\|",2);if(p.length<2)continue;MarketHub.Quote q=market.quote(p[0]);if(q.name.equals(p[0]))q.name=p[1];qs.add(q);}qs.sort(Comparator.comparing(q->q.name));runOnUiThread(()->{if(u.token==screenToken)renderWatchlist(qs);});});
    }
    private void renderWatchlist(List<MarketHub.Quote> qs){LinearLayout root=page("관심종목 LIVE",true);root.addView(label("15초 자동 갱신 · 시세 공급측 polling 간격/장 상태에 따라 실제 체결보다 지연될 수 있음",11,MUTED,false));Button refresh=smallButton("지금 새로고침",RED);refresh.setOnClickListener(v->showWatchlist());root.addView(refresh,new LinearLayout.LayoutParams(-1,dp(46)));addSpace(root,7);for(MarketHub.Quote q:qs){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(PANEL);String info=q.ok?q.name+"  "+money.format(q.price)+"원  "+String.format(Locale.KOREA,"%+.2f%%",q.changePct)+"\n거래량 "+money.format(q.volume)+" · 거래대금 "+compactMoney(q.turnover):q.name+"  시세 조회 실패";card.addView(label(info,15,q.changePct>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));LinearLayout row=new LinearLayout(this);Button open=smallButton("분석",BLUE);open.setOnClickListener(v->analyze(q.code,"일봉"));row.addView(open,new LinearLayout.LayoutParams(0,dp(46),1));Button buy=smallButton("매수",PURPLE);buy.setOnClickListener(v->showBuyPrefilled(q.code,q.name,q.price));row.addView(buy,new LinearLayout.LayoutParams(0,dp(46),1));Button del=smallButton("삭제",Color.DKGRAY);del.setOnClickListener(v->{removeWatch(q.code);showWatchlist();});row.addView(del,new LinearLayout.LayoutParams(0,dp(46),1));card.addView(row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));root.addView(card,lp);}int token=screenToken;handler.postDelayed(()->{if(token==screenToken)showWatchlist();},LIVE_REFRESH_MS);}
    private void addWatch(String code,String name){Set<String> set=new HashSet<>(prefs.getStringSet("watchlist",new HashSet<>()));set.removeIf(x->x.startsWith(code+"|"));set.add(code+"|"+name);prefs.edit().putStringSet("watchlist",set).apply();}
    private void removeWatch(String code){Set<String> set=new HashSet<>(prefs.getStringSet("watchlist",new HashSet<>()));set.removeIf(x->x.startsWith(code+"|"));prefs.edit().putStringSet("watchlist",set).apply();}

    private void showPortfolio(){
        List<Holding> hs=loadHoldings();if(hs.isEmpty()){renderPortfolio(Collections.emptyList(),Collections.emptyMap());return;}
        LoadingUi u=showLoading("10억 가상투자","보유종목 현재가를 반영합니다...");executor.submit(()->{HashMap<String,MarketHub.Quote> quotes=new HashMap<>();for(Holding h:hs)quotes.put(h.code,market.quote(h.code));runOnUiThread(()->{if(u.token==screenToken)renderPortfolio(hs,quotes);});});
    }
    private void renderPortfolio(List<Holding> hs,Map<String,MarketHub.Quote> quotes){LinearLayout root=page("10억 가상투자",true);double cash=getCash(),value=0;for(Holding h:hs){MarketHub.Quote q=quotes.get(h.code);value+=(q!=null&&q.ok?q.price:h.avg)*h.qty;}double realized=getRealized(),total=cash+value;double pnl=total-START_CASH;addCard(root,label("총자산  "+money.format(total)+"원\n현금  "+money.format(cash)+"원\n주식평가  "+money.format(value)+"원\n총손익  "+signedMoney(pnl)+"  ·  실현손익 "+signedMoney(realized),18,pnl>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));LinearLayout act=new LinearLayout(this);Button buy=smallButton("신규 가상매수",PURPLE);buy.setOnClickListener(v->showBuy());act.addView(buy,new LinearLayout.LayoutParams(0,dp(50),1));Button reset=smallButton("계좌 초기화",Color.DKGRAY);reset.setOnClickListener(v->confirmResetPortfolio());act.addView(reset,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(act);addSpace(root,8);if(hs.isEmpty()){root.addView(label("보유종목이 없습니다. 시작자산은 10억원입니다.",14,MUTED,false));return;}for(Holding h:hs){MarketHub.Quote q=quotes.get(h.code);double cur=q!=null&&q.ok?q.price:h.avg,eval=cur*h.qty,pl=(cur-h.avg)*h.qty,rate=h.avg>0?(cur/h.avg-1)*100:0;LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(PANEL);card.addView(label(h.name+"  "+h.qty+"주\n평균 "+money.format(h.avg)+"원 · 현재 "+money.format(cur)+"원\n평가 "+money.format(eval)+"원 · "+signedMoney(pl)+" ("+String.format(Locale.KOREA,"%+.2f%%",rate)+")",15,pl>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));LinearLayout row=new LinearLayout(this);Button detail=smallButton("분석",BLUE);detail.setOnClickListener(v->analyze(h.code,"일봉"));row.addView(detail,new LinearLayout.LayoutParams(0,dp(46),1));Button sell=smallButton("매도",RED);sell.setOnClickListener(v->showSellDialog(h,cur));row.addView(sell,new LinearLayout.LayoutParams(0,dp(46),1));card.addView(row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));root.addView(card,lp);}int token=screenToken;handler.postDelayed(()->{if(token==screenToken)showPortfolio();},LIVE_REFRESH_MS);}

    private void showBuy(){LinearLayout root=page("가상매수",true);EditText q=input("종목명 또는 코드");EditText qty=input("수량");qty.setInputType(InputType.TYPE_CLASS_NUMBER);EditText price=input("매수 단가");price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);root.addView(q,new LinearLayout.LayoutParams(-1,dp(55)));root.addView(qty,new LinearLayout.LayoutParams(-1,dp(55)));root.addView(price,new LinearLayout.LayoutParams(-1,dp(55)));root.addView(label("가용현금 "+money.format(getCash())+"원",13,MUTED,false));Button go=smallButton("가상매수 실행",PURPLE);go.setOnClickListener(v->{int n=parseInt(qty.getText().toString());double p=parseDouble(price.getText().toString());String query=q.getText().toString().trim();if(query.isEmpty()||n<=0||p<=0){toast("종목·수량·단가를 확인하세요.");return;}resolveAndBuy(query,n,p);});root.addView(go,new LinearLayout.LayoutParams(-1,dp(54)));}
    private void showBuyPrefilled(String code,String name,double current){LinearLayout root=page("가상매수 · "+name,true);EditText qty=input("수량");qty.setInputType(InputType.TYPE_CLASS_NUMBER);EditText price=input("매수 단가");price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);if(current>0)price.setText(String.valueOf((long)current));root.addView(label(code+" · 가용현금 "+money.format(getCash())+"원",13,MUTED,false));root.addView(qty,new LinearLayout.LayoutParams(-1,dp(55)));root.addView(price,new LinearLayout.LayoutParams(-1,dp(55)));Button go=smallButton("가상매수 실행",PURPLE);go.setOnClickListener(v->{int n=parseInt(qty.getText().toString());double p=parseDouble(price.getText().toString());if(n<=0||p<=0){toast("수량과 단가를 확인하세요.");return;}doBuy(code,name,n,p);});root.addView(go,new LinearLayout.LayoutParams(-1,dp(54)));}
    private void resolveAndBuy(String query,int qty,double price){LoadingUi u=showLoading("가상매수","종목을 확인합니다...");executor.submit(()->{try{StockEngine.Candidate c=engine.resolve(query);runOnUiThread(()->{if(u.token==screenToken)doBuy(c.code,c.name,qty,price);});}catch(Exception e){runOnUiThread(()->showError("종목 확인 오류",e));}});}
    private void doBuy(String code,String name,int qty,double price){double amount=qty*price,cash=getCash();if(amount>cash){toast("가용현금이 부족합니다.");return;}List<Holding> hs=loadHoldings();Holding target=null;for(Holding h:hs)if(h.code.equals(code)){target=h;break;}if(target==null){target=new Holding();target.code=code;target.name=name;target.qty=0;target.avg=0;hs.add(target);}double oldCost=target.avg*target.qty;target.qty+=qty;target.avg=(oldCost+amount)/target.qty;setCash(cash-amount);saveHoldings(hs);addWatch(code,name);toast("가상매수 완료");showPortfolio();}
    private void showSellDialog(Holding h,double current){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(10),dp(20),0);TextView count=label("매도수량: "+h.qty+" / "+h.qty+"주",15,Color.BLACK,true);box.addView(count);SeekBar seek=new SeekBar(this);seek.setMax(Math.max(1,h.qty));seek.setProgress(h.qty);box.addView(seek);EditText price=new EditText(this);price.setHint("매도 단가");price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);if(current>0)price.setText(String.valueOf((long)current));box.addView(price);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){if(p<1){s.setProgress(1);return;}count.setText("매도수량: "+p+" / "+h.qty+"주");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});new AlertDialog.Builder(this).setTitle(h.name+" 가상매도").setView(box).setNegativeButton("취소",null).setPositiveButton("매도",(d,w)->{int qty=Math.max(1,seek.getProgress());double p=parseDouble(price.getText().toString());if(p<=0){toast("매도 단가를 확인하세요.");return;}doSell(h,qty,p);}).show();}
    private void doSell(Holding h,int qty,double price){List<Holding> hs=loadHoldings();Holding t=null;for(Holding x:hs)if(x.code.equals(h.code)){t=x;break;}if(t==null||qty<=0||qty>t.qty){toast("보유수량을 확인하세요.");return;}double realized=(price-t.avg)*qty;setRealized(getRealized()+realized);setCash(getCash()+price*qty);t.qty-=qty;if(t.qty==0)hs.remove(t);saveHoldings(hs);toast("가상매도 완료");showPortfolio();}
    private void confirmResetPortfolio(){new AlertDialog.Builder(this).setTitle("가상계좌 초기화").setMessage("보유종목과 손익을 모두 삭제하고 다시 10억원으로 시작합니다.").setNegativeButton("취소",null).setPositiveButton("초기화",(d,w)->{prefs.edit().remove("portfolio_holdings").remove("portfolio_cash").remove("portfolio_realized").apply();showPortfolio();}).show();}

    private List<Holding> loadHoldings(){ArrayList<Holding> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("portfolio_holdings","[]"));for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;Holding h=new Holding();h.code=j.optString("code");h.name=j.optString("name");h.qty=j.optInt("qty");h.avg=j.optDouble("avg");if(h.qty>0)out.add(h);}}catch(Exception ignored){}return out;}
    private void saveHoldings(List<Holding> hs){JSONArray a=new JSONArray();try{for(Holding h:hs){JSONObject j=new JSONObject();j.put("code",h.code);j.put("name",h.name);j.put("qty",h.qty);j.put("avg",h.avg);a.put(j);}prefs.edit().putString("portfolio_holdings",a.toString()).apply();}catch(Exception ignored){}}
    private double getCash(){return parseDouble(prefs.getString("portfolio_cash",String.valueOf(START_CASH)));}
    private void setCash(double v){prefs.edit().putString("portfolio_cash",String.valueOf(v)).apply();}
    private double getRealized(){return parseDouble(prefs.getString("portfolio_realized","0"));}
    private void setRealized(double v){prefs.edit().putString("portfolio_realized",String.valueOf(v)).apply();}

    private void showInfo(){LinearLayout root=page("분석 기준 · 데이터 안내",true);addCard(root,label("핵심 점수\n\n① 차트: 이동평균, RSI, MACD, CMO, ADX, 거래량, 과열도\n② 수급: 외국인·기관 순매수 랭킹\n③ 뉴스: 종목 뉴스와 포털 기사 노출, 긍정/위험 키워드\n④ 유동성: 거래대금·거래량 상위 후보\n⑤ 중기후보: 주봉 추세, 돌파거리, 4주 수익률, 주봉 거래량\n⑥ 섹터: 업종/테마 등락강도 + 뉴스 노출\n\n마샬 추천주는 당일 이슈 TOP10과 상승가능성 TOP20의 교집합을 우선합니다.",14,TEXT,false));addCard(root,label("데이터는 네이버 증권의 로그인 없는 공개 GET 경로를 중심으로 사용합니다. 해당 경로는 공식 개발자 API가 아니며 구조가 바뀔 수 있어 일부 항목은 중립점수 또는 조회 실패로 표시될 수 있습니다. 공개 시세는 REST polling 방식이므로 증권사 체결창과 완전히 같은 틱 실시간을 보장하지 않습니다.\n\n가상투자는 실제 주문을 전송하지 않으며 휴대폰 내부 데이터에만 저장됩니다. 본 앱은 투자 판단 보조 도구이며 수익을 보장하지 않습니다.",13,MUTED,false));}

    private void saveCache(String key,List<StockEngine.Candidate> list){try{JSONObject root=new JSONObject();root.put("date",today());JSONArray arr=new JSONArray();for(StockEngine.Candidate c:list){JSONObject j=new JSONObject();j.put("code",c.code);j.put("name",c.name);j.put("market",c.market);j.put("current",c.current);j.put("change",c.changePct);j.put("secondary",c.secondaryScore);j.put("flow",c.flowScore);j.put("news",c.newsScore);j.put("final",c.finalScore);j.put("reason",c.reason);j.put("detail",c.detail);j.put("weeklyRsi",c.weeklyRsi);j.put("gap",c.breakoutGap);j.put("vRatio",c.weeklyVolRatio);j.put("slope",c.ma20Slope);j.put("ret4w",c.ret4w);j.put("overheat",c.overheat);j.put("fr",c.foreignRank);j.put("ir",c.institutionRank);arr.put(j);}root.put("items",arr);prefs.edit().putString("cache_"+key,root.toString()).apply();}catch(Exception ignored){}}
    private List<StockEngine.Candidate> loadCache(String key){try{String raw=prefs.getString("cache_"+key,"");if(raw.isEmpty())return null;JSONObject root=new JSONObject(raw);if(!today().equals(root.optString("date")))return null;JSONArray arr=root.optJSONArray("items");if(arr==null)return null;ArrayList<StockEngine.Candidate> out=new ArrayList<>();for(int i=0;i<arr.length();i++){JSONObject j=arr.optJSONObject(i);if(j==null)continue;StockEngine.Candidate c=new StockEngine.Candidate();c.code=j.optString("code");c.name=j.optString("name");c.market=j.optString("market");c.current=j.optDouble("current");c.changePct=j.optDouble("change");c.secondaryScore=j.optDouble("secondary");c.flowScore=j.optDouble("flow",50);c.newsScore=j.optDouble("news",50);c.finalScore=j.optDouble("final");c.reason=j.optString("reason");c.detail=j.optString("detail");c.weeklyRsi=j.optDouble("weeklyRsi");c.breakoutGap=j.optDouble("gap");c.weeklyVolRatio=j.optDouble("vRatio");c.ma20Slope=j.optDouble("slope");c.ret4w=j.optDouble("ret4w");c.overheat=j.optDouble("overheat");c.foreignRank=j.optInt("fr");c.institutionRank=j.optInt("ir");out.add(c);}return out;}catch(Exception e){return null;}}

    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.KOREA).format(new Date());}
    private void showError(String title,Exception e){LinearLayout root=page(title,true);String msg=e.getMessage()==null?e.toString():e.getMessage();addCard(root,label(msg+"\n\n인터넷 연결 또는 데이터 공급 경로를 확인한 뒤 다시 시도해 주세요.",14,Color.rgb(252,165,165),false));}
    private String joinPrices(List<Double>a){if(a==null||a.isEmpty())return "-";ArrayList<String>x=new ArrayList<>();for(double v:a)x.add(money.format(v)+"원");return String.join(" / ",x);}
    private String f1(double v){return String.format(Locale.KOREA,"%.1f",v);}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    private int parseInt(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return 0;}}
    private double parseDouble(String s){try{return Double.parseDouble(s.replace(",","").trim());}catch(Exception e){return 0;}}
    private String signedMoney(double v){return (v>=0?"+":"")+money.format(v)+"원";}
    private String compactMoney(double v){if(v>=1_000_000_000_000d)return String.format(Locale.KOREA,"%.1f조",v/1_000_000_000_000d);if(v>=100_000_000d)return String.format(Locale.KOREA,"%.1f억",v/100_000_000d);if(v>=10_000d)return String.format(Locale.KOREA,"%.1f만",v/10_000d);return money.format(v);}
    private double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
