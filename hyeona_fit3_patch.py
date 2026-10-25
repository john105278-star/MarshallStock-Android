from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/'app/src/main/java/kr/hyeona/lowguard/MonitorService.java'
s=p.read_text()

def rep(old,new,name):
    global s
    if old not in s:
        raise SystemExit(f'missing marker: {name}')
    s=s.replace(old,new,1)

rep('private static final int PREDICTION_ID = 1103;\n    private static final String CHANNEL_MONITOR = "monitor_v2";',
    'private static final int PREDICTION_ID = 1103;\n'
    '    private static final int FIT3_STATUS_ID = 1201;\n'
    '    private static final int FIT3_ALERT_ID = 1202;\n'
    '    private static final String CHANNEL_MONITOR = "monitor_v2";', 'ids')
rep('private static final String CHANNEL_ALARM = "alarm_v2";',
    'private static final String CHANNEL_ALARM = "alarm_v2";\n'
    '    private static final String CHANNEL_FIT3_STATUS = "fit3_status_v1";\n'
    '    private static final String CHANNEL_FIT3_ALERT = "fit3_alert_v1";', 'channels')
rep('private long lastKnownReadingDate = 0L;',
    'private long lastKnownReadingDate = 0L;\n    private long lastFit3ReadingDate = 0L;', 'field')
rep('            Severity severity = classify(sgv, delta, direction, p);',
    '            postFit3GlucoseIfNew(date, sgv, delta, direction, extras);\n'
    '            Severity severity = classify(sgv, delta, direction, p);', 'fresh reading')
rep('        nm.createNotificationChannel(alarm);',
    '''        nm.createNotificationChannel(alarm);

        NotificationChannel fitStatus = new NotificationChannel(
                CHANNEL_FIT3_STATUS, "Galaxy Fit3 혈당 표시", NotificationManager.IMPORTANCE_DEFAULT);
        fitStatus.setDescription("새 Nightscout 혈당이 들어올 때 Fit3에서 확인하기 위한 일반 알림");
        fitStatus.setSound(null, null);
        fitStatus.enableVibration(false);
        fitStatus.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(fitStatus);

        NotificationChannel fitAlert = new NotificationChannel(
                CHANNEL_FIT3_ALERT, "Galaxy Fit3 저혈 긴급 알림", NotificationManager.IMPORTANCE_HIGH);
        fitAlert.setDescription("Fit3 미러링용 저혈·예측·수신중단 경고");
        fitAlert.setSound(null, null);
        fitAlert.enableVibration(true);
        fitAlert.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 800});
        fitAlert.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(fitAlert);''', 'create channels')
rep('''        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(ALARM_ID, b.build());''',
    '''        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(ALARM_ID, b.build());
        postFit3Alert(title, body, test);''', 'alarm mirror')
rep('        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(PREDICTION_ID, b.build());',
    '        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(PREDICTION_ID, b.build());\n'
    '        postFit3Alert(title, body, test);', 'predict mirror')
marker='    private void cancelAlarmNotification() {'
methods='''    private void postFit3GlucoseIfNew(long readingDate, int sgv, double delta, String direction, NightscoutExtras x) {
        if (readingDate <= 0L || readingDate == lastFit3ReadingDate) return;
        lastFit3ReadingDate = readingDate;
        String title = "현아 혈당 " + sgv + " " + arrowFor(direction);
        StringBuilder body = new StringBuilder(String.format(Locale.KOREA, "변화 %+.1f mg/dL", delta));
        if (x != null && !Double.isNaN(x.iob)) body.append(String.format(Locale.KOREA, " · IOB %.2fU", x.iob));
        if (x != null && x.pumpBattery >= 0) body.append(" · 펌프 ").append(x.pumpBattery).append("%");
        if (x != null && !Double.isNaN(x.reservoir)) body.append(String.format(Locale.KOREA, " · 잔량 %.0fU", x.reservoir));

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1201, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_FIT3_STATUS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(body.toString())
                .setStyle(new Notification.BigTextStyle().bigText(body.toString()))
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(FIT3_STATUS_ID, n);
    }

    private void postFit3Alert(String originalTitle, String body, boolean test) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1202, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = (test ? "[Fit3 연동 테스트] " : "") + originalTitle;
        Notification n = new Notification.Builder(this, CHANNEL_FIT3_ALERT)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(FIT3_ALERT_ID, n);
    }

'''
rep(marker, methods+marker, 'methods')
p.write_text(s)

g=root/'app/build.gradle'
gs=g.read_text()
if "versionCode 5" not in gs or "versionName '1.3.1'" not in gs:
    raise SystemExit('unexpected Family version')
gs=gs.replace('versionCode 5','versionCode 6').replace("versionName '1.3.1'","versionName '1.3.2'")
g.write_text(gs)
print('Fit3 bridge patch applied')
