package com.marshall.stockai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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
    private static final int BG=Color.rgb(11,18,32);
    private static final int PANEL=Color.rgb(17,24,39);
    private static final int TEXT=Color.rgb(229,231,235);
    private static final int MUTED=Color.rgb(148,163,184);
    private static final int RED=Color.rgb(220,38,38);
    private static final int GREEN=Color.rgb(34,197,94);
    private static final int BLUE=Color.rgb(59,130,246);
    private static final int AMBER=Color.rgb(245,158,11);
    private static final int PURPLE=Color.rgb(139,92,246);

    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final StockEngine engine=new StockEngine();
    private SharedPreferences prefs;
    private final DecimalFormat money=new DecimalFormat("#,###");

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("marshall_stock_standalone",MODE_PRIVATE);
        showHome();
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density);}

    private LinearLayout page(String title,boolean homeButton){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(30));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.HORIZONTAL);h.setGravity(Gravity.CENTER_VERTICAL);
        if(homeButton){
            Button back=smallButton("← 메인",BLUE);back.setOnClickListener(v->showHome());h.addView(back);
        }
        TextView t=label(title,24,TEXT,true);t.setPadding(dp(10),0,0,0);h.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(h);addSpace(root,14);setContentView(scroll);return root;
    }

    private TextView label(String s,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setLineSpacing(0,1.18f);return v;
    }

    private Button smallButton(String text,int color){
        Button b=new Button(this);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);
        b.setBackgroundTintList(ColorStateList.valueOf(color));return b;
    }

    private Button menuButton(String title,String sub,int color){
        Button b=smallButton(title+"\n"+sub,color);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18),dp(11),dp(18),dp(11));b.setTextSize(16);b.setMinHeight(dp(74));return b;
    }

    private void addSpace(LinearLayout root,int h){root.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(h)));}

    private void addCard(LinearLayout root,View view){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(13),dp(14),dp(13));card.setBackgroundColor(PANEL);card.addView(view);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));root.addView(card,lp);
    }

    private void showHome(){
        LinearLayout root=page("마샬 주식분석",false);
        root.addView(label("Android v0.3 · 스마트폰 완전 독립형",14,GREEN,true));
        root.addView(label("PC 연동 불필요 · 스마트폰 인터넷 연결만 있으면 사용",12,MUTED,false));
        addSpace(root,15);

        Button search=menuButton("종목 검색 · 상세분석","종목명/코드 · 일봉/주봉/월봉",RED);
        search.setOnClickListener(v->showSearch());root.addView(search);addSpace(root,8);

        Button watch=menuButton("관심종목","휴대폰에 저장 · 바로 상세분석",BLUE);
        watch.setOnClickListener(v->showWatchlist());root.addView(watch);addSpace(root,8);

        Button top=menuButton("오늘의 TOP10","차트 + 외국인·기관 + 국내·해외 뉴스",GREEN);
        top.setOnClickListener(v->loadRadar(false,false));root.addView(top);addSpace(root,8);

        Button setup=menuButton("상승준비 TOP20","주봉 전환 + 수급 + 뉴스 촉매",AMBER);
        setup.setOnClickListener(v->loadRadar(true,false));root.addView(setup);addSpace(root,8);

        Button info=menuButton("데이터 · 분석 안내","스마트폰 단독 실행 방식 확인",Color.DKGRAY);
        info.setOnClickListener(v->showInfo());root.addView(info);

        addSpace(root,16);
        addCard(root,label(
                "이 버전은 PC 서버를 사용하지 않습니다.\n\n"+
                "스마트폰이 네이버 증권/금융의 공개 시세·시장·뉴스 데이터를 직접 조회하고, "+
                "이동평균·RSI·MACD·CMO·ADX·거래량·지지저항을 휴대폰 안에서 계산합니다.\n\n"+
                "TOP10/TOP20은 처음 계산할 때 여러 종목의 차트를 분석하므로 30초~수분 정도 걸릴 수 있습니다.",
                13,MUTED,false));
    }

    private void showInfo(){
        LinearLayout root=page("데이터 · 분석 안내",true);
        addCard(root,label(
                "독립형 구조\n\n"+
                "① 스마트폰 → 공개 주식 시세/차트 조회\n"+
                "② 스마트폰 내부에서 기술지표 계산\n"+
                "③ 외국인·기관 순매수 상위 랭킹 결합\n"+
                "④ 종목 뉴스 + 포털 기사 노출 + 해외뉴스를 결합\n"+
                "⑤ TOP10 / 상승준비 TOP20 순위 계산\n\n"+
                "PC, Python, 별도 서버 주소는 필요하지 않습니다.",
                14,TEXT,false));
        addCard(root,label(
                "데이터 공급 화면이나 비공식 공개 경로의 구조가 변경되면 일부 데이터가 일시적으로 조회되지 않을 수 있습니다. "+
                "그 경우 해당 항목은 중립점수로 처리하거나 오류를 표시합니다.\n\n"+
                "본 앱은 매수/수익을 보장하지 않는 투자 보조 분석 도구입니다.",
                13,MUTED,false));
    }

    private void showSearch(){
        LinearLayout root=page("종목 분석",true);
        EditText q=new EditText(this);
        q.setHint("종목명 또는 코드 (예: 지엔씨에너지 / 119850)");
        q.setHintTextColor(MUTED);q.setTextColor(TEXT);q.setSingleLine(true);
        root.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));

        Spinner period=new Spinner(this);
        ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"일봉","주봉","월봉"});
        period.setAdapter(ad);root.addView(period,new LinearLayout.LayoutParams(-1,dp(58)));

        Button go=smallButton("스마트폰에서 바로 분석",RED);
        go.setOnClickListener(v->{
            String query=q.getText().toString().trim();
            if(query.isEmpty()){toast("종목명 또는 코드를 입력하세요.");return;}
            analyze(query,String.valueOf(period.getSelectedItem()));
        });
        root.addView(go,new LinearLayout.LayoutParams(-1,dp(56)));
        addSpace(root,14);
        addCard(root,label("종목명을 입력하면 네이버 증권 자동검색에서 가장 일치하는 국내 종목을 찾아 분석합니다.",12,MUTED,false));
    }

    private static class LoadingUi{
        ProgressBar bar;TextView text;
    }

    private LoadingUi showLoading(String title,String message){
        LinearLayout root=page(title,true);
        LoadingUi u=new LoadingUi();
        u.bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);u.bar.setMax(100);u.bar.setProgress(1);
        root.addView(u.bar,new LinearLayout.LayoutParams(-1,dp(14)));addSpace(root,12);
        u.text=label(message,14,MUTED,false);u.text.setGravity(Gravity.CENTER);root.addView(u.text);
        return u;
    }

    private StockEngine.Progress progress(LoadingUi u){
        return (pct,msg)->runOnUiThread(()->{u.bar.setProgress(Math.max(0,Math.min(100,pct)));u.text.setText(msg);});
    }

    private void analyze(String query,String period){
        LoadingUi u=showLoading("종목 분석","스마트폰에서 데이터를 가져오는 중...");
        executor.submit(()->{
            try{
                StockEngine.Analysis a=engine.analyze(query,period,progress(u));
                runOnUiThread(()->showAnalysis(a));
            }catch(Exception e){runOnUiThread(()->showError("종목 분석 오류",e));}
        });
    }

    private void showAnalysis(StockEngine.Analysis a){
        LinearLayout root=page(a.name+" · "+a.period,true);
        root.addView(label(money.format(a.current)+"원",30,TEXT,true));
        root.addView(label(String.format(Locale.KOREA,"%+.2f%% · %s · %s",a.changePct,a.code,a.market),
                14,a.changePct>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));
        addSpace(root,12);

        StockEngine.Score s=a.score;
        if(s!=null){
            addCard(root,label(
                    "등급  "+s.grade+"   ·   "+s.regime+"\n"+
                    "추세 "+f1(s.trend)+"   모멘텀 "+f1(s.momentum)+"\n"+
                    "거래량 "+f1(s.volume)+"   과열도 "+f1(s.overheat)+"\n"+
                    "신규접근매력 "+f1(s.attractiveness),
                    15,TEXT,true));
            addCard(root,label(
                    "RSI "+f1(s.rsi)+" · CMO "+f1(s.cmo)+" · ADX "+f1(s.adx)+"\n"+
                    "MACD "+String.format(Locale.KOREA,"%.2f",s.macd)+" / Signal "+String.format(Locale.KOREA,"%.2f",s.signal)+"\n"+
                    "거래량 20평균 대비 "+String.format(Locale.KOREA,"%.2f배",s.volRatio),
                    13,MUTED,false));
        }

        addCard(root,label("지지  "+joinPrices(a.supports)+"\n저항  "+joinPrices(a.resistances),14,TEXT,false));
        if(a.flow!=null)addCard(root,label("[외국인·기관]\n"+a.flow.summary+"\n수급점수 "+f1(a.flow.score)+"/100",14,TEXT,false));
        if(a.news!=null)addCard(root,label("[뉴스]\n"+a.news.summary+"\n뉴스점수 "+f1(a.news.score)+"/100",14,TEXT,false));

        Button add=smallButton("관심종목 + 추가",BLUE);
        add.setOnClickListener(v->{addWatch(a.code,a.name);toast("관심종목에 추가했습니다.");});
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(54)));addSpace(root,10);

        addCard(root,label(a.report,14,TEXT,false));
        addSpace(root,12);
        addMarshallBox(root,a);
    }

    private void addMarshallBox(LinearLayout root,StockEngine.Analysis a){
        TextView title=label("마샬 분석 붙여넣기",17,TEXT,true);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(12),dp(14),dp(12));box.setBackgroundColor(PANEL);
        box.addView(title);
        box.addView(label("ChatGPT의 [MARSHALL_SCORE]를 붙이면 프로그램 60% + 마샬 40%로 점수를 합칩니다.",12,MUTED,false));
        EditText input=new EditText(this);input.setTextColor(TEXT);input.setHintTextColor(MUTED);
        input.setHint("[MARSHALL_SCORE]\\n추세=...\\n모멘텀=...");input.setMinHeight(dp(150));
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        TextView result=label("",14,GREEN,true);box.addView(result);
        Button apply=smallButton("마샬 분석 반영",PURPLE);
        apply.setOnClickListener(v->{
            Map<String,Double> m=parseMarshall(input.getText().toString());
            if(m.isEmpty()){toast("점수를 읽지 못했습니다. MARSHALL_SCORE 블록을 확인해 주세요.");return;}
            StockEngine.Score s=a.score;
            double tr=blend(s.trend,m.get("추세")),mo=blend(s.momentum,m.get("모멘텀"));
            double vo=blend(s.volume,m.get("거래량")),he=blend(s.overheat,m.get("과열도"));
            Double av=m.containsKey("신규접근매력")?m.get("신규접근매력"):m.get("매수매력");
            double at=blend(s.attractiveness,av);
            result.setText("통합점수\n추세 "+f1(tr)+" · 모멘텀 "+f1(mo)+" · 거래량 "+f1(vo)+
                    "\n과열도 "+f1(he)+" · 신규접근매력 "+f1(at));
        });
        box.addView(apply,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));root.addView(box,lp);
    }

    private Map<String,Double> parseMarshall(String raw){
        HashMap<String,Double> out=new HashMap<>();
        String[] keys={"추세","모멘텀","거래량","과열도","신규접근매력","매수매력"};
        for(String k:keys){
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(k)+"\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(raw);
            if(m.find())try{out.put(k,Math.max(0,Math.min(10,Double.parseDouble(m.group(1)))));}catch(Exception ignored){}
        }
        return out;
    }
    private double blend(double p,Double m){return m==null?p:p*0.6+m*0.4;}

    private void loadRadar(boolean setup20,boolean force){
        String key=setup20?"setup20":"top10";
        if(!force){
            List<StockEngine.Candidate> cached=loadCache(key);
            if(cached!=null&&!cached.isEmpty()){showRadarList(cached,setup20,true);return;}
        }

        LoadingUi u=showLoading(setup20?"상승준비 TOP20":"오늘의 TOP10",
                setup20?"주봉 준비종목을 스마트폰에서 분석합니다...":"오늘의 종목을 스마트폰에서 분석합니다...");
        executor.submit(()->{
            try{
                List<StockEngine.Candidate> list=setup20?engine.setup20(progress(u)):engine.top10(progress(u));
                saveCache(key,list);
                runOnUiThread(()->showRadarList(list,setup20,false));
            }catch(Exception e){runOnUiThread(()->showError(setup20?"상승준비 TOP20 오류":"TOP10 오류",e));}
        });
    }

    private void showRadarList(List<StockEngine.Candidate> list,boolean setup20,boolean cached){
        LinearLayout root=page(setup20?"상승준비 TOP20":"오늘의 TOP10",true);
        root.addView(label(cached?"오늘 계산한 저장 결과":"방금 스마트폰에서 새로 계산한 결과",12,MUTED,false));
        Button refresh=smallButton("오늘 데이터 새로고침",RED);
        refresh.setOnClickListener(v->loadRadar(setup20,true));root.addView(refresh,new LinearLayout.LayoutParams(-1,dp(50)));
        addSpace(root,8);
        if(list==null||list.isEmpty()){root.addView(label("결과가 없습니다.",15,MUTED,false));return;}

        for(int i=0;i<list.size();i++){
            StockEngine.Candidate c=list.get(i);final int rank=i+1;
            String second=setup20?"주봉 "+f1(c.secondaryScore):"기술 "+f1(c.secondaryScore);
            String line=rank+"위  "+c.name+"   "+f1(c.finalScore)+"점\n"+
                    money.format(c.current)+"원  "+String.format(Locale.KOREA,"%+.2f%%",c.changePct)+"\n"+
                    second+" · 수급 "+f1(c.flowScore)+" · 뉴스 "+f1(c.newsScore)+"\n"+c.reason;
            Button b=menuButton(line,"",setup20?AMBER:GREEN);
            b.setOnClickListener(v->showRadarDetail(c,rank,setup20));
            root.addView(b);addSpace(root,6);
        }
    }

    private void showRadarDetail(StockEngine.Candidate c,int rank,boolean setup20){
        LinearLayout root=page(rank+"위 · "+c.name,true);
        addCard(root,label(c.detail.isEmpty()?c.reason:c.detail,14,TEXT,false));
        Button add=smallButton("관심종목 + 추가",BLUE);
        add.setOnClickListener(v->{addWatch(c.code,c.name);toast("관심종목에 추가했습니다.");});
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));addSpace(root,8);
        Button full=smallButton(setup20?"주봉 상세분석":"일봉 상세분석",RED);
        full.setOnClickListener(v->analyze(c.code,setup20?"주봉":"일봉"));
        root.addView(full,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    private void showWatchlist(){
        LinearLayout root=page("관심종목",true);
        Set<String> set=prefs.getStringSet("watchlist",new HashSet<>());
        if(set.isEmpty()){root.addView(label("관심종목이 없습니다.",15,MUTED,false));return;}
        ArrayList<String> list=new ArrayList<>(set);Collections.sort(list);
        for(String row:list){
            String[] p=row.split("\\|",2);if(p.length<2)continue;
            LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
            Button open=smallButton(p[1]+"  "+p[0],BLUE);open.setOnClickListener(v->analyze(p[0],"일봉"));
            line.addView(open,new LinearLayout.LayoutParams(0,dp(56),1));
            Button del=smallButton("삭제",Color.DKGRAY);del.setOnClickListener(v->{removeWatch(p[0]);showWatchlist();});
            line.addView(del,new LinearLayout.LayoutParams(dp(82),dp(56)));root.addView(line);addSpace(root,6);
        }
    }

    private void addWatch(String code,String name){
        Set<String> set=new HashSet<>(prefs.getStringSet("watchlist",new HashSet<>()));
        set.removeIf(x->x.startsWith(code+"|"));set.add(code+"|"+name);prefs.edit().putStringSet("watchlist",set).apply();
    }
    private void removeWatch(String code){
        Set<String> set=new HashSet<>(prefs.getStringSet("watchlist",new HashSet<>()));
        set.removeIf(x->x.startsWith(code+"|"));prefs.edit().putStringSet("watchlist",set).apply();
    }

    private void saveCache(String key,List<StockEngine.Candidate> list){
        try{
            JSONObject root=new JSONObject();root.put("date",today());JSONArray arr=new JSONArray();
            for(StockEngine.Candidate c:list){
                JSONObject j=new JSONObject();
                j.put("code",c.code);j.put("name",c.name);j.put("market",c.market);j.put("current",c.current);
                j.put("change",c.changePct);j.put("secondary",c.secondaryScore);j.put("flow",c.flowScore);
                j.put("news",c.newsScore);j.put("final",c.finalScore);j.put("reason",c.reason);j.put("detail",c.detail);
                j.put("weeklyRsi",c.weeklyRsi);j.put("gap",c.breakoutGap);j.put("vRatio",c.weeklyVolRatio);
                j.put("slope",c.ma20Slope);j.put("ret4w",c.ret4w);j.put("overheat",c.overheat);
                j.put("fr",c.foreignRank);j.put("ir",c.institutionRank);arr.put(j);
            }
            root.put("items",arr);prefs.edit().putString("cache_"+key,root.toString()).apply();
        }catch(Exception ignored){}
    }

    private List<StockEngine.Candidate> loadCache(String key){
        try{
            String raw=prefs.getString("cache_"+key,"");if(raw.isEmpty())return null;
            JSONObject root=new JSONObject(raw);if(!today().equals(root.optString("date")))return null;
            JSONArray arr=root.optJSONArray("items");if(arr==null)return null;
            ArrayList<StockEngine.Candidate> out=new ArrayList<>();
            for(int i=0;i<arr.length();i++){
                JSONObject j=arr.optJSONObject(i);if(j==null)continue;StockEngine.Candidate c=new StockEngine.Candidate();
                c.code=j.optString("code");c.name=j.optString("name");c.market=j.optString("market");
                c.current=j.optDouble("current");c.changePct=j.optDouble("change");c.secondaryScore=j.optDouble("secondary");
                c.flowScore=j.optDouble("flow",50);c.newsScore=j.optDouble("news",50);c.finalScore=j.optDouble("final");
                c.reason=j.optString("reason");c.detail=j.optString("detail");c.weeklyRsi=j.optDouble("weeklyRsi");
                c.breakoutGap=j.optDouble("gap");c.weeklyVolRatio=j.optDouble("vRatio");c.ma20Slope=j.optDouble("slope");
                c.ret4w=j.optDouble("ret4w");c.overheat=j.optDouble("overheat");c.foreignRank=j.optInt("fr");c.institutionRank=j.optInt("ir");
                out.add(c);
            }
            return out;
        }catch(Exception e){return null;}
    }

    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.KOREA).format(new Date());}

    private void showError(String title,Exception e){
        LinearLayout root=page(title,true);
        String msg=e.getMessage()==null?e.toString():e.getMessage();
        addCard(root,label(msg+"\n\n인터넷 연결을 확인한 뒤 다시 시도해 주세요.",14,Color.rgb(252,165,165),false));
        Button home=smallButton("메인으로",BLUE);home.setOnClickListener(v->showHome());root.addView(home,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    private String joinPrices(List<Double>a){
        if(a==null||a.isEmpty())return "-";ArrayList<String>x=new ArrayList<>();for(double v:a)x.add(money.format(v)+"원");return String.join(" / ",x);
    }
    private String f1(double v){return String.format(Locale.KOREA,"%.1f",v);}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
}
