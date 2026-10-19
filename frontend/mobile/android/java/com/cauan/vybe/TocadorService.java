package com.cauan.vybe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Serviço em primeiro plano de reprodução de mídia.
 *
 * Sem ele, o Android suspende o app pouco depois de a tela apagar e a música para. Enquanto há
 * uma música, o serviço mostra a notificação (título, artista e os botões anterior,
 * tocar/pausar e próxima) e uma sessão de mídia (tela de bloqueio e botões do fone Bluetooth).
 *
 * Quem toca de verdade é o WebView (o elemento audio da página). Aqui só se mantém o app
 * acordado: CPU e Wi-Fi ficam ligados apenas enquanto toca uma música que pode seguir em
 * segundo plano ("fundo" = áudio comum; o player do YouTube não pode, pelas regras dele).
 */
public class TocadorService extends Service {

    private static final String TAG = "VYBE-Tocador";
    private static final String CANAL = "vybe_tocando";
    private static final int ID_NOTIFICACAO = 1077;

    static final String ACAO_ANTERIOR = "com.cauan.vybe.ANTERIOR";
    static final String ACAO_ALTERNAR = "com.cauan.vybe.ALTERNAR";
    static final String ACAO_PROXIMA = "com.cauan.vybe.PROXIMA";

    /** O que está tocando agora, como o JavaScript informou por último. */
    private static final class Estado {
        final String titulo;
        final String artista;
        final boolean tocando;
        final boolean fundo;

        Estado(String titulo, String artista, boolean tocando, boolean fundo) {
            this.titulo = titulo == null || titulo.isEmpty() ? "VYBE" : titulo;
            this.artista = artista == null ? "" : artista;
            this.tocando = tocando;
            this.fundo = fundo;
        }
    }

    private static volatile TocadorService instancia;
    private static volatile Estado estado = new Estado("VYBE", "", false, true);

    private MediaSession sessao;
    private PowerManager.WakeLock travaCpu;
    private WifiManager.WifiLock travaWifi;
    private Bitmap logo;
    private boolean emPrimeiroPlano;

    // ---------- chamado pelo plugin ----------

    static void atualizar(Context contexto, String titulo, String artista, boolean tocando, boolean fundo) {
        estado = new Estado(titulo, artista, tocando, fundo);
        TocadorService servico = instancia;
        if (servico != null) {
            servico.aplicar();
            return;
        }
        if (!tocando) {
            return; // nada tocando e o serviço parado: não precisa de notificação
        }
        Intent intencao = new Intent(contexto, TocadorService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                contexto.startForegroundService(intencao);
            } else {
                contexto.startService(intencao);
            }
        } catch (RuntimeException e) {
            // Android 12+ não deixa ligar o serviço com o app já em segundo plano. Na próxima
            // vez que a pessoa abrir o app e tocar algo, ele liga.
            Log.w(TAG, "Não foi possível ligar o serviço de música", e);
        }
    }

    static void parar(Context contexto) {
        estado = new Estado("VYBE", "", false, true);
        TocadorService servico = instancia;
        if (servico != null) {
            servico.encerrar();
        }
    }

    /** Usado pela MainActivity: deixar o WebView rodando ao sair do app? */
    static boolean tocandoEmSegundoPlano() {
        Estado atual = estado;
        return instancia != null && atual.tocando && atual.fundo;
    }

    // ---------- ciclo de vida ----------

    @Override
    public void onCreate() {
        super.onCreate();
        instancia = this;
        criarCanal();
        logo = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        sessao = new MediaSession(this, "VYBE");
        sessao.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (!estado.tocando) {
                    TocadorPlugin.avisar("alternar");
                }
            }

            @Override
            public void onPause() {
                if (estado.tocando) {
                    TocadorPlugin.avisar("alternar");
                }
            }

            @Override
            public void onStop() {
                onPause();
            }

            @Override
            public void onSkipToNext() {
                TocadorPlugin.avisar("proxima");
            }

            @Override
            public void onSkipToPrevious() {
                TocadorPlugin.avisar("anterior");
            }
        });
        sessao.setActive(true);

        PowerManager energia = (PowerManager) getSystemService(Context.POWER_SERVICE);
        travaCpu = energia.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VYBE:tocando");
        travaCpu.setReferenceCounted(false);
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            travaWifi = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VYBE:tocando");
            travaWifi.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intencao, int flags, int startId) {
        String acao = intencao == null ? null : intencao.getAction();
        if (ACAO_ANTERIOR.equals(acao)) {
            TocadorPlugin.avisar("anterior");
        } else if (ACAO_ALTERNAR.equals(acao)) {
            TocadorPlugin.avisar("alternar");
        } else if (ACAO_PROXIMA.equals(acao)) {
            TocadorPlugin.avisar("proxima");
        }
        aplicar();
        // Se o Android matar o app, não adianta recriar só o serviço: quem toca é o WebView.
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent intencaoRaiz) {
        // A pessoa fechou o app na lista de recentes: a música para junto.
        encerrar();
        super.onTaskRemoved(intencaoRaiz);
    }

    @Override
    public void onDestroy() {
        soltarTravas();
        if (sessao != null) {
            sessao.setActive(false);
            sessao.release();
            sessao = null;
        }
        if (instancia == this) {
            instancia = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intencao) {
        return null;
    }

    // ---------- notificação, sessão e travas ----------

    private void aplicar() {
        Estado atual = estado;
        Notification notificacao = montarNotificacao(atual);
        try {
            if (!emPrimeiroPlano) {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(ID_NOTIFICACAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(ID_NOTIFICACAO, notificacao);
                }
                emPrimeiroPlano = true;
            } else {
                NotificationManager gerente = getSystemService(NotificationManager.class);
                if (gerente != null) {
                    gerente.notify(ID_NOTIFICACAO, notificacao);
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Não foi possível mostrar a notificação de música", e);
            encerrar();
            return;
        }
        atualizarSessao(atual);
        if (atual.tocando && atual.fundo) {
            pegarTravas();
        } else {
            soltarTravas();
        }
    }

    private void encerrar() {
        soltarTravas();
        if (emPrimeiroPlano) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            emPrimeiroPlano = false;
        }
        stopSelf();
    }

    private Notification montarNotificacao(Estado atual) {
        Notification.Builder construtor = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CANAL)
                : new Notification.Builder(this);
        construtor
                .setSmallIcon(R.drawable.ic_vybe_notificacao)
                .setLargeIcon(logo)
                .setContentTitle(atual.titulo)
                .setContentText(atual.artista)
                .setContentIntent(abrirApp())
                .setOngoing(atual.tocando)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(botao(android.R.drawable.ic_media_previous, "Anterior", ACAO_ANTERIOR, 1))
                .addAction(atual.tocando
                        ? botao(android.R.drawable.ic_media_pause, "Pausar", ACAO_ALTERNAR, 2)
                        : botao(android.R.drawable.ic_media_play, "Tocar", ACAO_ALTERNAR, 2))
                .addAction(botao(android.R.drawable.ic_media_next, "Próxima", ACAO_PROXIMA, 3))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(sessao.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return construtor.build();
    }

    private Notification.Action botao(int icone, String rotulo, String acao, int codigo) {
        Intent intencao = new Intent(this, TocadorService.class).setAction(acao);
        PendingIntent pendente = PendingIntent.getService(this, codigo, intencao,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(this, icone), rotulo, pendente).build();
    }

    private PendingIntent abrirApp() {
        Intent intencao = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intencao == null) {
            intencao = new Intent(this, MainActivity.class);
        }
        intencao.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 0, intencao,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void atualizarSessao(Estado atual) {
        if (sessao == null) {
            return;
        }
        sessao.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, atual.titulo)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, atual.artista)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, logo)
                .build());
        sessao.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(atual.tocando ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN, atual.tocando ? 1f : 0f)
                .build());
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel canal = new NotificationChannel(CANAL, "Tocando agora",
                NotificationManager.IMPORTANCE_LOW); // sem som nem vibração
        canal.setDescription("Música que está tocando, com os botões do player");
        canal.setShowBadge(false);
        canal.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager gerente = getSystemService(NotificationManager.class);
        if (gerente != null) {
            gerente.createNotificationChannel(canal);
        }
    }

    private void pegarTravas() {
        if (travaCpu != null && !travaCpu.isHeld()) {
            travaCpu.acquire(); // solta ao pausar ou parar (soltarTravas)
        }
        if (travaWifi != null && !travaWifi.isHeld()) {
            travaWifi.acquire();
        }
    }

    private void soltarTravas() {
        if (travaCpu != null && travaCpu.isHeld()) {
            travaCpu.release();
        }
        if (travaWifi != null && travaWifi.isHeld()) {
            travaWifi.release();
        }
    }
}
