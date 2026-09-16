package com.stockradar.app;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.*;
import com.stockradar.app.api.KiwoomClient;
import com.stockradar.app.security.SecurePrefs;

public class SettingsActivity extends Activity {
    private int pad(float dp){return (int)(dp*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        SecurePrefs p=new SecurePrefs(this);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad(20),pad(24),pad(20),pad(24));

        TextView title=new TextView(this);
        title.setText("키움 REST API 설정 · v0.5");
        title.setTextSize(24);
        root.addView(title);

        TextView info=new TextView(this);
        info.setText("중요: 실전용 App Key는 반드시 실전(real) 서버와, 모의용 App Key는 모의(demo) 서버와 사용해야 합니다. 오류 8030은 이 둘이 서로 맞지 않을 때 발생합니다.");
        info.setPadding(0,pad(12),0,pad(12));
        root.addView(info);

        EditText key=new EditText(this);
        key.setHint("App Key");
        key.setText(p.getSecret("appKey"));
        root.addView(key,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText secret=new EditText(this);
        secret.setHint("App Secret");
        secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secret.setText(p.getSecret("secret"));
        root.addView(secret,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView modeTitle=new TextView(this);
        modeTitle.setText("서버 선택");
        modeTitle.setTextSize(18);
        modeTitle.setPadding(0,pad(14),0,pad(4));
        root.addView(modeTitle);

        RadioGroup group=new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        RadioButton real=new RadioButton(this);
        real.setText("실전투자 (real) · 계좌 App Key 관리에서 발급받은 키");
        RadioButton demo=new RadioButton(this);
        demo.setText("모의투자 (demo) · 모의투자 App Key 관리에서 발급받은 키");
        group.addView(real); group.addView(demo);
        boolean isMock=p.getBool("mock_v2",false);
        if(isMock) demo.setChecked(true); else real.setChecked(true);
        root.addView(group);

        TextView status=new TextView(this);
        status.setPadding(0,pad(12),0,pad(8));
        status.setText("현재 기본값: 실전투자(real)");
        root.addView(status);

        Button save=new Button(this);
        save.setText("저장");
        save.setOnClickListener(v->{
            p.putSecret("appKey",key.getText().toString().trim());
            p.putSecret("secret",secret.getText().toString().trim());
            p.putBool("mock_v2",demo.isChecked());
            Toast.makeText(this,"저장했습니다 · "+(demo.isChecked()?"모의투자(demo)":"실전투자(real)"),Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        Button test=new Button(this);
        test.setText("키움 연결 테스트");
        test.setOnClickListener(v->{
            String k=key.getText().toString().trim();
            String s=secret.getText().toString().trim();
            if(k.isBlank()||s.isBlank()){
                status.setText("App Key와 App Secret을 먼저 입력하세요.");
                return;
            }
            boolean m=demo.isChecked();
            p.putSecret("appKey",k); p.putSecret("secret",s); p.putBool("mock_v2",m);
            status.setText((m?"모의투자(demo)":"실전투자(real)")+" 서버 연결 확인 중…");
            new Thread(()->{
                try{
                    KiwoomClient c=new KiwoomClient(k,s,m);
                    c.getToken();
                    runOnUiThread(()->status.setText("✅ 연결 성공 · "+(m?"모의투자(demo)":"실전투자(real)")));
                }catch(Exception e){
                    String msg=e.getMessage()==null?e.toString():e.getMessage();
                    if(msg.contains("8030")) msg="오류 8030: App Key의 실전/모의 구분과 선택한 서버가 다릅니다.";
                    final String out=msg;
                    runOnUiThread(()->status.setText("❌ 연결 실패 · "+out));
                }
            }).start();
        });
        root.addView(test);

        Button done=new Button(this);
        done.setText("설정 닫기");
        done.setOnClickListener(v->finish());
        root.addView(done);

        setContentView(root);
    }
}
