package com.stockradar.app;

import android.app.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.stockradar.app.api.KiwoomClient;
import com.stockradar.app.core.*;
import com.stockradar.app.security.SecurePrefs;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

public class ChartActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText query; private TextView title,status,signal; private ReboundChartView chart;
    private int pad(float dp){return (int)(dp*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(12),pad(14),pad(12),pad(12));
        title=new TextView(this);title.setText("📈 반등주 전용 차트 · v0.4");title.setTextSize(23);title.setTextColor(Color.rgb(14,53,93));root.addView(title);
        TextView desc=new TextView(this);desc.setText("캔들 + MA5/20/60/120 + 거래량 + CMO + DMI(+DI/-DI/ADX) + 기관/외국인 수급\n자동추천 후보를 눌러도 이 화면으로 연결됩니다.");desc.setPadding(0,pad(4),0,pad(8));root.addView(desc);

        LinearLayout search=new LinearLayout(this);search.setOrientation(LinearLayout.HORIZONTAL);
        query=new EditText(this);query.setHint("종목명 / 코드");search.addView(query,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button go=new Button(this);go.setText("차트");go.setOnClickListener(v->load(query.getText().toString().trim(),null));search.addView(go);root.addView(search);

        LinearLayout range=new LinearLayout(this);range.setOrientation(LinearLayout.HORIZONTAL);
        Button d60=new Button(this);d60.setText("60일");d60.setOnClickListener(v->chart.setVisibleDays(60));range.addView(d60,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button d120=new Button(this);d120.setText("120일");d120.setOnClickListener(v->chart.setVisibleDays(120));range.addView(d120,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(range);

        status=new TextView(this);status.setText("종목을 검색하거나 자동추천 카드에서 '차트 보기'를 누르세요.");status.setPadding(0,pad(6),0,pad(4));root.addView(status);
        signal=new TextView(this);signal.setTextSize(14);signal.setPadding(pad(8),pad(8),pad(8),pad(8));signal.setBackgroundColor(Color.rgb(245,247,250));root.addView(signal);

        chart=new ReboundChartView(this);root.addView(chart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,pad(760)));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);

        String code=getIntent().getStringExtra("code"), name=getIntent().getStringExtra("name");
        if(code!=null&&!code.isBlank()){query.setText(name==null||name.isBlank()?code:name);load(code,name);}
    }

    private KiwoomClient client(){SecurePrefs p=new SecurePrefs(this);String k=p.getSecret("appKey"),s=p.getSecret("secret");if(k.isBlank()||s.isBlank())throw new IllegalStateException("API 설정에서 App Key/Secret을 먼저 저장하세요.");return new KiwoomClient(k,s,p.getBool("mock_v2",false));}
    private void load(String q,String knownName){if(q==null||q.isBlank()){toast("종목명 또는 코드를 입력하세요");return;}status.setText("'"+q+"' 차트와 반등지표 계산 중…");signal.setText("");
        worker.submit(()->{try{KiwoomClient c=client();KiwoomClient.Candidate cand=knownName!=null?new KiwoomClient.Candidate(q,knownName):c.resolveStock(q);StockData d=c.fetchStock(cand.code(),cand.name());ScanResult r=new SignalEngine().score(d);runOnUiThread(()->show(d,r));}catch(Exception e){runOnUiThread(()->{String m=e.getMessage()==null?e.toString():e.getMessage();status.setText("오류: "+m);toast(m);});}});
    }
    private void show(StockData d,ScanResult r){title.setText("📈 "+d.name()+"  "+d.code()+" · 반등주 차트");status.setText("차트 로딩 완료 · 신호단계 "+r.stage()+" · 신호강도 "+r.score());
        StringBuilder sb=new StringBuilder();sb.append("현재 종가 ").append(NumberFormat.getIntegerInstance().format(r.lastClose())).append("원  ·  거래량 ").append(String.format(Locale.KOREA,"%.1f배",r.volumeRatio())).append("\n");
        sb.append("주봉 CMO ").append(String.format(Locale.KOREA,"%.1f",r.cmoWeekly())).append("  ·  +DI ").append(String.format(Locale.KOREA,"%.1f",r.plusDI())).append(" / -DI ").append(String.format(Locale.KOREA,"%.1f",r.minusDI())).append("\n");
        int n=Math.min(4,r.positives().size());for(int i=0;i<n;i++)sb.append("✓ ").append(r.positives().get(i)).append("\n");if(!r.warnings().isEmpty())sb.append("⚠ ").append(r.warnings().get(0));
        signal.setText(sb.toString().trim());chart.setData(d);chart.setVisibleDays(60);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
