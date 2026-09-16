package com.stockradar.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.stockradar.app.api.KiwoomClient;
import java.util.*;

/** 업종별 상승흐름 점수를 0~100 수평 막대로 표시. 50은 중립. */
public final class SectorBarView extends View {
    private List<KiwoomClient.SectorSnapshot> data=List.of();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);

    public SectorBarView(Context c){super(c);setBackgroundColor(Color.WHITE);text.setColor(Color.rgb(55,62,72));text.setTextSize(dp(12));}
    private float dp(float x){return x*getResources().getDisplayMetrics().density;}
    public void setData(List<KiwoomClient.SectorSnapshot> d){data=d==null?List.of():d;getLayoutParams().height=(int)dp(Math.max(180,52+data.size()*48));requestLayout();invalidate();}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float left=dp(10), right=getWidth()-dp(10), top=dp(20);float labelW=dp(102);float barL=left+labelW, barR=right-dp(48);float bw=Math.max(1,barR-barL);
        p.setColor(Color.rgb(235,238,242));c.drawRect(barL,top-dp(8),barR,top+data.size()*dp(48),p);
        for(int i=0;i<data.size();i++){
            KiwoomClient.SectorSnapshot s=data.get(i);float y=top+i*dp(48);
            text.setTextSize(dp(11));text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));String nm=s.name();if(nm.length()>9)nm=nm.substring(0,9);c.drawText(nm,left,y+dp(12),text);
            text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.NORMAL));text.setTextSize(dp(9));c.drawText(String.format(Locale.KOREA,"%+.2f%% · 상승 %d/하락 %d",s.changePct(),s.rising(),s.falling()),left,y+dp(27),text);
            float score=(float)s.flowScore();float x=barL+bw*score/100f;
            p.setColor(score>=60?Color.rgb(47,139,87):score>=45?Color.rgb(220,151,49):Color.rgb(193,69,69));c.drawRect(barL,y,x,y+dp(18),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(.7f));p.setColor(Color.rgb(190,195,203));c.drawRect(barL,y,barR,y+dp(18),p);p.setStyle(Paint.Style.FILL);
            text.setTextSize(dp(11));text.setColor(Color.rgb(45,50,58));c.drawText(String.valueOf((int)Math.round(score)),barR+dp(8),y+dp(14),text);
        }
        text.setColor(Color.rgb(55,62,72));
    }
}
