package com.marshall.stockai;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,18,32);
    private static final int PANEL = Color.rgb(17,24,39);
    private static final int TEXT = Color.rgb(229,231,235);
    private static final int MUTED = Color.rgb(148,163,184);
    private static final int RED = Color.rgb(220,38,38);
    private static final int GREEN = Color.rgb(34,197,94);
    private static final int BLUE = Color.rgb(59,130,246);
    private static final int AMBER = Color.rgb(245,158,11);
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private SharedPreferences prefs;
    private final DecimalFormat money = new DecimalFormat("#,###");

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("marshall_stock", MODE_PRIVATE);
        showHome();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private LinearLayout page(String title, boolean homeButton) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (homeButton) {
            Button back = smallButton("← 메인", BLUE);
            back.setOnClickListener(v -> showHome());
            header.addView(back);
        }
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(10),0,0,0);
        header.addView(t, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header);
        addSpace(root, 14);
        setContentView(scroll);
        return root;
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0,1.15f);
        return v;
    }

    private Button smallButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        return b;
    }

    private Button menuButton(String title, String sub, int color) {
        Button b = smallButton(title + "\n" + sub, color);
        b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18),dp(12),dp(18),dp(12));
        b.setTextSize(16);
        b.setMinHeight(dp(74));
        return b;
    }

    private void addSpace(LinearLayout root, int h) {
        Space s = new Space(this);
        root.addView(s, new LinearLayout.LayoutParams(1,dp(h)));
    }

    private void addCard(LinearLayout root, View view) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12));
        card.setBackgroundColor(PANEL);
        card.addView(view);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(5),0,dp(5));
        root.addView(card,lp);
    }

    private String server() {
        return prefs.getString("server_url","http://10.0.2.2:8787").replaceAll("/+$","");
    }

    private void showHome() {
        LinearLayout root = page("마샬 주식분석", false);
        root.addView(label("Android v0.2 · PC 분석엔진 연동",13,MUTED,false));
        addSpace(root,14);

        Button search = menuButton("종목 검색 · 상세분석","일봉 / 주봉 / 월봉",RED);
        search.setOnClickListener(v -> showSearch()); root.addView(search); addSpace(root,8);
        Button watch = menuButton("관심종목","휴대폰에 저장한 종목",BLUE);
        watch.setOnClickListener(v -> showWatchlist()); root.addView(watch); addSpace(root,8);
        Button top = menuButton("오늘의 TOP10","차트 + 외인·기관 + 뉴스",GREEN);
        top.setOnClickListener(v -> loadRadar(false,false)); root.addView(top); addSpace(root,8);
        Button setup = menuButton("상승준비 TOP20","주봉 전환 + 수급 + 뉴스 촉매",AMBER);
        setup.setOnClickListener(v -> loadRadar(true,false)); root.addView(setup); addSpace(root,8);
        Button settings = menuButton("연결 설정","PC 분석서버 주소 입력",Color.DKGRAY);
        settings.setOnClickListener(v -> showSettings()); root.addView(settings);
        addSpace(root,16);
        TextView info = label("현재 서버\n" + server() + "\n\n같은 Wi-Fi에서 PC_분석서버의 run_mobile_server.bat을 실행한 뒤 사용하세요.",13,MUTED,false);
        addCard(root,info);
    }

    private void showSearch() {
        LinearLayout root = page("종목 분석", true);
        EditText q = new EditText(this);
        q.setHint("종목명 또는 코드 (예: 지엔씨에너지 / 119850)");
        q.setHintTextColor(MUTED); q.setTextColor(TEXT); q.setSingleLine(true);
        root.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));
        Spinner period = new Spinner(this);
        period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"일봉","주봉","월봉"}));
        root.addView(period,new LinearLayout.LayoutParams(-1,dp(58)));
        Button go = smallButton("분석",RED);
        go.setOnClickListener(v -> {
            String query=q.getText().toString().trim();
            if(query.isEmpty()){toast("종목명 또는 코드를 입력하세요.");return;}
            analyze(query,String.valueOf(period.getSelectedItem()));
        });
        root.addView(go,new LinearLayout.LayoutParams(-1,dp(56)));
    }

    private void showLoading(String title, String text) {
        LinearLayout root = page(title,true);
        ProgressBar p = new ProgressBar(this); root.addView(p,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView t=label(text,14,MUTED,false); t.setGravity(Gravity.CENTER); root.addView(t);
    }

    private void analyze(String query,String period) {
        showLoading("종목 분석","데이터를 불러오고 분석 중입니다...");
        runJson("/analyze?q="+enc(query)+"&period="+enc(period),
            j -> showAnalysis(j),
            e -> showError("종목 분석 오류",e));
    }

    private void showAnalysis(JSONObject j) {
        LinearLayout root = page(j.optString("name","종목")+" · "+j.optString("period",""),true);
        double current=j.optDouble("current",0), change=j.optDouble("change_pct",0);
        TextView price=label(money.format(current)+"원",30,TEXT,true); root.addView(price);
        root.addView(label(String.format(Locale.KOREA,"%+.2f%% · %s",change,j.optString("code","")),15,change>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));
        addSpace(root,12);

        JSONObject s=j.optJSONObject("scores");
        if(s!=null){
            addCard(root,label("등급  "+s.optString("grade","-")+"\n추세 "+f1(s.optDouble("trend"))+"   모멘텀 "+f1(s.optDouble("momentum"))+"\n거래량 "+f1(s.optDouble("volume"))+"   과열도 "+f1(s.optDouble("overheat"))+"\n신규접근매력 "+f1(s.optDouble("attractiveness")),15,TEXT,true));
        }
        addCard(root,label("지지  "+joinPrices(j.optJSONArray("supports"))+"\n저항  "+joinPrices(j.optJSONArray("resistances")),14,TEXT,false));

        Button add=smallButton("관심종목 + 추가",BLUE);
        add.setOnClickListener(v->{addWatch(j.optString("code"),j.optString("name")); toast("관심종목에 추가했습니다.");});
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(54)));
        addSpace(root,10);
        addCard(root,label(j.optString("report","분석내용이 없습니다."),14,TEXT,false));
    }

    private void loadRadar(boolean setup20, boolean force) {
        showLoading(setup20?"상승준비 TOP20":"오늘의 TOP10", setup20?"주봉 준비 종목을 계산 중입니다. 첫 실행은 시간이 걸릴 수 있습니다.":"오늘의 상위 종목을 계산 중입니다.");
        String path=setup20?"/radar/setup20":"/radar/top10";
        runJson(path+"?force="+force, j->showRadarList(j,setup20), e->showError("랭킹 오류",e));
    }

    private void showRadarList(JSONObject payload, boolean setup20) {
        LinearLayout root=page(setup20?"상승준비 TOP20":"오늘의 TOP10",true);
        Button refresh=smallButton("오늘 데이터 새로고침",RED);
        refresh.setOnClickListener(v->loadRadar(setup20,true)); root.addView(refresh,new LinearLayout.LayoutParams(-1,dp(50)));
        addSpace(root,8);
        JSONArray arr=payload.optJSONArray("results");
        if(arr==null||arr.length()==0){root.addView(label("결과가 없습니다.",15,MUTED,false));return;}
        for(int i=0;i<arr.length();i++){
            JSONObject x=arr.optJSONObject(i); if(x==null) continue;
            String second=setup20?"주봉 "+f1(x.optDouble("setup_score")):"기술 "+f1(x.optDouble("technical_score"));
            String line=x.optInt("rank",i+1)+"위  "+x.optString("name")+"  "+f1(x.optDouble("final_score"))+"점\n"+
                    money.format(x.optDouble("current"))+"원  "+String.format(Locale.KOREA,"%+.2f%%",x.optDouble("change_pct"))+"\n"+
                    second+" · 수급 "+f1(x.optDouble("flow_score",50))+" · 뉴스 "+f1(setup20?x.optDouble("news_catalyst_score",50):x.optDouble("news_score",50))+"\n"+x.optString("reason","");
            Button b=menuButton(line,"", setup20?AMBER:GREEN);
            b.setOnClickListener(v->showRadarDetail(x,setup20));
            root.addView(b); addSpace(root,6);
        }
    }

    private void showRadarDetail(JSONObject x, boolean setup20) {
        LinearLayout root=page(x.optInt("rank")+"위 · "+x.optString("name"),true);
        addCard(root,label(x.optString("detail",x.optString("reason","")),14,TEXT,false));
        Button add=smallButton("관심종목 + 추가",BLUE);
        add.setOnClickListener(v->{addWatch(x.optString("code"),x.optString("name"));toast("관심종목에 추가했습니다.");});
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(52))); addSpace(root,8);
        Button full=smallButton("메인 상세분석",RED);
        full.setOnClickListener(v->analyze(x.optString("code"),setup20?"주봉":"일봉"));
        root.addView(full,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    private void showWatchlist() {
        LinearLayout root=page("관심종목",true);
        Set<String> set=prefs.getStringSet("watchlist",new HashSet<>());
        if(set.isEmpty()){root.addView(label("관심종목이 없습니다.",15,MUTED,false));return;}
        ArrayList<String> list=new ArrayList<>(set); Collections.sort(list);
        for(String row:list){
            String[] p=row.split("\\|",2); if(p.length<2) continue;
            LinearLayout line=new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
            Button open=smallButton(p[1]+"  "+p[0],BLUE); open.setOnClickListener(v->analyze(p[0],"일봉"));
            line.addView(open,new LinearLayout.LayoutParams(0,dp(56),1));
            Button del=smallButton("삭제",Color.DKGRAY); del.setOnClickListener(v->{removeWatch(p[0]);showWatchlist();});
            line.addView(del,new LinearLayout.LayoutParams(dp(82),dp(56)));
            root.addView(line); addSpace(root,6);
        }
    }

    private void addWatch(String code,String name){
        Set<String> set=new HashSet<>(prefs.getStringSet("watchlist",new HashSet<>()));
        set.removeIf(x->x.startsWith(code+"|")); set.add(code+"|"+name);
        prefs.edit().putStringSet("watchlist",set).apply();
    }
    private void removeWatch(String code){
        Set<String> set=new HashSet<>(prefs.getStringSet("watchlist",new HashSet<>()));
        set.removeIf(x->x.startsWith(code+"|")); prefs.edit().putStringSet("watchlist",set).apply();
    }

    private void showSettings() {
        LinearLayout root=page("연결 설정",true);
        root.addView(label("PC_분석서버에서 표시되는 http://192.168.x.x:8787 주소를 입력하세요.",13,MUTED,false));
        addSpace(root,8);
        EditText url=new EditText(this); url.setText(server()); url.setTextColor(TEXT); url.setSingleLine(true); root.addView(url,new LinearLayout.LayoutParams(-1,dp(58)));
        Button save=smallButton("저장",BLUE); save.setOnClickListener(v->{prefs.edit().putString("server_url",url.getText().toString().trim().replaceAll("/+$","")).apply();toast("저장했습니다.");});
        root.addView(save,new LinearLayout.LayoutParams(-1,dp(52))); addSpace(root,8);
        Button test=smallButton("서버 연결 테스트",GREEN); test.setOnClickListener(v->{prefs.edit().putString("server_url",url.getText().toString().trim().replaceAll("/+$","")).apply(); runJson("/health",j->toast(j.optString("message","연결 성공")),e->toast("연결 실패: "+e.getMessage()));});
        root.addView(test,new LinearLayout.LayoutParams(-1,dp(52)));
        addSpace(root,12);
        addCard(root,label("실제 갤럭시에서는 10.0.2.2가 아니라 PC 서버창에 표시되는 192.168.x.x 주소를 사용하세요. 스마트폰과 PC는 같은 Wi-Fi에 연결합니다.",13,MUTED,false));
    }

    private void showError(String title, Exception e) {
        LinearLayout root=page(title,true);
        addCard(root,label(e.getMessage()==null?e.toString():e.getMessage(),14,Color.rgb(252,165,165),false));
        Button retry=smallButton("메인으로",BLUE); retry.setOnClickListener(v->showHome()); root.addView(retry,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    private String joinPrices(JSONArray a){
        if(a==null||a.length()==0)return "-"; StringBuilder sb=new StringBuilder();
        for(int i=0;i<a.length();i++){if(i>0)sb.append(" / ");sb.append(money.format(a.optDouble(i))).append("원");} return sb.toString();
    }
    private String f1(double v){return String.format(Locale.KOREA,"%.1f",v);}
    private String enc(String s){try{return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());}catch(Exception e){return s;}}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}

    private void runJson(String path, java.util.function.Consumer<JSONObject> ok, java.util.function.Consumer<Exception> fail){
        executor.submit(()->{try{JSONObject j=getJson(path);runOnUiThread(()->ok.accept(j));}catch(Exception e){runOnUiThread(()->fail.accept(e));}});
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(server()+path).openConnection();
        c.setRequestMethod("GET"); c.setConnectTimeout(15000); c.setReadTimeout(600000); c.setRequestProperty("Accept","application/json");
        int status=c.getResponseCode(); InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();
        String body=readAll(in); c.disconnect();
        if(status<200||status>=300)throw new IOException("서버 오류 "+status+"\n"+body);
        return new JSONObject(body);
    }

    private String readAll(InputStream in) throws Exception {
        if(in==null)return ""; BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line;
        while((line=r.readLine())!=null)sb.append(line).append('\n'); r.close(); return sb.toString();
    }
}
