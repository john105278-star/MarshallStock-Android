package com.stockradar.app;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.stockradar.app.api.KiwoomClient;
import com.stockradar.app.core.ScanResult;
import com.stockradar.app.data.*;
import com.stockradar.app.security.SecurePrefs;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout results; private TextView status, market; private EditText query;
    private int pad(float dp){return (int)(dp*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle b){super.onCreate(b);
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(16),pad(18),pad(16),pad(32));scroll.addView(root);
        TextView title=new TextView(this);title.setText("Stock Radar v0.6 · 자동추천 14 + 수급TOP20 + 손바뀜");title.setTextSize(25);title.setTextColor(Color.rgb(14,53,93));root.addView(title);
        TextView sub=new TextView(this);sub.setText("삼화콘덴서 · DI · 로보티즈 상승 전 공통패턴을 기반으로 시황 + 수급 + 주봉/일봉을 자동 분석합니다.\n※ 신호강도는 투자수익을 보장하지 않습니다.");sub.setPadding(0,pad(6),0,pad(12));root.addView(sub);

        Button auto=new Button(this);auto.setText("⚡ 마샬 패턴 자동추천 TOP 14");auto.setOnClickListener(v->runMarshallTop());root.addView(auto);
        Button chartMenu=new Button(this);chartMenu.setText("📈 반등주 전용 차트 분석");chartMenu.setOnClickListener(v->startActivity(new Intent(this,ChartActivity.class)));root.addView(chartMenu);
        LinearLayout nav2=new LinearLayout(this);nav2.setOrientation(LinearLayout.HORIZONTAL);
        Button watch=new Button(this);watch.setText("⭐ 관심종목");watch.setOnClickListener(v->startActivity(new Intent(this,WatchlistActivity.class)));nav2.addView(watch,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button sector=new Button(this);sector.setText("🏭 섹터 상승흐름");sector.setOnClickListener(v->startActivity(new Intent(this,SectorFlowActivity.class)));nav2.addView(sector,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(nav2);

        LinearLayout flowNav=new LinearLayout(this);flowNav.setOrientation(LinearLayout.HORIZONTAL);
        Button flowTop=new Button(this);flowTop.setText("💰 외국인·기관 TOP20");flowTop.setOnClickListener(v->{Intent i=new Intent(this,InvestorFlowActivity.class);i.putExtra("mode","FOREIGN");startActivity(i);});flowNav.addView(flowTop,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button handoff=new Button(this);handoff.setText("🔄 손바뀜 포착");handoff.setOnClickListener(v->{Intent i=new Intent(this,InvestorFlowActivity.class);i.putExtra("mode","HANDOFF");startActivity(i);});flowNav.addView(handoff,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(flowNav);

        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button demo=new Button(this);demo.setText("패턴 데모");demo.setOnClickListener(v->showResults(DemoRepository.demoResults()));buttons.addView(demo,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button settings=new Button(this);settings.setText("API 설정");settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));buttons.addView(settings,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(buttons);

        market=new TextView(this);market.setText("자동추천을 누르면 KOSPI/KOSDAQ 시황도 함께 분석합니다.");market.setPadding(0,pad(10),0,pad(10));market.setTextColor(Color.rgb(70,70,70));root.addView(market);

        query=new EditText(this);query.setHint("종목명 또는 종목코드 (예: 한미반도체 / 042700)");root.addView(query);
        Button one=new Button(this);one.setText("이 종목 분석");one.setOnClickListener(v->runOne());root.addView(one);

        status=new TextView(this);status.setPadding(0,pad(10),0,pad(10));status.setText("자동추천 TOP 14 · 외국인/기관 수급 TOP20 · 손바뀜 · 관심종목 · 섹터흐름을 한 앱에서 확인합니다.");root.addView(status);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);setContentView(scroll);
    }

    private ScannerRepository repo(){SecurePrefs p=new SecurePrefs(this);String k=p.getSecret("appKey"),s=p.getSecret("secret");if(k.isBlank()||s.isBlank())throw new IllegalStateException("API 설정에서 App Key/Secret을 먼저 저장하세요.");return new ScannerRepository(new KiwoomClient(k,s,p.getBool("mock_v2",false)));}

    private void runOne(){String q=query.getText().toString().trim();if(q.isBlank()){toast("종목명 또는 종목코드를 입력하세요");return;}status.setText("'"+q+"' 검색 후 분석 중…");worker.submit(()->{try{ScanResult r=repo().analyzeQuery(q);runOnUiThread(()->{status.setText("분석 완료");showResults(List.of(r));});}catch(Exception e){runOnUiThread(()->error(e));}});}

    private void runMarshallTop(){
        status.setText("시황 확인 → 기관/외국인/거래량/거래대금 후보 수집 → 세 종목 공통패턴 정밀분석 중…\nTOP 14 정밀분석이라 약 40~120초 걸릴 수 있습니다.");
        results.removeAllViews();
        worker.submit(()->{try{
            ScannerRepository.ScanBundle b=repo().scanMarshallTop(14);
            runOnUiThread(()->{market.setText(b.marketSummary());status.setText("자동분석 완료 · 조건 일치 상위 "+b.results().size()+"종목");showResults(b.results());});
        }catch(Exception e){runOnUiThread(()->error(e));}});
    }

    private void error(Exception e){String m=e.getMessage()==null?e.toString():e.getMessage();status.setText("오류: "+m);toast(m);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showResults(List<ScanResult> list){results.removeAllViews();if(list.isEmpty()){TextView t=new TextView(this);t.setText("현재 조건을 강하게 만족하는 후보가 없습니다.");results.addView(t);return;}for(ScanResult r:list)addCard(r);}

    private void addCard(ScanResult r){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(pad(14),pad(12),pad(14),pad(12));GradientDrawableCompat.set(card,gradeColor(r.grade()));
        TextView h=new TextView(this);h.setText(r.name()+"  "+r.code()+"    신호강도 "+r.score()+" / "+r.grade());h.setTextSize(19);h.setTextColor(Color.WHITE);card.addView(h);
        TextView s=new TextView(this);s.setText("단계: "+r.stage()+"   종가 "+NumberFormat.getIntegerInstance().format(r.lastClose())+"\n거래량 "+String.format(Locale.KOREA,"%.1f배",r.volumeRatio())+" · 당일 큰손순매수율 "+String.format(Locale.KOREA,"%.1f%%",r.bigMoneyRatio())+" · 주봉 CMO "+String.format(Locale.KOREA,"%.1f",r.cmoWeekly()));s.setTextColor(Color.WHITE);s.setPadding(0,pad(6),0,pad(6));card.addView(s);
        for(String x:r.positives()){TextView t=new TextView(this);t.setText("✓ "+x);t.setTextColor(Color.WHITE);card.addView(t);}for(String x:r.warnings()){TextView t=new TextView(this);t.setText("⚠ "+x);t.setTextColor(Color.rgb(255,230,160));card.addView(t);}
        Button chartBtn=new Button(this);chartBtn.setText("📈 차트로 확인");chartBtn.setOnClickListener(v->{Intent i=new Intent(this,ChartActivity.class);i.putExtra("code",r.code());i.putExtra("name",r.name());startActivity(i);});card.addView(chartBtn);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,pad(10));results.addView(card,lp);}
    private int gradeColor(String g){return switch(g){case "S"->Color.rgb(177,35,35);case "A"->Color.rgb(26,92,150);case "B"->Color.rgb(34,120,91);default->Color.rgb(80,88,98);};}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private static final class GradientDrawableCompat{static void set(View v,int color){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(22);v.setBackground(d);}}
}
