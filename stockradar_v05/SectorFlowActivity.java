package com.stockradar.app;

import android.app.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.stockradar.app.api.KiwoomClient;
import com.stockradar.app.security.SecurePrefs;
import java.util.*;
import java.util.concurrent.*;

public class SectorFlowActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status; private SectorBarView chart;
    private int pad(float d){return (int)(d*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(12),pad(14),pad(12),pad(18));
        TextView title=new TextView(this);title.setText("🏭 섹터 상승흐름 · v0.5");title.setTextSize(24);title.setTextColor(Color.rgb(14,53,93));root.addView(title);
        TextView desc=new TextView(this);desc.setText("키움 전업종지수(ka20003)의 당일 등락률과 상승/하락 종목 비율을 합성한 0~100 흐름 점수입니다. 50 부근은 중립이며 투자수익 확률이 아닙니다.");desc.setPadding(0,pad(5),0,pad(8));root.addView(desc);
        Button refresh=new Button(this);refresh.setText("업종 흐름 새로고침");refresh.setOnClickListener(v->load());root.addView(refresh);
        status=new TextView(this);status.setPadding(0,pad(8),0,pad(8));root.addView(status);
        chart=new SectorBarView(this);root.addView(chart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,pad(300)));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);load();
    }
    private KiwoomClient client(){SecurePrefs p=new SecurePrefs(this);String k=p.getSecret("appKey"),s=p.getSecret("secret");if(k.isBlank()||s.isBlank())throw new IllegalStateException("API 설정에서 App Key/Secret을 먼저 저장하세요.");return new KiwoomClient(k,s,p.getBool("mock_v2",false));}
    private void load(){status.setText("KOSPI/KOSDAQ 전업종 상승흐름 계산 중…");worker.submit(()->{try{List<KiwoomClient.SectorSnapshot> d=client().fetchAllSectorFlow();runOnUiThread(()->{status.setText("업종 "+d.size()+"개 · 상승흐름 높은 순");chart.setData(d);});}catch(Exception e){String m=e.getMessage()==null?e.toString():e.getMessage();runOnUiThread(()->status.setText("오류: "+m));}});}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
