package com.stockradar.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.stockradar.app.api.KiwoomClient;
import com.stockradar.app.core.*;
import com.stockradar.app.security.SecurePrefs;
import java.util.*;
import java.util.concurrent.*;

public class WatchlistActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText query; private TextView status; private LinearLayout list;
    private int pad(float d){return (int)(d*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(12),pad(14),pad(12),pad(20));
        TextView title=new TextView(this);title.setText("⭐ 관심종목 · 장기 상승 신호 점수");title.setTextSize(24);title.setTextColor(Color.rgb(14,53,93));root.addView(title);
        TextView desc=new TextView(this);desc.setText("관심종목 옆 점수는 장기 이평선·주봉 모멘텀·DMI·기관/외국인 수급을 합성한 0~100 규칙 기반 점수입니다. 실제 상승확률은 아닙니다.");desc.setPadding(0,pad(4),0,pad(8));root.addView(desc);
        LinearLayout add=new LinearLayout(this);add.setOrientation(LinearLayout.HORIZONTAL);query=new EditText(this);query.setHint("종목명 / 코드");add.addView(query,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));Button btn=new Button(this);btn.setText("추가");btn.setOnClickListener(v->add());add.addView(btn);root.addView(add);
        Button refresh=new Button(this);refresh.setText("관심종목 점수 새로고침");refresh.setOnClickListener(v->refreshScores());root.addView(refresh);
        status=new TextView(this);status.setPadding(0,pad(8),0,pad(8));root.addView(status);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);renderSaved();refreshScores();
    }
    private KiwoomClient client(){SecurePrefs p=new SecurePrefs(this);String k=p.getSecret("appKey"),s=p.getSecret("secret");if(k.isBlank()||s.isBlank())throw new IllegalStateException("API 설정에서 App Key/Secret을 먼저 저장하세요.");return new KiwoomClient(k,s,p.getBool("mock_v2",false));}
    private SharedPreferences prefs(){return getSharedPreferences("stockradar_watchlist",MODE_PRIVATE);}
    private Set<String> saved(){return new LinkedHashSet<>(prefs().getStringSet("items",Set.of()));}
    private void put(Set<String> s){prefs().edit().putStringSet("items",new HashSet<>(s)).apply();}
    private void add(){String q=query.getText().toString().trim();if(q.isBlank())return;status.setText("'"+q+"' 확인 중…");worker.submit(()->{try{KiwoomClient.Candidate c=client().resolveStock(q);Set<String> s=saved();if(s.size()>=30)throw new IllegalStateException("관심종목은 최대 30개까지 저장합니다.");s.add(c.code()+"|"+c.name());put(s);runOnUiThread(()->{query.setText("");status.setText(c.name()+" 추가 완료");renderSaved();refreshScores();});}catch(Exception e){String m=e.getMessage()==null?e.toString():e.getMessage();runOnUiThread(()->status.setText("오류: "+m));}});}
    private void renderSaved(){list.removeAllViews();List<String> items=new ArrayList<>(saved());items.sort(Comparator.naturalOrder());if(items.isEmpty()){TextView t=new TextView(this);t.setText("관심종목이 없습니다. 위에서 종목명을 입력해 추가하세요.");list.addView(t);return;}for(String raw:items){String[] a=raw.split("\\|",2);addRow(a[0],a.length>1?a[1]:a[0],null);}}
    private void refreshScores(){List<String> items=new ArrayList<>(saved());if(items.isEmpty())return;status.setText("관심종목 장기 신호 점수 계산 중… 0/"+items.size());list.removeAllViews();worker.submit(()->{KiwoomClient c;try{c=client();}catch(Exception e){runOnUiThread(()->status.setText("오류: "+e.getMessage()));return;}int done=0;for(String raw:items){String[] a=raw.split("\\|",2);String code=a[0],name=a.length>1?a[1]:a[0];LongTermScoreEngine.Result r=null;String err=null;try{StockData d=c.fetchStock(code,name);r=new LongTermScoreEngine().score(d);}catch(Exception e){err=e.getMessage();}done++;final int n=done;final LongTermScoreEngine.Result rr=r;final String ee=err;runOnUiThread(()->{addRow(code,name,rr);status.setText("관심종목 장기 신호 점수 계산 중… "+n+"/"+items.size()+(ee==null?"":" · 일부 오류"));});try{Thread.sleep(280);}catch(Exception ignored){}}
            runOnUiThread(()->status.setText("점수 계산 완료 · "+items.size()+"종목"));});}
    private void addRow(String code,String name,LongTermScoreEngine.Result score){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(pad(12),pad(10),pad(12),pad(10));card.setBackgroundColor(Color.rgb(247,249,252));TextView h=new TextView(this);h.setText(name+"  "+code+(score==null?"":"     "+score.score()+"점"));h.setTextSize(18);h.setTextColor(Color.rgb(25,48,75));card.addView(h);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(score==null?0:score.score());card.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,pad(12)));TextView info=new TextView(this);if(score==null)info.setText("점수 계산 전");else{String reason=score.positives().isEmpty()?"":score.positives().get(0);info.setText("장기 상승 신호: "+score.label()+" · "+reason);}card.addView(info);LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);Button chart=new Button(this);chart.setText("차트");chart.setOnClickListener(v->{Intent i=new Intent(this,ChartActivity.class);i.putExtra("code",code);i.putExtra("name",name);startActivity(i);});buttons.addView(chart,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));Button del=new Button(this);del.setText("삭제");del.setOnClickListener(v->{Set<String>s=saved();s.removeIf(x->x.startsWith(code+"|"));put(s);renderSaved();});buttons.addView(del,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));card.addView(buttons);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,pad(8));list.addView(card,lp);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
