package com.cauan.vybe;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

/**
 * Tela única do app: o WebView do Capacitor com o VYBE dentro.
 * Registra o plugin "Tocador" (notificação e música com a tela apagada).
 */
public class MainActivity extends BridgeActivity {

    private static final String PERMISSAO_NOTIFICACOES = "android.permission.POST_NOTIFICATIONS";
    private static final int PEDIDO_NOTIFICACOES = 1077;

    @Override
    public void onCreate(Bundle estadoSalvo) {
        registerPlugin(TocadorPlugin.class); // precisa vir antes do super.onCreate
        super.onCreate(estadoSalvo);
        pedirPermissaoDeNotificacoes();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Saindo do app com música (enviada ou do Jamendo) tocando: o WebView continua rodando,
        // para a música seguinte da fila começar quando esta terminar.
        if (TocadorService.tocandoEmSegundoPlano() && getBridge() != null) {
            WebView webView = getBridge().getWebView();
            if (webView != null) {
                webView.onResume();
                webView.resumeTimers();
            }
        }
    }

    /** Android 13+: sem essa permissão a notificação com a música não aparece. */
    private void pedirPermissaoDeNotificacoes() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(PERMISSAO_NOTIFICACOES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{PERMISSAO_NOTIFICACOES}, PEDIDO_NOTIFICACOES);
        }
    }
}
