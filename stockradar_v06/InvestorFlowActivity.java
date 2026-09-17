package com.stockradar.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.stockradar.app.api.InvestorApiClient;
import com.stockradar.app.data.InvestorFlowRepository;
import com.stockradar.app.security.SecurePrefs;
import java.util.*;
import java.util.concurrent.*;

public class InvestorFlowActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String market="001";
    private String mode="FOREIGN";
    private TextView status,selection; private LinearLayout list;
    private Button kospi,kosdaq,foreignBtn,instBtn,handoffBtn;
    private int pad(float d){return (int)(d*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle b){super.onCreate(b);
        String extra=getIntent().getStringExtra("mode"); if(extra!=null)mode=extra;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(12),pad(14),pad(12),pad(20));
        TextView title=new TextView(this);title.setText("💰 수급 TOP20 · 외국인 / 기관 / 손바뀜");title.setTextSize(24);title.setTextColor(Color.rgb(14,53,93));root.addView(title);
        TextView desc=new TextView(this);desc.setText("외국인·기관 순매수 상위에서 실제 상승 중인 종목을 다시 걸러 최근 5일 수급을 분석합니다. 손바뀜은 개인 순매도 + 외국인 순매수 + 기관 순매수를 동시에 확인합니다. 점수는 수급 신호강도이며 상승확률이 아닙니다.");desc.setPadding(0,pad(4),0,pad(8));root.addView(desc);

        LinearLayout markets=new LinearLayout(this);markets.setOrientation(LinearLayout.HORIZONTAL);
        kospi=new Button(this);kospi.setText("KOSPI");kospi.setOnClickListener(v->{market="001";paint();});markets.addView(kospi,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        kosdaq=new Button(this);kosdaq.setText("KOSDAQ");kosdaq.setOnClickListener(v->{market="101";paint();});markets.addView(kosdaq,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(markets);

        LinearLayout modes=new LinearLayout(this);modes.setOrientation(LinearLayout.HORIZONTAL);
        foreignBtn=new Button(this);foreignBtn.setText("외국인 TOP20");foreignBtn.setOnClickListener(v->{mode="FOREIGN";paint();});modes.addView(foreignBtn,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        instBtn=new Button(this);instBtn.setText("기관 TOP20");instBtn.setOnClickListener(v->{mode="INSTITUTION";paint();});modes.addView(instBtn,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        handoffBtn=new Button(this);handoffBtn.setText("손바뀜");handoffBtn.setOnClickListener(v->{mode="HANDOFF";paint();});modes.addView(handoffBtn,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(modes);

        selection=new TextView(this);selection.setTextSize(17);selection.setPadding(0,pad(8),0,pad(6));root.addView(selection);
        Button run=new Button(this);run.setText("🔍 선택 조건 분석 실행");run.setOnClickListener(v->runAnalysis());root.addView(run);
        status=new TextView(this);status.setPadding(0,pad(8),0,pad(8));root.addView(status);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);paint();
    }

    private void paint(){
        kospi.setEnabled(!"001".equals(market));kosdaq.setEnabled(!"101".equals(market));
        foreignBtn.setEnabled(!"FOREIGN".equals(mode));instBtn.setEnabled(!"INSTITUTION".equals(mode));handoffBtn.setEnabled(!"HANDOFF".equals(mode));
        selection.setText(("001".equals(market)?"KOSPI":"KOSDAQ")+" · "+label()+" 선택됨");
    }
    private String label(){return switch(mode){case "INSTITUTION"->"기관 순매수 상승종목 TOP20";case "HANDOFF"->"개인→외국인·기관 손바뀜 TOP20";default->"외국인 순매수 상승종목 TOP20";};}
    private InvestorApiClient client(){SecurePrefs p=new SecurePrefs(this);String k=p.getSecret("appKey"),s=p.getSecret("secret");if(k.isBlank()||s.isBlank())throw new IllegalStateException("API 설정에서 App Key/Secret을 먼저 저장하세요.");return new InvestorApiClient(k,s,p.getBool("mock_v2",false));}

    private void runAnalysis(){
        list.removeAllViews();status.setText(label()+" 분석 중…\n순위 조회 후 각 종목의 최근 5거래일 개인·외국인·기관 수급을 확인합니다. 20~60초 정도 걸릴 수 있습니다.");
        worker.submit(()->{try{
            InvestorFlowRepository repo=new InvestorFlowRepository(client());
            List<InvestorFlowRepository.FlowRankRow> rows="HANDOFF".equals(mode)?repo.analyzeHandoff(market,20):repo.analyzeTop(market,mode,20);
            runOnUiThread(()->{status.setText("분석 완료 · "+rows.size()+"종목");render(rows);});
        }catch(Exception e){String m=e.getMessage()==null?e.toString():e.getMessage();runOnUiThread(()->status.setText("오류: "+m));}});
    }

    private void render(List<InvestorFlowRepository.FlowRankRow> rows){
        list.removeAllViews();if(rows.isEmpty()){TextView t=new TextView(this);t.setText("현재 조건을 만족하는 종목이 없습니다.");list.addView(t);return;}
        int rank=1;for(var r:rows)addCard(rank++,r);
    }
    private void addCard(int rank,InvestorFlowRepository.FlowRankRow r){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(pad(12),pad(10),pad(12),pad(10));card.setBackgroundColor(Color.rgb(246,249,252));
        TextView h=new TextView(this);h.setText(rank+". "+r.name()+"  "+r.code()+"     수급강도 "+r.score()+"점");h.setTextSize(18);h.setTextColor(Color.rgb(19,52,84));card.addView(h);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(r.score());card.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,pad(10)));
        TextView p=new TextView(this);p.setText(String.format(Locale.KOREA,"당일 등락 %+.2f%% · 순매수 순위값 %,d\n최근5일  개인 %+,d주   외국인 %+,d주   기관 %+,d주\n외국인 %d/5일 순매수 · 기관 %d/5일 순매수\n%s",r.latestChangePct(),r.rankNetBuy(),r.personal5(),r.foreign5(),r.institution5(),r.foreignPositiveDays(),r.institutionPositiveDays(),r.note()));p.setPadding(0,pad(5),0,pad(5));card.addView(p);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button chart=new Button(this);chart.setText("📈 차트");chart.setOnClickListener(v->{Intent i=new Intent(this,ChartActivity.class);i.putExtra("code",r.code());i.putExtra("name",r.name());startActivity(i);});buttons.addView(chart,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button watch=new Button(this);watch.setText("⭐ 관심종목");watch.setOnClickListener(v->addWatch(r.code(),r.name()));buttons.addView(watch,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));card.addView(buttons);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,pad(8));list.addView(card,lp);
    }
    private void addWatch(String code,String name){SharedPreferences p=getSharedPreferences("stockradar_watchlist",MODE_PRIVATE);Set<String>s=new LinkedHashSet<>(p.getStringSet("items",Set.of()));if(s.size()>=30&&!s.stream().anyMatch(x->x.startsWith(code+"|"))){Toast.makeText(this,"관심종목은 최대 30개입니다.",Toast.LENGTH_LONG).show();return;}s.removeIf(x->x.startsWith(code+"|"));s.add(code+"|"+name);p.edit().putStringSet("items",new HashSet<>(s)).apply();Toast.makeText(this,name+" 관심종목 추가",Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
